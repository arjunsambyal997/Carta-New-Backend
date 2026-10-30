package com.equigcc.ubo;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.document.DocumentPdfService;
import com.equigcc.organization.Jurisdiction;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A beneficial-ownership filing pack for the registry of the company's jurisdiction: the company, the owners looked
 * through holding companies, anything not traced to a person, the documents usually attached, and a declaration to sign.
 *
 * The layout is generic. It carries every field these registers ask for, but each registry publishes its own form and
 * changes it from time to time, so check the current form and copy the answers across.
 */
@Service
public class FilingPackService {

    public record Registry(String jurisdictionEn, String jurisdictionAr, String registryEn, String registryAr) {
    }

    static Registry registry(Jurisdiction j) {
        return switch (j) {
            case ADGM -> new Registry("Abu Dhabi Global Market (ADGM)", "سوق أبوظبي العالمي", "ADGM Registration Authority", "سلطة التسجيل في سوق أبوظبي العالمي");
            case DIFC -> new Registry("Dubai International Financial Centre (DIFC)", "مركز دبي المالي العالمي", "DIFC Registrar of Companies", "مسجل الشركات في مركز دبي المالي العالمي");
            case MAINLAND_UAE -> new Registry("United Arab Emirates (mainland)", "دولة الإمارات العربية المتحدة (البر الرئيسي)", "Ministry of Economy, UBO register", "وزارة الاقتصاد، سجل المستفيدين الفعليين");
            case KSA -> new Registry("Kingdom of Saudi Arabia", "المملكة العربية السعودية", "Ministry of Commerce, companies register", "وزارة التجارة، سجل الشركات");
        };
    }

    private final UboService ubo;
    private final OrganizationRepository organizations;
    private final DocumentPdfService pdf;
    private final AuditService audit;

    public FilingPackService(UboService ubo, OrganizationRepository organizations, DocumentPdfService pdf, AuditService audit) {
        this.ubo = ubo;
        this.organizations = organizations;
        this.pdf = pdf;
        this.audit = audit;
    }

    @Transactional
    public UboService.Export export(UUID orgId, BigDecimal threshold, String format, String user) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var report = ubo.report(orgId, threshold);
        var reg = registry(org.getJurisdiction());
        var pack = build(report, reg, org.getBaseCurrency().name());
        String f = format.toLowerCase();
        var out = switch (f) {
            case "pdf" -> new UboService.Export(pdf.filingPack(pack), "application/pdf", "ubo-filing-pack.pdf");
            case "csv" -> new UboService.Export(csv(pack).getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8", "ubo-filing-pack.csv");
            default -> throw new BusinessRuleException("Unsupported format: " + format);
        };
        audit.record(orgId, "FilingPack", orgId, AuditAction.CREATE,
                "generated the " + reg.registryEn() + " beneficial ownership filing pack as " + f + " (threshold " + threshold.stripTrailingZeros().toPlainString() + "%)", user);
        return out;
    }

    DocumentPdfService.FilingPack build(UboReport r, Registry reg, String currency) {
        var owners = r.beneficialOwners().stream().map(p -> new DocumentPdfService.FilingOwner(p.nameEn(), p.nameAr(), p.nationality(), p.documentNo(),
                p.economicPct(), p.votingPct(), p.qualifiesBy(), route(p))).toList();
        var gaps = r.gaps().stream().map(g -> new DocumentPdfService.FilingGap(g.nameEn(), g.nameAr(), g.economicPct(), g.votingPct(), g.reason())).toList();
        return new DocumentPdfService.FilingPack(r.organizationNameEn(), r.organizationNameAr(), r.commercialLicenseNo(), reg.jurisdictionEn(), reg.jurisdictionAr(),
                reg.registryEn(), reg.registryAr(), r.asOf().atZone(ZoneOffset.UTC).toLocalDate(), r.thresholdPct(), currency, owners, gaps, r.warnings());
    }

    private static String route(UboReport.Person p) {
        if (p.direct()) {
            return "Direct holding";
        }
        return p.paths().stream().map(path -> String.join(" > ", path.chain())).reduce((a, b) -> a + "; " + b).orElse("");
    }

    static String csv(DocumentPdfService.FilingPack p) {
        var sb = new StringBuilder("﻿");
        sb.append("section,name_en,name_ar,nationality,id_number,economic_pct,voting_pct,basis,route\r\n");
        row(sb, "COMPANY", p.orgEn(), p.orgAr(), "", p.licence(), "", "", p.registryEn(), p.jurisdictionEn());
        for (var o : p.owners()) {
            row(sb, "BENEFICIAL_OWNER", o.nameEn(), o.nameAr(), o.nationality(), o.documentNo(), pct(o.economicPct()), pct(o.votingPct()), o.qualifiesBy(), o.route());
        }
        for (var g : p.gaps()) {
            row(sb, "NOT_TRACED", g.nameEn(), g.nameAr(), "", "", pct(g.economicPct()), pct(g.votingPct()), "", g.reason());
        }
        return sb.toString();
    }

    private static String pct(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(UboCsv.cell(cells[i] == null ? "" : cells[i]));
        }
        sb.append("\r\n");
    }
}
