package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "share_issuance",
        indexes = {
                @Index(name = "idx_issuance_org_status", columnList = "organization_id, status"),
                @Index(name = "idx_issuance_holder", columnList = "shareholder_id")
        })
public class ShareIssuance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "certificate_no", nullable = false, unique = true, length = 32)
    private String certificateNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_class_id", nullable = false)
    private ShareClass shareClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shareholder_id", nullable = false)
    private Shareholder shareholder;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 16)
    private InstrumentType instrumentType = InstrumentType.SHARES;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "price_per_share", nullable = false, precision = 19, scale = 6)
    private BigDecimal pricePerShare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "date_issued", nullable = false)
    private LocalDate dateIssued;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssuanceStatus status = IssuanceStatus.ISSUED;

    /** SHA-256 over the canonical certificate fields; printed as the QR verification payload. */
    @Column(name = "record_hash", nullable = false, length = 64, updatable = false)
    private String recordHash;

    @Column(name = "issued_by", nullable = false, length = 128, updatable = false)
    private String issuedBy;

    /** The certificate this one replaced in a succession conversion. */
    @Column(name = "predecessor_id")
    private java.util.UUID predecessorId;

    @Column(name = "succession_rule_id")
    private java.util.UUID successionRuleId;

    public boolean countsAsOutstanding() {
        return status == IssuanceStatus.ISSUED && instrumentType == InstrumentType.SHARES;
    }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public ShareClass getShareClass() { return shareClass; }
    public void setShareClass(ShareClass shareClass) { this.shareClass = shareClass; }
    public Shareholder getShareholder() { return shareholder; }
    public void setShareholder(Shareholder shareholder) { this.shareholder = shareholder; }
    public InstrumentType getInstrumentType() { return instrumentType; }
    public void setInstrumentType(InstrumentType instrumentType) { this.instrumentType = instrumentType; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public BigDecimal getPricePerShare() { return pricePerShare; }
    public void setPricePerShare(BigDecimal pricePerShare) { this.pricePerShare = pricePerShare; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public LocalDate getDateIssued() { return dateIssued; }
    public void setDateIssued(LocalDate dateIssued) { this.dateIssued = dateIssued; }
    public IssuanceStatus getStatus() { return status; }
    public void setStatus(IssuanceStatus status) { this.status = status; }
    public String getRecordHash() { return recordHash; }
    public void setRecordHash(String recordHash) { this.recordHash = recordHash; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public java.util.UUID getPredecessorId() { return predecessorId; }
    public void setPredecessorId(java.util.UUID predecessorId) { this.predecessorId = predecessorId; }
    public java.util.UUID getSuccessionRuleId() { return successionRuleId; }
    public void setSuccessionRuleId(java.util.UUID successionRuleId) { this.successionRuleId = successionRuleId; }
}
