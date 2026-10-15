package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.Shareholder;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.OwnershipLinkRepository;
import com.equigcc.repository.ShareholderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the ultimate beneficial owner report and its exports. */
@Service
public class UboService {

    public static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(25);

    static final String DISCLAIMER = "Computed from the register and the ownership links recorded in it. Economic share uses "
            + "fully diluted holdings; voting share uses weighted voting power. A person qualifies at or above the threshold "
            + "of either measure. This is a working schedule: the official Ministry of Economy / Ministry of Commerce filing "
            + "formats are not reproduced here and the output must be checked by the company's legal counsel before filing.";

    private final CapTableService capTable;
    private final OrganizationRepository organizations;
    private final ShareholderRepository shareholders;
    private final OwnershipLinkRepository links;
    private final UboPdfService pdf;
    private final AuditService audit;
    private final Clock clock;

    public UboService(CapTableService capTable, OrganizationRepository organizations, ShareholderRepository shareholders,
                      OwnershipLinkRepository links, UboPdfService pdf, AuditService audit, Clock clock) {
        this.capTable = capTable;
        this.organizations = organizations;
        this.shareholders = shareholders;
        this.links = links;
        this.pdf = pdf;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UboReport report(UUID orgId, BigDecimal thresholdPct) {
        if (thresholdPct == null || thresholdPct.signum() <= 0 || thresholdPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("Threshold must be above 0 and at most 100");
        }
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        CapTable table = capTable.calculate(orgId);
        Map<UUID, UboCalculator.Party> parties = new HashMap<>();
        for (Shareholder s : shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId)) {
            String doc = firstNonBlank(s.getPassportNo(), s.getNationalId(), s.getRegistrationNo());
            parties.put(s.getId(), new UboCalculator.Party(s.getId(), s.getHolderType(), s.getFullNameEn(),
                    s.getFullNameAr(), s.getNationality(), doc));
        }
        List<UboCalculator.Position> positions = table.holders().stream()
                .map(h -> new UboCalculator.Position(h.shareholderId(), h.fullyDilutedOwnershipPct(), h.votingPct())).toList();
        List<UboCalculator.Link> ownership = links.findByOrganization(orgId).stream()
                .map(l -> new UboCalculator.Link(l.getOwner().getId(), l.getOwned().getId(), l.getEconomicPct(), l.getVotingPct()))
                .toList();
        var result = UboCalculator.calculate(parties, positions, ownership, thresholdPct, org.getLegalNameEn());
        return new UboReport(orgId, org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo(), clock.instant(),
                thresholdPct, "Economic: fully diluted ownership. Voting: weighted voting power.",
                result.beneficialOwners(), result.belowThreshold(), result.gaps(), result.warnings(), DISCLAIMER);
    }

    public record Export(byte[] bytes, String contentType, String filename) {
    }

    /** Renders the report as CSV or PDF and records the export in the audit trail. */
    @Transactional
    public Export export(UUID orgId, BigDecimal thresholdPct, String format, String user) {
        UboReport r = report(orgId, thresholdPct);
        String f = format.toLowerCase();
        Export out = switch (f) {
            case "csv" -> new Export(UboCsv.render(r), "text/csv; charset=utf-8", "ubo-register.csv");
            case "pdf" -> new Export(pdf.render(r), "application/pdf", "ubo-register.pdf");
            default -> throw new BusinessRuleException("Unsupported export format: " + format);
        };
        audit.record(orgId, "UboExport", orgId, AuditAction.CREATE,
                "exported UBO schedule as " + f + " (threshold " + thresholdPct.stripTrailingZeros().toPlainString() + "%)", user);
        return out;
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
