package com.equigcc.service;

import com.equigcc.domain.AppUser;
import com.equigcc.domain.Membership;
import com.equigcc.domain.Organization;
import com.equigcc.domain.PendingChange;
import com.equigcc.domain.Role;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.domain.Shareholder;
import com.equigcc.repository.MembershipRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.PendingChangeRepository;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.web.dto.RegisterToolsDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final UUID orgId = UUID.randomUUID();
    private final PendingChangeRepository changes = mock(PendingChangeRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final MembershipRepository members = mock(MembershipRepository.class);
    private final ShareholderRepository shareholders = mock(ShareholderRepository.class);
    private final ShareClassRepository classes = mock(ShareClassRepository.class);
    private final CertificateIssuanceService issuance = mock(CertificateIssuanceService.class);
    private final TransferService transfers = mock(TransferService.class);
    private final VestingService vesting = mock(VestingService.class);
    private final NoticeService notices = mock(NoticeService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApprovalService service = new ApprovalService(changes, organizations, members, shareholders, classes, issuance, transfers,
            vesting, notices, audit, new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));

    private Organization org;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setId(orgId);
        org.setRequireApproval(true);
        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(changes.save(any(PendingChange.class))).thenAnswer(i -> {
            PendingChange p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    private static Membership member(String email, Role role) {
        var user = mock(AppUser.class);
        when(user.getEmail()).thenReturn(email);
        var m = mock(Membership.class);
        when(m.getUser()).thenReturn(user);
        when(m.getRole()).thenReturn(role);
        return m;
    }

    private static RegisterToolsDtos.TransferRequest transferRequest(UUID from, UUID to) {
        return new RegisterToolsDtos.TransferRequest(from, to, UUID.randomUUID(), 100, null, null, null, "ref");
    }

    private PendingChange pendingTransfer(UUID to) {
        var p = service.propose(orgId, PendingChange.Kind.TRANSFER, transferRequest(UUID.randomUUID(), to), "sec@example.com");
        when(changes.findForUpdate(p.getId(), orgId)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void offMeansNoApproval() {
        org.setRequireApproval(false);
        assertThat(service.needsApproval(orgId, "sec@example.com")).isFalse();
    }

    @Test
    void waitsOnlyWhenSomeoneElseCanApprove() {
        var team = List.of(member("founder@example.com", Role.FOUNDER), member("sec@example.com", Role.CORPORATE_SECRETARY));
        when(members.findByOrganization(orgId)).thenReturn(team);
        assertThat(service.needsApproval(orgId, "sec@example.com")).isTrue();
        // a secretary alone, or the founder alone, has nobody else to approve: never locked out
        assertThat(service.needsApproval(orgId, "founder@example.com")).isFalse();
    }

    @Test
    void boardMemberCountsAsAnApprover() {
        var team = List.of(member("founder@example.com", Role.FOUNDER), member("board@example.com", Role.BOARD_MEMBER));
        when(members.findByOrganization(orgId)).thenReturn(team);
        assertThat(service.needsApproval(orgId, "founder@example.com")).isTrue();
    }

    @Test
    void proposingStoresASummaryAndAuditsIt() {
        var holder = new Shareholder();
        holder.setFullNameEn("Fatima");
        when(shareholders.findByIdAndOrganizationId(any(), eq(orgId))).thenReturn(Optional.of(holder));
        var p = service.propose(orgId, PendingChange.Kind.TRANSFER, transferRequest(UUID.randomUUID(), UUID.randomUUID()), "sec@example.com");
        assertThat(p.getStatus()).isEqualTo(PendingChange.Status.PENDING);
        assertThat(p.getSummary()).contains("Transfer 100").contains("Fatima");
        verify(audit).record(eq(orgId), eq("PendingChange"), eq(p.getId()), any(), any(), eq("sec@example.com"));
    }

    @Test
    void nobodyApprovesTheirOwnChange() {
        var p = pendingTransfer(UUID.randomUUID());
        assertThatThrownBy(() -> service.approve(orgId, p.getId(), "SEC@example.com", null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("someone other");
        verify(transfers, never()).transfer(any(), any(), any());
    }

    @Test
    void approvingRunsTheTransferAndNamesBothPeopleInTheAudit() {
        UUID to = UUID.randomUUID();
        var p = pendingTransfer(to);
        var cert = mock(ShareIssuance.class);
        var holder = new Shareholder();
        holder.setId(to);
        when(cert.getShareholder()).thenReturn(holder);
        when(cert.getCertificateNo()).thenReturn("ACME-000007");
        when(transfers.transfer(eq(orgId), any(), any())).thenReturn(List.of(cert));

        var done = service.approve(orgId, p.getId(), "founder@example.com", "  looks right ");

        assertThat(done.getStatus()).isEqualTo(PendingChange.Status.APPROVED);
        assertThat(done.getResult()).isEqualTo("ACME-000007");
        assertThat(done.getDecisionNote()).isEqualTo("looks right");
        var actor = ArgumentCaptor.forClass(String.class);
        verify(transfers).transfer(eq(orgId), any(), actor.capture());
        assertThat(actor.getValue()).isEqualTo("sec@example.com (approved by founder@example.com)");
        verify(notices).certificateIssued(orgId, "ACME-000007");
    }

    @Test
    void aDecidedChangeCannotBeDecidedAgain() {
        var p = pendingTransfer(UUID.randomUUID());
        service.reject(orgId, p.getId(), "founder@example.com", "no");
        assertThat(p.getStatus()).isEqualTo(PendingChange.Status.REJECTED);
        assertThatThrownBy(() -> service.approve(orgId, p.getId(), "board@example.com", null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already rejected");
    }

    @Test
    void onlyTheRequesterCanWithdraw() {
        var p = pendingTransfer(UUID.randomUUID());
        assertThatThrownBy(() -> service.cancel(orgId, p.getId(), "founder@example.com")).isInstanceOf(BusinessRuleException.class);
        assertThat(service.cancel(orgId, p.getId(), "sec@example.com").getStatus()).isEqualTo(PendingChange.Status.CANCELLED);
    }

    @Test
    void settingCanBeToggled() {
        when(organizations.findByIdForUpdate(orgId)).thenReturn(Optional.of(org));
        assertThat(service.setRequired(orgId, false, "founder@example.com")).isFalse();
        assertThat(service.setRequired(orgId, true, "founder@example.com")).isTrue();
    }
}
