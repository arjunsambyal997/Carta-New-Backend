package com.equigcc.captable;

import com.equigcc.history.CertificateChain;

import com.equigcc.certificate.ShareIssuance;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.common.NotFoundException;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the cap table from the live share register.
 *
 * <ul>
 *   <li><b>Issued</b>: ISSUED rows of instrument type SHARES (excluding treasury).</li>
 *   <li><b>Fully diluted</b>: issued shares plus outstanding options, warrants and convertibles,
 *       each counted as if exercised/converted 1:1 into its share class.</li>
 *   <li><b>Voting power</b>: issued shares x the class voting multiplier. Unexercised instruments
 *       and treasury shares carry no votes.</li>
 * </ul>
 */
@Service
public class CapTableService {

    private static final int PCT_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrganizationRepository organizations;
    private final ShareIssuanceRepository issuances;
    private final Clock clock;

    public CapTableService(OrganizationRepository organizations, ShareIssuanceRepository issuances, Clock clock) {
        this.organizations = organizations;
        this.issuances = issuances;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CapTable calculate(UUID organizationId) {
        if (!organizations.existsById(organizationId)) {
            throw new NotFoundException("Organization " + organizationId + " not found");
        }
        return compute(organizationId, issuances.findActiveByOrganization(organizationId));
    }

    /**
     * The cap table as it stood at the end of a given day, rebuilt from the certificate history: certificates issued
     * by then, minus those already replaced. Dates are the certificates' own dates, not when they were typed in.
     */
    @Transactional(readOnly = true)
    public CapTable calculateAsOf(UUID organizationId, java.time.LocalDate day) {
        if (!organizations.existsById(organizationId)) {
            throw new NotFoundException("Organization " + organizationId + " not found");
        }
        return computeAsOf(organizationId, issuances.findAllByOrganization(organizationId), day);
    }

    public CapTable computeAsOf(UUID organizationId, List<ShareIssuance> all, java.time.LocalDate day) {
        CapTable t = compute(organizationId, activeAsOf(all, day), i -> i.getInstrumentType() == com.equigcc.captable.InstrumentType.SHARES);
        Instant endOfDay = day.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusSeconds(1);
        return new CapTable(t.organizationId(), endOfDay, t.totalIssuedShares(), t.totalFullyDilutedShares(), t.treasuryShares(),
                t.totalVotingPower(), t.holders(), t.classes());
    }

    /** The certificates that counted at the end of {@code day}: issued by then and not yet replaced. */
    public List<ShareIssuance> activeAsOf(List<ShareIssuance> all, java.time.LocalDate day) {
        var retired = CertificateChain.retiredOn(all);
        return all.stream()
                .filter(i -> !i.getDateIssued().isAfter(day))
                .filter(i -> i.getStatus() == com.equigcc.certificate.IssuanceStatus.ISSUED || retired.get(i.getId()).isAfter(day))
                .toList();
    }

    /** Pure calculation over a set of ISSUED register rows; no database access. */
    CapTable compute(UUID organizationId, List<ShareIssuance> rows) {
        return compute(organizationId, rows, ShareIssuance::countsAsOutstanding);
    }

    /** {@code outstanding} says which rows are issued shares (as opposed to options and other instruments). */
    CapTable compute(UUID organizationId, List<ShareIssuance> rows, java.util.function.Predicate<ShareIssuance> outstanding) {
        Map<UUID, HolderAcc> byHolder = new LinkedHashMap<>();
        Map<UUID, ClassAcc> byClass = new LinkedHashMap<>();
        long treasury = 0;

        for (ShareIssuance row : rows) {
            ShareClass shareClass = row.getShareClass();
            if (shareClass.isTreasury()) {
                treasury += row.getQuantity();
                continue;
            }
            HolderAcc holder = byHolder.computeIfAbsent(row.getShareholder().getId(),
                    id -> new HolderAcc(row.getShareholder()));
            ClassAcc cls = byClass.computeIfAbsent(shareClass.getId(), id -> new ClassAcc(shareClass));

            holder.fullyDiluted += row.getQuantity();
            cls.fullyDiluted += row.getQuantity();

            if (outstanding.test(row)) {
                BigDecimal votes = shareClass.getVotingMultiplier().multiply(BigDecimal.valueOf(row.getQuantity()));
                holder.issued += row.getQuantity();
                holder.votes = holder.votes.add(votes);
                cls.issued += row.getQuantity();
                cls.votes = cls.votes.add(votes);
            }
        }

        long totalIssued = byHolder.values().stream().mapToLong(h -> h.issued).sum();
        long totalFd = byHolder.values().stream().mapToLong(h -> h.fullyDiluted).sum();
        BigDecimal totalVotes = byHolder.values().stream().map(h -> h.votes).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CapTable.HolderPosition> holders = byHolder.values().stream()
                .map(h -> new CapTable.HolderPosition(
                        h.shareholder.getId(),
                        h.shareholder.getFullNameEn(),
                        h.shareholder.getFullNameAr(),
                        h.issued,
                        h.fullyDiluted,
                        pct(BigDecimal.valueOf(h.issued), BigDecimal.valueOf(totalIssued)),
                        pct(BigDecimal.valueOf(h.fullyDiluted), BigDecimal.valueOf(totalFd)),
                        h.votes.stripTrailingZeros(),
                        pct(h.votes, totalVotes)))
                .sorted(Comparator.comparingLong(CapTable.HolderPosition::fullyDilutedShares).reversed())
                .toList();

        List<CapTable.ClassSummary> classes = byClass.values().stream()
                .map(c -> new CapTable.ClassSummary(
                        c.shareClass.getId(),
                        c.shareClass.getName(),
                        c.shareClass.getClassType(),
                        c.issued,
                        c.fullyDiluted,
                        c.votes.stripTrailingZeros(),
                        pct(c.votes, totalVotes)))
                .toList();

        return new CapTable(organizationId, clock.instant(), totalIssued, totalFd, treasury,
                totalVotes.stripTrailingZeros(), holders, classes);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(PCT_SCALE);
        }
        return part.multiply(HUNDRED).divide(whole, PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static final class HolderAcc {
        final Shareholder shareholder;
        long issued;
        long fullyDiluted;
        BigDecimal votes = BigDecimal.ZERO;

        HolderAcc(Shareholder shareholder) { this.shareholder = shareholder; }
    }

    private static final class ClassAcc {
        final ShareClass shareClass;
        long issued;
        long fullyDiluted;
        BigDecimal votes = BigDecimal.ZERO;

        ClassAcc(ShareClass shareClass) { this.shareClass = shareClass; }
    }
}
