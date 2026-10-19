package com.equigcc.compliance;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditLog;
import com.equigcc.audit.AuditLogRepository;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.organization.Jurisdiction;
import com.equigcc.organization.Organization;
import com.equigcc.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplianceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final UUID orgId = UUID.randomUUID();
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final ComplianceFilingRepository filings = mock(ComplianceFilingRepository.class);
    private final AuditLogRepository auditLog = mock(AuditLogRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ComplianceService service =
            new ComplianceService(orgs, filings, auditLog, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    private void org(Jurisdiction j) {
        var o = new Organization();
        o.setId(orgId);
        o.setJurisdiction(j);
        ReflectionTestUtils.setField(o, "createdAt", Instant.parse("2026-03-01T00:00:00Z"));
        when(orgs.findById(orgId)).thenReturn(Optional.of(o));
        when(filings.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());
        when(filings.save(any(ComplianceFiling.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void change(String at) {
        var e = new AuditLog(orgId, "ShareIssuance", UUID.randomUUID(), AuditAction.CREATE, "x", "u", Instant.parse(at), null, "h");
        when(auditLog.findFirstByOrganizationIdAndEntityNameInAndOccurredAtAfterOrderByIdAsc(any(), any(), any())).thenReturn(Optional.of(e));
    }

    private void noChange() {
        when(auditLog.findFirstByOrganizationIdAndEntityNameInAndOccurredAtAfterOrderByIdAsc(any(), any(), any())).thenReturn(Optional.empty());
    }

    private ComplianceService.Item item(String key) {
        return service.view(orgId).items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void workingDaysSkipTheWeekend() {
        var satSun = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(ComplianceService.addWorkingDays(LocalDate.of(2026, 10, 16), 1, satSun)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(ComplianceService.addWorkingDays(LocalDate.of(2026, 10, 16), 15, satSun)).isEqualTo(LocalDate.of(2026, 11, 6));
        var friSat = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        assertThat(ComplianceService.addWorkingDays(LocalDate.of(2026, 10, 15), 1, friSat)).isEqualTo(LocalDate.of(2026, 10, 18));
    }

    @Test
    void noRegisterChangeMeansUpToDate() {
        org(Jurisdiction.MAINLAND_UAE);
        noChange();
        assertThat(item("UAE_REGISTRY_CHANGE").status()).isEqualTo(ComplianceService.Status.UP_TO_DATE);
    }

    @Test
    void recentChangeIsPendingAndOldChangeIsOverdue() {
        org(Jurisdiction.MAINLAND_UAE);
        change("2026-10-16T09:00:00Z");
        var recent = item("UAE_REGISTRY_CHANGE");
        assertThat(recent.dueDate()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(recent.status()).isEqualTo(ComplianceService.Status.PENDING);

        change("2026-08-03T09:00:00Z");
        assertThat(item("UAE_REGISTRY_CHANGE").status()).isEqualTo(ComplianceService.Status.OVERDUE);
    }

    @Test
    void dueWithinAWeekIsFlaggedSoon() {
        org(Jurisdiction.KSA);
        change("2026-10-07T09:00:00Z");
        assertThat(item("KSA_REGISTER_FILING").status()).isEqualTo(ComplianceService.Status.DUE_SOON);
    }

    @Test
    void onlyRulesForTheCompanysJurisdictionAreListed() {
        org(Jurisdiction.DIFC);
        noChange();
        assertThat(service.view(orgId).items()).extracting(ComplianceService.Item::key).containsExactly("DIFC_UBO_CHANGE");
    }

    @Test
    void filingIsRecordedWithReferenceTrimmed() {
        org(Jurisdiction.KSA);
        noChange();
        var it = service.recordFiling(orgId, "KSA_REGISTER_FILING", null, " REF-1 ", "sec");
        assertThat(it.lastFiledOn()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(it.lastReference()).isEqualTo("REF-1");
        assertThat(it.status()).isEqualTo(ComplianceService.Status.UP_TO_DATE);
    }

    @Test
    void futureDatedOrForeignFilingsAreRejected() {
        org(Jurisdiction.KSA);
        assertThatThrownBy(() -> service.recordFiling(orgId, "KSA_REGISTER_FILING", LocalDate.of(2026, 10, 20), null, "u"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.recordFiling(orgId, "UAE_REGISTRY_CHANGE", null, null, "u"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void januaryFilingIsDueEndOfNextJanuaryUntilFiled() {
        org(Jurisdiction.MAINLAND_UAE);
        noChange();
        assertThat(item("UAE_LLC_PARTNERS_ANNUAL").dueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }
}
