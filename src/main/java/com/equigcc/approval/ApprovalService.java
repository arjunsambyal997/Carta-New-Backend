package com.equigcc.approval;

import com.equigcc.certificate.IssueCertificateCommand;
import com.equigcc.audit.AuditService;
import com.equigcc.certificate.CertificateIssuanceService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.equity.VestingService;
import com.equigcc.notice.NoticeService;
import com.equigcc.register.TransferService;

import com.equigcc.audit.AuditAction;
import com.equigcc.captable.InstrumentType;
import com.equigcc.approval.PendingChange.Kind;
import com.equigcc.approval.PendingChange.Status;
import com.equigcc.auth.Role;
import com.equigcc.certificate.ShareIssuance;
import com.equigcc.auth.MembershipRepository;
import com.equigcc.organization.OrganizationRepository;
import com.equigcc.captable.ShareClassRepository;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.certificate.IssueCertificateRequest;
import com.equigcc.equity.OptionsAndRoundsDtos;
import com.equigcc.register.RegisterToolsDtos;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Two-person approval. When an organization turns it on, issuing, transferring, buying back and exercising
 * are recorded as pending changes. A different founder or board member then approves (the change runs through
 * the normal services, so every normal rule still applies) or rejects it.
 */
@Service
public class ApprovalService {

    public record ExercisePayload(String certificateNo, OptionsAndRoundsDtos.ExerciseRequest request) {
    }

    private static final Set<Role> APPROVERS = Set.of(Role.FOUNDER, Role.BOARD_MEMBER);

    private final PendingChangeRepository changes;
    private final OrganizationRepository organizations;
    private final MembershipRepository members;
    private final ShareholderRepository shareholders;
    private final ShareClassRepository classes;
    private final CertificateIssuanceService issuance;
    private final TransferService transfers;
    private final VestingService vesting;
    private final NoticeService notices;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public ApprovalService(PendingChangeRepository changes, OrganizationRepository organizations, MembershipRepository members,
                           ShareholderRepository shareholders, ShareClassRepository classes, CertificateIssuanceService issuance,
                           TransferService transfers, VestingService vesting, NoticeService notices, AuditService audit,
                           ObjectMapper json, Clock clock) {
        this.changes = changes;
        this.organizations = organizations;
        this.members = members;
        this.shareholders = shareholders;
        this.classes = classes;
        this.issuance = issuance;
        this.transfers = transfers;
        this.vesting = vesting;
        this.notices = notices;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    /**
     * True when this change must wait for a second person: the organization requires approval and somebody other
     * than the requester could approve it. A sole founder with nobody else to approve is never locked out.
     */
    @Transactional(readOnly = true)
    public boolean needsApproval(UUID orgId, String requester) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        if (!org.isRequireApproval()) {
            return false;
        }
        return members.findByOrganization(orgId).stream()
                .anyMatch(m -> APPROVERS.contains(m.getRole()) && !sameUser(m.getUser().getEmail(), requester));
    }

