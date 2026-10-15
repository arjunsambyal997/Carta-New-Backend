package com.equigcc.web;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.AuditLog;
import com.equigcc.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/audit")
@Tag(name = "Audit trail")
public class AuditController {

    public record Entry(long id, String entityName, UUID entityId, AuditAction action, String detail,
                        String performedBy, Instant occurredAt, String hash) {
        static Entry from(AuditLog e) {
            return new Entry(e.getId(), e.getEntityName(), e.getEntityId(), e.getAction(), e.getDetail(),
                    e.getPerformedBy(), e.getOccurredAt(), e.getHash());
        }
    }

    /** {@code firstBrokenEntry} is null when the whole hash chain verifies. */
    public record Trail(boolean chainIntact, Long firstBrokenEntry, List<Entry> entries) {
    }

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','LEGAL_AUDITOR') "
            + "and @orgAccess.canAccess(authentication, #orgId)")
    public Trail trail(@PathVariable UUID orgId, @RequestParam(defaultValue = "100") int limit) {
        Long broken = audit.firstBrokenEntry(orgId);
        return new Trail(broken == null, broken,
                audit.latest(orgId, limit).stream().map(Entry::from).toList());
    }
}
