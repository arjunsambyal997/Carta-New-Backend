package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.AuditLog;
import com.equigcc.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Append-only, hash-chained audit trail per organization. Entries are written in the caller's
 * transaction, so an equity change and its audit entry commit or roll back together.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final EntityManager em;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, EntityManager em, Clock clock) {
        this.repository = repository;
        this.em = em;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(UUID organizationId, String entityName, UUID entityId, AuditAction action,
                           String detail, String performedBy) {
        // Serialize chain appends per organization so two writers cannot fork the chain.
        em.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(hashtext(:k))) t")
                .setParameter("k", organizationId.toString())
                .getSingleResult();
        String prev = repository.findFirstByOrganizationIdOrderByIdDesc(organizationId)
                .map(AuditLog::getHash).orElse(AuditHasher.GENESIS);
        var at = clock.instant().truncatedTo(ChronoUnit.MICROS); // matches PostgreSQL timestamptz precision
        String hash = AuditHasher.hash(prev, organizationId, entityName, entityId, action, detail, performedBy, at);
        return repository.save(new AuditLog(organizationId, entityName, entityId, action, detail, performedBy, at, prev, hash));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> latest(UUID organizationId, int limit) {
        return repository.findByOrganizationIdOrderByIdDesc(organizationId, PageRequest.of(0, Math.min(limit, 500)));
    }

    /** Recomputes the whole chain; returns the id of the first broken entry, or null when intact. */
    @Transactional(readOnly = true)
    public Long firstBrokenEntry(UUID organizationId) {
        String prev = AuditHasher.GENESIS;
        for (AuditLog e : repository.findByOrganizationIdOrderByIdAsc(organizationId)) {
            String expected = AuditHasher.hash(prev, e.getOrganizationId(), e.getEntityName(), e.getEntityId(),
                    e.getAction(), e.getDetail(), e.getPerformedBy(), e.getOccurredAt());
            if (!e.getPrevHash().equals(prev) || !e.getHash().equals(expected)) {
                return e.getId();
            }
            prev = e.getHash();
        }
        return null;
    }
}
