package com.equigcc.succession;

import com.equigcc.captable.ShareClass;
import com.equigcc.captable.Shareholder;
import com.equigcc.common.BaseEntity;
import com.equigcc.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A pre-agreed conversion of shares from one class to another (and optionally to a beneficiary) when a trigger
 * event occurs, e.g. founder shares converting to ordinary shares for an heir. Executing it issues new certificates.
 */
@Entity
@Table(name = "succession_rule")
public class SuccessionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 32)
    private SuccessionTrigger triggerEvent;

    /** Only this holder's shares are converted; null applies the rule to every holder of the source class. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Shareholder subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_class_id", nullable = false)
    private ShareClass fromClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_class_id", nullable = false)
    private ShareClass toClass;

    /** New shares received per {@code ratioDenominator} shares given up. */
    @Column(name = "ratio_numerator", nullable = false)
    private long ratioNumerator = 1;

    @Column(name = "ratio_denominator", nullable = false)
    private long ratioDenominator = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private Shareholder beneficiary;

    @Column(name = "legal_reference", nullable = false, length = 500)
    private String legalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SuccessionStatus status = SuccessionStatus.DRAFT;

    @Column(name = "resolution_id")
    private UUID resolutionId;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "executed_by", length = 128)
    private String executedBy;

    @Column(name = "created_by", nullable = false, length = 128, updatable = false)
    private String createdBy;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SuccessionTrigger getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(SuccessionTrigger triggerEvent) { this.triggerEvent = triggerEvent; }
    public Shareholder getSubject() { return subject; }
    public void setSubject(Shareholder subject) { this.subject = subject; }
    public ShareClass getFromClass() { return fromClass; }
    public void setFromClass(ShareClass fromClass) { this.fromClass = fromClass; }
    public ShareClass getToClass() { return toClass; }
    public void setToClass(ShareClass toClass) { this.toClass = toClass; }
    public long getRatioNumerator() { return ratioNumerator; }
    public void setRatioNumerator(long ratioNumerator) { this.ratioNumerator = ratioNumerator; }
    public long getRatioDenominator() { return ratioDenominator; }
    public void setRatioDenominator(long ratioDenominator) { this.ratioDenominator = ratioDenominator; }
    public Shareholder getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Shareholder beneficiary) { this.beneficiary = beneficiary; }
    public String getLegalReference() { return legalReference; }
    public void setLegalReference(String legalReference) { this.legalReference = legalReference; }
    public SuccessionStatus getStatus() { return status; }
    public void setStatus(SuccessionStatus status) { this.status = status; }
    public UUID getResolutionId() { return resolutionId; }
    public void setResolutionId(UUID resolutionId) { this.resolutionId = resolutionId; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
