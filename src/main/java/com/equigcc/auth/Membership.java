package com.equigcc.auth;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_user_org", columnNames = {"user_id", "organization_id"}))
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    /** The shareholder this account votes as, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shareholder_id")
    private Shareholder shareholder;

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Shareholder getShareholder() { return shareholder; }
    public void setShareholder(Shareholder shareholder) { this.shareholder = shareholder; }
}
