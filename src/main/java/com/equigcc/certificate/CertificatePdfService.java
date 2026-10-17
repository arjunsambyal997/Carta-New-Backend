package com.equigcc.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfGState;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a bilingual (English / Arabic) share certificate as a PDF with a QR code that points at the public
 * verification URL, which carries the record fingerprint. The PDF is a presentation of the register entry; the
 * register itself remains the source of truth.
 */
@Service
public class CertificatePdfService {

    private static final Color INK = new Color(0x0f, 0x17, 0x2a);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color GREEN = new Color(0x04, 0x78, 0x57);

    private final String verificationBaseUrl;
    private final byte[] regular;
    private final byte[] bold;

    public CertificatePdfService(@Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl)
            throws IOException {
        this.verificationBaseUrl = verificationBaseUrl;
        this.regular = read("fonts/DejaVuSans.ttf");
        this.bold = read("fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    public String verificationUrl(ShareIssuance i) {
        return verificationBaseUrl + i.getCertificateNo() + "?h=" + i.getRecordHash();
    }

    public byte[] render(ShareIssuance i) {
        try {
            var out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            doc.addTitle("Share certificate " + i.getCertificateNo());
            doc.open();
            draw(w.getDirectContent(), doc.getPageSize(), i);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not render certificate " + i.getCertificateNo(), e);
        }
    }

    private void draw(PdfContentByte cb, Rectangle page, ShareIssuance i) throws DocumentException, IOException {
        BaseFont reg = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, regular, null);
        BaseFont bld = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bold, null);
        float w = page.getWidth();
        float h = page.getHeight();

        cb.setColorStroke(GREEN);
        cb.setLineWidth(3);
        cb.rectangle(24, 24, w - 48, h - 48);
        cb.stroke();
        cb.setLineWidth(0.8f);
        cb.rectangle(32, 32, w - 64, h - 64);
        cb.stroke();

        var org = i.getOrganization();
        var holder = i.getShareholder();
        var cls = i.getShareClass();

        text(cb, new Font(bld, 22, Font.NORMAL, GREEN), "SHARE CERTIFICATE", w / 2, h - 80, Element.ALIGN_CENTER, false);
        text(cb, new Font(bld, 22, Font.NORMAL, GREEN), "شهادة أسهم", w / 2, h - 108, Element.ALIGN_CENTER, true);

        text(cb, new Font(bld, 15, Font.NORMAL, INK), org.getLegalNameEn(), w / 2, h - 142, Element.ALIGN_CENTER, false);
        if (org.getLegalNameAr() != null && !org.getLegalNameAr().isBlank()) {
            text(cb, new Font(bld, 15, Font.NORMAL, INK), org.getLegalNameAr(), w / 2, h - 164, Element.ALIGN_CENTER, true);
        }
        Font meta = new Font(reg, 9, Font.NORMAL, MUTED);
        text(cb, meta, "Licence no. " + org.getCommercialLicenseNo(), w / 2 - 8, h - 182, Element.ALIGN_RIGHT, false);
        text(cb, meta, "رقم الرخصة " + org.getCommercialLicenseNo(), w / 2 + 8, h - 182, Element.ALIGN_LEFT, true);

        float left = 64;
        float right = w - 64;
        float y = h - 220;
        Font label = new Font(reg, 9, Font.NORMAL, MUTED);
        Font value = new Font(bld, 12, Font.NORMAL, INK);
        DecimalFormat qty = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        DecimalFormat money = new DecimalFormat("#,##0.00####", DecimalFormatSymbols.getInstance(Locale.US));
        BigDecimal total = i.getPricePerShare().multiply(BigDecimal.valueOf(i.getQuantity()));

