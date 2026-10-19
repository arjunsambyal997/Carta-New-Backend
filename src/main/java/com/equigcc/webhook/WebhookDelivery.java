package com.equigcc.webhook;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One attempt-tracked message to one endpoint. Created in the outbox as PENDING and sent by the dispatcher. */
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery extends BaseEntity {

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID endpointId, UUID organizationId, String eventType, String payload, Instant nextAttemptAt) {
        this.endpointId = endpointId;
        this.organizationId = organizationId;
        this.eventType = eventType;
        this.payload = payload;
        this.nextAttemptAt = nextAttemptAt;
    }

    public UUID getEndpointId() { return endpointId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant v) { this.nextAttemptAt = v; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer v) { this.responseCode = v; }
    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v == null ? null : v.substring(0, Math.min(v.length(), 500)); }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant v) { this.deliveredAt = v; }
}
