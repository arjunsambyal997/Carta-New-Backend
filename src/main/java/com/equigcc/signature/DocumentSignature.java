package com.equigcc.signature;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A signer's attestation to the fingerprint of a document at a moment in time. Never edited. */
@Entity
@Table(name = "document_signature")
public class DocumentSignature extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 16)
    private DocumentType documentType;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "document_ref", nullable = false, updatable = false, length = 100)
    private String documentRef;

    @Column(name = "document_hash", nullable = false, updatable = false, length = 64)
    private String documentHash;

    @Column(name = "signer_email", nullable = false, updatable = false)
    private String signerEmail;

    @Column(name = "signer_name", nullable = false, updatable = false, length = 200)
    private String signerName;

    @Column(name = "signer_role", nullable = false, updatable = false, length = 32)
    private String signerRole;

    @Column(name = "shareholder_id", updatable = false)
    private UUID shareholderId;

    @Column(nullable = false, updatable = false, length = 300)
    private String statement;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @Column(name = "signature_hash", nullable = false, updatable = false, length = 64)
    private String signatureHash;

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID v) { this.organizationId = v; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType v) { this.documentType = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getDocumentRef() { return documentRef; }
    public void setDocumentRef(String v) { this.documentRef = v; }
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String v) { this.documentHash = v; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String v) { this.signerEmail = v; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String v) { this.signerName = v; }
    public String getSignerRole() { return signerRole; }
    public void setSignerRole(String v) { this.signerRole = v; }
    public UUID getShareholderId() { return shareholderId; }
    public void setShareholderId(UUID v) { this.shareholderId = v; }
    public String getStatement() { return statement; }
    public void setStatement(String v) { this.statement = v; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant v) { this.signedAt = v; }
    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String v) { this.signatureHash = v; }
}
