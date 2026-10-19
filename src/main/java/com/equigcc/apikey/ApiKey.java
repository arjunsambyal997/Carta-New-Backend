package com.equigcc.apikey;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A read-only credential for one organization. Only the SHA-256 of the key is stored. */
@Entity
@Table(name = "api_key")
public class ApiKey extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    /** First characters of the key, safe to show so people can tell their keys apart. */
    @Column(name = "key_prefix", nullable = false, updatable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, updatable = false, length = 64)
    private String keyHash;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(UUID organizationId, String name, String keyPrefix, String keyHash, String createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdBy = createdBy;
    }

    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public String getCreatedBy() { return createdBy; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant v) { this.lastUsedAt = v; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant v) { this.revokedAt = v; }
    public boolean isRevoked() { return revokedAt != null; }
}
