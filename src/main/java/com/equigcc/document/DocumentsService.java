package com.equigcc.document;

import com.equigcc.audit.AuditService;
import com.equigcc.captable.CapTableService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.equity.VestingService;
import com.equigcc.governance.ResolutionService;
import com.equigcc.history.CertificateChain;
import com.equigcc.signature.SignatureService;

import com.equigcc.audit.AuditAction;
import com.equigcc.signature.DocumentType;
import com.equigcc.captable.InstrumentType;
import com.equigcc.organization.Organization;
import com.equigcc.governance.ResolutionStatus;
import com.equigcc.captable.Shareholder;
import com.equigcc.organization.OrganizationRepository;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.captable.ShareholderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Gathers the facts for the paperwork PDFs and records each download in the audit trail. */
@Service
public class DocumentsService {

    public record Pdf(byte[] bytes, String filename) {
    }

    private final OrganizationRepository organizations;
    private final ResolutionService resolutions;
    private final SignatureService signatures;
    private final ShareIssuanceRepository issuances;
    private final ShareholderRepository shareholders;
    private final CapTableService capTables;
    private final VestingService vesting;
    private final DocumentPdfService pdf;
    private final AuditService audit;
    private final Clock clock;

    public DocumentsService(OrganizationRepository organizations, ResolutionService resolutions, SignatureService signatures,
                            ShareIssuanceRepository issuances, ShareholderRepository shareholders, CapTableService capTables,
                            VestingService vesting, DocumentPdfService pdf, AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.resolutions = resolutions;
        this.signatures = signatures;
        this.issuances = issuances;
        this.shareholders = shareholders;
        this.capTables = capTables;
        this.vesting = vesting;
        this.pdf = pdf;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Pdf minutes(UUID orgId, UUID resolutionId, String user) {
        Organization org = org(orgId);
        var r = resolutions.get(orgId, resolutionId);
        if (r.getStatus() != ResolutionStatus.PASSED && r.getStatus() != ResolutionStatus.REJECTED) {
            throw new BusinessRuleException("Minutes can be produced once voting has closed");
        }
        var tally = resolutions.tallyOf(r);
        var votes = resolutions.votesOf(resolutionId).stream()
                .map(v -> new DocumentPdfService.VoteLine(v.getShareholder().getFullNameEn(), v.getShareholder().getFullNameAr(), v.getVote().name(),
                        v.getWeightApplied(), v.getCastAt()))
                .toList();
        var signs = signatures.list(orgId, DocumentType.RESOLUTION, resolutionId.toString(), new SignatureService.Signer(user, "FOUNDER", null)).stream()
                .map(s -> new DocumentPdfService.SignLine(s.signerName(), s.signerRole(), s.statement(), s.signedAt(), s.valid()))
                .toList();
        byte[] bytes = pdf.minutes(new DocumentPdfService.Minutes(org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo(), r.getTitle(), r.getTitleAr(),
                r.getDescription(), r.getThreshold().name(), r.getQuorumPct(), r.getVotingDeadline(), r.getClosedAt(), r.getStatus().name(),
                tally.forWeight(), tally.againstWeight(), tally.abstainWeight(), tally.participationPct(), tally.quorumMet(),
                r.getTotalVotingPower() == null ? BigDecimal.ZERO : r.getTotalVotingPower(), votes, signs));
        audit.record(orgId, "DocumentPdf", resolutionId, AuditAction.CREATE, "generated minutes of resolution: " + r.getTitle(), user);
        return new Pdf(bytes, "minutes-" + resolutionId.toString().substring(0, 8) + ".pdf");
    }

    @Transactional
    public Pdf transferForm(UUID orgId, String newCertificateNo, String user) {
        Organization org = org(orgId);
        var all = issuances.findAllByOrganization(orgId);
        var cert = all.stream().filter(c -> c.getCertificateNo().equals(newCertificateNo)).findFirst()
                .orElseThrow(() -> new NotFoundException("Certificate " + newCertificateNo + " not found"));
        var old = cert.getPredecessorId() == null ? null : all.stream().filter(c -> c.getId().equals(cert.getPredecessorId())).findFirst().orElse(null);
        if (old == null || !"TRANSFER".equals(CertificateChain.reason(cert, old))) {
            throw new BusinessRuleException("Certificate " + newCertificateNo + " did not come from a transfer; give the certificate issued to the buyer");
        }
        BigDecimal consideration = cert.getPricePerShare().multiply(BigDecimal.valueOf(cert.getQuantity()));
        byte[] bytes = pdf.transferForm(new DocumentPdfService.TransferForm(org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo(),
                party(old.getShareholder()), party(cert.getShareholder()), cert.getShareClass().getName(), cert.getQuantity(), cert.getCurrency().name(),
                cert.getPricePerShare(), consideration, cert.getDateIssued(), old.getCertificateNo(), cert.getCertificateNo()));
        audit.record(orgId, "DocumentPdf", cert.getId(), AuditAction.CREATE, "generated share transfer instrument for " + cert.getCertificateNo(), user);
        return new Pdf(bytes, "transfer-" + cert.getCertificateNo() + ".pdf");
    }

    @Transactional
    public Pdf statement(UUID orgId, UUID shareholderId, LocalDate asOf, String user) {
        Organization org = org(orgId);
        Shareholder holder = shareholders.findByIdAndOrganizationId(shareholderId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + shareholderId + " not found"));
        LocalDate day = asOf == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : asOf;
        var all = issuances.findAllByOrganization(orgId);
        var held = new ArrayList<>(capTables.activeAsOf(all, day).stream().filter(c -> c.getShareholder().getId().equals(shareholderId)).toList());
        held.sort(Comparator.comparing((com.equigcc.certificate.ShareIssuance c) -> c.getDateIssued()).thenComparing(c -> c.getCertificateNo()));
        var table = capTables.computeAsOf(orgId, all, day);
        var pos = table.holders().stream().filter(h -> h.shareholderId().equals(shareholderId)).findFirst().orElse(null);
        var holdings = held.stream().map(c -> new DocumentPdfService.Holding(c.getCertificateNo(), c.getShareClass().getName(),
                c.getInstrumentType() == InstrumentType.SHARES ? "Shares" : c.getInstrumentType().name(), c.getQuantity(), c.getDateIssued(),
                c.getCurrency().name(), c.getPricePerShare())).toList();
        byte[] bytes = pdf.statement(new DocumentPdfService.Statement(org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo(), party(holder), day,
                holdings, pos == null ? 0 : pos.issuedShares(), pos == null ? 0 : pos.fullyDilutedShares(),
                pos == null ? BigDecimal.ZERO : pos.issuedOwnershipPct(), pos == null ? BigDecimal.ZERO : pos.fullyDilutedOwnershipPct(),
                pos == null ? BigDecimal.ZERO : pos.votingPower(), pos == null ? BigDecimal.ZERO : pos.votingPct()));
        audit.record(orgId, "DocumentPdf", shareholderId, AuditAction.CREATE, "generated shareholder statement for " + holder.getFullNameEn() + " as of " + day, user);
        return new Pdf(bytes, "statement-" + day + ".pdf");
    }

