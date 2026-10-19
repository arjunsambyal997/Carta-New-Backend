package com.equigcc.approval;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Two-person approval of register changes. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Approvals")
public class ApprovalController {

    private static final String ORG = "@orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";
    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and " + ORG;
    private static final String APPROVER = "hasAnyRole('FOUNDER','BOARD_MEMBER') and " + ORG;
    private static final String REQUESTER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and " + ORG;
    private static final String FOUNDER = "hasRole('FOUNDER') and " + ORG;

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @Operation(summary = "Changes waiting for approval, and past decisions")
    @GetMapping("/approvals")
    @PreAuthorize(READ)
    public List<ApprovalViews.Change> list(@PathVariable UUID orgId) {
        return service.list(orgId).stream().map(ApprovalViews.Change::from).toList();
    }

    @Operation(summary = "Approve a pending change. It runs now through the normal rules, and must be a different person than the requester")
    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize(APPROVER)
    public ApprovalViews.Change approve(@PathVariable UUID orgId, @PathVariable UUID id, @RequestBody(required = false) ApprovalViews.Decision d, Authentication auth) {
        return ApprovalViews.Change.from(service.approve(orgId, id, auth.getName(), d == null ? null : d.note()));
    }

    @Operation(summary = "Reject a pending change")
    @PostMapping("/approvals/{id}/reject")
    @PreAuthorize(APPROVER)
    public ApprovalViews.Change reject(@PathVariable UUID orgId, @PathVariable UUID id, @RequestBody(required = false) ApprovalViews.Decision d, Authentication auth) {
        return ApprovalViews.Change.from(service.reject(orgId, id, auth.getName(), d == null ? null : d.note()));
    }

    @Operation(summary = "Withdraw your own pending change")
    @PostMapping("/approvals/{id}/cancel")
    @PreAuthorize(REQUESTER)
    public ApprovalViews.Change cancel(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        return ApprovalViews.Change.from(service.cancel(orgId, id, auth.getName()));
    }

    @Operation(summary = "Turn two-person approval on or off (founder only)")
    @PutMapping("/settings/approvals")
    @PreAuthorize(FOUNDER)
    public ApprovalViews.Setting setting(@PathVariable UUID orgId, @RequestBody ApprovalViews.Setting s, Authentication auth) {
        return new ApprovalViews.Setting(service.setRequired(orgId, s.required(), auth.getName()));
    }
}
