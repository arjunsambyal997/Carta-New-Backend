package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.AuditLog;
import com.equigcc.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final UUID org = UUID.randomUUID();
    private final AuditLogRepository repo = mock(AuditLogRepository.class);
    private final AuditService service = new AuditService(repo, null, Clock.systemUTC());

    @Test
    void intactChainVerifies() {
        when(repo.findByOrganizationIdOrderByIdAsc(org)).thenReturn(chain(3));
        assertThat(service.firstBrokenEntry(org)).isNull();
    }

    @Test
    void editedEntryIsDetected() {
        List<AuditLog> chain = chain(3);
        var original = chain.get(1);
        var forged = new AuditLog(org, original.getEntityName(), original.getEntityId(), original.getAction(),
                "tampered detail", original.getPerformedBy(), original.getOccurredAt(), original.getPrevHash(), original.getHash());
        ReflectionTestUtils.setField(forged, "id", original.getId());
        chain.set(1, forged);
        when(repo.findByOrganizationIdOrderByIdAsc(org)).thenReturn(chain);
        assertThat(service.firstBrokenEntry(org)).isEqualTo(2L);
    }

    @Test
    void deletedEntryBreaksTheChain() {
        List<AuditLog> chain = chain(3);
        chain.remove(1);
        when(repo.findByOrganizationIdOrderByIdAsc(org)).thenReturn(chain);
        assertThat(service.firstBrokenEntry(org)).isEqualTo(3L);
    }

    private List<AuditLog> chain(int n) {
        List<AuditLog> out = new ArrayList<>();
        String prev = AuditHasher.GENESIS;
        Instant at = Instant.parse("2026-10-15T10:00:00Z");
        for (int i = 0; i < n; i++) {
            UUID entity = UUID.randomUUID();
            at = at.plusSeconds(60);
            String hash = AuditHasher.hash(prev, org, "ShareIssuance", entity, AuditAction.CREATE, "entry " + i, "user", at);
            var entry = new AuditLog(org, "ShareIssuance", entity, AuditAction.CREATE, "entry " + i, "user", at, prev, hash);
            ReflectionTestUtils.setField(entry, "id", i + 1L);
            out.add(entry);
            prev = hash;
        }
        return out;
    }
}
