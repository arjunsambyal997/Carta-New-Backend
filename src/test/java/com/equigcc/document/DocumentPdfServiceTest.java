package com.equigcc.document;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentPdfServiceTest {

    private final DocumentPdfService service = newService();

    private static DocumentPdfService newService() {
        try {
            return new DocumentPdfService();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        var reader = new PdfReader(pdf);
        var extractor = new PdfTextExtractor(reader);
        var all = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) {
            all.append(extractor.getTextFromPage(p)).append('\n');
        }
        return all.toString();
    }

    @Test
    void minutesShowTheResultTheTallyAndEveryVote() throws Exception {
        var minutes = new DocumentPdfService.Minutes("Acme Holdings", "أكمي القابضة", "LIC-1", "Approve budget", "اعتماد الميزانية", "Annual budget for 2027.",
                "ORDINARY", new BigDecimal("50"), Instant.parse("2026-10-20T12:00:00Z"), Instant.parse("2026-10-20T12:05:00Z"), "PASSED",
                new BigDecimal("700"), new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("90.00"), true, new BigDecimal("1000"),
                List.of(new DocumentPdfService.VoteLine("Fatima Al Mansoori", "فاطمة المنصوري", "FOR", new BigDecimal("700"), Instant.parse("2026-10-18T09:00:00Z")),
                        new DocumentPdfService.VoteLine("Omar Haddad", null, "AGAINST", new BigDecimal("200"), Instant.parse("2026-10-18T10:00:00Z"))),
                List.of(new DocumentPdfService.SignLine("Sara Secretary", "CORPORATE_SECRETARY", "I have read this document and confirm it.", Instant.parse("2026-10-21T08:00:00Z"), true)));
        String text = text(service.minutes(minutes));
        assertThat(text).contains("Minutes of board resolution", "Acme Holdings", "Approve budget", "Passed", "Fatima Al Mansoori", "Omar Haddad",
                "700", "200", "Sara Secretary", "90");
    }

    @Test
    void transferFormNamesBothPartiesAndBothCertificates() throws Exception {
        var form = new DocumentPdfService.TransferForm("Acme Holdings", null, "LIC-1",
                new DocumentPdfService.Party("Fatima Al Mansoori", "فاطمة المنصوري", "Passport / جواز السفر: P123 (AE)"),
                new DocumentPdfService.Party("Omar Haddad", null, null), "Ordinary", 400, "AED", new BigDecimal("12.5"), new BigDecimal("5000"),
                LocalDate.parse("2026-06-01"), "ACME-000001", "ACME-000003");
        String text = text(service.transferForm(form));
        assertThat(text).contains("Share transfer instrument", "Fatima Al Mansoori", "Omar Haddad", "ACME-000001", "ACME-000003", "400", "AED 5000", "2026-06-01");
    }

    @Test
    void statementListsHoldingsAndOwnership() throws Exception {
        var s = new DocumentPdfService.Statement("Acme Holdings", "أكمي القابضة", "LIC-1", new DocumentPdfService.Party("Fatima Al Mansoori", null, null),
                LocalDate.parse("2026-10-16"),
                List.of(new DocumentPdfService.Holding("ACME-000002", "Ordinary", "Shares", 600, LocalDate.parse("2025-06-01"), "AED", new BigDecimal("10"))),
                600, 650, new BigDecimal("60.0000"), new BigDecimal("55.5556"), new BigDecimal("600"), new BigDecimal("60.0000"));
        String text = text(service.statement(s));
        assertThat(text).contains("Shareholder statement", "Fatima Al Mansoori", "ACME-000002", "600", "60", "2026-10-16");
    }

    @Test
    void anEmptyStatementStillRenders() throws Exception {
        var s = new DocumentPdfService.Statement("Acme", null, "LIC-1", new DocumentPdfService.Party("Nobody", null, null), LocalDate.parse("2020-01-01"),
                List.of(), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(text(service.statement(s))).contains("Shareholder statement", "None");
    }

    @Test
    void optionStatementShowsEachGrantsVesting() throws Exception {
        var g = new DocumentPdfService.OptionGrant("ACME-000009", "Ordinary", "AED", new BigDecimal("2.5"), 4800, 1200, 200, 1000, 3600,
                LocalDate.parse("2025-01-01"), 12, 48, LocalDate.parse("2026-01-01"), LocalDate.parse("2029-01-01"), LocalDate.parse("2026-11-01"));
        String text = text(service.optionStatement(new DocumentPdfService.OptionStatement("Acme", null, "LIC-1",
                new DocumentPdfService.Party("Omar Haddad", null, null), LocalDate.parse("2026-10-16"), List.of(g))));
        assertThat(text).contains("Option statement", "Omar Haddad", "ACME-000009", "4,800", "3,600", "2029-01-01");
    }
}
