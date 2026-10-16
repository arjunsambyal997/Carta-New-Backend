package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.IssuanceStatus;
import com.equigcc.domain.Organization;
import com.equigcc.domain.ResolutionStatus;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.ShareholderType;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.ShareholderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Moves shares between holders and buys shares back into treasury. Certificates are never edited: each one touched is
 * retired as TRANSFERRED and replaced by new certificates that point back to it, so the register keeps a full chain.
 * Shares are taken oldest certificate first; a certificate only partly used leaves a remainder certificate with its holder.
 */
@Service
public class TransferService {

    public record Transfer(UUID fromId, UUID toId, UUID shareClassId, long quantity, BigDecimal pricePerShare,
                           LocalDate date, UUID resolutionId, String reference) {
    }

    public record BuyBack(UUID fromId, UUID shareClassId, long quantity, BigDecimal pricePerShare, LocalDate date,
                          UUID resolutionId, String reference) {
    }

    static final String TREASURY_HOLDER = "Company Treasury";

    private final OrganizationRepository organizations;
    private final ShareClassRepository classes;
    private final ShareholderRepository shareholders;
    private final ShareIssuanceRepository issuances;
    private final BoardResolutionRepository resolutions;
    private final CertificateHasher hasher;
    private final AuditService audit;
    private final Clock clock;

    public TransferService(OrganizationRepository organizations, ShareClassRepository classes, ShareholderRepository shareholders,
                           ShareIssuanceRepository issuances, BoardResolutionRepository resolutions, CertificateHasher hasher,
                           AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.classes = classes;
        this.shareholders = shareholders;
        this.issuances = issuances;
        this.resolutions = resolutions;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public List<ShareIssuance> transfer(UUID orgId, Transfer t, String user) {
        if (t.fromId().equals(t.toId())) {
            throw new BusinessRuleException("The seller and the buyer must be different holders");
        }
        Organization org = lockOrg(orgId);
        ShareClass cls = shareClass(orgId, t.shareClassId());
        if (cls.isTreasury()) {
            throw new BusinessRuleException("Treasury shares are not transferred between holders");
        }
        Shareholder from = holder(orgId, t.fromId());
        Shareholder to = holder(orgId, t.toId());
        requirePassed(orgId, t.resolutionId());
        if (t.pricePerShare() != null && t.pricePerShare().signum() < 0) {
            throw new BusinessRuleException("The price per share cannot be negative");
        }
        List<ShareIssuance> created = new ArrayList<>();
        String ref = suffix(t.reference(), t.resolutionId());
        for (Slice s : take(orgId, from, cls, t.quantity())) {
            ShareIssuance old = s.cert();
            old.setStatus(IssuanceStatus.TRANSFERRED);
            ShareIssuance moved = replacement(org, old, to, cls, s.used(), t.pricePerShare() != null ? t.pricePerShare() : old.getPricePerShare(), t.date(), user);
            created.add(moved);
            audit.record(orgId, "ShareIssuance", moved.getId(), AuditAction.CREATE,
                    "transfer" + ref + ": " + s.used() + " " + cls.getName() + " from " + from.getFullNameEn() + " to " + to.getFullNameEn()
                            + " (certificate " + old.getCertificateNo() + " replaced by " + moved.getCertificateNo() + ")", user);
            if (s.used() < old.getQuantity()) {
                ShareIssuance rest = replacement(org, old, from, cls, old.getQuantity() - s.used(), old.getPricePerShare(), t.date(), user);
                created.add(rest);
                audit.record(orgId, "ShareIssuance", rest.getId(), AuditAction.CREATE,
                        "remainder " + rest.getQuantity() + " " + cls.getName() + " kept by " + from.getFullNameEn() + " as certificate " + rest.getCertificateNo(), user);
            }
        }
        return created;
    }

    @Transactional
    public List<ShareIssuance> buyBack(UUID orgId, BuyBack b, String user) {
        Organization org = lockOrg(orgId);
        ShareClass cls = shareClass(orgId, b.shareClassId());
        if (cls.isTreasury()) {
            throw new BusinessRuleException("Pick the class the shares are bought back from, not the treasury class");
        }
        ShareClass treasury = classes.findByOrganizationIdOrderBySeniorityRankDescNameAsc(orgId).stream()
                .filter(ShareClass::isTreasury).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Add a treasury share class under Setup before buying shares back"));
        if (treasury.getCurrency() != cls.getCurrency()) {
            throw new BusinessRuleException("The treasury class currency must match " + cls.getName());
        }
        if (b.pricePerShare() == null || b.pricePerShare().signum() < 0) {
            throw new BusinessRuleException("A buy-back price per share of zero or more is required");
        }
        if (treasury.getAuthorizedShares() != null
                && b.quantity() > treasury.getAuthorizedShares() - issuances.sumIssuedQuantityByClass(treasury.getId())) {
            throw new BusinessRuleException("The treasury class does not have room for " + b.quantity() + " more shares");
        }
        Shareholder from = holder(orgId, b.fromId());
        requirePassed(orgId, b.resolutionId());
        Shareholder vault = treasuryHolder(org);
        List<ShareIssuance> created = new ArrayList<>();
        String ref = suffix(b.reference(), b.resolutionId());
        for (Slice s : take(orgId, from, cls, b.quantity())) {
            ShareIssuance old = s.cert();
            old.setStatus(IssuanceStatus.TRANSFERRED);
            ShareIssuance moved = replacement(org, old, vault, treasury, s.used(), b.pricePerShare(), b.date(), user);
            created.add(moved);
            audit.record(orgId, "ShareIssuance", moved.getId(), AuditAction.CREATE,
                    "buy-back" + ref + ": " + s.used() + " " + cls.getName() + " from " + from.getFullNameEn() + " into treasury at "
                            + b.pricePerShare().toPlainString() + " (certificate " + old.getCertificateNo() + " replaced by " + moved.getCertificateNo() + ")", user);
            if (s.used() < old.getQuantity()) {
                ShareIssuance rest = replacement(org, old, from, cls, old.getQuantity() - s.used(), old.getPricePerShare(), b.date(), user);
                created.add(rest);
            }
        }
        return created;
    }

    // ---- internals --------------------------------------------------------------------------------

    private record Slice(ShareIssuance cert, long used) {
    }

    /** Oldest active certificates of this holder and class that together cover the quantity. */
    private List<Slice> take(UUID orgId, Shareholder from, ShareClass cls, long quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("The quantity must be positive");
        }
        List<ShareIssuance> held = issuances.findActiveByOrganization(orgId).stream()
                .filter(i -> i.getInstrumentType() == InstrumentType.SHARES
                        && i.getShareholder().getId().equals(from.getId()) && i.getShareClass().getId().equals(cls.getId()))
                .sorted(Comparator.comparing(ShareIssuance::getDateIssued).thenComparing(ShareIssuance::getCertificateNo))
                .toList();
        long total = held.stream().mapToLong(ShareIssuance::getQuantity).sum();
        if (quantity > total) {
            throw new BusinessRuleException(from.getFullNameEn() + " holds " + total + " " + cls.getName() + " shares, not " + quantity);
        }
        List<Slice> out = new ArrayList<>();
        long left = quantity;
        for (ShareIssuance c : held) {
            if (left == 0) {
                break;
            }
            long use = Math.min(left, c.getQuantity());
            out.add(new Slice(c, use));
            left -= use;
        }
        return out;
    }

