package com.equigcc.service;

import com.equigcc.domain.BoardResolution;
import com.equigcc.domain.Currency;
import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.IssuanceStatus;
import com.equigcc.domain.Organization;
import com.equigcc.domain.ResolutionStatus;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareClassType;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.SuccessionRule;
import com.equigcc.domain.SuccessionStatus;
import com.equigcc.domain.SuccessionTrigger;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.repository.SuccessionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuccessionServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final SuccessionRuleRepository rules = mock(SuccessionRuleRepository.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final ShareClassRepository classes = mock(ShareClassRepository.class);
    private final ShareholderRepository holders = mock(ShareholderRepository.class);
    private final ShareIssuanceRepository issuances = mock(ShareIssuanceRepository.class);
    private final BoardResolutionRepository resolutions = mock(BoardResolutionRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
    private final SuccessionService service = new SuccessionService(rules, orgs, classes, holders, issuances, resolutions,
            new CertificateHasher(), audit, clock);

    private Organization org;
    private ShareClass founder;
    private ShareClass ordinary;
    private Shareholder fatima;
    private Shareholder heir;
    private ShareIssuance cert;
    private SuccessionRule rule;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setId(orgId);
        org.setCertificatePrefix("ALN");
        org.setCommercialLicenseNo("CN-1");
        founder = cls("Founder", 10L);
        ordinary = cls("Ordinary", 1_000_000L);
        fatima = holder("Fatima");
        heir = holder("Heir");
        cert = issuance(fatima, founder, 1000, "2.50");
        rule = rule(null, null, 1, 1);
        when(orgs.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));
        when(orgs.findById(orgId)).thenReturn(Optional.of(org));
        when(issuances.findActiveByOrganization(orgId)).thenReturn(List.of(cert));
        when(issuances.findById(cert.getId())).thenReturn(Optional.of(cert));
        when(issuances.sumIssuedQuantityByClass(ordinary.getId())).thenReturn(0L);
        when(issuances.save(any())).thenAnswer(i -> {
            ShareIssuance s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(classes.findByIdAndOrganizationId(founder.getId(), orgId)).thenReturn(Optional.of(founder));
        when(classes.findByIdAndOrganizationId(ordinary.getId(), orgId)).thenReturn(Optional.of(ordinary));
        when(holders.findByIdAndOrganizationId(heir.getId(), orgId)).thenReturn(Optional.of(heir));
    }

    private ShareClass cls(String name, Long authorized) {
        var c = new ShareClass();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setClassType(ShareClassType.COMMON_VOTING);
        c.setParValue(BigDecimal.ONE);
        c.setAuthorizedShares(authorized);
        return c;
    }

    private Shareholder holder(String name) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn(name);
        return h;
    }

    private ShareIssuance issuance(Shareholder h, ShareClass c, long qty, String price) {
        var i = new ShareIssuance();
        i.setId(UUID.randomUUID());
        i.setOrganization(org);
        i.setShareholder(h);
        i.setShareClass(c);
        i.setInstrumentType(InstrumentType.SHARES);
        i.setQuantity(qty);
        i.setPricePerShare(new BigDecimal(price));
        i.setCurrency(Currency.AED);
        i.setDateIssued(LocalDate.of(2025, 1, 1));
        i.setCertificateNo("ALN-000001");
        i.setRecordHash("x");
        return i;
    }

    private SuccessionRule rule(Shareholder subject, Shareholder beneficiary, long num, long den) {
        var r = new SuccessionRule();
        r.setId(UUID.randomUUID());
        r.setOrganization(org);
        r.setName("Founder succession");
        r.setTriggerEvent(SuccessionTrigger.FOUNDER_DEATH);
        r.setFromClass(founder);
        r.setToClass(ordinary);
        r.setSubject(subject);
        r.setBeneficiary(beneficiary);
        r.setRatioNumerator(num);
        r.setRatioDenominator(den);
        r.setLegalReference("AoA art. 14");
        r.setCreatedBy("u");
        when(rules.findByIdAndOrganizationId(r.getId(), orgId)).thenReturn(Optional.of(r));
        when(rules.lockByIdAndOrganizationId(r.getId(), orgId)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void executeConvertsKeepsTotalConsiderationAndLinksPredecessor() {
        var created = service.execute(orgId, rule.getId(), null, "u@x.ae");
        assertThat(created).hasSize(1);
        var n = created.get(0);
        assertThat(n.getShareClass()).isSameAs(ordinary);
        assertThat(n.getShareholder()).isSameAs(fatima);
        assertThat(n.getQuantity()).isEqualTo(1000);
        assertThat(n.getPricePerShare()).isEqualByComparingTo("2.5");
        assertThat(n.getPredecessorId()).isEqualTo(cert.getId());
        assertThat(n.getSuccessionRuleId()).isEqualTo(rule.getId());
        assertThat(n.getCertificateNo()).isEqualTo("ALN-000001");
        assertThat(n.getDateIssued()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(n.getRecordHash()).hasSize(64);
        assertThat(cert.getStatus()).isEqualTo(IssuanceStatus.CONVERTED);
        assertThat(rule.getStatus()).isEqualTo(SuccessionStatus.EXECUTED);
        assertThat(rule.getExecutedBy()).isEqualTo("u@x.ae");
        verify(audit, times(2)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ratioChangesQuantityAndRespreadsThePrice() {
        rule = rule(null, null, 10, 1); // 10 new shares per 1 old
        var plan = service.preview(orgId, rule.getId());
        assertThat(plan.get(0).toQuantity()).isEqualTo(10_000);
        assertThat(plan.get(0).toPricePerShare()).isEqualByComparingTo("0.25");
        assertThat(plan.get(0).problem()).isNull();
    }

    @Test
    void beneficiaryReceivesTheSharesAndTheOldCertificateIsTransferred() {
        rule = rule(fatima, heir, 1, 1);
        var created = service.execute(orgId, rule.getId(), null, "u");
        assertThat(created.get(0).getShareholder()).isSameAs(heir);
        assertThat(cert.getStatus()).isEqualTo(IssuanceStatus.TRANSFERRED);
    }

    @Test
    void subjectLimitsWhichHoldersAreConverted() {
        var other = issuance(heir, founder, 500, "1");
        when(issuances.findActiveByOrganization(orgId)).thenReturn(List.of(cert, other));
        rule = rule(heir, null, 1, 1);
        var plan = service.preview(orgId, rule.getId());
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).holderId()).isEqualTo(heir.getId());
    }

    @Test
    void fractionalSharesAreRefused() {
        rule = rule(null, null, 1, 3); // 1000 -> 333.33
        assertThat(service.preview(orgId, rule.getId()).get(0).problem()).contains("fractional");
        var id = rule.getId();
        assertThatThrownBy(() -> service.execute(orgId, id, null, "u")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("fractional");
        assertThat(cert.getStatus()).isEqualTo(IssuanceStatus.ISSUED);
    }

    @Test
    void authorizedHeadroomOfTheTargetClassIsEnforced() {
        ordinary.setAuthorizedShares(500L);
        assertThatThrownBy(() -> service.execute(orgId, rule.getId(), null, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("authorized");
        verify(issuances, never()).save(any());
    }

    @Test
    void aRuleExecutesOnlyOnce() {
        rule.setStatus(SuccessionStatus.EXECUTED);
        assertThatThrownBy(() -> service.execute(orgId, rule.getId(), null, "u")).hasMessageContaining("already");
    }

    @Test
    void nothingToConvertIsAnError() {
        when(issuances.findActiveByOrganization(orgId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.execute(orgId, rule.getId(), null, "u")).hasMessageContaining("No shares");
    }

    @Test
    void anAuthorisingResolutionMustHavePassed() {
        var res = new BoardResolution();
        res.setStatus(ResolutionStatus.ACTIVE);
        UUID rid = UUID.randomUUID();
        when(resolutions.findByIdAndOrganizationId(rid, orgId)).thenReturn(Optional.of(res));
        assertThatThrownBy(() -> service.execute(orgId, rule.getId(), rid, "u")).hasMessageContaining("not passed");
        res.setStatus(ResolutionStatus.PASSED);
        assertThat(service.execute(orgId, rule.getId(), rid, "u")).hasSize(1);
        assertThat(rule.getResolutionId()).isEqualTo(rid);
    }

    @Test
    void createValidatesClassesAndRatio() {
        var same = new SuccessionService.NewRule("r", SuccessionTrigger.CUSTOM, null, founder.getId(), founder.getId(), 1, 1, null, "x");
        assertThatThrownBy(() -> service.create(orgId, same, "u")).hasMessageContaining("differ");
        var zero = new SuccessionService.NewRule("r", SuccessionTrigger.CUSTOM, null, founder.getId(), ordinary.getId(), 0, 1, null, "x");
        assertThatThrownBy(() -> service.create(orgId, zero, "u")).hasMessageContaining("ratio");
        var treasury = cls("Treasury", null);
        treasury.setClassType(ShareClassType.TREASURY);
        when(classes.findByIdAndOrganizationId(treasury.getId(), orgId)).thenReturn(Optional.of(treasury));
        var t = new SuccessionService.NewRule("r", SuccessionTrigger.CUSTOM, null, founder.getId(), treasury.getId(), 1, 1, null, "x");
        assertThatThrownBy(() -> service.create(orgId, t, "u")).hasMessageContaining("Treasury");
    }

    @Test
    void onlyDraftRulesCanBeCancelled() {
        service.cancel(orgId, rule.getId(), "u");
        assertThat(rule.getStatus()).isEqualTo(SuccessionStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(orgId, rule.getId(), "u")).hasMessageContaining("draft");
    }
}
