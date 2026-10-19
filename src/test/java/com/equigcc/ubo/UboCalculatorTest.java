package com.equigcc.ubo;

import com.equigcc.captable.ShareholderType;
import com.equigcc.ubo.UboCalculator.Link;
import com.equigcc.ubo.UboCalculator.Party;
import com.equigcc.ubo.UboCalculator.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UboCalculatorTest {

    private static final BigDecimal T = new BigDecimal("25");

    private static Party person(String n) {
        return new Party(UUID.randomUUID(), ShareholderType.INDIVIDUAL, n, null, "AE", "P-" + n);
    }

    private static Party company(String n) {
        return new Party(UUID.randomUUID(), ShareholderType.CORPORATE, n, null, "AE", "R-" + n);
    }

    private static Position pos(Party p, String econ, String vote) {
        return new Position(p.id(), new BigDecimal(econ), new BigDecimal(vote));
    }

    private static Link link(Party owner, Party owned, String econ, String vote) {
        return new Link(owner.id(), owned.id(), new BigDecimal(econ), new BigDecimal(vote));
    }

    private static UboCalculator.Result run(List<Party> parties, List<Position> positions, List<Link> links) {
        return UboCalculator.calculate(parties.stream().collect(Collectors.toMap(Party::id, Function.identity())),
                positions, links, T, "Register");
    }

    @Test
    void directIndividualsAreSplitByThreshold() {
        var a = person("A");
        var b = person("B");
        var r = run(List.of(a, b), List.of(pos(a, "30", "30"), pos(b, "20", "20")), List.of());
        assertThat(r.beneficialOwners()).extracting(UboReport.Person::nameEn).containsExactly("A");
        assertThat(r.belowThreshold()).extracting(UboReport.Person::nameEn).containsExactly("B");
        assertThat(r.beneficialOwners().get(0).direct()).isTrue();
        assertThat(r.beneficialOwners().get(0).qualifiesBy()).isEqualTo("BOTH");
    }

    @Test
    void thresholdIsInclusive() {
        var a = person("A");
        var r = run(List.of(a), List.of(pos(a, "25", "25")), List.of());
        assertThat(r.beneficialOwners()).hasSize(1);
    }

    @Test
    void looksThroughAHoldingCompanyAndMultipliesAlongThePath() {
        var holdco = company("Holdco");
        var x = person("X");
        var y = person("Y");
        var r = run(List.of(holdco, x, y), List.of(pos(holdco, "60", "60")),
                List.of(link(x, holdco, "50", "50"), link(y, holdco, "50", "50")));
        assertThat(r.beneficialOwners()).extracting(UboReport.Person::nameEn).containsExactlyInAnyOrder("X", "Y");
        assertThat(r.beneficialOwners().get(0).economicPct()).isEqualByComparingTo("30");
        assertThat(r.beneficialOwners().get(0).direct()).isFalse();
        assertThat(r.beneficialOwners().get(0).paths().get(0).chain()).containsExactly("Register", "Holdco", r.beneficialOwners().get(0).nameEn());
        assertThat(r.gaps()).isEmpty();
    }

    @Test
    void sumsDirectAndIndirectPathsToTheSamePerson() {
        var holdco = company("Holdco");
        var x = person("X");
        var r = run(List.of(holdco, x), List.of(pos(x, "10", "10"), pos(holdco, "40", "40")), List.of(link(x, holdco, "50", "50")));
        var ubo = r.beneficialOwners().get(0);
        assertThat(ubo.economicPct()).isEqualByComparingTo("30");
        assertThat(ubo.paths()).hasSize(2);
        assertThat(ubo.direct()).isTrue();
    }

    @Test
    void votingControlAloneMakesAUbo() {
        var holdco = company("Holdco");
        var x = person("X");
        var r = run(List.of(holdco, x), List.of(pos(holdco, "10", "60")), List.of(link(x, holdco, "100", "100")));
        var ubo = r.beneficialOwners().get(0);
        assertThat(ubo.economicPct()).isEqualByComparingTo("10");
        assertThat(ubo.qualifiesBy()).isEqualTo("VOTING");
    }

    @Test
    void multiLevelChainsResolve() {
        var top = company("Top");
        var mid = company("Mid");
        var x = person("X");
        var r = run(List.of(top, mid, x), List.of(pos(top, "80", "80")),
                List.of(link(mid, top, "50", "50"), link(x, mid, "100", "100")));
        assertThat(r.beneficialOwners().get(0).economicPct()).isEqualByComparingTo("40");
        assertThat(r.beneficialOwners().get(0).paths().get(0).chain()).hasSize(4);
    }

    @Test
    void companyWithNoRecordedOwnersIsReportedAsAGap() {
        var holdco = company("Holdco");
        var r = run(List.of(holdco), List.of(pos(holdco, "40", "40")), List.of());
        assertThat(r.beneficialOwners()).isEmpty();
        assertThat(r.gaps()).hasSize(1);
        assertThat(r.gaps().get(0).economicPct()).isEqualByComparingTo("40");
    }

    @Test
    void partiallyTracedOwnershipLeavesTheRemainderAsAGap() {
        var holdco = company("Holdco");
        var x = person("X");
        var r = run(List.of(holdco, x), List.of(pos(holdco, "50", "50")), List.of(link(x, holdco, "60", "60")));
        assertThat(r.beneficialOwners().get(0).economicPct()).isEqualByComparingTo("30");
        assertThat(r.gaps().get(0).economicPct()).isEqualByComparingTo("20");
    }

    @Test
    void circularOwnershipTerminatesWithAWarning() {
        var a = company("A");
        var b = company("B");
        var x = person("X");
        var r = run(List.of(a, b, x), List.of(pos(a, "50", "50")),
                List.of(link(b, a, "50", "50"), link(a, b, "50", "50"), link(x, b, "50", "50")));
        assertThat(r.warnings()).anyMatch(w -> w.contains("Circular"));
        assertThat(r.belowThreshold().get(0).economicPct()).isEqualByComparingTo("12.5");
    }
}
