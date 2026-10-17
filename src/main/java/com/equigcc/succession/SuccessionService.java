package com.equigcc.succession;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.captable.InstrumentType;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.ShareClassRepository;
import com.equigcc.captable.Shareholder;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.certificate.CertificateHasher;
import com.equigcc.certificate.IssuanceStatus;
import com.equigcc.certificate.ShareIssuance;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.governance.BoardResolutionRepository;
import com.equigcc.governance.ResolutionStatus;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Succession rules convert shares from one class to another when a trigger event happens. Executing a rule retires the
 * old certificates (CONVERTED, or TRANSFERRED when a beneficiary receives them) and issues new ones that point back at
 * their predecessor, keeping the total consideration the holder paid.
 */
@Service
public class SuccessionService {

    public record NewRule(String name, SuccessionTrigger trigger, UUID subjectId, UUID fromClassId, UUID toClassId,
                          long ratioNumerator, long ratioDenominator, UUID beneficiaryId, String legalReference) {
    }

    /** One planned conversion. {@code problem} is set when it cannot be executed as is. */
    public record Conversion(UUID issuanceId, String certificateNo, UUID holderId, String holderNameEn, String holderNameAr,
                             long fromQuantity, UUID toHolderId, String toHolderNameEn, String toHolderNameAr,
                             long toQuantity, BigDecimal toPricePerShare, String problem) {
    }

    private final SuccessionRuleRepository rules;
    private final OrganizationRepository organizations;
    private final ShareClassRepository classes;
    private final ShareholderRepository shareholders;
    private final ShareIssuanceRepository issuances;
    private final BoardResolutionRepository resolutions;
    private final CertificateHasher hasher;
    private final AuditService audit;
    private final Clock clock;

