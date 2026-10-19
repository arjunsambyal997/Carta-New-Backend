package com.equigcc.ubo;

import com.equigcc.ownership.OwnershipDtos;
import com.equigcc.ownership.OwnershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Beneficial ownership")
public class UboController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId)";

    private final OwnershipService ownership;
    private final UboService ubo;

    public UboController(OwnershipService ownership, UboService ubo) {
        this.ownership = ownership;
        this.ubo = ubo;
    }

    @Operation(summary = "All recorded ownership links between shareholders")
    @GetMapping("/ownership-links")
    @PreAuthorize(READ)
    public List<OwnershipDtos.LinkView> links(@PathVariable UUID orgId) {
        return ownership.all(orgId).stream().map(UboController::view).toList();
    }

    @Operation(summary = "Recorded owners of one corporate shareholder")
    @GetMapping("/shareholders/{holderId}/owners")
    @PreAuthorize(READ)
    public List<OwnershipDtos.LinkView> owners(@PathVariable UUID orgId, @PathVariable UUID holderId) {
        return ownership.ownersOf(orgId, holderId).stream().map(UboController::view).toList();
    }

    @Operation(summary = "Replace the recorded owners of a corporate shareholder",
            description = "Percentages are of the corporate shareholder's capital and votes; totals may not exceed 100 and "
                    + "circular ownership is rejected.")
    @PutMapping("/shareholders/{holderId}/owners")
    @PreAuthorize(WRITE)
    public List<OwnershipDtos.LinkView> replace(@PathVariable UUID orgId, @PathVariable UUID holderId,
                                                @Valid @RequestBody OwnershipDtos.ReplaceOwnersRequest body, Authentication auth) {
        return ownership.replaceOwners(orgId, holderId, body.owners(), auth.getName()).stream().map(UboController::view).toList();
    }

    @Operation(summary = "Ultimate beneficial owners, looked through holding companies")
    @GetMapping("/ubo")
    @PreAuthorize(READ)
    public UboReport report(@PathVariable UUID orgId,
                            @RequestParam(defaultValue = "25") BigDecimal threshold) {
        return ubo.report(orgId, threshold);
    }

    @Operation(summary = "Download the UBO schedule as CSV or PDF (the export is audited)")
    @GetMapping("/ubo/export")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> export(@PathVariable UUID orgId, @RequestParam String format,
                                         @RequestParam(defaultValue = "25") BigDecimal threshold, Authentication auth) {
        var out = ubo.export(orgId, threshold, format, auth.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(out.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + out.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .body(out.bytes());
    }

    private static OwnershipDtos.LinkView view(com.equigcc.ownership.OwnershipLink l) {
        return new OwnershipDtos.LinkView(l.getOwner().getId(), l.getOwner().getFullNameEn(), l.getOwner().getFullNameAr(),
                l.getOwned().getId(), l.getOwned().getFullNameEn(), l.getOwned().getFullNameAr(),
                l.getEconomicPct(), l.getVotingPct());
    }
}
