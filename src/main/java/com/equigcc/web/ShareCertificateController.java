package com.equigcc.web;

import com.equigcc.domain.ShareIssuance;
import com.equigcc.service.CapTable;
import com.equigcc.service.CapTableService;
import com.equigcc.service.CertificateIssuanceService;
import com.equigcc.service.IssueCertificateCommand;
import com.equigcc.web.dto.CertificateResponse;
import com.equigcc.web.dto.CertificateVerification;
import com.equigcc.web.dto.IssueCertificateRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Share register")
public class ShareCertificateController {

    private final CertificateIssuanceService issuanceService;
    private final CapTableService capTableService;
    private final String verificationBaseUrl;

    public ShareCertificateController(CertificateIssuanceService issuanceService,
                                      CapTableService capTableService,
                                      @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.issuanceService = issuanceService;
        this.capTableService = capTableService;
        this.verificationBaseUrl = verificationBaseUrl;
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
