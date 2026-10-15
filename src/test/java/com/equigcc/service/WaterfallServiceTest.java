package com.equigcc.service;

import com.equigcc.domain.Currency;
import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareClassType;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.domain.Shareholder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaterfallServiceTest {

    private final WaterfallService service = new WaterfallService(null, null);

    private final Shareholder founder = holder("Founder");
    private final Shareholder investor = holder("Investor");
    private final Shareholder investorB = holder("Investor B");

    private final ShareClass common = shareClass("Common", 0, false, 0);
    private final ShareClass seriesA = shareClass("Series A", 1, false, 1);
    private final ShareClass seriesAParticipating = shareClass("Series A", 1, true, 1);
    private final ShareClass seriesB = shareClass("Series B", 1, false, 2);

    private ShareIssuance[] baseRows(ShareClass pref) {
        return new ShareIssuance[]{
                row(founder, common, 1_000_000, "0.10"),   // invested 100,000
                row(investor, pref, 500_000, "2.00")};     // invested 1,000,000, 1x
    }

    private CapTableAmounts run(String exit, ShareIssuance... rows) {
        return new CapTableAmounts(service.compute(UUID.randomUUID(), new BigDecimal(exit), List.of(rows)));
    }

    @Test
    void lowExitPaysPreferenceThenCommon() {
        var r = run("1500000", baseRows(seriesA));
        assertThat(r.cls("Series A").preferencePaid()).isEqualByComparingTo("1000000");
        assertThat(r.cls("Series A").converted()).isFalse();
        assertThat(r.cls("Common").total()).isEqualByComparingTo("500000");
    }

    @Test
    void exitBelowPreferenceGoesEntirelyToPreferred() {
        var r = run("500000", baseRows(seriesA));
        assertThat(r.cls("Series A").total()).isEqualByComparingTo("500000");
        assertThat(r.cls("Common").total()).isEqualByComparingTo("0");
    }

    @Test
    void highExitConvertsNonParticipatingPreferred() {
        var r = run("10000000", baseRows(seriesA));
        assertThat(r.cls("Series A").converted()).isTrue();
        assertThat(r.cls("Series A").total()).isEqualByComparingTo("3333333.33");
        assertThat(r.cls("Common").total()).isEqualByComparingTo("6666666.67");
        assertThat(r.result.commonPricePerShare()).isEqualByComparingTo("6.666667");
    }

    @Test
    void participatingPreferredTakesPreferencePlusShare() {
        var r = run("10000000", baseRows(seriesAParticipating));
        assertThat(r.cls("Series A").preferencePaid()).isEqualByComparingTo("1000000");
        assertThat(r.cls("Series A").residualPaid()).isEqualByComparingTo("3000000");
        assertThat(r.cls("Series A").total()).isEqualByComparingTo("4000000");
        assertThat(r.cls("Common").total()).isEqualByComparingTo("6000000");
    }

    @Test
    void seniorSeriesIsPaidFirst() {
        var r = run("2500000",
                row(founder, common, 1_000_000, "0.10"),
                row(investor, seriesA, 500_000, "2.00"),        // owed 1,000,000, rank 1
                row(investorB, seriesB, 1_000_000, "2.00"));    // owed 2,000,000, rank 2
        assertThat(r.cls("Series B").total()).isEqualByComparingTo("2000000");
        assertThat(r.cls("Series A").total()).isEqualByComparingTo("500000");
        assertThat(r.cls("Common").total()).isEqualByComparingTo("0");
    }

    @Test
    void holderPayoutsSumToClassPayouts() {
        var r = run("10000000", baseRows(seriesA));
        assertThat(r.result.holders()).extracting(WaterfallResult.HolderPayout::nameEn)
                .containsExactly("Founder", "Investor");
        assertThat(r.result.holders().get(0).total()).isEqualByComparingTo("6666666.67");
        assertThat(r.result.holders().get(0).pctOfExit()).isEqualByComparingTo("66.6667");
    }

    @Test
    void ignoresOptionsAndTreasury() {
        var option = row(investorB, common, 999_999, "1.00");
        option.setInstrumentType(InstrumentType.OPTION);
        var treasury = row(founder, shareClass("Treasury", 0, false, 0, ShareClassType.TREASURY), 500_000, "1.00");
        var r = run("1500000", row(founder, common, 1_000_000, "0.10"), option, treasury);
        assertThat(r.cls("Common").total()).isEqualByComparingTo("1500000");
    }

    @Test
    void zeroExitYieldsZeroes() {
        var r = run("0", baseRows(seriesA));
        assertThat(r.result.classes()).allSatisfy(c -> assertThat(c.total()).isEqualByComparingTo("0"));
    }

    private record CapTableAmounts(WaterfallResult result) {
        WaterfallResult.ClassPayout cls(String name) {
            return result.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        }
    }

    private static ShareClass shareClass(String name, int rank, boolean participating, double multiple) {
        return shareClass(name, rank, participating, multiple, ShareClassType.COMMON_VOTING);
    }

    private static ShareClass shareClass(String name, int rank, boolean participating, double multiple, ShareClassType type) {
        var c = new ShareClass();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setClassType(type);
        c.setParValue(BigDecimal.ZERO);
        c.setCurrency(Currency.USD);
        c.setSeniorityRank(rank);
        c.setParticipating(participating);
        c.setLiquidationMultiple(BigDecimal.valueOf(multiple));
        return c;
    }

    private static Shareholder holder(String name) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn(name);
        return h;
    }

    private static ShareIssuance row(Shareholder h, ShareClass c, long qty, String price) {
        var i = new ShareIssuance();
        i.setShareholder(h);
        i.setShareClass(c);
        i.setQuantity(qty);
        i.setPricePerShare(new BigDecimal(price));
        i.setInstrumentType(InstrumentType.SHARES);
        return i;
    }
}
