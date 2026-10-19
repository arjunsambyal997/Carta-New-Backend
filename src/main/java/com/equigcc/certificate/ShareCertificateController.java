package com.equigcc.certificate;

import com.equigcc.captable.CapTable;
import com.equigcc.captable.CapTableService;
import com.equigcc.captable.WaterfallResult;
import com.equigcc.captable.WaterfallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Share register")
public class ShareCertificateController {

    private final CertificateIssuanceService issuanceService;
    private final CapTableService capTableService;
    private final WaterfallService waterfallService;
    private final String verificationBaseUrl;
    private final com.equigcc.certificate.CertificatePdfService pdfService;
    private final com.equigcc.notice.NoticeService notices;

    public ShareCertificateController(com.equigcc.certificate.CertificatePdfService pdfService,
                                      CertificateIssuanceService issuanceService,
                                      CapTableService capTableService,
                                      WaterfallService waterfallService,
                                      com.equigcc.notice.NoticeService notices,
                                      @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.issuanceService = issuanceService;
        this.capTableService = capTableService;
        this.waterfallService = waterfallService;
        this.verificationBaseUrl = verificationBaseUrl;
        this.pdfService = pdfService;
        this.notices = notices;
    }

    @Operation(summary = "Issue a new share certificate",
            description = "Allocates the next certificate number, enforces par value and authorized share limits, "
                    + "and records a SHA-256 fingerprint for QR verification. Requires an MFA-verified session.")
    @PostMapping("/organizations/{orgId}/certificates")
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId) "
            + "and @orgAccess.mfaVerified(authentication)")
    public ResponseEntity<CertificateResponse> issue(@PathVariable UUID orgId,
                                                     @Valid @RequestBody IssueCertificateRequest req,
                                                     Authentication auth) {
        ShareIssuance issued = issuanceService.issue(new IssueCertificateCommand(
                orgId,
                req.shareholderId(),
                req.shareClassId(),
                req.instrumentType(),
                req.quantity(),
                req.pricePerShare(),
                req.currency(),
                req.dateIssued(),
                auth.getName()));

        notices.certificateIssued(orgId, issued.getCertificateNo());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{certificateNo}")
                .buildAndExpand(issued.getCertificateNo())
                .toUri();
        return ResponseEntity.created(location).body(CertificateResponse.from(issued, verificationBaseUrl));
    }

    @Operation(summary = "Cap table: totals, fully diluted ownership and voting power per shareholder")
    @GetMapping("/organizations/{orgId}/cap-table")
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') "
            + "and @orgAccess.canAccess(authentication, #orgId)")
    public CapTable capTable(@PathVariable UUID orgId) {
        return capTableService.calculate(orgId);
    }

    @Operation(summary = "Exit waterfall: proceeds per share class and shareholder for a given exit value")
    @GetMapping("/organizations/{orgId}/waterfall")
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') "
            + "and @orgAccess.canAccess(authentication, #orgId)")
    public WaterfallResult waterfall(@PathVariable UUID orgId, @RequestParam BigDecimal exitValue) {
        return waterfallService.calculate(orgId, exitValue);
    }

    @Operation(summary = "List the organization's certificates, newest first")
    @GetMapping("/organizations/{orgId}/certificates")
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') "
            + "and @orgAccess.canAccess(authentication, #orgId)")
    public java.util.List<CertificateResponse> list(@PathVariable UUID orgId) {
        return issuanceService.listForOrganization(orgId).stream()
                .map(i -> CertificateResponse.from(i, verificationBaseUrl)).toList();
    }

    @Operation(summary = "Bilingual PDF of a certificate with a verification QR code",
            description = "Founders, secretaries and auditors can download any certificate; a shareholder only their own.")
    @GetMapping(value = "/organizations/{orgId}/certificates/{certificateNo}/pdf", produces = "application/pdf")
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','LEGAL_AUDITOR','SHAREHOLDER') "
            + "and @orgAccess.canAccess(authentication, #orgId)")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID orgId, @PathVariable String certificateNo, Authentication auth) {
        ShareIssuance i = issuanceService.findInOrganization(orgId, certificateNo);
        boolean isHolder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHAREHOLDER"));
        if (isHolder) {
            var me = (com.equigcc.auth.AuthenticatedUser) auth.getPrincipal();
            if (me.shareholderId() == null || !me.shareholderId().equals(i.getShareholder().getId())) {
                throw new com.equigcc.common.NotFoundException("Certificate " + certificateNo + " not found");
            }
        }
        byte[] bytes = pdfService.render(i);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + i.getCertificateNo() + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @Operation(summary = "Public QR verification of a certificate")
    @GetMapping("/certificates/{certificateNo}/verify")
    public CertificateVerification verify(@PathVariable String certificateNo,
                                          @RequestParam(name = "h", required = false) String hash) {
        ShareIssuance i = issuanceService.findByCertificateNo(certificateNo);
        return new CertificateVerification(
                i.getCertificateNo(),
                i.getOrganization().getLegalNameEn(),
                i.getOrganization().getLegalNameAr(),
                i.getShareClass().getName(),
                i.getQuantity(),
                i.getDateIssued(),
                i.getStatus(),
                issuanceService.verify(i, hash));
    }
}
