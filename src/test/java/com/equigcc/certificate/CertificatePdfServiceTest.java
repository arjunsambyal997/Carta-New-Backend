package com.equigcc.certificate;

import com.equigcc.captable.Currency;
import com.equigcc.captable.InstrumentType;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.Shareholder;
import com.equigcc.organization.Organization;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfServiceTest {

    private final CertificatePdfService service = newService();

    private static CertificatePdfService newService() {
        try {
            return new CertificatePdfService("https://verify.example/c/");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void rendersOnePagePdfWithCertificateDetails() throws Exception {
        byte[] pdf = service.render(issuance(IssuanceStatus.ISSUED));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        var reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("SHARE CERTIFICATE", "ACME-000001", "Fatima Al Mansoori", "Class A", "1,000", "10.00 AED");
    }

    @Test
    void cancelledCertificatesCarryAWatermark() throws Exception {
        var reader = new PdfReader(service.render(issuance(IssuanceStatus.CANCELLED)));
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("CANCELLED");
    }

    @Test
    void verificationUrlCarriesTheFingerprint() {
        assertThat(service.verificationUrl(issuance(IssuanceStatus.ISSUED)))
                .isEqualTo("https://verify.example/c/ACME-000001?h=abc123");
    }

    @Test
    void detectsArabicText() {
        assertThat(CertificatePdfService.containsArabic("فاطمة")).isTrue();
        assertThat(CertificatePdfService.containsArabic("Fatima")).isFalse();
    }

    private static ShareIssuance issuance(IssuanceStatus status) {
        var org = new Organization();
        org.setId(UUID.randomUUID());
        org.setLegalNameEn("Al Noor Trading LLC");
        org.setLegalNameAr("النور للتجارة ذ.م.م");
        org.setCommercialLicenseNo("CN-123");
        var holder = new Shareholder();
        holder.setId(UUID.randomUUID());
        holder.setFullNameEn("Fatima Al Mansoori");
        holder.setFullNameAr("فاطمة المنصوري");
        var cls = new ShareClass();
        cls.setName("Class A");
        cls.setNameAr("الفئة أ");
        var i = new ShareIssuance();
        i.setOrganization(org);
        i.setShareholder(holder);
        i.setShareClass(cls);
        i.setCertificateNo("ACME-000001");
        i.setInstrumentType(InstrumentType.SHARES);
        i.setQuantity(1000);
        i.setPricePerShare(new BigDecimal("10.00"));
        i.setCurrency(Currency.AED);
        i.setDateIssued(LocalDate.of(2026, 10, 15));
        i.setRecordHash("abc123");
        i.setStatus(status);
        return i;
    }
}
