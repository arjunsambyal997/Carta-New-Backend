package com.equigcc.history;

import com.equigcc.captable.CapTable;

import com.equigcc.captable.CapTableService;

import com.equigcc.captable.Currency;
import com.equigcc.captable.InstrumentType;
import com.equigcc.certificate.IssuanceStatus;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.ShareClassType;
import com.equigcc.certificate.ShareIssuance;
import com.equigcc.captable.Shareholder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story: Fatima is issued 1,000 shares on 1 Jan 2025. On 1 Jun 2025 she sells 400 to Omar: her certificate is retired
 * (TRANSFERRED) and replaced by 600 for her and 400 for Omar. On 1 Sep 2025 Omar's 400 are cancelled (only the cap table test does this).
 */
class HistoryServiceTest {

    private final HistoryService history = new HistoryService(null, null);
    private final CapTableService capTables = new CapTableService(null, null, Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));
    private final UUID orgId = UUID.randomUUID();

    private final ShareClass ordinary = shareClass();
    private final Shareholder fatima = holder("Fatima");
    private final Shareholder omar = holder("Omar");

    private final ShareIssuance c1 = cert("A-1", fatima, 1000, "2025-01-01", IssuanceStatus.TRANSFERRED, null);
    private final ShareIssuance c2 = cert("A-2", fatima, 600, "2025-06-01", IssuanceStatus.ISSUED, c1);
    private final ShareIssuance c3 = cert("A-3", omar, 400, "2025-06-01", IssuanceStatus.ISSUED, c1);
    private final List<ShareIssuance> all = List.of(c1, c2, c3);

    @Test
    void capTableAsOfRewindsThroughTheTransferAndTheCancellation() {
        assertThat(sharesOf(capTables.computeAsOf(orgId, all, LocalDate.parse("2024-12-31")))).isEmpty();
        // before the sale only Fatima, with everything
        var jan = capTables.computeAsOf(orgId, all, LocalDate.parse("2025-03-01"));
        assertThat(jan.totalIssuedShares()).isEqualTo(1000);
        assertThat(jan.holders()).hasSize(1);
        // on the sale date the old certificate no longer counts and the two new ones do
        var june = capTables.computeAsOf(orgId, all, LocalDate.parse("2025-06-01"));
        assertThat(june.totalIssuedShares()).isEqualTo(1000);
        assertThat(june.holders()).hasSize(2);
        // after Omar's certificate is cancelled (retired on the day it was last changed) only Fatima's 600 remain
        c3.setStatus(IssuanceStatus.CANCELLED);
        org.springframework.test.util.ReflectionTestUtils.setField(c3, "updatedAt", Instant.parse("2025-09-01T10:00:00Z"));
        assertThat(capTables.computeAsOf(orgId, all, LocalDate.parse("2025-08-31")).totalIssuedShares()).isEqualTo(1000);
        var later = capTables.computeAsOf(orgId, all, LocalDate.parse("2026-01-01"));
        assertThat(later.totalIssuedShares()).isEqualTo(600);
        assertThat(later.asOf()).isEqualTo(Instant.parse("2026-01-01T23:59:59Z"));
    }

    @Test
    void ledgerShowsTheSaleFromBothSides() {
        var f = history.buildLedger(fatima.getId(), "Fatima", null, all);
        assertThat(f.currentShares()).isEqualTo(600);
        assertThat(f.events()).extracting(HistoryService.LedgerEvent::type, HistoryService.LedgerEvent::certificateNo, HistoryService.LedgerEvent::sharesAfter)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ACQUIRED", "A-1", 1000L),
                        org.assertj.core.groups.Tuple.tuple("ACQUIRED", "A-2", 1600L),   // new certificate first, then the old one is retired
                        org.assertj.core.groups.Tuple.tuple("DISPOSED", "A-1", 600L));
        var sale = f.events().getLast();
        assertThat(sale.counterpartyEn()).isEqualTo("Omar");
        assertThat(sale.reason()).isEqualTo("TRANSFER");

        var o = history.buildLedger(omar.getId(), "Omar", null, all);
        var bought = o.events().getFirst();
        assertThat(bought.type()).isEqualTo("ACQUIRED");
        assertThat(bought.counterpartyEn()).isEqualTo("Fatima");
        assertThat(bought.reason()).isEqualTo("TRANSFER");
        assertThat(o.currentShares()).isEqualTo(400);
        assertThat(o.events()).hasSize(1);
    }

    @Test
    void lineageStartsAtTheRootWhicheverCertificateYouAskAbout() {
        var tree = history.buildLineage(c3, all);
        assertThat(tree).extracting(HistoryService.LineageNode::certificateNo).containsExactly("A-1", "A-2", "A-3");
        assertThat(tree.get(0).depth()).isZero();
        assertThat(tree.get(0).reason()).isEqualTo("ISSUE");
        assertThat(tree.get(1).reason()).isEqualTo("REISSUE");
        assertThat(tree.get(2).reason()).isEqualTo("TRANSFER");
        assertThat(tree.get(2).predecessorNo()).isEqualTo("A-1");
        assertThat(tree).filteredOn(HistoryService.LineageNode::current).extracting(HistoryService.LineageNode::certificateNo).containsExactly("A-3");
    }

    private static List<CapTable.HolderPosition> sharesOf(CapTable t) {
        return t.holders();
    }

    private ShareIssuance cert(String no, Shareholder h, long qty, String date, IssuanceStatus status, ShareIssuance predecessor) {
        var i = new ShareIssuance();
        i.setId(UUID.randomUUID());
        i.setCertificateNo(no);
        i.setShareholder(h);
        i.setShareClass(ordinary);
        i.setQuantity(qty);
        i.setInstrumentType(InstrumentType.SHARES);
        i.setDateIssued(LocalDate.parse(date));
        i.setStatus(status);
        if (predecessor != null) {
            i.setPredecessorId(predecessor.getId());
        }
        return i;
    }

    private static ShareClass shareClass() {
        var c = new ShareClass();
        c.setId(UUID.randomUUID());
        c.setName("Ordinary");
        c.setClassType(ShareClassType.COMMON_VOTING);
        c.setParValue(BigDecimal.ONE);
        c.setCurrency(Currency.AED);
        c.setVotingMultiplier(BigDecimal.ONE);
        return c;
    }

    private static Shareholder holder(String name) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn(name);
        return h;
    }
}
