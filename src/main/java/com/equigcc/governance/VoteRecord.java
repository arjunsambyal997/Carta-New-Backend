package com.equigcc.governance;

import com.equigcc.captable.Shareholder;
import com.equigcc.common.BaseEntity;
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
import java.time.Instant;

@Entity
@Table(name = "vote_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_resolution_shareholder",
                columnNames = {"resolution_id", "shareholder_id"}))
public class VoteRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resolution_id", nullable = false)
    private BoardResolution resolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shareholder_id", nullable = false)
    private Shareholder shareholder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private VoteChoice vote;

    /** The shareholder's voting power when the vote was cast. */
    @Column(name = "weight_applied", nullable = false, precision = 24, scale = 4)
    private BigDecimal weightApplied;

    @Column(name = "cast_at", nullable = false)
    private Instant castAt;

    @Column(name = "cast_by", nullable = false, length = 128)
    private String castBy;

    public BoardResolution getResolution() { return resolution; }
    public void setResolution(BoardResolution resolution) { this.resolution = resolution; }
    public Shareholder getShareholder() { return shareholder; }
    public void setShareholder(Shareholder shareholder) { this.shareholder = shareholder; }
    public VoteChoice getVote() { return vote; }
    public void setVote(VoteChoice vote) { this.vote = vote; }
    public BigDecimal getWeightApplied() { return weightApplied; }
    public void setWeightApplied(BigDecimal weightApplied) { this.weightApplied = weightApplied; }
    public Instant getCastAt() { return castAt; }
    public void setCastAt(Instant castAt) { this.castAt = castAt; }
    public String getCastBy() { return castBy; }
    public void setCastBy(String castBy) { this.castBy = castBy; }
}
