package com.equigcc.ubo;

import com.equigcc.document.DocumentPdfService;
import com.equigcc.organization.Jurisdiction;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FilingPackServiceTest {

    private final FilingPackService service = new FilingPackService(null, null, null, null);

    private static UboReport report(String ownerName) {
        var fatima = new UboReport.Person(UUID.randomUUID(), ownerName, "فاطمة المنصوري", "AE", "P1234567", new BigDecimal("52.5"), new BigDecimal("61"),
                "BOTH", false, List.of(new UboReport.Path(List.of("Acme", "Al Mansoori Holding", ownerName), new BigDecimal("52.5"), new BigDecimal("61"))));
        var gap = new UboReport.Gap(UUID.randomUUID(), "Falcon Growth Fund", null, new BigDecimal("13.2"), new BigDecimal("7.7"), "Owners not recorded");
        return new UboReport(UUID.randomUUID(), "Acme Holdings", "أكمي القابضة", "LIC-77", Instant.parse("2026-10-16T09:00:00Z"), new BigDecimal("25"),
                "basis", List.of(fatima), List.of(), List.of(gap), List.of("One holder has no recorded owners"), "disclaimer");
    }

    @Test
    void everyJurisdictionNamesItsOwnRegistry() {
        assertThat(FilingPackService.registry(Jurisdiction.ADGM).registryEn()).contains("ADGM");
        assertThat(FilingPackService.registry(Jurisdiction.DIFC).registryEn()).contains("DIFC");
        assertThat(FilingPackService.registry(Jurisdiction.MAINLAND_UAE).registryEn()).contains("Ministry of Economy");
        assertThat(FilingPackService.registry(Jurisdiction.KSA).registryEn()).contains("Ministry of Commerce");
        for (Jurisdiction j : Jurisdiction.values()) {
            assertThat(FilingPackService.registry(j).registryAr()).isNotBlank();
        }
    }

    @Test
    void thePackCarriesOwnersTheirRouteAndTheUntracedHolders() {
        var pack = service.build(report("Fatima Al Mansoori"), FilingPackService.registry(Jurisdiction.ADGM), "AED");
        assertThat(pack.owners()).hasSize(1);
        assertThat(pack.owners().getFirst().route()).isEqualTo("Acme > Al Mansoori Holding > Fatima Al Mansoori");
        assertThat(pack.gaps()).extracting(DocumentPdfService.FilingGap::nameEn).containsExactly("Falcon Growth Fund");
        assertThat(pack.registryEn()).isEqualTo("ADGM Registration Authority");
    }

    @Test
    void theCsvHasOneRowPerOwnerAndGapAndStopsSpreadsheetFormulas() {
        var pack = service.build(report("=HYPERLINK(\"http://evil\")"), FilingPackService.registry(Jurisdiction.DIFC), "AED");
        String csv = FilingPackService.csv(pack);
        var lines = csv.strip().split("\r\n");
        assertThat(lines).hasSize(4); // header, company, one owner, one gap
        assertThat(lines[0]).contains("section,name_en");
        assertThat(lines[1]).startsWith("COMPANY,");
        assertThat(lines[2]).startsWith("BENEFICIAL_OWNER,\"'=HYPERLINK(");
        assertThat(lines[3]).startsWith("NOT_TRACED,");
    }

    @Test
    void thePdfCarriesTheDeclarationAndTheRegistry() throws Exception {
        var pdf = new DocumentPdfService().filingPack(service.build(report("Fatima Al Mansoori"), FilingPackService.registry(Jurisdiction.ADGM), "AED"));
        var reader = new PdfReader(pdf);
        var extractor = new PdfTextExtractor(reader);
        var text = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) {
            text.append(extractor.getTextFromPage(p));
        }
        assertThat(text.toString()).contains("Beneficial ownership filing pack", "ADGM Registration Authority", "Acme Holdings", "LIC-77",
                "Fatima Al Mansoori", "P1234567", "Falcon Growth Fund", "Declaration", "Authorised signatory");
    }
}
