package com.equigcc.equity;

import com.equigcc.captable.CapTable.HolderPosition;
import com.equigcc.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundSimulatorTest {

    private static HolderPosition holder(String name, long fd) {
        return new HolderPosition(UUID.randomUUID(), name, null, fd, fd, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static final List<HolderPosition> HOLDERS = List.of(holder("A", 600_000), holder("B", 400_000));

    @Test
    void plainRoundGivesTheInvestorTheirShareOfPostMoney() {
        var r = RoundSimulator.compute(HOLDERS, new RoundSimulator.Input(bd(9_000_000), bd(1_000_000), null, "Fund"));
        assertThat(r.postMoney()).isEqualByComparingTo("10000000");
        assertThat(r.pricePerShare()).isEqualByComparingTo("9");
        assertThat(r.newInvestorShares()).isEqualTo(111_111);
        assertThat(r.investorPct()).isEqualByComparingTo("10.0000");
        assertThat(r.newPoolShares()).isZero();
        assertThat(r.sharesAfter()).isEqualTo(1_111_111);
        assertThat(r.rows()).hasSize(3);
        assertThat(r.rows().getLast().name()).isEqualTo("Fund");
    }

    @Test
    void optionPoolIsTakenFromExistingHoldersBeforeTheInvestor() {
        var r = RoundSimulator.compute(HOLDERS, new RoundSimulator.Input(bd(9_000_000), bd(1_000_000), bd(10), null));
        assertThat(r.poolPct()).isEqualByComparingTo("10.0000");
        assertThat(r.investorPct()).isBetween(bd(9.99), bd(10.01));
        // existing holders carry the pool: A goes from 60% to 60% x 80% = 48%
        var a = r.rows().getFirst();
        assertThat(a.pctAfter()).isBetween(bd(47.99), bd(48.01));
        assertThat(r.pricePerShare()).isLessThan(bd(9));
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> RoundSimulator.compute(HOLDERS, new RoundSimulator.Input(bd(0), bd(1), null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> RoundSimulator.compute(HOLDERS, new RoundSimulator.Input(bd(1), bd(0), null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> RoundSimulator.compute(List.of(), new RoundSimulator.Input(bd(1), bd(1), null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> RoundSimulator.compute(HOLDERS, new RoundSimulator.Input(bd(1), bd(9), bd(50), null))).isInstanceOf(BusinessRuleException.class);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
