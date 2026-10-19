package com.equigcc.apikey;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates, lists and revokes API keys, and resolves a presented key to its record. A key looks like
 * {@code eqk_<8 chars>_<40 chars>}; the whole string is hashed, so a database leak does not leak working keys.
 */
@Service
public class ApiKeyService {

    static final int MAX_ACTIVE_KEYS = 10;
    private static final String ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration TOUCH_EVERY = Duration.ofMinutes(5);

    /** The record plus the one and only time the plaintext key is available. */
    public record Created(ApiKey key, String plaintext) {
    }

    private final ApiKeyRepository repository;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Created create(UUID organizationId, String name, String actor) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new BusinessRuleException("Give the key a name of up to 120 characters");
        }
        if (repository.countByOrganizationIdAndRevokedAtIsNull(organizationId) >= MAX_ACTIVE_KEYS) {
            throw new BusinessRuleException("An organization can have at most " + MAX_ACTIVE_KEYS + " active API keys; revoke one first");
        }
        String prefix = randomString(8);
        String plaintext = "eqk_" + prefix + "_" + randomString(40);
        ApiKey saved = repository.save(new ApiKey(organizationId, trimmed, "eqk_" + prefix, sha256(plaintext), actor));
        audit.record(organizationId, "ApiKey", saved.getId(), AuditAction.CREATE, "API key created: " + trimmed + " (" + saved.getKeyPrefix() + ")", actor);
        return new Created(saved, plaintext);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(UUID organizationId) {
        return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional
    public void revoke(UUID organizationId, UUID id, String actor) {
        ApiKey key = repository.findById(id).filter(k -> k.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException("API key not found"));
        if (key.isRevoked()) {
            return;
        }
        key.setRevokedAt(clock.instant());
        audit.record(organizationId, "ApiKey", id, AuditAction.DELETE, "API key revoked: " + key.getName() + " (" + key.getKeyPrefix() + ")", actor);
    }

    /** Returns the active key for a presented secret, and notes when it was last used (at most every few minutes). */
    @Transactional
    public Optional<ApiKey> authenticate(String presented) {
        if (presented == null || presented.length() < 20 || presented.length() > 100 || !presented.startsWith("eqk_")) {
            return Optional.empty();
        }
        Optional<ApiKey> found = repository.findByKeyHash(sha256(presented)).filter(k -> !k.isRevoked());
        found.ifPresent(k -> {
            Instant now = clock.instant();
            if (k.getLastUsedAt() == null || Duration.between(k.getLastUsedAt(), now).compareTo(TOUCH_EVERY) > 0) {
                k.setLastUsedAt(now);
            }
        });
        return found;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