    public SuccessionService(SuccessionRuleRepository rules, OrganizationRepository organizations, ShareClassRepository classes,
                             ShareholderRepository shareholders, ShareIssuanceRepository issuances,
                             BoardResolutionRepository resolutions, CertificateHasher hasher, AuditService audit, Clock clock) {
        this.rules = rules;
        this.organizations = organizations;
        this.classes = classes;
        this.shareholders = shareholders;
        this.issuances = issuances;
        this.resolutions = resolutions;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SuccessionRule> list(UUID orgId) {
        return rules.findAllByOrganization(orgId);
    }

    @Transactional(readOnly = true)
    public SuccessionRule get(UUID orgId, UUID id) {
        return rules.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Succession rule " + id + " not found"));
    }

    @Transactional
    public SuccessionRule create(UUID orgId, NewRule n, String user) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        if (n.fromClassId().equals(n.toClassId())) {
            throw new BusinessRuleException("The source and target share classes must differ");
        }
        if (n.ratioNumerator() <= 0 || n.ratioDenominator() <= 0) {
            throw new BusinessRuleException("The conversion ratio must be positive");
        }
        ShareClass from = classes.findByIdAndOrganizationId(n.fromClassId(), orgId)
                .orElseThrow(() -> new NotFoundException("Share class " + n.fromClassId() + " not found in organization"));
        ShareClass to = classes.findByIdAndOrganizationId(n.toClassId(), orgId)
                .orElseThrow(() -> new NotFoundException("Share class " + n.toClassId() + " not found in organization"));
        if (to.isTreasury() || from.isTreasury()) {
            throw new BusinessRuleException("Treasury shares cannot take part in a succession conversion");
        }
        var r = new SuccessionRule();
        r.setOrganization(org);
        r.setName(n.name());
        r.setTriggerEvent(n.trigger());
        r.setFromClass(from);
        r.setToClass(to);
        r.setRatioNumerator(n.ratioNumerator());
        r.setRatioDenominator(n.ratioDenominator());
        r.setLegalReference(n.legalReference());
        r.setCreatedBy(user);
        if (n.subjectId() != null) {
            r.setSubject(shareholders.findByIdAndOrganizationId(n.subjectId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Shareholder " + n.subjectId() + " not found in organization")));
        }
        if (n.beneficiaryId() != null) {
            r.setBeneficiary(shareholders.findByIdAndOrganizationId(n.beneficiaryId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Shareholder " + n.beneficiaryId() + " not found in organization")));
        }
        r = rules.save(r);
        audit.record(orgId, "SuccessionRule", r.getId(), AuditAction.CREATE,
                "succession rule \"" + r.getName() + "\" on " + r.getTriggerEvent() + ": " + from.getName() + " to " + to.getName()
                        + " at " + r.getRatioNumerator() + ":" + r.getRatioDenominator() + " (" + r.getLegalReference() + ")", user);
        return r;
    }

    @Transactional
    public SuccessionRule cancel(UUID orgId, UUID id, String user) {
        SuccessionRule r = rules.lockByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Succession rule " + id + " not found"));
        if (r.getStatus() != SuccessionStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft rule can be cancelled");
        }
        r.setStatus(SuccessionStatus.CANCELLED);
        audit.record(orgId, "SuccessionRule", r.getId(), AuditAction.UPDATE, "cancelled succession rule \"" + r.getName() + "\"", user);
        return r;
    }

    @Transactional(readOnly = true)
    public List<Conversion> preview(UUID orgId, UUID ruleId) {
        return plan(get(orgId, ruleId), orgId);
    }

    private List<Conversion> plan(SuccessionRule r, UUID orgId) {
        var out = new ArrayList<Conversion>();
        for (ShareIssuance i : issuances.findActiveByOrganization(orgId)) {
            if (!i.getShareClass().getId().equals(r.getFromClass().getId()) || i.getInstrumentType() != InstrumentType.SHARES) {
                continue;
            }
            if (r.getSubject() != null && !i.getShareholder().getId().equals(r.getSubject().getId())) {
                continue;
            }
            Shareholder to = r.getBeneficiary() != null ? r.getBeneficiary() : i.getShareholder();
            long scaled = Math.multiplyExact(i.getQuantity(), r.getRatioNumerator());
            String problem = null;
            long newQty = scaled / r.getRatioDenominator();
            if (scaled % r.getRatioDenominator() != 0) {
                problem = "Converting " + i.getQuantity() + " shares at " + r.getRatioNumerator() + ":" + r.getRatioDenominator()
                        + " would produce a fractional share";
            }
            // The holder keeps the total they paid: the price per new share is spread over the new quantity.
            BigDecimal price = newQty == 0 ? i.getPricePerShare()
                    : i.getPricePerShare().multiply(BigDecimal.valueOf(i.getQuantity()))
                            .divide(BigDecimal.valueOf(newQty), 6, RoundingMode.HALF_UP);
            out.add(new Conversion(i.getId(), i.getCertificateNo(), i.getShareholder().getId(), i.getShareholder().getFullNameEn(),
                    i.getShareholder().getFullNameAr(), i.getQuantity(), to.getId(), to.getFullNameEn(), to.getFullNameAr(),
                    newQty, price, problem));
        }
        return out;
    }

    @Transactional
    public List<ShareIssuance> execute(UUID orgId, UUID ruleId, UUID resolutionId, String user) {
        // Same lock order as issuance: organization first, so numbering and authorized checks stay serialized.
        var org = organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        SuccessionRule r = rules.lockByIdAndOrganizationId(ruleId, orgId)
                .orElseThrow(() -> new NotFoundException("Succession rule " + ruleId + " not found"));
        if (r.getStatus() != SuccessionStatus.DRAFT) {
            throw new BusinessRuleException("This rule has already been " + r.getStatus().name().toLowerCase());
        }
        if (resolutionId != null) {
            var res = resolutions.findByIdAndOrganizationId(resolutionId, orgId)
                    .orElseThrow(() -> new NotFoundException("Resolution " + resolutionId + " not found"));
            if (res.getStatus() != ResolutionStatus.PASSED) {
                throw new BusinessRuleException("The authorising resolution has not passed");
            }
        }
        SuccessionRule full = rules.findByIdAndOrganizationId(ruleId, orgId).orElseThrow();
        List<Conversion> plan = plan(full, orgId);
        if (plan.isEmpty()) {
            throw new BusinessRuleException("No shares of " + full.getFromClass().getName() + " match this rule");
        }
        plan.stream().filter(c -> c.problem() != null).findFirst().ifPresent(c -> {
            throw new BusinessRuleException(c.problem() + " (certificate " + c.certificateNo() + ")");
        });
        ShareClass to = full.getToClass();
        if (to.getAuthorizedShares() != null) {
            long headroom = to.getAuthorizedShares() - issuances.sumIssuedQuantityByClass(to.getId());
            long needed = plan.stream().mapToLong(Conversion::toQuantity).sum();
            if (needed > headroom) {
                throw new BusinessRuleException("Converting would issue " + needed + " " + to.getName()
                        + " shares but only " + headroom + " are authorized and unissued");
            }
        }

        var created = new ArrayList<ShareIssuance>();
        LocalDate today = LocalDate.now(clock);
        for (Conversion c : plan) {
            ShareIssuance old = issuances.findById(c.issuanceId()).orElseThrow();
            Shareholder holder = full.getBeneficiary() != null ? full.getBeneficiary() : old.getShareholder();
            boolean moved = !holder.getId().equals(old.getShareholder().getId());
            old.setStatus(moved ? IssuanceStatus.TRANSFERRED : IssuanceStatus.CONVERTED);

            var n = new ShareIssuance();
            n.setOrganization(org);
            n.setShareClass(to);
            n.setShareholder(holder);
            n.setInstrumentType(InstrumentType.SHARES);
            n.setQuantity(c.toQuantity());
            n.setPricePerShare(c.toPricePerShare());
            n.setCurrency(old.getCurrency());
            n.setDateIssued(today);
            n.setIssuedBy(user);
            n.setPredecessorId(old.getId());
            n.setSuccessionRuleId(full.getId());
            n.setCertificateNo(org.nextCertificateNumber());
            n.setRecordHash(hasher.hash(n));
            n = issuances.save(n);
            created.add(n);
            audit.record(orgId, "ShareIssuance", n.getId(), AuditAction.CREATE,
                    "succession \"" + full.getName() + "\": certificate " + old.getCertificateNo() + " (" + old.getQuantity() + " "
                            + old.getShareClass().getName() + ") replaced by " + n.getCertificateNo() + " (" + n.getQuantity() + " "
                            + to.getName() + " to " + holder.getFullNameEn() + ")", user);
        }
        r.setStatus(SuccessionStatus.EXECUTED);
        r.setResolutionId(resolutionId);
        r.setExecutedAt(Instant.now(clock));
        r.setExecutedBy(user);
        audit.record(orgId, "SuccessionRule", r.getId(), AuditAction.UPDATE,
                "executed succession rule \"" + r.getName() + "\": " + created.size() + " certificate(s) replaced"
                        + (resolutionId != null ? " under resolution " + resolutionId : ""), user);
        return created;
    }
}
