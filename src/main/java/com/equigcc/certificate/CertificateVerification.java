package com.equigcc.certificate;


import java.time.LocalDate;

/** Public QR-scan result. Deliberately carries no shareholder PII. */
public record CertificateVerification(
        String certificateNo,
        String issuerNameEn,
        String issuerNameAr,
        String shareClassName,
        long quantity,
        LocalDate dateIssued,
        IssuanceStatus status,
        boolean authentic) {
}
