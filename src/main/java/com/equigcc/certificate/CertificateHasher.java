package com.equigcc.certificate;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of a certificate's legally relevant fields. The hex digest goes into the
 * verification QR code; anyone can recompute it from the register to prove the certificate is untampered.
 * Bump the version prefix if the canonical form ever changes.
 */
@Component
public class CertificateHasher {

    private static final String CANONICAL_VERSION = "v1";

    public String hash(ShareIssuance issuance) {
        String canonical = String.join("|",
                CANONICAL_VERSION,
                issuance.getCertificateNo(),
                issuance.getOrganization().getId().toString(),
                issuance.getOrganization().getCommercialLicenseNo(),
                issuance.getShareholder().getId().toString(),
                issuance.getShareClass().getId().toString(),
                issuance.getInstrumentType().name(),
                Long.toString(issuance.getQuantity()),
                issuance.getPricePerShare().stripTrailingZeros().toPlainString(),
                issuance.getCurrency().name(),
                issuance.getDateIssued().toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
