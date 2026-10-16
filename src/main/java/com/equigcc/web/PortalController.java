package com.equigcc.web;

import com.equigcc.domain.DocumentType;
import com.equigcc.security.AuthenticatedUser;
import com.equigcc.service.PortalService;
import com.equigcc.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The shareholder portal summary, and electronic signatures for everyone who may sign. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Portal and signatures")
public class PortalController {

    private static final String SIGNERS = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','SHAREHOLDER') and @orgAccess.canAccess(authentication, #orgId)";

    public record SignRequest(@NotNull DocumentType documentType, @NotBlank @Size(max = 100) String documentRef,
                              @NotBlank @Size(max = 12) String code, @Size(max = 300) String statement) {
    }

    private final PortalService portal;
    private final SignatureService signatures;
    private final String verificationBaseUrl;

    public PortalController(PortalService portal, SignatureService signatures, @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.portal = portal;
        this.signatures = signatures;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Operation(summary = "A shareholder's own holdings, certificates and open votes")
    @GetMapping("/portal/summary")
    @PreAuthorize("hasRole('SHAREHOLDER') and @orgAccess.mfaVerified(authentication)")
    public PortalService.Summary summary(Authentication auth) {
        var me = (AuthenticatedUser) auth.getPrincipal();
        if (me.shareholderId() == null || me.organizationIds().isEmpty()) {
            throw new AccessDeniedException("No shareholder is linked to this account");
        }
        return portal.summary(me.organizationIds().iterator().next(), me.shareholderId(), verificationBaseUrl);
    }

    @Operation(summary = "Sign a resolution or certificate. Needs a fresh second-factor code")
    @PostMapping("/organizations/{orgId}/signatures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SIGNERS)
    public SignatureService.View sign(@PathVariable UUID orgId, @Valid @RequestBody SignRequest r, Authentication auth) {
        return signatures.sign(orgId, r.documentType(), r.documentRef(), r.code(), r.statement(), signer(auth));
    }

    @Operation(summary = "Signatures on a document, with whether each is still valid")
    @GetMapping("/organizations/{orgId}/signatures")
    @PreAuthorize(SIGNERS)
    public List<SignatureService.View> list(@PathVariable UUID orgId, @RequestParam DocumentType documentType, @RequestParam String documentRef, Authentication auth) {
        return signatures.list(orgId, documentType, documentRef, signer(auth));
    }

    private static SignatureService.Signer signer(Authentication auth) {
        var me = (AuthenticatedUser) auth.getPrincipal();
        String role = auth.getAuthorities().stream().findFirst().map(g -> g.getAuthority().replace("ROLE_", "")).orElse("");
        return new SignatureService.Signer(me.subject(), role, me.shareholderId());
    }
}
