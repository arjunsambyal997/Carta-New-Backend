package com.equigcc.web;

import com.equigcc.security.AuthenticatedUser;
import com.equigcc.service.DocumentsService;
import com.equigcc.service.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Bilingual PDFs: board minutes, a share transfer instrument and a shareholder statement. Each download is audited. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/documents")
@Tag(name = "Documents")
public class DocumentsController {

    private static final String ORG = "@orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";
    private static final String STAFF = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and " + ORG;
    private static final String STAFF_OR_HOLDER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR','SHAREHOLDER') and " + ORG;

    private final DocumentsService documents;

    public DocumentsController(DocumentsService documents) {
        this.documents = documents;
    }

    @Operation(summary = "Minutes of a closed board resolution: result, tally, every vote and the signatures")
    @GetMapping(value = "/minutes/{resolutionId}", produces = "application/pdf")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> minutes(@PathVariable UUID orgId, @PathVariable UUID resolutionId, Authentication auth) {
        return pdf(documents.minutes(orgId, resolutionId, auth.getName()));
    }

    @Operation(summary = "Share transfer instrument for the certificate issued to the buyer of a transfer")
    @GetMapping(value = "/transfer-form/{certificateNo}", produces = "application/pdf")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> transferForm(@PathVariable UUID orgId, @PathVariable String certificateNo, Authentication auth) {
        return pdf(documents.transferForm(orgId, certificateNo, auth.getName()));
    }

    @Operation(summary = "A shareholder's holdings and ownership as of a date (default today). Shareholders can fetch only their own")
    @GetMapping(value = "/statement/{shareholderId}", produces = "application/pdf")
    @PreAuthorize(STAFF_OR_HOLDER)
    public ResponseEntity<byte[]> statement(@PathVariable UUID orgId, @PathVariable UUID shareholderId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                            Authentication auth) {
        ownOnly(auth, shareholderId);
        return pdf(documents.statement(orgId, shareholderId, asOf, auth.getName()));
    }

    @Operation(summary = "A holder's option grants with vested, exercised and unvested counts as of a date. Shareholders can fetch only their own")
    @GetMapping(value = "/option-statement/{shareholderId}", produces = "application/pdf")
    @PreAuthorize(STAFF_OR_HOLDER)
    public ResponseEntity<byte[]> optionStatement(@PathVariable UUID orgId, @PathVariable UUID shareholderId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                                  Authentication auth) {
        ownOnly(auth, shareholderId);
        return pdf(documents.optionStatement(orgId, shareholderId, asOf, auth.getName()));
    }

    /** A shareholder account may only fetch its own paperwork; to everyone else the holder simply does not exist. */
    private static void ownOnly(Authentication auth, UUID shareholderId) {
        boolean holder = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHAREHOLDER"));
        if (holder) {
            var me = (AuthenticatedUser) auth.getPrincipal();
            if (me.shareholderId() == null || !me.shareholderId().equals(shareholderId)) {
                throw new NotFoundException("Shareholder " + shareholderId + " not found");
            }
        }
    }

    private static ResponseEntity<byte[]> pdf(DocumentsService.Pdf p) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + p.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(p.bytes());
    }
}
