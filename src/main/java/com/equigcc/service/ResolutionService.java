package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.BoardResolution;
import com.equigcc.domain.ResolutionStatus;
import com.equigcc.domain.ResolutionThreshold;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.VoteChoice;
import com.equigcc.domain.VoteRecord;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.repository.VoteRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resolution lifecycle: DRAFT -> ACTIVE (voting) -> PASSED or REJECTED.
 * Votes are weighted by the voter's voting power on the cap table at the moment they vote, are final once
 * cast, and the quorum denominator is the total voting power frozen when the resolution opened.
 */
@Service
public class ResolutionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrganizationRepository organizations;
    private final BoardResolutionRepository resolutions;
    private final VoteRecordRepository votes;
    private final ShareholderRepository shareholders;
    private final CapTableService capTables;
    private final AuditService audit;
    private final Clock clock;

    public ResolutionService(OrganizationRepository organizations, BoardResolutionRepository resolutions,
                             VoteRecordRepository votes, ShareholderRepository shareholders,
                             CapTableService capTables, AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.resolutions = resolutions;
        this.votes = votes;
        this.shareholders = shareholders;
        this.capTables = capTables;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public BoardResolution create(UUID orgId, CreateResolutionCommand cmd, String user) {
        var org = organizations.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        if (cmd.votingDeadline() == null || !cmd.votingDeadline().isAfter(clock.instant())) {
            throw new BusinessRuleException("Voting deadline must be in the future");
        }
        BigDecimal quorum = cmd.quorumPct() != null ? cmd.quorumPct() : BigDecimal.valueOf(50);
        if (quorum.signum() < 0 || quorum.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("Quorum must be between 0 and 100");
        }
        var r = new BoardResolution();
        r.setOrganization(org);
        r.setTitle(cmd.title());
        r.setTitleAr(cmd.titleAr());
        r.setDescription(cmd.description());
        r.setThreshold(cmd.threshold() != null ? cmd.threshold() : ResolutionThreshold.ORDINARY);
        r.setQuorumPct(quorum);
        r.setVotingDeadline(cmd.votingDeadline());
        r.setCreatedBy(user);
        r = resolutions.save(r);
        audit.record(orgId, "BoardResolution", r.getId(), AuditAction.CREATE, "created draft: " + r.getTitle(), user);
        return r;
    }

    @Transactional
    public BoardResolution open(UUID orgId, UUID id, String user) {
        var r = lock(orgId, id);
        if (r.getStatus() != ResolutionStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft resolution can be opened");
        }
        if (!r.getVotingDeadline().isAfter(clock.instant())) {
            throw new BusinessRuleException("Voting deadline has already passed");
        }
        BigDecimal total = capTables.calculate(orgId).totalVotingPower();
        if (total.signum() <= 0) {
            throw new BusinessRuleException("No voting power on the register; issue voting shares first");
        }
        r.setTotalVotingPower(total);
        r.setOpenedAt(clock.instant());
        r.setStatus(ResolutionStatus.ACTIVE);
        audit.record(orgId, "BoardResolution", id, AuditAction.UPDATE, "opened for voting, total voting power " + total.toPlainString(), user);
        return r;
    }

    @Transactional
    public VoteRecord vote(UUID orgId, UUID id, UUID shareholderId, VoteChoice choice, String user) {
        var r = lock(orgId, id);
        if (r.getStatus() != ResolutionStatus.ACTIVE) {
            throw new BusinessRuleException("Resolution is not open for voting");
        }
        if (!clock.instant().isBefore(r.getVotingDeadline())) {
            throw new BusinessRuleException("Voting deadline has passed");
        }
        Shareholder holder = shareholders.findByIdAndOrganizationId(shareholderId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + shareholderId + " not found in organization"));
        if (votes.existsByResolutionIdAndShareholderId(id, shareholderId)) {
            throw new BusinessRuleException("This shareholder has already voted; votes are final");
        }
        BigDecimal weight = capTables.calculate(orgId).holders().stream()
                .filter(h -> h.shareholderId().equals(shareholderId))
                .map(h -> h.votingPower())
                .findFirst().orElse(BigDecimal.ZERO);
        if (weight.signum() <= 0) {
            throw new BusinessRuleException("This shareholder holds no voting power");
        }
        var v = new VoteRecord();
        v.setResolution(r);
        v.setShareholder(holder);
        v.setVote(choice);
        v.setWeightApplied(weight);
        v.setCastAt(clock.instant());
        v.setCastBy(user);
        v = votes.save(v);
        audit.record(orgId, "VoteRecord", v.getId(), AuditAction.CREATE,
                holder.getFullNameEn() + " voted " + choice + " with weight " + weight.toPlainString()
                        + " on " + r.getTitle(), user);
        return v;
    }

    @Transactional
    public BoardResolution close(UUID orgId, UUID id, String user) {
        var r = lock(orgId, id);
        if (r.getStatus() != ResolutionStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active resolution can be closed");
        }
        ResolutionTally tally = tally(r, votes.findByResolution(id));
        r.setStatus(tally.wouldPass() ? ResolutionStatus.PASSED : ResolutionStatus.REJECTED);
        r.setClosedAt(clock.instant());
        audit.record(orgId, "BoardResolution", id, AuditAction.UPDATE,
                "closed as " + r.getStatus() + " (for " + tally.forWeight().toPlainString() + ", against "
                        + tally.againstWeight().toPlainString() + ", participation " + tally.participationPct() + "%)", user);
        return r;
    }

    @Transactional(readOnly = true)
    public List<BoardResolution> list(UUID orgId) {
        return resolutions.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public BoardResolution get(UUID orgId, UUID id) {
        return resolutions.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Resolution " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<VoteRecord> votesOf(UUID id) {
        return votes.findByResolution(id);
    }

    public ResolutionTally tallyOf(BoardResolution r) {
        return tally(r, votes.findByResolution(r.getId()));
    }

    private BoardResolution lock(UUID orgId, UUID id) {
        return resolutions.findForUpdate(id, orgId)
                .orElseThrow(() -> new NotFoundException("Resolution " + id + " not found"));
    }

    /** Pure tally. A resolution that never opened has no voting power snapshot, so participation is 0. */
    static ResolutionTally tally(BoardResolution r, List<VoteRecord> cast) {
        BigDecimal f = BigDecimal.ZERO;
        BigDecimal a = BigDecimal.ZERO;
        BigDecimal ab = BigDecimal.ZERO;
        for (VoteRecord v : cast) {
            switch (v.getVote()) {
                case FOR -> f = f.add(v.getWeightApplied());
                case AGAINST -> a = a.add(v.getWeightApplied());
                case ABSTAIN -> ab = ab.add(v.getWeightApplied());
            }
        }
        BigDecimal total = r.getTotalVotingPower();
        BigDecimal participation = total == null || total.signum() == 0 ? BigDecimal.ZERO
                : f.add(a).add(ab).multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
        boolean quorum = total != null && total.signum() > 0 && participation.compareTo(r.getQuorumPct()) >= 0;
        BigDecimal decided = f.add(a);
        BigDecimal forPct = decided.signum() == 0 ? BigDecimal.ZERO
                : f.multiply(HUNDRED).divide(decided, 2, RoundingMode.HALF_UP);
        return new ResolutionTally(f, a, ab, participation, quorum, forPct,
                quorum && r.getThreshold().passes(f, a));
    }
}
