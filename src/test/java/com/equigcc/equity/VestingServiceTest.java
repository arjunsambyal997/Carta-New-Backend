package com.equigcc.equity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VestingServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    private static long vested(LocalDate asOf) {
        return VestingService.vestedShares(48_000, START, 12, 48, asOf);
    }

    @Test
    void nothingBeforeTheCliff() {
        assertThat(vested(START.minusDays(1))).isZero();
        assertThat(vested(LocalDate.of(2026, 12, 14))).isZero();
    }

    @Test
    void cliffReleasesTheFirstYearAtOnce() {
        assertThat(vested(LocalDate.of(2027, 1, 15))).isEqualTo(12_000);
    }

    @Test
    void thenVestsMonthlyAndRoundsDown() {
        assertThat(vested(LocalDate.of(2027, 2, 15))).isEqualTo(13_000);
        assertThat(vested(LocalDate.of(2027, 2, 14))).isEqualTo(12_000);
        assertThat(VestingService.vestedShares(1_000, START, 0, 48, LocalDate.of(2026, 2, 15))).isEqualTo(20); // 1000 / 48 = 20.8
    }

    @Test
    void fullyVestedAtTheEndAndStaysThere() {
        assertThat(vested(LocalDate.of(2030, 1, 15))).isEqualTo(48_000);
        assertThat(vested(LocalDate.of(2040, 1, 1))).isEqualTo(48_000);
    }
}
