package com.equigcc.notice;

import com.equigcc.auth.MembershipRepository;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.governance.BoardResolution;
import com.equigcc.governance.BoardResolutionRepository;
import com.equigcc.governance.ResolutionStatus;
import com.equigcc.governance.VoteRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Email notices: a vote opens, a vote is about to close, a certificate is issued. Sending is best effort and runs
 * after the register change has committed, so a mail failure can never undo or block a register change.
 */
@Service
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);
    static final Duration REMINDER_WINDOW = Duration.ofHours(24);

    private final NoticeMailer mailer;
    private final MembershipRepository members;
    private final ShareholderRepository shareholders;
    private final BoardResolutionRepository resolutions;
    private final VoteRecordRepository votes;
    private final ShareIssuanceRepository issuances;
    private final Clock clock;
    private final String baseUrl;
    private final String verifyUrl;
    private final boolean enabled;

    public NoticeService(NoticeMailer mailer, MembershipRepository members, ShareholderRepository shareholders,
                         BoardResolutionRepository resolutions, VoteRecordRepository votes, ShareIssuanceRepository issuances, Clock clock,
                         @Value("${equigcc.public-base-url}") String baseUrl,
                         @Value("${equigcc.certificates.verification-base-url}") String verifyUrl,
                         @Value("${equigcc.notices.enabled:true}") boolean enabled) {
        this.mailer = mailer;
        this.members = members;
        this.shareholders = shareholders;
        this.resolutions = resolutions;
        this.votes = votes;
        this.issuances = issuances;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.verifyUrl = verifyUrl;
        this.enabled = enabled;
    }

    @Async
    @Transactional(readOnly = true)
    public void resolutionOpened(UUID orgId, UUID resolutionId) {
        if (!enabled) {
            return;
        }
        try {
            BoardResolution r = resolutions.findByIdAndOrganizationId(resolutionId, orgId).orElse(null);
            if (r == null || r.getStatus() != ResolutionStatus.ACTIVE) {
                return;
            }
            Set<String> to = voterEmails(orgId, Set.of(), false);
            String org = r.getOrganization().getLegalNameEn();
            send(to, "Vote open: " + r.getTitle() + " / تصويت مفتوح",
                    "A vote is now open at " + org + ".\n\n" + r.getTitle() + "\nVoting closes: " + r.getVotingDeadline() + "\nVote here: " + baseUrl + "/resolutions/" + r.getId()
                            + "\n\nتم فتح التصويت في " + org + ".\n" + (r.getTitleAr() != null ? r.getTitleAr() + "\n" : "") + "آخر موعد للتصويت: " + r.getVotingDeadline() + "\n" + baseUrl + "/resolutions/" + r.getId() + "\n");
        } catch (RuntimeException e) {
            log.warn("Could not send vote-open notices for {}: {}", resolutionId, e.toString());
        }
    }

    @Async
    @Transactional(readOnly = true)
    public void certificateIssued(UUID orgId, String certificateNo) {
        if (!enabled) {
            return;
        }
        try {
            var c = issuances.findByCertificateNo(certificateNo).orElse(null);
            if (c == null || !c.getOrganization().getId().equals(orgId)) {
                return;
            }
            String email = c.getShareholder().getEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            String org = c.getOrganization().getLegalNameEn();
            send(Set.of(email.trim()), "Share certificate " + c.getCertificateNo() + " issued / تم إصدار شهادة أسهم",
                    "A share certificate has been issued to you by " + org + ".\n\nCertificate: " + c.getCertificateNo() + "\nQuantity: " + c.getQuantity() + " " + c.getShareClass().getName()
                            + "\nCheck it is genuine: " + verifyUrl + c.getCertificateNo() + "?h=" + c.getRecordHash()
                            + "\n\nتم إصدار شهادة أسهم لك من " + org + ".\nرقم الشهادة: " + c.getCertificateNo() + "\n");
        } catch (RuntimeException e) {
            log.warn("Could not send certificate notice for {}: {}", certificateNo, e.toString());
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** Emails everyone who can still vote on an open resolution, now. Returns how many addresses it went to. */
    @Transactional(readOnly = true)
    public int remindNonVoters(UUID orgId, UUID resolutionId) {
        BoardResolution r = resolutions.findByIdAndOrganizationId(resolutionId, orgId).orElse(null);
        if (!enabled || r == null || r.getStatus() != ResolutionStatus.ACTIVE) {
            return 0;
        }
        Set<UUID> voted = votes.findByResolution(r.getId()).stream().map(v -> v.getShareholder().getId()).collect(Collectors.toSet());
        Set<String> to = voterEmails(orgId, voted, true);
        sendReminder(r, to);
        return to.size();
    }

    @Async
    @Transactional(readOnly = true)
    public void resolutionClosed(UUID orgId, UUID resolutionId) {
        if (!enabled) {
            return;
        }
        try {
            BoardResolution r = resolutions.findByIdAndOrganizationId(resolutionId, orgId).orElse(null);
            if (r == null || (r.getStatus() != ResolutionStatus.PASSED && r.getStatus() != ResolutionStatus.REJECTED)) {
                return;
            }
            boolean passed = r.getStatus() == ResolutionStatus.PASSED;
            String org = r.getOrganization().getLegalNameEn();
            send(voterEmails(orgId, Set.of(), false), (passed ? "Passed: " : "Rejected: ") + r.getTitle() + (passed ? " / تمت الموافقة" : " / تم الرفض"),
                    "Voting has closed at " + org + ".\n\n" + r.getTitle() + "\nResult: " + (passed ? "PASSED" : "REJECTED") + "\nDetails: " + baseUrl + "/resolutions/" + r.getId()
                            + "\n\nأُغلق التصويت في " + org + ".\n" + (r.getTitleAr() != null ? r.getTitleAr() + "\n" : "") + "النتيجة: " + (passed ? "تمت الموافقة" : "مرفوض") + "\n");
        } catch (RuntimeException e) {
            log.warn("Could not send result notices for {}: {}", resolutionId, e.toString());
        }
    }

    /** Sends the one-off deadline reminder to everyone who has not voted yet. Returns how many resolutions were handled. */
    @Scheduled(fixedDelayString = "${equigcc.notices.reminder-check:PT15M}", initialDelayString = "PT1M")
    @Transactional
    public int sendDueReminders() {
        if (!enabled) {
            return 0;
        }
        Instant now = clock.instant();
        int handled = 0;
        for (BoardResolution r : resolutions.findDueForReminder(now, now.plus(REMINDER_WINDOW))) {
            try {
                Set<UUID> voted = votes.findByResolution(r.getId()).stream().map(v -> v.getShareholder().getId()).collect(Collectors.toSet());
                sendReminder(r, voterEmails(r.getOrganization().getId(), voted, true));
                r.setReminderSentAt(now);
                handled++;
            } catch (RuntimeException e) {
                log.warn("Could not send reminder for {}: {}", r.getId(), e.toString());
            }
        }
        return handled;
    }

    private void sendReminder(BoardResolution r, Set<String> to) {
        send(to, "Reminder: vote closes soon on " + r.getTitle() + " / تذكير بالتصويت",
                "You have not voted yet on: " + r.getTitle() + "\nVoting closes: " + r.getVotingDeadline() + "\nVote here: " + baseUrl + "/resolutions/" + r.getId()
                        + "\n\nلم تصوّت بعد على: " + (r.getTitleAr() != null ? r.getTitleAr() : r.getTitle()) + "\nآخر موعد: " + r.getVotingDeadline() + "\n");
    }

    /** Members and shareholders with an email, minus holders who already voted. For reminders only people who can vote are kept. */
    private Set<String> voterEmails(UUID orgId, Set<UUID> alreadyVoted, boolean votersOnly) {
        Set<String> out = new LinkedHashSet<>();
        for (var m : members.findByOrganization(orgId)) {
            if (votersOnly && m.getShareholder() == null) {
                continue;
            }
            if (m.getShareholder() != null && alreadyVoted.contains(m.getShareholder().getId())) {
                continue;
            }
            out.add(m.getUser().getEmail().toLowerCase(Locale.ROOT));
        }
        for (var h : shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId)) {
            if (h.getEmail() != null && !h.getEmail().isBlank() && !alreadyVoted.contains(h.getId())) {
                out.add(h.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private void send(Set<String> to, String subject, String body) {
        for (String addr : to) {
            try {
                mailer.send(addr, subject, body);
            } catch (RuntimeException e) {
                log.warn("Notice to {} failed: {}", addr, e.toString());
            }
        }
    }
}
