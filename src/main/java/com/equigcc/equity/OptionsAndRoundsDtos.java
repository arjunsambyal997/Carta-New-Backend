package com.equigcc.equity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class OptionsAndRoundsDtos {

    private OptionsAndRoundsDtos() {
    }

    public record SimulateRoundRequest(
            @NotNull @Positive BigDecimal preMoney,
            @NotNull @Positive BigDecimal investment,
            /* Option pool as a share of the company after the round, 0 to 50. */
            @DecimalMin("0") @DecimalMax("50") BigDecimal poolPct,
            @Size(max = 200) String investorName,
            /* SAFEs and convertible notes that convert in this round. */
            @Size(max = 20) java.util.List<@jakarta.validation.Valid Convertible> convertibles) {
    }

    public record Convertible(
            @Size(max = 100) String name,
            /* SAFE or NOTE. */
            @jakarta.validation.constraints.Pattern(regexp = "SAFE|NOTE") String kind,
            @NotNull @Positive BigDecimal amount,
            /* Notes only: simple interest per year, and how many months it has run. */
            @DecimalMin("0") @DecimalMax("100") BigDecimal interestPct,
            @Min(0) @Max(120) Integer months,
            @Positive BigDecimal capValuation,
            @DecimalMin("0") @DecimalMax("99.99") BigDecimal discountPct) {
    }

    public record VestingRequest(
            @NotNull LocalDate startDate,
            @Min(0) @Max(120) int cliffMonths,
            @Min(1) @Max(120) int vestingMonths) {
    }

    public record ExerciseRequest(
            @Positive long quantity,
            /* Class the new shares go into; defaults to the option's own class. */
            UUID shareClassId,
            LocalDate date) {
    }
}
