package com.equigcc.history;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Ownership history: a holder's ledger and a certificate's family tree. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Ownership history")
@PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') "
        + "and @orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @Operation(summary = "Everything a shareholder acquired and disposed of, in date order, with a running share count")
    @GetMapping("/shareholders/{shareholderId}/ledger")
    public HistoryService.Ledger ledger(@PathVariable UUID orgId, @PathVariable UUID shareholderId) {
        return history.ledger(orgId, shareholderId);
    }

    @Operation(summary = "The family tree of a certificate: what it replaced and what replaced it")
    @GetMapping("/certificates/{certificateNo}/lineage")
    public List<HistoryService.LineageNode> lineage(@PathVariable UUID orgId, @PathVariable String certificateNo) {
        return history.lineage(orgId, certificateNo);
    }
}
