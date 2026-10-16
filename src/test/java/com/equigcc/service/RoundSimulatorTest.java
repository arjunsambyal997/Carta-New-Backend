package com.equigcc.service;

import com.equigcc.service.CapTable.HolderPosition;
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

    private static RoundSimulator.Input withConvertibles(RoundSimulator.Convertible... cs) {
        return new RoundSimulator.Input(bd(9_000_000), bd(1_000_000), null, "Fund", List.of(cs));
    }

    @Test
    void aSafeWithACapConvertsAtTheCapPriceAndDilutesOnlyTheExistingHolders() {
        // 1,000,000 shares. Cap $4m / 1,000,000 = $4.00 a share, far below the round price, so $400,000 buys 100,000 shares.
        var r = RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible("Seed SAFE", "SAFE", bd(400_000), null, null, bd(4_000_000), null)));
        var c = r.conversions().getFirst();
        assertThat(c.basis()).isEqualTo("CAP");
        assertThat(c.conversionPrice()).isEqualByComparingTo("4");
        assertThat(c.shares()).isEqualTo(100_000);
        // price x (1,000,000 + 100,000) = 9,000,000
        assertThat(r.pricePerShare()).isEqualByComparingTo("8.181818");
        assertThat(r.convertedShares()).isEqualTo(100_000);
        assertThat(r.rows()).extracting(RoundSimulator.Row::name).contains("Seed SAFE", "Fund");
        assertThat(r.sharesAfter()).isEqualTo(r.sharesBefore() + r.convertedShares() + r.newPoolShares() + r.newInvestorShares());
        // converted shares sit in the pre-money, so the new investor still gets $1m of $10m: 10%
        assertThat(r.investorPct()).isBetween(bd(9.99), bd(10.01));
        // the existing holders carry the SAFE: A goes from 60% to 60% x 1,000,000 / 1,222,222 = 49.1%
        assertThat(r.rows().getFirst().pctAfter()).isBetween(bd(49.0), bd(49.2));
    }

    @Test
    void aDiscountSafeBuysAtADiscountToTheRoundPrice() {
        // 20% discount, $800,000: converts at 0.8 x price. price x 1,000,000 + 800,000/0.8 = 9,000,000, so price = 8.
        var r = RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible(null, "SAFE", bd(800_000), null, null, null, bd(20))));
        assertThat(r.pricePerShare()).isEqualByComparingTo("8");
        var c = r.conversions().getFirst();
        assertThat(c.name()).isEqualTo("SAFE 1");
        assertThat(c.basis()).isEqualTo("DISCOUNT");
        assertThat(c.conversionPrice()).isEqualByComparingTo("6.4");
        assertThat(c.shares()).isEqualTo(125_000);
    }

    @Test
    void whicheverIsLowerWinsBetweenCapAndDiscount() {
        // the cap price is $20 (above the round price), so the 20% discount wins
        var r = RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible("S", "SAFE", bd(800_000), null, null, bd(20_000_000), bd(20))));
        assertThat(r.conversions().getFirst().basis()).isEqualTo("DISCOUNT");
    }

    @Test
    void aNoteConvertsPrincipalPlusSimpleInterest() {
        // $100,000 at 8% for 18 months = $112,000
        var r = RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible("Bridge", "NOTE", bd(100_000), bd(8), 18, null, null)));
        assertThat(r.conversions().getFirst().amountConverted()).isEqualByComparingTo("112000");
        assertThat(r.conversions().getFirst().basis()).isEqualTo("ROUND");
    }

    @Test
    void convertiblesAndAPoolTogetherStillAddUp() {
        var in = new RoundSimulator.Input(bd(9_000_000), bd(1_000_000), bd(10), "Fund",
                List.of(new RoundSimulator.Convertible("S", "SAFE", bd(300_000), null, null, bd(5_000_000), bd(10))));
        var r = RoundSimulator.compute(HOLDERS, in);
        assertThat(r.poolPct()).isBetween(bd(9.99), bd(10.01));
        assertThat(r.sharesAfter()).isEqualTo(r.sharesBefore() + r.convertedShares() + r.newPoolShares() + r.newInvestorShares());
        // price x (existing + pool + converted) is the pre-money, within rounding
        double check = r.pricePerShare().doubleValue() * (r.sharesBefore() + r.newPoolShares() + r.convertedShares());
        assertThat(check).isBetween(8_990_000d, 9_010_000d);
    }

    @Test
    void convertiblesBiggerThanThePreMoneyAreRejected() {
        assertThatThrownBy(() -> RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible("Huge", "SAFE", bd(50_000_000), null, null, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("too large");
        assertThatThrownBy(() -> RoundSimulator.compute(HOLDERS, withConvertibles(new RoundSimulator.Convertible("Bad", "SAFE", bd(1), null, null, null, bd(100)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
