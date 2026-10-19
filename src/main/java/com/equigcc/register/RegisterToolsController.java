package com.equigcc.register;

import com.equigcc.captable.CapTableExportService;
import com.equigcc.captable.SearchService;
import com.equigcc.certificate.CertificateResponse;
import com.equigcc.common.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Everyday register tools: bulk import, share transfers and buy-backs, search and cap table export. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Register tools")
public class RegisterToolsController {

    private static final long MAX_IMPORT_BYTES = 1024 * 1024;
    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";

    private final ImportService imports;
    private final TransferService transfers;
    private final SearchService search;
    private final CapTableExportService exports;
    private final String verificationBaseUrl;
    private final com.equigcc.notice.NoticeService notices;
    private final com.equigcc.webhook.WebhookService webhooks;
    private final com.equigcc.approval.ApprovalService approvals;

    public RegisterToolsController(com.equigcc.notice.NoticeService notices, com.equigcc.webhook.WebhookService webhooks, com.equigcc.approval.ApprovalService approvals, ImportService imports, TransferService transfers, SearchService search, CapTableExportService exports,
                                   @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.notices = notices;
        this.webhooks = webhooks;
        this.approvals = approvals;
        this.imports = imports;
        this.transfers = transfers;
        this.search = search;
        this.exports = exports;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Operation(summary = "Search holders, certificates, resolutions, documents and share classes")
    @GetMapping("/search")
    @PreAuthorize(READ)
    public RegisterToolsDtos.SearchResponse search(@PathVariable UUID orgId, @RequestParam String q) {
        return new RegisterToolsDtos.SearchResponse(q, search.search(orgId, q));
    }

    @Operation(summary = "Download the cap table as CSV or PDF (audited)")
    @GetMapping("/cap-table/export")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> exportCapTable(@PathVariable UUID orgId, @RequestParam String format, Authentication auth) {
        var out = exports.export(orgId, format, auth.getName());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(out.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + out.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "no-store").body(out.bytes());
    }

    @Operation(summary = "CSV template for an import: holders or issuances")
    @GetMapping("/import/templates/{kind}")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> template(@PathVariable UUID orgId, @PathVariable String kind) {
        String body = switch (kind) {
            case "holders" -> ImportService.HOLDERS_TEMPLATE;
            case "issuances" -> ImportService.ISSUANCES_TEMPLATE;
            default -> throw new BusinessRuleException("Unknown template: " + kind);
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header("Content-Disposition", "attachment; filename=\"" + kind + "-template.csv\"")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Import shareholders from CSV. dryRun=true only checks the file")
    @PostMapping(value = "/import/shareholders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    public ImportService.Result importHolders(@PathVariable UUID orgId, @RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "true") boolean dryRun, Authentication auth) throws IOException {
        return imports.importShareholders(orgId, bytes(file), dryRun, auth.getName());
    }

    @Operation(summary = "Import opening issuances from CSV. dryRun=true only checks the file")
    @PostMapping(value = "/import/issuances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    public ImportService.Result importIssuances(@PathVariable UUID orgId, @RequestParam("file") MultipartFile file,
                                                @RequestParam(defaultValue = "true") boolean dryRun, Authentication auth) throws IOException {
        return imports.importIssuances(orgId, bytes(file), dryRun, auth.getName());
    }

    @Operation(summary = "Transfer shares from one holder to another; certificates are retired and reissued")
    @PostMapping("/transfers")
    @PreAuthorize(WRITE)
    public ResponseEntity<Object> transfer(@PathVariable UUID orgId, @Valid @RequestBody RegisterToolsDtos.TransferRequest r, Authentication auth) {
        if (approvals.needsApproval(orgId, auth.getName())) {
            return waiting(approvals.propose(orgId, com.equigcc.approval.PendingChange.Kind.TRANSFER, r, auth.getName()));
        }
        var created = transfers.transfer(orgId, new TransferService.Transfer(r.fromShareholderId(), r.toShareholderId(), r.shareClassId(), r.quantity(),
                r.pricePerShare(), r.date(), r.resolutionId(), r.reference()), auth.getName());
        created.stream().filter(i -> i.getShareholder().getId().equals(r.toShareholderId())).forEach(i -> notices.certificateIssued(orgId, i.getCertificateNo()));
        webhooks.publish(orgId, com.equigcc.webhook.WebhookEvent.TRANSFER_RECORDED, java.util.Map.of("fromShareholderId", r.fromShareholderId().toString(),
                "toShareholderId", r.toShareholderId().toString(), "quantity", r.quantity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created.stream().map(i -> CertificateResponse.from(i, verificationBaseUrl)).toList());
    }

    @Operation(summary = "Buy shares back from a holder into treasury")
    @PostMapping("/buybacks")
    @PreAuthorize(WRITE)
    public ResponseEntity<Object> buyBack(@PathVariable UUID orgId, @Valid @RequestBody RegisterToolsDtos.BuyBackRequest r, Authentication auth) {
        if (approvals.needsApproval(orgId, auth.getName())) {
            return waiting(approvals.propose(orgId, com.equigcc.approval.PendingChange.Kind.BUY_BACK, r, auth.getName()));
        }
        var created = transfers.buyBack(orgId, new TransferService.BuyBack(r.fromShareholderId(), r.shareClassId(), r.quantity(), r.pricePerShare(),
                r.date(), r.resolutionId(), r.reference()), auth.getName());
        webhooks.publish(orgId, com.equigcc.webhook.WebhookEvent.BUYBACK_RECORDED, java.util.Map.of("fromShareholderId", r.fromShareholderId().toString(),
                "quantity", r.quantity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created.stream().map(i -> CertificateResponse.from(i, verificationBaseUrl)).toList());
    }

    /** 202: the change was recorded and waits for a second person. */
    private static ResponseEntity<Object> waiting(com.equigcc.approval.PendingChange p) {
        return ResponseEntity.accepted().body(com.equigcc.approval.ApprovalViews.Change.from(p));
    }

    private static byte[] bytes(MultipartFile f) throws IOException {
        if (f.isEmpty()) {
            throw new BusinessRuleException("Choose a CSV file");
        }
        if (f.getSize() > MAX_IMPORT_BYTES) {
            throw new BusinessRuleException("The file is larger than 1 MB");
        }
        return f.getBytes();
    }
}
