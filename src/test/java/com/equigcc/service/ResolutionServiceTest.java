package com.equigcc.service;

import com.equigcc.domain.BoardResolution;
import com.equigcc.domain.Organization;
import com.equigcc.domain.ResolutionStatus;
import com.equigcc.domain.ResolutionThreshold;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.VoteChoice;
import com.equigcc.domain.VoteRecord;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.repository.VoteRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final UUID orgId = UUID.randomUUID();
    private final UUID resId = UUID.randomUUID();
    private final Shareholder fatima = holder("Fatima");

    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final BoardResolutionRepository resolutions = mock(BoardResolutionRepository.class);
    private final VoteRecordRepository votes = mock(VoteRecordRepository.class);
    private final ShareholderRepository shareholders = mock(ShareholderRepository.class);
    private final CapTableService capTables = mock(CapTableService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ResolutionService service =
            new ResolutionService(orgs, resolutions, votes, shareholders, capTables, audit, clock);

    private BoardResolution active;

    @BeforeEach
    void setUp() {
        active = resolution(ResolutionThreshold.ORDINARY, "50", "1000");
        active.setStatus(ResolutionStatus.ACTIVE);
        active.setVotingDeadline(NOW.plusSeconds(3600));
        when(resolutions.findForUpdate(resId, orgId)).thenReturn(Optional.of(active));
        when(shareholders.findByIdAndOrganizationId(fatima.getId(), orgId)).thenReturn(Optional.of(fatima));
        when(votes.save(any(VoteRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(resolutions.save(any(BoardResolution.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- tally maths -------------------------------------------------------------------------

    @Test
    void ordinaryNeedsStrictMajorityOfDecidedVotes() {
        assertThat(tally(ResolutionThreshold.ORDINARY, v("510", VoteChoice.FOR), v("490", VoteChoice.AGAINST)).wouldPass()).isTrue();
        assertThat(tally(ResolutionThreshold.ORDINARY, v("500", VoteChoice.FOR), v("500", VoteChoice.AGAINST)).wouldPass()).isFalse();
    }

    @Test
    void specialNeedsSeventyFivePercentInclusive() {
        assertThat(tally(ResolutionThreshold.SPECIAL, v("750", VoteChoice.FOR), v("250", VoteChoice.AGAINST)).wouldPass()).isTrue();
        assertThat(tally(ResolutionThreshold.SPECIAL, v("740", VoteChoice.FOR), v("260", VoteChoice.AGAINST)).wouldPass()).isFalse();
    }

    @Test
    void abstentionsCountForQuorumButNotForTheMajority() {
        var t = tally(ResolutionThreshold.ORDINARY, v("10", VoteChoice.FOR), v("5", VoteChoice.AGAINST), v("500", VoteChoice.ABSTAIN));
        assertThat(t.participationPct()).isEqualByComparingTo("51.50");
        assertThat(t.quorumMet()).isTrue();
        assertThat(t.forPctOfDecided()).isEqualByComparingTo("66.67");
        assertThat(t.wouldPass()).isTrue();
    }

    @Test
    void missingQuorumRejectsEvenUnanimousVote() {
        var t = tally(ResolutionThreshold.ORDINARY, v("100", VoteChoice.FOR));
        assertThat(t.participationPct()).isEqualByComparingTo("10.00");
        assertThat(t.quorumMet()).isFalse();
        assertThat(t.wouldPass()).isFalse();
    }

    @Test
    void allAbstainingNeverPasses() {
        assertThat(tally(ResolutionThreshold.ORDINARY, v("900", VoteChoice.ABSTAIN)).wouldPass()).isFalse();
    }

    @Test
    void unopenedResolutionHasNoParticipation() {
        var draft = resolution(ResolutionThreshold.ORDINARY, "50", null);
        var t = ResolutionService.tally(draft, List.of());
        assertThat(t.participationPct()).isEqualByComparingTo("0");
        assertThat(t.quorumMet()).isFalse();
    }

    // ---- voting rules ------------------------------------------------------------------------

    @Test
    void voteUsesCurrentVotingPowerFromTheCapTable() {
        stubVotingPower(fatima, "600");
        VoteRecord v = service.vote(orgId, resId, fatima.getId(), VoteChoice.FOR, "fatima@x");
        assertThat(v.getWeightApplied()).isEqualByComparingTo("600");
        assertThat(v.getCastAt()).isEqualTo(NOW);
    }

    @Test
    void voteIsRejectedAfterTheDeadline() {
        active.setVotingDeadline(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.vote(orgId, resId, fatima.getId(), VoteChoice.FOR, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("deadline");
    }

    @Test
    void secondVoteIsRejected() {
        stubVotingPower(fatima, "600");
        when(votes.existsByResolutionIdAndShareholderId(resId, fatima.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.vote(orgId, resId, fatima.getId(), VoteChoice.FOR, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already voted");
    }

    @Test
    void shareholderWithoutVotingPowerCannotVote() {
        stubVotingPower(fatima, "0");
        assertThatThrownBy(() -> service.vote(orgId, resId, fatima.getId(), VoteChoice.FOR, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no voting power");
    }

    @Test
    void cannotVoteOnADraft() {
        active.setStatus(ResolutionStatus.DRAFT);
        assertThatThrownBy(() -> service.vote(orgId, resId, fatima.getId(), VoteChoice.FOR, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not open");
    }

    @Test
    void closingDecidesPassedOrRejected() {
        when(votes.findByResolution(resId)).thenReturn(List.of(vote("600", VoteChoice.FOR), vote("100", VoteChoice.AGAINST)));
        assertThat(service.close(orgId, resId, "u").getStatus()).isEqualTo(ResolutionStatus.PASSED);
        assertThat(active.getClosedAt()).isEqualTo(NOW);

        active.setStatus(ResolutionStatus.ACTIVE);
        when(votes.findByResolution(resId)).thenReturn(List.of(vote("100", VoteChoice.FOR), vote("600", VoteChoice.AGAINST)));
        assertThat(service.close(orgId, resId, "u").getStatus()).isEqualTo(ResolutionStatus.REJECTED);
    }

    @Test
    void openFreezesTotalVotingPowerAndRequiresAFutureDeadline() {
        active.setStatus(ResolutionStatus.DRAFT);
        active.setTotalVotingPower(null);
        when(capTables.calculate(orgId)).thenReturn(capTable("1400", List.of()));
        var opened = service.open(orgId, resId, "u");
        assertThat(opened.getStatus()).isEqualTo(ResolutionStatus.ACTIVE);
        assertThat(opened.getTotalVotingPower()).isEqualByComparingTo("1400");

        active.setStatus(ResolutionStatus.DRAFT);
        active.setVotingDeadline(NOW.minusSeconds(5));
        assertThatThrownBy(() -> service.open(orgId, resId, "u")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createRejectsPastDeadline() {
        var org = new Organization();
        org.setId(orgId);
        when(orgs.findById(orgId)).thenReturn(Optional.of(org));
        var cmd = new CreateResolutionCommand("t", null, null, null, null, NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.create(orgId, cmd, "u")).isInstanceOf(BusinessRuleException.class);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void stubVotingPower(Shareholder h, String power) {
        var pos = new CapTable.HolderPosition(h.getId(), h.getFullNameEn(), null, 0, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(power), BigDecimal.ZERO);
        when(capTables.calculate(orgId)).thenReturn(capTable("1000", List.of(pos)));
    }

    private CapTable capTable(String totalVotes, List<CapTable.HolderPosition> holders) {
        return new CapTable(orgId, NOW, 0, 0, 0, new BigDecimal(totalVotes), holders, List.of());
    }

    private ResolutionTally tally(ResolutionThreshold threshold, VoteRecord... cast) {
        var r = resolution(threshold, "50", "1000");
        return ResolutionService.tally(r, List.of(cast));
    }

    private BoardResolution resolution(ResolutionThreshold threshold, String quorum, String totalPower) {
        var r = new BoardResolution();
        r.setId(resId);
        r.setTitle("Approve budget");
        r.setThreshold(threshold);
        r.setQuorumPct(new BigDecimal(quorum));
        r.setTotalVotingPower(totalPower == null ? null : new BigDecimal(totalPower));
        return r;
    }

    private VoteRecord v(String weight, VoteChoice choice) {
        return vote(weight, choice);
    }

    private VoteRecord vote(String weight, VoteChoice choice) {
        var vr = new VoteRecord();
        vr.setVote(choice);
        vr.setWeightApplied(new BigDecimal(weight));
        return vr;
    }

    private static Shareholder holder(String name) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn(name);
        return h;
    }
}
