package com.equigcc.service;

import com.equigcc.domain.AuthSession;
import com.equigcc.repository.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Server-side sign-in sessions. Every access token carries the session id, so signing out (or changing a password)
 * ends the session at once. The refresh token is rotated on every use; presenting an already-used one means it was
 * copied, so the whole session is ended.
 */
@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record Opened(AuthSession session, String refreshToken) {
    }

    private final AuthSessionRepository sessions;
    private final Clock clock;
    private final Duration refreshTtl;

    public SessionService(AuthSessionRepository sessions, Clock clock, @Value("${equigcc.security.refresh-token-ttl:P14D}") Duration refreshTtl) {
        this.sessions = sessions;
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public Opened open(UUID userId, UUID orgId, boolean mfa) {
        var now = clock.instant();
        var s = new AuthSession();
        s.setUserId(userId);
        s.setOrganizationId(orgId);
        s.setMfa(mfa);
        s.setLastUsedAt(now);
        s.setRefreshExpiresAt(now.plus(refreshTtl));
        String token = newToken();
        s.setRefreshHash(sha256(token));
        return new Opened(sessions.save(s), token);
    }

    /** Exchanges a refresh token for the next one. A replayed token ends the session. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Opened rotate(String refreshToken) {
        String hash = sha256(refreshToken == null ? "" : refreshToken.trim());
        var now = clock.instant();
        var s = sessions.findByRefreshHash(hash).orElse(null);
        if (s == null) {
            var replayed = sessions.findByPrevRefreshHash(hash).orElse(null);
            if (replayed != null && replayed.getRevokedAt() == null) {
                replayed.setRevokedAt(now);
            }
            throw new UnauthorizedException("Your session has ended. Sign in again.");
        }
        if (s.getRevokedAt() != null || !now.isBefore(s.getRefreshExpiresAt())) {
            throw new UnauthorizedException("Your session has ended. Sign in again.");
        }
        String next = newToken();
        s.setPrevRefreshHash(s.getRefreshHash());
        s.setRefreshHash(sha256(next));
        s.setLastUsedAt(now);
        s.setRefreshExpiresAt(now.plus(refreshTtl));
        return new Opened(s, next);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId) {
        return sessions.isActive(sessionId);
    }

    @Transactional
    public void revoke(UUID sessionId) {
        sessions.findById(sessionId).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(clock.instant());
            }
        });
    }

    /** Ends every session of the user, except the one named (the caller's own, when changing a password). */
    @Transactional
    public void revokeAll(UUID userId, UUID except) {
        var now = clock.instant();
        for (var s : sessions.findActiveByUser(userId)) {
            if (!s.getId().equals(except)) {
                s.setRevokedAt(now);
            }
        }
    }

    private static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
