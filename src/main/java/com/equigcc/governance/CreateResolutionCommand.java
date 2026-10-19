package com.equigcc.governance;


import java.math.BigDecimal;
import java.time.Instant;

public record CreateResolutionCommand(
        String title,
        String titleAr,
        String description,
        ResolutionThreshold threshold,
        BigDecimal quorumPct,
        Instant votingDeadline) {
}
