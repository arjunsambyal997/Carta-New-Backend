package com.equigcc.ownership;

import com.equigcc.captable.Shareholder;
import com.equigcc.common.BaseEntity;
import com.equigcc.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** {@code owner} holds {@code economicPct} of the capital and {@code votingPct} of the votes of the corporate {@code owned}. */
@Entity
@Table(name = "ownership_link")
public class OwnershipLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Shareholder owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owned_id", nullable = false)
    private Shareholder owned;

    @Column(name = "economic_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal economicPct;

    @Column(name = "voting_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal votingPct;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public Shareholder getOwner() { return owner; }
    public void setOwner(Shareholder owner) { this.owner = owner; }
    public Shareholder getOwned() { return owned; }
    public void setOwned(Shareholder owned) { this.owned = owned; }
    public BigDecimal getEconomicPct() { return economicPct; }
    public void setEconomicPct(BigDecimal economicPct) { this.economicPct = economicPct; }
    public BigDecimal getVotingPct() { return votingPct; }
    public void setVotingPct(BigDecimal votingPct) { this.votingPct = votingPct; }
}
