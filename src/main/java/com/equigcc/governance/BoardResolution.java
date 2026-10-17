package com.equigcc.governance;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "board_resolution")
public class BoardResolution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_ar")
    private String titleAr;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResolutionThreshold threshold = ResolutionThreshold.ORDINARY;

    /** Share of total voting power that must take part (for, against or abstain) for the vote to count. */
    @Column(name = "quorum_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal quorumPct = BigDecimal.valueOf(50);

    @Column(name = "voting_deadline", nullable = false)
    private Instant votingDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResolutionStatus status = ResolutionStatus.DRAFT;

    /** Total voting power frozen when the resolution is opened; the quorum denominator. */
    @Column(name = "total_voting_power", precision = 24, scale = 4)
    private BigDecimal totalVotingPower;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "manual_reminder_at")
    private Instant manualReminderAt;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ResolutionThreshold getThreshold() { return threshold; }
    public void setThreshold(ResolutionThreshold threshold) { this.threshold = threshold; }
    public BigDecimal getQuorumPct() { return quorumPct; }
    public void setQuorumPct(BigDecimal quorumPct) { this.quorumPct = quorumPct; }
    public Instant getVotingDeadline() { return votingDeadline; }
    public void setVotingDeadline(Instant votingDeadline) { this.votingDeadline = votingDeadline; }
    public ResolutionStatus getStatus() { return status; }
    public void setStatus(ResolutionStatus status) { this.status = status; }
    public BigDecimal getTotalVotingPower() { return totalVotingPower; }
    public void setTotalVotingPower(BigDecimal totalVotingPower) { this.totalVotingPower = totalVotingPower; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }
    public Instant getManualReminderAt() { return manualReminderAt; }
    public void setManualReminderAt(Instant manualReminderAt) { this.manualReminderAt = manualReminderAt; }
}
