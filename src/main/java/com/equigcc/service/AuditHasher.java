package com.equigcc.service;

import com.equigcc.domain.AuditAction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** SHA-256 chain link: each entry's hash covers its own fields and the previous entry's hash. */
final class AuditHasher {

    static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    static String hash(String prevHash, UUID organizationId, String entityName, UUID entityId,
                       AuditAction action, String detail, String performedBy, Instant occurredAt) {
        String canonical = String.join("|", "v1", prevHash, organizationId.toString(), entityName,
                entityId.toString(), action.name(), detail == null ? "" : detail, performedBy, occurredAt.toString());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