    @Transactional
    public PendingChange propose(UUID orgId, Kind kind, Object request, String requester) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var p = new PendingChange();
        p.setOrganization(org);
        p.setKind(kind);
        p.setPayload(write(request));
        p.setSummary(summarize(orgId, kind, request));
        p.setRequestedBy(requester);
        p = changes.save(p);
        audit.record(orgId, "PendingChange", p.getId(), AuditAction.CREATE, "approval requested: " + p.getSummary(), requester);
        return p;
    }

    @Transactional(readOnly = true)
    public List<PendingChange> list(UUID orgId) {
        return changes.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public PendingChange approve(UUID orgId, UUID id, String approver, String note) {
        var p = lock(orgId, id);
        if (sameUser(p.getRequestedBy(), approver)) {
            throw new BusinessRuleException("A change must be approved by someone other than the person who requested it");
        }
        String result = execute(p, approver);
        p.setStatus(Status.APPROVED);
        p.setDecidedBy(approver);
        p.setDecidedAt(clock.instant());
        p.setDecisionNote(clean(note));
        p.setResult(result);
        audit.record(orgId, "PendingChange", id, AuditAction.UPDATE, "approved: " + p.getSummary() + " -> " + result, approver);
        return p;
    }

    @Transactional
    public PendingChange reject(UUID orgId, UUID id, String user, String note) {
        var p = lock(orgId, id);
        p.setStatus(Status.REJECTED);
        p.setDecidedBy(user);
        p.setDecidedAt(clock.instant());
        p.setDecisionNote(clean(note));
        audit.record(orgId, "PendingChange", id, AuditAction.UPDATE, "rejected: " + p.getSummary(), user);
        return p;
    }

    /** The requester withdraws their own change. */
    @Transactional
    public PendingChange cancel(UUID orgId, UUID id, String user) {
        var p = lock(orgId, id);
        if (!sameUser(p.getRequestedBy(), user)) {
            throw new BusinessRuleException("Only the person who requested a change can withdraw it");
        }
        p.setStatus(Status.CANCELLED);
        p.setDecidedBy(user);
        p.setDecidedAt(clock.instant());
        audit.record(orgId, "PendingChange", id, AuditAction.UPDATE, "withdrawn: " + p.getSummary(), user);
        return p;
    }

    @Transactional
    public boolean setRequired(UUID orgId, boolean required, String user) {
        var org = organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        if (org.isRequireApproval() != required) {
            org.setRequireApproval(required);
            audit.record(orgId, "Organization", orgId, AuditAction.UPDATE, "two-person approval " + (required ? "turned on" : "turned off"), user);
        }
        return org.isRequireApproval();
    }

    private String execute(PendingChange p, String approver) {
        UUID orgId = p.getOrganization().getId();
        // The audit trail names both people: who asked and who approved.
        String actor = p.getRequestedBy() + " (approved by " + approver + ")";
        List<ShareIssuance> created;
        switch (p.getKind()) {
            case ISSUE_CERTIFICATE -> {
                var r = read(p, IssueCertificateRequest.class);
                created = List.of(issuance.issue(new IssueCertificateCommand(orgId, r.shareholderId(), r.shareClassId(), r.instrumentType(), r.quantity(),
                        r.pricePerShare(), r.currency(), r.dateIssued(), actor)));
            }
            case TRANSFER -> {
                var r = read(p, RegisterToolsDtos.TransferRequest.class);
                created = transfers.transfer(orgId, new TransferService.Transfer(r.fromShareholderId(), r.toShareholderId(), r.shareClassId(), r.quantity(),
                        r.pricePerShare(), r.date(), r.resolutionId(), r.reference()), actor).stream()
                        .filter(i -> i.getShareholder().getId().equals(r.toShareholderId())).toList();
            }
            case BUY_BACK -> {
                var r = read(p, RegisterToolsDtos.BuyBackRequest.class);
                transfers.buyBack(orgId, new TransferService.BuyBack(r.fromShareholderId(), r.shareClassId(), r.quantity(), r.pricePerShare(),
                        r.date(), r.resolutionId(), r.reference()), actor);
                created = List.of();
            }
            case EXERCISE_OPTIONS -> {
                var r = read(p, ExercisePayload.class);
                created = vesting.exercise(orgId, r.certificateNo(), r.request().quantity(), r.request().shareClassId(), r.request().date(), actor).stream()
                        .filter(i -> i.getInstrumentType() == InstrumentType.SHARES).toList();
            }
            default -> throw new IllegalStateException("Unknown kind " + p.getKind());
        }
        created.forEach(i -> notices.certificateIssued(orgId, i.getCertificateNo()));
        return created.isEmpty() ? "done" : created.stream().map(ShareIssuance::getCertificateNo).collect(Collectors.joining(", "));
    }

    private String summarize(UUID orgId, Kind kind, Object req) {
        return switch (kind) {
            case ISSUE_CERTIFICATE -> {
                var r = (IssueCertificateRequest) req;
                yield "Issue " + count(r.quantity()) + " × " + className(orgId, r.shareClassId()) + " to " + holderName(orgId, r.shareholderId());
            }
            case TRANSFER -> {
                var r = (RegisterToolsDtos.TransferRequest) req;
                yield "Transfer " + count(r.quantity()) + " × " + className(orgId, r.shareClassId()) + " from " + holderName(orgId, r.fromShareholderId()) + " to " + holderName(orgId, r.toShareholderId());
            }
            case BUY_BACK -> {
                var r = (RegisterToolsDtos.BuyBackRequest) req;
                yield "Buy back " + count(r.quantity()) + " × " + className(orgId, r.shareClassId()) + " from " + holderName(orgId, r.fromShareholderId());
            }
            case EXERCISE_OPTIONS -> {
                var r = (ExercisePayload) req;
                yield "Exercise " + count(r.request().quantity()) + " options on certificate " + r.certificateNo();
            }
        };
    }

    /** 20000 -> "20,000", the way every other screen shows share counts. */
    private static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private String holderName(UUID orgId, UUID id) {
        return shareholders.findByIdAndOrganizationId(id, orgId).map(h -> h.getFullNameEn()).orElse("unknown holder");
    }

    private String className(UUID orgId, UUID id) {
        return classes.findByIdAndOrganizationId(id, orgId).map(c -> c.getName()).orElse("unknown class");
    }

    private PendingChange lock(UUID orgId, UUID id) {
        var p = changes.findForUpdate(id, orgId).orElseThrow(() -> new NotFoundException("Change " + id + " not found"));
        if (p.getStatus() != Status.PENDING) {
            throw new BusinessRuleException("This change was already " + p.getStatus().name().toLowerCase(Locale.ROOT));
        }
        return p;
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot store change", e);
        }
    }

    private <T> T read(PendingChange p, Class<T> type) {
        try {
            return json.readValue(p.getPayload(), type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored change " + p.getId() + " is unreadable", e);
        }
    }

    private static boolean sameUser(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static String clean(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String n = note.trim();
        return n.substring(0, Math.min(500, n.length()));
    }
}
