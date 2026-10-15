package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.repository.OrganizationRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Cap table as CSV (opens in Excel, Arabic safe) or PDF for lawyers and auditors. Every export is audited. */
@Service
public class CapTableExportService {

    public record Export(byte[] bytes, String contentType, String filename) {
    }

    private static final Color INK = new Color(0x0f, 0x17, 0x2a);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color GREEN = new Color(0x04, 0x78, 0x57);
    private static final Color HEAD = new Color(0xec, 0xfd, 0xf5);

    private final CapTableService capTable;
    private final OrganizationRepository organizations;
    private final AuditService audit;
    private final byte[] regular;
    private final byte[] bold;

    public CapTableExportService(CapTableService capTable, OrganizationRepository organizations, AuditService audit) throws IOException {
        this.capTable = capTable;
        this.organizations = organizations;
        this.audit = audit;
        this.regular = read("fonts/DejaVuSans.ttf");
        this.bold = read("fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Transactional
    public Export export(UUID orgId, String format, String user) {
        CapTable t = capTable.calculate(orgId);
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        String f = format == null ? "" : format.toLowerCase();
        Export out = switch (f) {
            case "csv" -> new Export(csv(t), "text/csv; charset=utf-8", "cap-table.csv");
            case "pdf" -> new Export(pdf(t, org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo()), "application/pdf", "cap-table.pdf");
            default -> throw new BusinessRuleException("Unsupported export format: " + format);
        };
        audit.record(orgId, "CapTableExport", orgId, AuditAction.CREATE, "exported the cap table as " + f, user);
        return out;
    }

    static byte[] csv(CapTable t) {
        var sb = new StringBuilder("﻿");
        row(sb, "Shareholder (EN)", "Shareholder (AR)", "Issued shares", "Issued %", "Fully diluted shares", "Fully diluted %", "Votes", "Voting %");
        for (var h : t.holders()) {
            row(sb, h.nameEn(), h.nameAr() == null ? "" : h.nameAr(), String.valueOf(h.issuedShares()), pct(h.issuedOwnershipPct()),
                    String.valueOf(h.fullyDilutedShares()), pct(h.fullyDilutedOwnershipPct()), h.votingPower().toPlainString(), pct(h.votingPct()));
        }
        row(sb, "TOTAL", "", String.valueOf(t.totalIssuedShares()), "", String.valueOf(t.totalFullyDilutedShares()), "", t.totalVotingPower().toPlainString(), "");
        row(sb, "Treasury shares (excluded)", "", String.valueOf(t.treasuryShares()), "", "", "", "", "");
        sb.append("\r\n");
        row(sb, "Share class", "Type", "Issued shares", "", "Fully diluted shares", "", "Votes", "Voting %");
        for (var c : t.classes()) {
            row(sb, c.name(), c.classType().name(), String.valueOf(c.issuedShares()), "", String.valueOf(c.fullyDilutedShares()), "",
                    c.votingPower().toPlainString(), pct(c.votingPct()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String pct(BigDecimal v) {
        return v.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(UboCsv.cell(cells[i]));
        }
        sb.append("\r\n");
    }

    private byte[] pdf(CapTable t, String nameEn, String nameAr, String licence) {
        try {
            var out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Cap table - " + nameEn);
            doc.open();
            BaseFont reg = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, regular, null);
            BaseFont bld = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bold, null);
            Font h1 = new Font(bld, 16, Font.NORMAL, GREEN);
            Font h2 = new Font(bld, 11, Font.NORMAL, INK);
            Font body = new Font(reg, 8, Font.NORMAL, INK);
            Font head = new Font(bld, 8, Font.NORMAL, INK);
            Font small = new Font(reg, 7, Font.NORMAL, MUTED);

            doc.add(new Paragraph("Cap table", h1));
            doc.add(new Paragraph(nameEn, h2));
            if (nameAr != null && !nameAr.isBlank()) {
                var line = new PdfPTable(1);
                line.setWidthPercentage(100);
                var c = new PdfPCell(new Phrase(nameAr, h2));
                c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                c.setHorizontalAlignment(Element.ALIGN_LEFT);
                c.setBorder(0);
                c.setPadding(0);
                line.addCell(c);
                doc.add(line);
            }
            doc.add(new Paragraph("Licence no. " + licence + "   |   As of " + DateTimeFormatter.ISO_LOCAL_DATE.format(t.asOf().atZone(ZoneOffset.UTC))
                    + " (UTC)   |   Treasury shares " + t.treasuryShares() + " are excluded from ownership and voting", small));
            doc.add(new Paragraph(" ", small));

            var table = new PdfPTable(new float[]{3.4f, 3.4f, 1.6f, 1.2f, 1.6f, 1.3f, 1.6f, 1.2f});
            table.setWidthPercentage(100);
            for (String h : List.of("Shareholder", "الاسم", "Issued", "Issued %", "Fully diluted", "FD %", "Votes", "Voting %")) {
                cell(table, h, head, h.equals("الاسم"), true, false);
            }
            for (var h : t.holders()) {
                cell(table, h.nameEn(), body, false, false, false);
                cell(table, h.nameAr() == null ? "" : h.nameAr(), body, true, false, false);
                cell(table, num(h.issuedShares()), body, false, false, true);
                cell(table, pct2(h.issuedOwnershipPct()), body, false, false, true);
                cell(table, num(h.fullyDilutedShares()), body, false, false, true);
                cell(table, pct2(h.fullyDilutedOwnershipPct()), body, false, false, true);
                cell(table, h.votingPower().stripTrailingZeros().toPlainString(), body, false, false, true);
                cell(table, pct2(h.votingPct()), body, false, false, true);
            }
            cell(table, "Total", head, false, false, false);
            cell(table, "", head, false, false, false);
            cell(table, num(t.totalIssuedShares()), head, false, false, true);
            cell(table, "", head, false, false, true);
            cell(table, num(t.totalFullyDilutedShares()), head, false, false, true);
            cell(table, "", head, false, false, true);
            cell(table, t.totalVotingPower().stripTrailingZeros().toPlainString(), head, false, false, true);
            cell(table, "", head, false, false, true);
            doc.add(table);

            doc.add(new Paragraph(" ", small));
            doc.add(new Paragraph("Share classes", h2));
            var ct = new PdfPTable(new float[]{4f, 3f, 2f, 2f, 1.5f});
            ct.setWidthPercentage(70);
            ct.setHorizontalAlignment(Element.ALIGN_LEFT);
            for (String h : List.of("Class", "Type", "Issued", "Fully diluted", "Voting %")) {
                cell(ct, h, head, false, true, false);
            }
            for (var c : t.classes()) {
                cell(ct, c.name(), body, false, false, false);
                cell(ct, c.classType().name().toLowerCase().replace('_', ' '), body, false, false, false);
                cell(ct, num(c.issuedShares()), body, false, false, true);
                cell(ct, num(c.fullyDilutedShares()), body, false, false, true);
                cell(ct, pct2(c.votingPct()), body, false, false, true);
            }
            doc.add(ct);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render the cap table PDF", e);
        }
    }

    private static String num(long n) {
        return String.format("%,d", n);
    }

    private static String pct2(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static void cell(PdfPTable t, String text, Font f, boolean arabic, boolean header, boolean right) {
        var c = new PdfPCell(new Phrase(text, f));
        c.setPadding(4);
        c.setBorderColor(new Color(0xe2, 0xe8, 0xf0));
        if (header) {
            c.setBackgroundColor(HEAD);
        }
        if (arabic) {
            c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        } else if (right) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        t.addCell(c);
    }
}
