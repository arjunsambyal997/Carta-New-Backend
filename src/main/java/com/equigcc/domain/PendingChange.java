package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A register change prepared by one person and waiting for a second person to approve it. */
@Entity
@Table(name = "pending_change")
public class PendingChange extends BaseEntity {

    public enum Kind { ISSUE_CERTIFICATE, TRANSFER, BUY_BACK, EXERCISE_OPTIONS }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private Kind kind;

    /** The original request as JSON, replayed through the normal services on approval. */
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(nullable = false, length = 500, updatable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "requested_by", nullable = false, length = 128, updatable = false)
    private String requestedBy;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /** Certificate numbers created when the change was approved. */
    @Column(length = 500)
    private String result;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
