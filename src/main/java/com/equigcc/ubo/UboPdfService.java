package com.equigcc.ubo;

import com.equigcc.certificate.CertificatePdfService;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** PDF rendering of the UBO schedule. Arabic names are shaped and laid out right to left. */
@Service
public class UboPdfService {

    private static final Color INK = new Color(0x0f, 0x17, 0x2a);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color GREEN = new Color(0x04, 0x78, 0x57);
    private static final Color HEAD = new Color(0xec, 0xfd, 0xf5);

    private final byte[] regular;
    private final byte[] bold;

    public UboPdfService() throws IOException {
        this.regular = read("fonts/DejaVuSans.ttf");
        this.bold = read("fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    public byte[] render(UboReport r) {
        try {
            var out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Beneficial ownership schedule - " + r.organizationNameEn());
            doc.open();
            BaseFont reg = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, regular, null);
            BaseFont bld = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bold, null);
            Font h1 = new Font(bld, 16, Font.NORMAL, GREEN);
            Font h2 = new Font(bld, 11, Font.NORMAL, INK);
            Font body = new Font(reg, 8, Font.NORMAL, INK);
            Font bodyBold = new Font(bld, 8, Font.NORMAL, INK);
            Font small = new Font(reg, 7, Font.NORMAL, MUTED);

            doc.add(new Paragraph("Beneficial ownership schedule", h1));
            doc.add(new Paragraph(r.organizationNameEn(), h2));
            if (r.organizationNameAr() != null && !r.organizationNameAr().isBlank()) {
                doc.add(arabicLine(r.organizationNameAr(), h2));
            }
            doc.add(new Paragraph("Licence no. " + r.commercialLicenseNo() + "   |   As of "
                    + DateTimeFormatter.ISO_LOCAL_DATE.format(r.asOf().atZone(ZoneOffset.UTC)) + " (UTC)   |   Threshold "
                    + r.thresholdPct().stripTrailingZeros().toPlainString() + "% of economic or voting rights", small));
            doc.add(new Paragraph(" ", small));

            doc.add(new Paragraph("Beneficial owners", h2));
            doc.add(personTable(r.beneficialOwners(), true, bodyBold, body));
            doc.add(new Paragraph(" ", small));
            if (!r.belowThreshold().isEmpty()) {
                doc.add(new Paragraph("Natural persons below the threshold", h2));
                doc.add(personTable(r.belowThreshold(), false, bodyBold, body));
                doc.add(new Paragraph(" ", small));
            }
            if (!r.gaps().isEmpty()) {
                doc.add(new Paragraph("Ownership not traced to a natural person", h2));
                var t = new PdfPTable(new float[]{4, 1.2f, 1.2f, 4});
                t.setWidthPercentage(100);
                head(t, List.of("Company", "Economic %", "Voting %", "Reason"), bodyBold);
                for (var g : r.gaps()) {
                    cell(t, name(g.nameEn(), g.nameAr()), body, false, false);
                    cell(t, g.economicPct().toPlainString(), body, false, true);
                    cell(t, g.votingPct().toPlainString(), body, false, true);
                    cell(t, g.reason(), body, false, false);
                }
                doc.add(t);
                doc.add(new Paragraph(" ", small));
            }
            for (String w : r.warnings()) {
                doc.add(new Paragraph("Warning: " + w, new Font(reg, 8, Font.NORMAL, new Color(0xb4, 0x53, 0x09))));
            }
            doc.add(new Paragraph(r.disclaimer(), small));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not render UBO schedule", e);
        }
    }

    /** A right-to-left, shaped Arabic line. OpenPDF applies run direction per cell, so each line gets its own. */
    private static PdfPTable arabicLine(String text, Font f) {
        var t = new PdfPTable(1);
        t.setWidthPercentage(100);
        var c = new PdfPCell(new Phrase(text, f));
        c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBorder(0);
        c.setPadding(0);
        t.addCell(c);
        return t;
    }

    private static PdfPTable personTable(List<UboReport.Person> people, boolean ubo, Font bold, Font body) {
        var t = new PdfPTable(new float[]{3.2f, 1.2f, 1.6f, 1.4f, 1.2f, 1.7f, 5f});
        t.setWidthPercentage(100);
        head(t, List.of("Name", "Nationality", "Document no.", "Economic %", "Voting %", ubo ? "Qualifies by" : "Holding", "Route"), bold);
        if (people.isEmpty()) {
            var c = new PdfPCell(new Phrase("None", body));
            c.setColspan(7);
            c.setPadding(5);
            t.addCell(c);
        }
        for (var p : people) {
            cell(t, name(p.nameEn(), p.nameAr()), body, false, false);
            cell(t, p.nationality() == null ? "" : p.nationality(), body, false, false);
            cell(t, p.documentNo() == null ? "" : p.documentNo(), body, false, false);
            cell(t, p.economicPct().toPlainString(), body, false, true);
            cell(t, p.votingPct().toPlainString(), body, false, true);
            cell(t, ubo ? p.qualifiesBy() : (p.direct() ? "Direct" : "Indirect"), body, false, false);
            var routes = new StringBuilder();
            for (var path : p.paths()) {
                if (routes.length() > 0) {
                    routes.append('\n');
                }
                routes.append(String.join(" > ", path.chain())).append("  (").append(path.economicPct().toPlainString())
                        .append("% / ").append(path.votingPct().toPlainString()).append("%)");
            }
            cell(t, routes.toString(), body, false, false);
        }
        return t;
    }

    /** English name, then the Arabic name on its own line when present. */
    private static String name(String en, String ar) {
        return ar == null || ar.isBlank() ? en : en + "\n" + ar;
    }

    private static void head(PdfPTable t, List<String> labels, Font f) {
        for (String l : labels) {
            var c = new PdfPCell(new Phrase(l, f));
            c.setBackgroundColor(HEAD);
            c.setPadding(5);
            c.setBorderColor(new Color(0xd1, 0xd5, 0xdb));
            t.addCell(c);
        }
    }

    private static void cell(PdfPTable t, String text, Font f, boolean header, boolean right) {
        var c = new PdfPCell();
        // Lines containing Arabic are shaped and run right to left; the rest stay left to right.
        for (String line : text.split("\n")) {
            if (CertificatePdfService.containsArabic(line)) {
                c.addElement(arabicLine(line, f));
            } else {
                c.addElement(new Paragraph(line, f));
            }
        }
        c.setPadding(4);
        c.setBorderColor(new Color(0xe5, 0xe7, 0xeb));
        if (right) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        t.addCell(c);
    }
}
