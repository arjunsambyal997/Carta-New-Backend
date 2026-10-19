package com.equigcc.apikey;

import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");
    private final UUID org = UUID.randomUUID();
    private final ApiKeyRepository repo = mock(ApiKeyRepository.class);
    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(i -> {
            ApiKey k = i.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });
        service = new ApiKeyService(repo, mock(AuditService.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createReturnsPlaintextOnceAndStoresOnlyItsHash() {
        var created = service.create(org, " Reporting ", "owner");
        assertThat(created.plaintext()).startsWith("eqk_").hasSize(4 + 8 + 1 + 40);
        assertThat(created.key().getName()).isEqualTo("Reporting");
        assertThat(created.key().getKeyHash()).isEqualTo(ApiKeyService.sha256(created.plaintext())).doesNotContain(created.plaintext());
        assertThat(created.plaintext()).startsWith(created.key().getKeyPrefix());
    }

    @Test
    void createRejectsBlankNamesAndTooManyKeys() {
        assertThatThrownBy(() -> service.create(org, "  ", "owner")).isInstanceOf(BusinessRuleException.class);
        when(repo.countByOrganizationIdAndRevokedAtIsNull(org)).thenReturn((long) ApiKeyService.MAX_ACTIVE_KEYS);
        assertThatThrownBy(() -> service.create(org, "One more", "owner")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void authenticateAcceptsActiveKeysAndTouchesLastUsed() {
        var created = service.create(org, "k", "owner");
        when(repo.findByKeyHash(created.key().getKeyHash())).thenReturn(Optional.of(created.key()));
        assertThat(service.authenticate(created.plaintext())).contains(created.key());
        assertThat(created.key().getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void authenticateRejectsRevokedUnknownAndMalformedKeys() {
        var created = service.create(org, "k", "owner");
        when(repo.findByKeyHash(created.key().getKeyHash())).thenReturn(Optional.of(created.key()));
        when(repo.findById(created.key().getId())).thenReturn(Optional.of(created.key()));
        service.revoke(org, created.key().getId(), "owner");
        assertThat(service.authenticate(created.plaintext())).isEmpty();
        assertThat(service.authenticate("eqk_" + "x".repeat(49))).isEmpty();
        assertThat(service.authenticate("nope")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
    }

    @Test
    void revokeIgnoresKeysOfOtherOrganizations() {
        var created = service.create(org, "k", "owner");
        when(repo.findById(created.key().getId())).thenReturn(Optional.of(created.key()));
        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), created.key().getId(), "owner")).isInstanceOf(NotFoundException.class);
    }
}
