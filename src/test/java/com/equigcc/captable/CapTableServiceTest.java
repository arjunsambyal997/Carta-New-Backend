package com.equigcc.captable;

import com.equigcc.certificate.ShareIssuance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapTableServiceTest {

    private final CapTableService service = new CapTableService(null, null,
            Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC));

    private final ShareClass classA = shareClass("Class A", ShareClassType.COMMON_VOTING, "1");
    private final ShareClass classB = shareClass("Class B", ShareClassType.NON_VOTING_ECONOMIC, "0");
    private final ShareClass founder = shareClass("Founder Pref", ShareClassType.PREFERRED_FOUNDER, "10");
    private final ShareClass treasury = shareClass("Treasury", ShareClassType.TREASURY, "0");

    private final Shareholder fatima = holder("Fatima Al Mansoori");
    private final Shareholder familyHolding = holder("Al Mansoori Family Holding Ltd");
    private final Shareholder employee = holder("Omar Haddad");

    @Test
    void computesIssuedFullyDilutedAndVotingSplit() {
        var table = service.compute(UUID.randomUUID(), List.of(
                row(fatima, founder, 100_000, InstrumentType.SHARES),        // 1,000,000 votes
                row(familyHolding, classA, 600_000, InstrumentType.SHARES),  //   600,000 votes
                row(familyHolding, classB, 200_000, InstrumentType.SHARES),  //         0 votes
                row(employee, classA, 100_000, InstrumentType.OPTION),       // FD only
                row(fatima, treasury, 50_000, InstrumentType.SHARES)));      // excluded

        assertThat(table.totalIssuedShares()).isEqualTo(900_000);
        assertThat(table.totalFullyDilutedShares()).isEqualTo(1_000_000);
        assertThat(table.treasuryShares()).isEqualTo(50_000);
        assertThat(table.totalVotingPower()).isEqualByComparingTo("1600000");

        var family = position(table, familyHolding);
        assertThat(family.issuedShares()).isEqualTo(800_000);
        assertThat(family.issuedOwnershipPct()).isEqualByComparingTo("88.8889");
        assertThat(family.fullyDilutedOwnershipPct()).isEqualByComparingTo("80.0000");
        assertThat(family.votingPct()).isEqualByComparingTo("37.5000");

        var f = position(table, fatima);
        assertThat(f.issuedOwnershipPct()).isEqualByComparingTo("11.1111");
        assertThat(f.votingPct()).isEqualByComparingTo("62.5000");

        var e = position(table, employee);
        assertThat(e.issuedShares()).isZero();
        assertThat(e.fullyDilutedOwnershipPct()).isEqualByComparingTo("10.0000");
        assertThat(e.votingPower()).isEqualByComparingTo("0");

        assertThat(table.holders().getFirst().shareholderId()).isEqualTo(familyHolding.getId());
        assertThat(table.classes()).hasSize(3);
    }

    @Test
    void emptyRegisterYieldsZeroes() {
        var table = service.compute(UUID.randomUUID(), List.of());
        assertThat(table.totalIssuedShares()).isZero();
        assertThat(table.totalVotingPower()).isEqualByComparingTo("0");
        assertThat(table.holders()).isEmpty();
    }

    private static CapTable.HolderPosition position(CapTable t, Shareholder h) {
        return t.holders().stream().filter(p -> p.shareholderId().equals(h.getId())).findFirst().orElseThrow();
    }

    private static ShareClass shareClass(String name, ShareClassType type, String votes) {
        var c = new ShareClass();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setClassType(type);
        c.setParValue(BigDecimal.ONE);
        c.setCurrency(Currency.AED);
        c.setVotingMultiplier(new BigDecimal(votes));
        return c;
    }

    private static Shareholder holder(String name) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn(name);
        return h;
    }

    private static ShareIssuance row(Shareholder h, ShareClass c, long qty, InstrumentType type) {
        var i = new ShareIssuance();
        i.setShareholder(h);
        i.setShareClass(c);
        i.setQuantity(qty);
        i.setInstrumentType(type);
        return i;
    }
}
