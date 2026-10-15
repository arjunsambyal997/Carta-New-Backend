package com.equigcc.service;

import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * M&amp;A exit waterfall over the live register.
 *
 * <ol>
 *   <li>Classes with a liquidation multiple are paid {@code multiple x invested capital}, most senior
 *       rank first; classes of equal rank share pro rata when proceeds run short.</li>
 *   <li>The remainder goes pro rata by shares to common, participating preferred, and any
 *       non-participating preferred that converts.</li>
 *   <li>Non-participating preferred converts exactly when its as-converted payout beats its preference.</li>
 * </ol>
 * Only ISSUED SHARES count: options, warrants, convertibles and treasury shares are ignored.
 * Invested capital is quantity x price per share on the register, i.e. the original issue price.
 */
@Service
public class WaterfallService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrganizationRepository organizations;
    private final ShareIssuanceRepository issuances;

    public WaterfallService(OrganizationRepository organizations, ShareIssuanceRepository issuances) {
        this.organizations = organizations;
        this.issuances = issuances;
    }

    @Transactional(readOnly = true)
    public WaterfallResult calculate(UUID organizationId, BigDecimal exitValue) {
        if (exitValue == null || exitValue.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exitValue must be zero or positive");
        }
        if (!organizations.existsById(organizationId)) {
            throw new NotFoundException("Organization " + organizationId + " not found");
        }
        return compute(organizationId, exitValue, issuances.findActiveByOrganization(organizationId));
    }

    WaterfallResult compute(UUID organizationId, BigDecimal exit, List<ShareIssuance> rows) {
        Map<UUID, ClassAcc> byClass = new LinkedHashMap<>();
        for (ShareIssuance row : rows) {
            ShareClass sc = row.getShareClass();
            if (sc.isTreasury() || !row.countsAsOutstanding()) {
                continue;
            }
            ClassAcc acc = byClass.computeIfAbsent(sc.getId(), id -> new ClassAcc(sc));
            acc.shares += row.getQuantity();
            acc.invested = acc.invested.add(row.getPricePerShare().multiply(BigDecimal.valueOf(row.getQuantity())));
            acc.holderShares.merge(row.getShareholder().getId(), row.getQuantity(), Long::sum);
            acc.holderNames.putIfAbsent(row.getShareholder().getId(),
                    new String[]{row.getShareholder().getFullNameEn(), row.getShareholder().getFullNameAr()});
        }
        List<ClassAcc> classes = new ArrayList<>(byClass.values());

        // Non-participating preferred, cheapest preference per share first: the first to convert as value rises.
        List<ClassAcc> convertible = classes.stream()
                .filter(c -> c.hasPreference() && !c.shareClass.isParticipating())
                .sorted(Comparator.comparing(ClassAcc::prefPerShare))
                .toList();

        Allocation chosen = null;
        for (int k = convertible.size(); k >= 0 && chosen == null; k--) {
            Set<UUID> converted = new HashSet<>();
            for (int i = 0; i < k; i++) {
                converted.add(convertible.get(i).shareClass.getId());
            }
            Allocation a = distribute(exit, classes, converted);
            boolean stable = k == 0 || convertible.get(k - 1).prefPerShare().compareTo(a.price) <= 0;
            if (stable) {
                chosen = a;
            }
        }

        List<WaterfallResult.ClassPayout> classPayouts = new ArrayList<>();
        Map<UUID, BigDecimal> holderTotals = new LinkedHashMap<>();
        Map<UUID, String[]> holderNames = new LinkedHashMap<>();
        for (ClassAcc c : classes) {
            UUID id = c.shareClass.getId();
            BigDecimal pref = chosen.prefPaid.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal residual = chosen.sharesInResidual.contains(id)
                    ? chosen.price.multiply(BigDecimal.valueOf(c.shares)) : BigDecimal.ZERO;
            BigDecimal total = pref.add(residual);
            classPayouts.add(new WaterfallResult.ClassPayout(
                    id, c.shareClass.getName(), c.shares, money(c.invested), money(c.preference()),
                    money(pref), chosen.converted.contains(id), money(residual), money(total), pct(total, exit)));
            for (var e : c.holderShares.entrySet()) {
                BigDecimal part = total.multiply(BigDecimal.valueOf(e.getValue()))
                        .divide(BigDecimal.valueOf(c.shares), 10, RoundingMode.HALF_UP);
                holderTotals.merge(e.getKey(), part, BigDecimal::add);
                holderNames.putIfAbsent(e.getKey(), c.holderNames.get(e.getKey()));
            }
        }

        List<WaterfallResult.HolderPayout> holders = holderTotals.entrySet().stream()
                .map(e -> new WaterfallResult.HolderPayout(e.getKey(), holderNames.get(e.getKey())[0],
                        holderNames.get(e.getKey())[1], money(e.getValue()), pct(e.getValue(), exit)))
                .sorted(Comparator.comparing(WaterfallResult.HolderPayout::total).reversed())
                .toList();

        return new WaterfallResult(organizationId, exit, chosen.price.setScale(6, RoundingMode.HALF_UP),
                classPayouts, holders);
    }

    /** One pass of the waterfall for a fixed set of converting classes. */
    private Allocation distribute(BigDecimal exit, List<ClassAcc> classes, Set<UUID> converted) {
        Allocation out = new Allocation();
        out.converted.addAll(converted);

        // Preference stack by seniority, highest rank paid first.
        TreeMap<Integer, List<ClassAcc>> byRank = new TreeMap<>(Comparator.reverseOrder());
        for (ClassAcc c : classes) {
            boolean takesPreference = c.hasPreference() && !converted.contains(c.shareClass.getId());
            if (takesPreference) {
                byRank.computeIfAbsent(c.shareClass.getSeniorityRank(), r -> new ArrayList<>()).add(c);
            }
        }
        BigDecimal remaining = exit;
        for (List<ClassAcc> tier : byRank.values()) {
            BigDecimal owed = tier.stream().map(ClassAcc::preference).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = owed.min(remaining);
            for (ClassAcc c : tier) {
                BigDecimal share = owed.signum() == 0 ? BigDecimal.ZERO
                        : paid.multiply(c.preference()).divide(owed, 10, RoundingMode.HALF_UP);
                out.prefPaid.put(c.shareClass.getId(), share);
            }
            remaining = remaining.subtract(paid);
        }

        long residualShares = 0;
        for (ClassAcc c : classes) {
            boolean joins = !c.hasPreference() || c.shareClass.isParticipating() || converted.contains(c.shareClass.getId());
            if (joins) {
                out.sharesInResidual.add(c.shareClass.getId());
                residualShares += c.shares;
            }
        }
        out.price = residualShares == 0 ? BigDecimal.ZERO
                : remaining.divide(BigDecimal.valueOf(residualShares), 10, RoundingMode.HALF_UP);
        return out;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(4)
                : part.multiply(HUNDRED).divide(whole, 4, RoundingMode.HALF_UP);
    }

    private static final class Allocation {
        final Map<UUID, BigDecimal> prefPaid = new LinkedHashMap<>();
        final Set<UUID> sharesInResidual = new HashSet<>();
        final Set<UUID> converted = new HashSet<>();
        BigDecimal price = BigDecimal.ZERO;
    }

    private static final class ClassAcc {
        final ShareClass shareClass;
        long shares;
        BigDecimal invested = BigDecimal.ZERO;
        final Map<UUID, Long> holderShares = new LinkedHashMap<>();
        final Map<UUID, String[]> holderNames = new LinkedHashMap<>();

        ClassAcc(ShareClass shareClass) { this.shareClass = shareClass; }

        boolean hasPreference() { return shareClass.getLiquidationMultiple().signum() > 0; }

        BigDecimal preference() { return invested.multiply(shareClass.getLiquidationMultiple()); }

        BigDecimal prefPerShare() {
            return shares == 0 ? BigDecimal.ZERO : preference().divide(BigDecimal.valueOf(shares), 10, RoundingMode.HALF_UP);
        }
    }
}
