package com.equigcc.service;

import com.equigcc.domain.BoardResolution;
import com.equigcc.domain.Organization;
import com.equigcc.domain.ResolutionStatus;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.VoteRecord;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.MembershipRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.repository.VoteRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final NoticeMailer mailer = mock(NoticeMailer.class);
    private final MembershipRepository members = mock(MembershipRepository.class);
    private final ShareholderRepository shareholders = mock(ShareholderRepository.class);
    private final BoardResolutionRepository resolutions = mock(BoardResolutionRepository.class);
    private final VoteRecordRepository votes = mock(VoteRecordRepository.class);
    private final ShareIssuanceRepository issuances = mock(ShareIssuanceRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private NoticeService service(boolean enabled) {
        return new NoticeService(mailer, members, shareholders, resolutions, votes, issuances, clock, "http://app", "http://app/verify/", enabled);
    }

    private static Shareholder holder(String email) {
        var h = new Shareholder();
        h.setId(UUID.randomUUID());
        h.setFullNameEn("H");
        h.setEmail(email);
        return h;
    }

    private BoardResolution due(UUID orgId) {
        var org = new Organization();
        org.setId(orgId);
        var r = new BoardResolution();
        r.setId(UUID.randomUUID());
        r.setOrganization(org);
        r.setTitle("Approve budget");
        r.setStatus(ResolutionStatus.ACTIVE);
        r.setVotingDeadline(NOW.plusSeconds(3600));
        return r;
    }

    @Test
    void reminderGoesOnlyToHoldersWhoHaveNotVotedAndIsSentOnce() {
        UUID orgId = UUID.randomUUID();
        var r = due(orgId);
        var voted = holder("voted@example.com");
        var waiting = holder("waiting@example.com");
        var vote = new VoteRecord();
        vote.setShareholder(voted);
        when(resolutions.findDueForReminder(any(), any())).thenReturn(List.of(r));
        when(votes.findByResolution(r.getId())).thenReturn(List.of(vote));
        when(members.findByOrganization(orgId)).thenReturn(List.of());
        when(shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId)).thenReturn(List.of(voted, waiting));

        assertThat(service(true).sendDueReminders()).isEqualTo(1);

        verify(mailer).send(eq("waiting@example.com"), any(), any());
        verify(mailer, never()).send(eq("voted@example.com"), any(), any());
        assertThat(r.getReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    void aFailingMailServerNeverBreaksTheJob() {
        UUID orgId = UUID.randomUUID();
        var r = due(orgId);
        when(resolutions.findDueForReminder(any(), any())).thenReturn(List.of(r));
        when(votes.findByResolution(r.getId())).thenReturn(List.of());
        when(members.findByOrganization(orgId)).thenReturn(List.of());
        when(shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId)).thenReturn(List.of(holder("a@example.com")));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(mailer).send(any(), any(), any());

        assertThat(service(true).sendDueReminders()).isEqualTo(1);
    }

    @Test
    void disabledMeansSilent() {
        assertThat(service(false).sendDueReminders()).isZero();
        service(false).resolutionOpened(UUID.randomUUID(), UUID.randomUUID());
        verify(mailer, never()).send(any(), any(), any());
    }
}
