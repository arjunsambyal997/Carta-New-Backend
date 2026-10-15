package com.equigcc.web;

import com.equigcc.domain.ShareIssuance;
import com.equigcc.service.SuccessionService;
import com.equigcc.web.dto.CertificateResponse;
import com.equigcc.web.dto.SuccessionDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/succession-rules")
@Tag(name = "Succession")
public class SuccessionController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId)";

    private final SuccessionService service;
    private final String verificationBaseUrl;

    public SuccessionController(SuccessionService service, @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.service = service;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Operation(summary = "Define a succession rule (a pre-agreed share class conversion)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public SuccessionDtos.RuleView create(@PathVariable UUID orgId, @Valid @RequestBody SuccessionDtos.CreateRequest r, Authentication auth) {
        var rule = service.create(orgId, new SuccessionService.NewRule(r.name(), r.trigger(), r.subjectId(), r.fromClassId(), r.toClassId(),
                r.ratioNumerator(), r.ratioDenominator(), r.beneficiaryId(), r.legalReference()), auth.getName());
        return SuccessionDtos.RuleView.from(service.get(orgId, rule.getId()));
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<SuccessionDtos.RuleView> list(@PathVariable UUID orgId) {
        return service.list(orgId).stream().map(SuccessionDtos.RuleView::from).toList();
    }

    @Operation(summary = "What executing the rule would do, without changing anything")
    @GetMapping("/{id}/preview")
    @PreAuthorize(READ)
    public List<SuccessionService.Conversion> preview(@PathVariable UUID orgId, @PathVariable UUID id) {
        return service.preview(orgId, id);
    }

    @Operation(summary = "Execute the rule: retire the matching certificates and issue their replacements")
    @PostMapping("/{id}/execute")
    @PreAuthorize(WRITE + " and @orgAccess.mfaVerified(authentication)")
    public List<CertificateResponse> execute(@PathVariable UUID orgId, @PathVariable UUID id,
                                             @RequestBody(required = false) SuccessionDtos.ExecuteRequest body, Authentication auth) {
        List<ShareIssuance> created = service.execute(orgId, id, body == null ? null : body.resolutionId(), auth.getName());
        return created.stream().map(i -> CertificateResponse.from(i, verificationBaseUrl)).toList();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public SuccessionDtos.RuleView cancel(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        service.cancel(orgId, id, auth.getName());
        return SuccessionDtos.RuleView.from(service.get(orgId, id));
    }
}
