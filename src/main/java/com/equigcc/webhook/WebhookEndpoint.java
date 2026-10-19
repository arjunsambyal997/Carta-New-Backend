package com.equigcc.webhook;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpoint extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 2000)
    private String url;

    /** The signing secret, encrypted at rest. */
    @Column(name = "secret_enc", nullable = false, updatable = false, length = 400)
    private String secretEnc;

    /** Comma separated wire names, e.g. {@code certificate.issued,resolution.closed}. */
    @Column(nullable = false, length = 500)
    private String events;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(UUID organizationId, String url, String secretEnc, List<String> events, String createdBy) {
        this.organizationId = organizationId;
        this.url = url;
        this.secretEnc = secretEnc;
        setEventNames(events);
        this.createdBy = createdBy;
    }

    public UUID getOrganizationId() { return organizationId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecretEnc() { return secretEnc; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }

    public List<String> eventNames() {
        return events.isBlank() ? List.of() : Arrays.asList(events.split(","));
    }

    public void setEventNames(List<String> names) {
        this.events = String.join(",", names);
    }

    public boolean subscribesTo(WebhookEvent event) {
        return eventNames().contains(event.wireName());
    }
}
