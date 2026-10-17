package com.equigcc.auth;

import com.equigcc.common.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordResetMailer mailer = mock(PasswordResetMailer.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private PasswordResetService service;
    private AppUser user;
    private final List<PasswordResetToken> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(users, tokens, encoder, mailer, new SessionService(mock(com.equigcc.auth.AuthSessionRepository.class), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(14)), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30), 3, "https://app.example/");
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("fatima@alnoor.ae");
        user.setDisplayName("Fatima");
        user.setPasswordHash(encoder.encode("old password value"));
        user.setFailedAttempts(5);
        user.setLockedUntil(NOW.plusSeconds(600));
        when(users.findByEmail("fatima@alnoor.ae")).thenReturn(Optional.of(user));
        when(tokens.save(any())).thenAnswer(i -> {
            PasswordResetToken t = i.getArgument(0);
            stored.add(t);
            return t;
        });
        when(tokens.findUnused(user.getId())).thenAnswer(i -> stored.stream().filter(t -> t.getUsedAt() == null).toList());
        when(tokens.findByTokenHash(anyString())).thenAnswer(i -> stored.stream().filter(t -> t.getTokenHash().equals(i.getArgument(0))).findFirst());
    }

    private String requestAndCaptureToken() {
        service.request("fatima@alnoor.ae");
        var link = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).send(anyString(), anyString(), link.capture(), anyInt());
        String l = link.getValue();
        assertThat(l).startsWith("https://app.example/reset-password?token=");
        return l.substring(l.indexOf("token=") + 6);
    }

    @Test
    void emailsALinkWhoseTokenIsStoredOnlyAsAHash() {
        String token = requestAndCaptureToken();
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTokenHash()).isEqualTo(PasswordResetService.sha256(token)).isNotEqualTo(token);
        assertThat(stored.get(0).getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void unknownAddressesGetNoEmailAndNoError() {
        service.request("nobody@x.ae");
        verify(mailer, never()).send(any(), any(), any(), anyInt());
        assertThat(stored).isEmpty();
    }

    @Test
    void resetSetsThePasswordClearsLockoutAndBurnsTheToken() {
        String token = requestAndCaptureToken();
        service.reset(token, "a brand new password");
        assertThat(encoder.matches("a brand new password", user.getPasswordHash())).isTrue();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isLocked(NOW)).isFalse();
        assertThatThrownBy(() -> service.reset(token, "another new password!")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyTheNewestLinkWorks() {
        service.request("fatima@alnoor.ae");
        var first = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(anyString(), anyString(), first.capture(), anyInt());
        String oldToken = first.getValue().substring(first.getValue().indexOf("token=") + 6);
        service.request("fatima@alnoor.ae");
        assertThatThrownBy(() -> service.reset(oldToken, "a brand new password")).hasMessageContaining("invalid or has expired");
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        String token = requestAndCaptureToken();
        stored.get(0).setExpiresAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.reset(token, "a brand new password")).hasMessageContaining("invalid or has expired");
        assertThatThrownBy(() -> service.reset("garbage", "a brand new password")).hasMessageContaining("invalid or has expired");
        assertThatThrownBy(() -> service.reset(null, "a brand new password")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void weakNewPasswordsAreRejectedBeforeTheTokenIsSpent() {
        String token = requestAndCaptureToken();
        assertThatThrownBy(() -> service.reset(token, "short")).hasMessageContaining("12 characters");
        assertThat(stored.get(0).getUsedAt()).isNull();
    }

    @Test
    void requestsAreThrottledPerHour() {
        when(tokens.countSince(any(), any())).thenReturn(3L);
        service.request("fatima@alnoor.ae");
        verify(mailer, never()).send(any(), any(), any(), anyInt());
    }
}
