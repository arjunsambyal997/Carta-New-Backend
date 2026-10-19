package com.equigcc.compliance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/compliance")
@Tag(name = "Compliance")
public class ComplianceController {

    private static final String ORG = "@orgAccess.canAccess(authentication, #orgId)";
    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and " + ORG;
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and " + ORG;

    public record FilingRequest(LocalDate filedOn, @Size(max = 200) String reference) { }

    private final ComplianceService service;

    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    @Operation(summary = "Filing deadlines that apply to this company's jurisdiction, with their legal basis and status")
    @GetMapping
    @PreAuthorize(READ)
    public ComplianceService.View view(@PathVariable UUID orgId) {
        return service.view(orgId);
    }

    @Operation(summary = "Record that a filing was made, which clears its deadline until the next register change")
    @PostMapping("/{key}/filings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public ComplianceService.Item file(@PathVariable UUID orgId, @PathVariable String key, @Valid @RequestBody FilingRequest r,
                                       Authentication auth) {
        return service.recordFiling(orgId, key, r.filedOn(), r.reference(), auth.getName());
    }
}
