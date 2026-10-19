package com.equigcc.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The one logo an organization shows in the app; replacing it overwrites the row. */
@Entity
@Table(name = "organization_logo")
public class OrganizationLogo {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationLogo() { }

    public OrganizationLogo(UUID organizationId) { this.organizationId = organizationId; }

    public void replace(String contentType, byte[] content, String sha256, String uploadedBy, Instant at) {
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.updatedAt = at;
    }

    public UUID getOrganizationId() { return organizationId; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public byte[] getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
}