        String[][] rows = {
                {"Certificate no.", "رقم الشهادة", i.getCertificateNo(), null},
                {"Registered holder", "المساهم المسجل", holder.getFullNameEn(), holder.getFullNameAr()},
                {"Share class", "فئة الأسهم", cls.getName(), cls.getNameAr()},
                {"Number of shares", "عدد الأسهم", qty.format(i.getQuantity()), null},
                {"Issue price per share", "سعر الإصدار للسهم", money.format(i.getPricePerShare()) + " " + i.getCurrency(), null},
                {"Total consideration", "إجمالي المقابل", money.format(total) + " " + i.getCurrency(), null},
                {"Date of issue", "تاريخ الإصدار", i.getDateIssued().format(DateTimeFormatter.ISO_LOCAL_DATE), null},
        };
        for (String[] r : rows) {
            text(cb, label, r[0], left, y + 14, Element.ALIGN_LEFT, false);
            text(cb, label, r[1], right, y + 14, Element.ALIGN_RIGHT, true);
            text(cb, value, r[2], left, y, Element.ALIGN_LEFT, false);
            if (r[3] != null && !r[3].isBlank()) {
                text(cb, value, r[3], right, y, Element.ALIGN_RIGHT, true);
            }
            cb.setColorStroke(new Color(0xe2, 0xe8, 0xf0));
            cb.setLineWidth(0.5f);
            cb.moveTo(left, y - 8);
            cb.lineTo(right, y - 8);
            cb.stroke();
            y -= 36;
        }

        // QR + fingerprint
        float qrSize = 90;
        drawQr(cb, verificationUrl(i), right - qrSize, 52, qrSize);
        Font small = new Font(reg, 7, Font.NORMAL, MUTED);
        text(cb, small, "Scan to verify", right - qrSize, 46, Element.ALIGN_LEFT, false);
        text(cb, small, "امسح للتحقق", right, 46, Element.ALIGN_RIGHT, true);
        text(cb, small, "Record fingerprint (SHA-256)", left, 100, Element.ALIGN_LEFT, false);
        text(cb, new Font(reg, 7, Font.NORMAL, INK), i.getRecordHash(), left, 90, Element.ALIGN_LEFT, false);
        text(cb, small, "Authorised signatory", left, 52, Element.ALIGN_LEFT, false);
        text(cb, small, "المفوّض بالتوقيع", left + 220, 56, Element.ALIGN_RIGHT, true);
        cb.setColorStroke(MUTED);
        cb.moveTo(left, 66);
        cb.lineTo(left + 220, 66);
        cb.stroke();
        text(cb, small, "Issued by the digital share register. Verify current status via the QR code.",
                left, 40, Element.ALIGN_LEFT, false);
        text(cb, small, "صادرة عن سجل الأسهم الرقمي؛ تحقق من الحالة الحالية عبر الرمز.", right - 112, 40, Element.ALIGN_RIGHT, true);

        if (i.getStatus() != IssuanceStatus.ISSUED) {
            watermark(cb, bld, i.getStatus().name(), w, h);
        }
    }

    private static void watermark(PdfContentByte cb, BaseFont f, String word, float w, float h) {
        cb.saveState();
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.15f);
        cb.setGState(gs);
        cb.beginText();
        cb.setFontAndSize(f, 96);
        cb.setColorFill(new Color(0xb9, 0x1c, 0x1c));
        cb.showTextAligned(Element.ALIGN_CENTER, word, w / 2, h / 2 - 30, 30);
        cb.endText();
        cb.restoreState();
    }

    private static void text(PdfContentByte cb, Font font, String s, float x, float y, int align, boolean rtl) {
        ColumnText.showTextAligned(cb, align, new Phrase(s, font), x, y, 0,
                rtl || containsArabic(s) ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR, 0);
    }

    public static boolean containsArabic(String s) {
        return s.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC);
    }

    /** Draws the QR as vector squares so it stays sharp at any print size. */
    static void drawQr(PdfContentByte cb, String content, float x, float y, float size) throws IOException {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    java.util.Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            float cell = size / m.getWidth();
            cb.saveState();
            cb.setColorFill(Color.BLACK);
            for (int r = 0; r < m.getHeight(); r++) {
                for (int c = 0; c < m.getWidth(); c++) {
                    if (m.get(c, r)) {
                        cb.rectangle(x + c * cell, y + size - (r + 1) * cell, cell + 0.15f, cell + 0.15f);
                    }
                }
            }
            cb.fill();
            cb.restoreState();
        } catch (com.google.zxing.WriterException e) {
            throw new IOException(e);
        }
    }
}
