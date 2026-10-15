package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Vesting terms of one option grant. {@code certificate} is always the grant's current certificate. */
@Entity
@Table(name = "vesting_schedule")
public class VestingSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private ShareIssuance certificate;

    @Column(name = "granted_quantity", nullable = false)
    private long grantedQuantity;

    @Column(name = "exercised_quantity", nullable = false)
    private long exercisedQuantity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "cliff_months", nullable = false)
    private int cliffMonths;

    @Column(name = "vesting_months", nullable = false)
    private int vestingMonths;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public ShareIssuance getCertificate() { return certificate; }
    public void setCertificate(ShareIssuance certificate) { this.certificate = certificate; }
    public long getGrantedQuantity() { return grantedQuantity; }
    public void setGrantedQuantity(long grantedQuantity) { this.grantedQuantity = grantedQuantity; }
    public long getExercisedQuantity() { return exercisedQuantity; }
    public void setExercisedQuantity(long exercisedQuantity) { this.exercisedQuantity = exercisedQuantity; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public int getCliffMonths() { return cliffMonths; }
    public void setCliffMonths(int cliffMonths) { this.cliffMonths = cliffMonths; }
    public int getVestingMonths() { return vestingMonths; }
    public void setVestingMonths(int vestingMonths) { this.vestingMonths = vestingMonths; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