    private ShareIssuance replacement(Organization org, ShareIssuance old, Shareholder holder, ShareClass cls, long qty, BigDecimal price, LocalDate date, String user) {
        var n = new ShareIssuance();
        n.setOrganization(org);
        n.setShareClass(cls);
        n.setShareholder(holder);
        n.setInstrumentType(InstrumentType.SHARES);
        n.setQuantity(qty);
        n.setPricePerShare(price);
        n.setCurrency(old.getCurrency());
        n.setDateIssued(date != null ? date : LocalDate.now(clock));
        n.setIssuedBy(user);
        n.setPredecessorId(old.getId());
        n.setCertificateNo(org.nextCertificateNumber());
        n.setRecordHash(hasher.hash(n));
        return issuances.save(n);
    }

    private Shareholder treasuryHolder(Organization org) {
        return shareholders.findByOrganizationIdOrderByFullNameEnAsc(org.getId()).stream()
                .filter(h -> TREASURY_HOLDER.equals(h.getFullNameEn())).findFirst().orElseGet(() -> {
                    var h = new Shareholder();
                    h.setOrganization(org);
                    h.setHolderType(ShareholderType.CORPORATE);
                    h.setFullNameEn(TREASURY_HOLDER);
                    h.setFullNameAr("خزينة الشركة");
                    return shareholders.save(h);
                });
    }

    private Organization lockOrg(UUID orgId) {
        return organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
    }

    private ShareClass shareClass(UUID orgId, UUID id) {
        return classes.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Share class " + id + " not found in organization"));
    }

    private Shareholder holder(UUID orgId, UUID id) {
        return shareholders.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Shareholder " + id + " not found in organization"));
    }

    private void requirePassed(UUID orgId, UUID resolutionId) {
        if (resolutionId == null) {
            return;
        }
        var res = resolutions.findByIdAndOrganizationId(resolutionId, orgId).orElseThrow(() -> new NotFoundException("Resolution " + resolutionId + " not found"));
        if (res.getStatus() != ResolutionStatus.PASSED) {
            throw new BusinessRuleException("The authorising resolution has not passed");
        }
    }

    private static String suffix(String reference, UUID resolutionId) {
        String r = reference == null || reference.isBlank() ? "" : " [" + reference.trim() + "]";
        return r + (resolutionId != null ? " under resolution " + resolutionId : "");
    }
}
