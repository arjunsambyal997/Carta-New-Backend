package com.equigcc.auth;

import com.equigcc.common.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final List<AuthSession> store = new ArrayList<>();
    private final AuthSessionRepository repo = mock(AuthSessionRepository.class);
    private SessionService service;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(i -> {
            AuthSession s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
                store.add(s);
            }
            return s;
        });
        when(repo.findByRefreshHash(anyString())).thenAnswer(i -> store.stream().filter(s -> s.getRefreshHash().equals(i.getArgument(0))).findFirst());
        when(repo.findByPrevRefreshHash(anyString())).thenAnswer(i -> store.stream().filter(s -> i.getArgument(0).equals(s.getPrevRefreshHash())).findFirst());
        when(repo.findById(any())).thenAnswer(i -> store.stream().filter(s -> s.getId().equals(i.getArgument(0))).findFirst());
        when(repo.findActiveByUser(any())).thenAnswer(i -> store.stream().filter(s -> s.getUserId().equals(i.getArgument(0)) && s.getRevokedAt() == null).toList());
        service = new SessionService(repo, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(14));
    }

    @Test
    void refreshTokenIsStoredHashedAndRotates() {
        var a = service.open(UUID.randomUUID(), UUID.randomUUID(), true);
        assertThat(a.session().getRefreshHash()).isNotEqualTo(a.refreshToken()).hasSize(64);
        var b = service.rotate(a.refreshToken());
        assertThat(b.refreshToken()).isNotEqualTo(a.refreshToken());
        assertThat(b.session().getId()).isEqualTo(a.session().getId());
        assertThat(service.rotate(b.refreshToken()).session().getRevokedAt()).isNull();
    }

    @Test
    void replayingAnOldRefreshTokenEndsTheSession() {
        var a = service.open(UUID.randomUUID(), UUID.randomUUID(), true);
        var b = service.rotate(a.refreshToken());
        assertThatThrownBy(() -> service.rotate(a.refreshToken())).isInstanceOf(UnauthorizedException.class);
        assertThat(a.session().getRevokedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.rotate(b.refreshToken())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unknownAndRevokedAndExpiredTokensAreRefused() {
        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(UnauthorizedException.class);
        var a = service.open(UUID.randomUUID(), UUID.randomUUID(), true);
        service.revoke(a.session().getId());
        assertThatThrownBy(() -> service.rotate(a.refreshToken())).isInstanceOf(UnauthorizedException.class);
        var b = service.open(UUID.randomUUID(), UUID.randomUUID(), true);
        b.session().setRefreshExpiresAt(NOW);
        assertThatThrownBy(() -> service.rotate(b.refreshToken())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void revokeAllKeepsTheNamedSession() {
        UUID user = UUID.randomUUID();
        var keep = service.open(user, UUID.randomUUID(), true);
        var other = service.open(user, UUID.randomUUID(), true);
        service.revokeAll(user, keep.session().getId());
        assertThat(keep.session().getRevokedAt()).isNull();
        assertThat(other.session().getRevokedAt()).isEqualTo(NOW);
    }
}
