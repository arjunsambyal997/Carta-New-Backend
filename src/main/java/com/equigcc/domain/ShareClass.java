package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "share_class",
        uniqueConstraints = @UniqueConstraint(name = "uk_share_class_org_name",
                columnNames = {"organization_id", "name"}))
public class ShareClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_ar", length = 100)
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 32)
    private ShareClassType classType;

    @Column(name = "par_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal parValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    /** Votes per share. 0 for non-voting classes, 10 for a 10x super-voting founder class. */
    @Column(name = "voting_multiplier", nullable = false, precision = 10, scale = 4)
    private BigDecimal votingMultiplier = BigDecimal.ONE;

    @Column(name = "dividend_preference_ratio", nullable = false, precision = 10, scale = 4)
    private BigDecimal dividendPreferenceRatio = BigDecimal.ONE;

    /** e.g. 1.0 = 1x liquidation preference. 0 for common. */
    @Column(name = "liquidation_multiple", nullable = false, precision = 10, scale = 4)
    private BigDecimal liquidationMultiple = BigDecimal.ZERO;

    @Column(name = "participating", nullable = false)
    private boolean participating;

    /** Seniority in the exit waterfall; higher is paid first. */
    @Column(name = "seniority_rank", nullable = false)
    private int seniorityRank;

    /** Cap set by the Articles of Association; null means uncapped. */
    @Column(name = "authorized_shares")
    private Long authorizedShares;

    public boolean isTreasury() { return classType == ShareClassType.TREASURY; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
    public ShareClassType getClassType() { return classType; }
    public void setClassType(ShareClassType classType) { this.classType = classType; }
    public BigDecimal getParValue() { return parValue; }
    public void setParValue(BigDecimal parValue) { this.parValue = parValue; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public BigDecimal getVotingMultiplier() { return votingMultiplier; }
    public void setVotingMultiplier(BigDecimal votingMultiplier) { this.votingMultiplier = votingMultiplier; }
    public BigDecimal getDividendPreferenceRatio() { return dividendPreferenceRatio; }
    public void setDividendPreferenceRatio(BigDecimal dividendPreferenceRatio) { this.dividendPreferenceRatio = dividendPreferenceRatio; }
    public BigDecimal getLiquidationMultiple() { return liquidationMultiple; }
    public void setLiquidationMultiple(BigDecimal liquidationMultiple) { this.liquidationMultiple = liquidationMultiple; }
    public boolean isParticipating() { return participating; }
    public void setParticipating(boolean participating) { this.participating = participating; }
    public int getSeniorityRank() { return seniorityRank; }
    public void setSeniorityRank(int seniorityRank) { this.seniorityRank = seniorityRank; }
    public Long getAuthorizedShares() { return authorizedShares; }
    public void setAuthorizedShares(Long authorizedShares) { this.authorizedShares = authorizedShares; }
}
