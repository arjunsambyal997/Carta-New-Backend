package com.equigcc.service;

import java.math.BigDecimal;

/** Running result of a resolution. {@code wouldPass} is what closing it right now would decide. */
public record ResolutionTally(
        BigDecimal forWeight,
        BigDecimal againstWeight,
        BigDecimal abstainWeight,
        BigDecimal participationPct,
        boolean quorumMet,
        BigDecimal forPctOfDecided,
        boolean wouldPass) {
}