    @Transactional
    public Pdf optionStatement(UUID orgId, UUID shareholderId, LocalDate asOf, String user) {
        Organization org = org(orgId);
        Shareholder holder = shareholders.findByIdAndOrganizationId(shareholderId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + shareholderId + " not found"));
        LocalDate day = asOf == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : asOf;
        var grants = vesting.list(orgId, day).stream().filter(g -> g.shareholderId().equals(shareholderId))
                .map(g -> new DocumentPdfService.OptionGrant(g.certificateNo(), g.shareClassName(), g.currency().name(), g.strikePrice(), g.granted(), g.vested(),
                        g.exercised(), g.exercisable(), g.unvested(), g.startDate(), g.cliffMonths(), g.vestingMonths(), g.cliffDate(), g.fullyVestedDate(), g.nextVestDate()))
                .toList();
        byte[] bytes = pdf.optionStatement(new DocumentPdfService.OptionStatement(org.getLegalNameEn(), org.getLegalNameAr(), org.getCommercialLicenseNo(),
                party(holder), day, grants));
        audit.record(orgId, "DocumentPdf", shareholderId, AuditAction.CREATE, "generated option statement for " + holder.getFullNameEn() + " as of " + day, user);
        return new Pdf(bytes, "options-" + day + ".pdf");
    }

    private Organization org(UUID orgId) {
        return organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
    }

    private static DocumentPdfService.Party party(Shareholder h) {
        String detail = h.getRegistrationNo() != null && !h.getRegistrationNo().isBlank() ? "Reg. no. / رقم السجل: " + h.getRegistrationNo()
                : h.getPassportNo() != null && !h.getPassportNo().isBlank() ? "Passport / جواز السفر: " + h.getPassportNo()
                + (h.getNationality() == null ? "" : " (" + h.getNationality() + ")") : null;
        return new DocumentPdfService.Party(h.getFullNameEn(), h.getFullNameAr(), detail);
    }
}
