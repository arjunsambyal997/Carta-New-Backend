package com.equigcc.governance;

import java.math.BigDecimal;

/**
 * Majority needed among votes cast FOR and AGAINST (abstentions excluded).
 * ORDINARY is a simple majority. SPECIAL is the three quarters the UAE Commercial Companies Law requires for a
 * special decision. TWO_THIRDS is the Saudi Companies Law (2022) majority for extraordinary general assembly
 * resolutions (Art. 93). Confirm the thresholds your Articles of Association and local law require.
 */
public enum ResolutionThreshold {
    ORDINARY(1, 2, false),
    TWO_THIRDS(2, 3, true),
    SPECIAL(3, 4, true);

    private final BigDecimal numerator;
    private final BigDecimal denominator;
    private final boolean inclusive;

    ResolutionThreshold(int numerator, int denominator, boolean inclusive) {
        this.numerator = BigDecimal.valueOf(numerator);
        this.denominator = BigDecimal.valueOf(denominator);
        this.inclusive = inclusive;
    }

    /** ORDINARY: strictly more than half. TWO_THIRDS and SPECIAL: at least the stated share. Exact, no rounding. */
    public boolean passes(BigDecimal forWeight, BigDecimal againstWeight) {
        BigDecimal decided = forWeight.add(againstWeight);
        if (decided.signum() == 0) {
            return false;
        }
        int cmp = forWeight.multiply(denominator).compareTo(decided.multiply(numerator));
        return inclusive ? cmp >= 0 : cmp > 0;
    }
}
