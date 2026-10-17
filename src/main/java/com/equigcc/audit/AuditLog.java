package com.equigcc.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** One link in an organization's hash chain. Rows are never updated; a database trigger enforces it. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "entity_name", nullable = false, updatable = false, length = 64)
    private String entityName;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditAction action;

    @Column(updatable = false, length = 1000)
    private String detail;

    @Column(name = "performed_by", nullable = false, updatable = false, length = 128)
    private String performedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String hash;

    protected AuditLog() {
    }

    public AuditLog(UUID organizationId, String entityName, UUID entityId, AuditAction action, String detail,
                    String performedBy, Instant occurredAt, String prevHash, String hash) {
        this.organizationId = organizationId;
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.detail = detail;
        this.performedBy = performedBy;
        this.occurredAt = occurredAt;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEntityName() { return entityName; }
    public UUID getEntityId() { return entityId; }
    public AuditAction getAction() { return action; }
    public String getDetail() { return detail; }
    public String getPerformedBy() { return performedBy; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
