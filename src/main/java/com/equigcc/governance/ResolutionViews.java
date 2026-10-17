package com.equigcc.governance;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResolutionViews {

    private ResolutionViews() {
    }

    public record Resolution(
            UUID id,
            String title,
            String titleAr,
            String description,
            ResolutionThreshold threshold,
            BigDecimal quorumPct,
            Instant votingDeadline,
            ResolutionStatus status,
            BigDecimal totalVotingPower,
            String createdBy,
            Instant openedAt,
            Instant closedAt,
            Instant lastReminderAt,
            ResolutionTally tally) {

        public static Resolution from(BoardResolution r, ResolutionTally tally) {
            return new Resolution(r.getId(), r.getTitle(), r.getTitleAr(), r.getDescription(), r.getThreshold(),
                    r.getQuorumPct(), r.getVotingDeadline(), r.getStatus(), r.getTotalVotingPower(), r.getCreatedBy(),
                    r.getOpenedAt(), r.getClosedAt(), latest(r.getReminderSentAt(), r.getManualReminderAt()), tally);
        }

        private static Instant latest(Instant a, Instant b) {
            return a == null ? b : b == null ? a : (a.isAfter(b) ? a : b);
        }
    }

    public record Vote(
            UUID shareholderId,
            String nameEn,
            String nameAr,
            VoteChoice vote,
            BigDecimal weight,
            Instant castAt) {

        public static Vote from(VoteRecord v) {
            return new Vote(v.getShareholder().getId(), v.getShareholder().getFullNameEn(),
                    v.getShareholder().getFullNameAr(), v.getVote(), v.getWeightApplied(), v.getCastAt());
        }
    }

    public record Reminder(int recipients, Instant sentAt) {
    }

    public record Detail(Resolution resolution, List<Vote> votes) {
    }
}
