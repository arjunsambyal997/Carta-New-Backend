package com.equigcc.domain;

import java.math.BigDecimal;

/**
 * Majority needed among votes cast FOR and AGAINST (abstentions excluded).
 * ORDINARY is a simple majority; SPECIAL is the 75% required for special resolutions under the UAE
 * Commercial Companies Law. Confirm the thresholds your Articles of Association and local law require.
 */
public enum ResolutionThreshold {
    ORDINARY(50, false),
    SPECIAL(75, true);

    private final BigDecimal percent;
    private final boolean inclusive;

    ResolutionThreshold(int percent, boolean inclusive) {
        this.percent = BigDecimal.valueOf(percent);
        this.inclusive = inclusive;
    }

    /** ORDINARY: strictly more than 50%. SPECIAL: at least 75%. */
    public boolean passes(BigDecimal forWeight, BigDecimal againstWeight) {
        BigDecimal decided = forWeight.add(againstWeight);
        if (decided.signum() == 0) {
            return false;
        }
        int cmp = forWeight.multiply(BigDecimal.valueOf(100)).compareTo(decided.multiply(percent));
        return inclusive ? cmp >= 0 : cmp > 0;
    }
}
