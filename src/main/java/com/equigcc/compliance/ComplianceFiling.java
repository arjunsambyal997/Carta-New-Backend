package com.equigcc.compliance;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "compliance_filing")
public class ComplianceFiling extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_key", nullable = false, length = 64)
    private String ruleKey;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(length = 200)
    private String reference;

    @Column(name = "filed_by", nullable = false)
    private String filedBy;

    protected ComplianceFiling() { }

    public ComplianceFiling(UUID organizationId, String ruleKey, LocalDate filedOn, String reference, String filedBy) {
        this.organizationId = organizationId;
        this.ruleKey = ruleKey;
        this.filedOn = filedOn;
        this.reference = reference;
        this.filedBy = filedBy;
    }

    public String getRuleKey() { return ruleKey; }
    public LocalDate getFiledOn() { return filedOn; }
    public String getReference() { return reference; }
    public String getFiledBy() { return filedBy; }
}
