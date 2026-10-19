package com.equigcc.ubo;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UboExportTest {

    private static UboReport report() {
        var path = new UboReport.Path(List.of("Al Noor LLC", "Holdco", "=cmd|' /C calc'!A0"), new BigDecimal("30.0000"), new BigDecimal("30.0000"));
        var evil = new UboReport.Person(UUID.randomUUID(), "=HYPERLINK(\"http://x\")", "فاطمة المنصوري", "AE", "P1",
                new BigDecimal("30.0000"), new BigDecimal("30.0000"), "BOTH", false, List.of(path));
        var low = new UboReport.Person(UUID.randomUUID(), "Sami, Jr.", null, "SA", null,
                new BigDecimal("5.0000"), new BigDecimal("5.0000"), null, true, List.of());
        var gap = new UboReport.Gap(UUID.randomUUID(), "Orphan Ltd", null, new BigDecimal("10"), new BigDecimal("10"), "No owners recorded for this company");
        return new UboReport(UUID.randomUUID(), "Al Noor LLC", "النور ذ.م.م", "CN-1", Instant.parse("2026-10-15T00:00:00Z"),
                new BigDecimal("25"), "basis", List.of(evil), List.of(low), List.of(gap), List.of("Circular ownership: x"), "Disclaimer text");
    }

    @Test
    void csvNeutralisesFormulasQuotesFieldsAndKeepsArabic() {
        String csv = new String(UboCsv.render(report()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("'=HYPERLINK(\"\"http://x\"\")");
        assertThat(csv).contains("\"Sami, Jr.\"");
        assertThat(csv).contains("فاطمة المنصوري");
        assertThat(csv).contains("UNRESOLVED").contains("WARNING").contains("NOTE");
    }

    @Test
    void cellEscaping() {
        assertThat(UboCsv.cell("plain")).isEqualTo("plain");
        assertThat(UboCsv.cell("+1")).isEqualTo("'+1");
        assertThat(UboCsv.cell("a\"b")).isEqualTo("\"a\"\"b\"");
    }

    @Test
    void pdfRendersAndCarriesTheDisclaimer() throws IOException {
        byte[] pdf = new UboPdfService().render(report());
        var text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Beneficial ownership schedule", "Al Noor LLC", "Disclaimer text", "Orphan Ltd");
    }
}
