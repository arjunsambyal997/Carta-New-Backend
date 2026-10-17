package com.equigcc.governance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateResolutionRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String titleAr,
        @Size(max = 4000) String description,
        /* Defaults to ORDINARY. */
        ResolutionThreshold threshold,
        /* Percent of total voting power that must take part; defaults to 50. */
        @DecimalMin("0") @DecimalMax("100") BigDecimal quorumPct,
        @NotNull Instant votingDeadline) {
}
