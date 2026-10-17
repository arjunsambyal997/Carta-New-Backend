package com.equigcc.certificate;

import com.equigcc.audit.AuditService;
import com.equigcc.captable.InstrumentType;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.ShareClassRepository;
import com.equigcc.captable.Shareholder;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.common.NotFoundException;
import com.equigcc.organization.Organization;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CertificateIssuanceService {

    private final OrganizationRepository organizations;
    private final ShareClassRepository shareClasses;
    private final ShareholderRepository shareholders;
    private final ShareIssuanceRepository issuances;
    private final CertificateHasher hasher;
    private final AuditService audit;
    private final Clock clock;

    public CertificateIssuanceService(OrganizationRepository organizations,
                                      ShareClassRepository shareClasses,
                                      ShareholderRepository shareholders,
                                      ShareIssuanceRepository issuances,
                                      CertificateHasher hasher,
                                      AuditService audit,
                                      Clock clock) {
        this.organizations = organizations;
        this.shareClasses = shareClasses;
        this.shareholders = shareholders;
        this.issuances = issuances;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ShareIssuance issue(IssueCertificateCommand cmd) {
        // Lock the organization row first: serializes certificate numbering and the authorized-share check.
        Organization org = organizations.findByIdForUpdate(cmd.organizationId())
                .orElseThrow(() -> new NotFoundException("Organization " + cmd.organizationId() + " not found"));
        ShareClass shareClass = shareClasses.findByIdAndOrganizationId(cmd.shareClassId(), org.getId())
                .orElseThrow(() -> new NotFoundException("Share class " + cmd.shareClassId() + " not found in organization"));
        Shareholder holder = shareholders.findByIdAndOrganizationId(cmd.shareholderId(), org.getId())
                .orElseThrow(() -> new NotFoundException("Shareholder " + cmd.shareholderId() + " not found in organization"));

        InstrumentType instrument = cmd.instrumentType() != null ? cmd.instrumentType() : InstrumentType.SHARES;
        var currency = cmd.currency() != null ? cmd.currency() : shareClass.getCurrency();

        if (cmd.quantity() <= 0) {
            throw new IssuanceRejectedException("Quantity must be positive");
        }
        if (currency != shareClass.getCurrency()) {
            throw new IssuanceRejectedException("Currency " + currency + " does not match share class currency "
                    + shareClass.getCurrency());
        }
        if (shareClass.isTreasury()) {
            throw new IssuanceRejectedException("Treasury shares are created by buy-back, not issued to holders");
        }
        // UAE CCL and KSA Companies Law both prohibit issuing shares below nominal (par) value.
        if (instrument == InstrumentType.SHARES && cmd.pricePerShare().compareTo(shareClass.getParValue()) < 0) {
            throw new IssuanceRejectedException("Price per share " + cmd.pricePerShare()
                    + " is below par value " + shareClass.getParValue());
        }
        if (shareClass.getAuthorizedShares() != null) {
            long allocated = issuances.sumIssuedQuantityByClass(shareClass.getId());
            long headroom = shareClass.getAuthorizedShares() - allocated;
            if (cmd.quantity() > headroom) {
                throw new IssuanceRejectedException("Issuing " + cmd.quantity() + " exceeds authorized "
                        + shareClass.getName() + " shares (" + headroom + " remaining)");
            }
        }

        ShareIssuance issuance = new ShareIssuance();
        issuance.setOrganization(org);
        issuance.setShareClass(shareClass);
        issuance.setShareholder(holder);
        issuance.setInstrumentType(instrument);
        issuance.setQuantity(cmd.quantity());
        issuance.setPricePerShare(cmd.pricePerShare());
        issuance.setCurrency(currency);
        issuance.setDateIssued(cmd.dateIssued() != null ? cmd.dateIssued() : LocalDate.now(clock));
        issuance.setIssuedBy(cmd.issuedBy());
        issuance.setCertificateNo(org.nextCertificateNumber());
        issuance.setRecordHash(hasher.hash(issuance));

        ShareIssuance saved = issuances.save(issuance);
        audit.record(org.getId(), "ShareIssuance", saved.getId(), com.equigcc.audit.AuditAction.CREATE,
                "certificate " + saved.getCertificateNo() + ": " + saved.getQuantity() + " " + shareClass.getName()
                        + " to " + holder.getFullNameEn(), cmd.issuedBy());
        return saved;
    }

    @Transactional(readOnly = true)
    public ShareIssuance findByCertificateNo(String certificateNo) {
        return issuances.findByCertificateNo(certificateNo)
                .orElseThrow(() -> new NotFoundException("Certificate " + certificateNo + " not found"));
    }

    @Transactional(readOnly = true)
    public java.util.List<ShareIssuance> listForOrganization(UUID organizationId) {
        return issuances.findAllByOrganization(organizationId);
    }

    /** Looks a certificate up inside one organization; a number from another tenant reads as not found. */
    @Transactional(readOnly = true)
    public ShareIssuance findInOrganization(UUID organizationId, String certificateNo) {
        ShareIssuance i = findByCertificateNo(certificateNo);
        if (!i.getOrganization().getId().equals(organizationId)) {
            throw new NotFoundException("Certificate " + certificateNo + " not found");
        }
        return i;
    }

    /** True if the stored record still hashes to its recorded fingerprint and matches the presented hash. */
    @Transactional(readOnly = true)
    public boolean verify(ShareIssuance issuance, String presentedHash) {
        String recomputed = hasher.hash(issuance);
        return recomputed.equals(issuance.getRecordHash())
                && (presentedHash == null || recomputed.equalsIgnoreCase(presentedHash));
    }
}
