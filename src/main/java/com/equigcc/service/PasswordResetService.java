package com.equigcc.service;

import com.equigcc.domain.PasswordResetToken;
import com.equigcc.repository.AppUserRepository;
import com.equigcc.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Email-based password reset. A request always looks the same from outside whether or not the address has an account.
 * Tokens are 256 random bits, stored only as a SHA-256, single use and short lived. Resetting a password also clears a
 * lockout but never turns off two-factor authentication.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Duration ttl;
    private final int maxPerHour;
    private final String baseUrl;

    public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                PasswordResetMailer mailer, Clock clock,
                                @Value("${equigcc.password-reset.token-ttl}") Duration ttl,
                                @Value("${equigcc.password-reset.max-requests-per-hour}") int maxPerHour,
                                @Value("${equigcc.public-base-url}") String baseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailer = mailer;
        this.clock = clock;
        this.ttl = ttl;
        this.maxPerHour = maxPerHour;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Sends a reset link if the address has an account. Silent either way so the endpoint cannot be used to find accounts. */
    @Transactional
    public void request(String email) {
        var user = users.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        if (tokens.countSince(user.getId(), now.minus(Duration.ofHours(1))) >= maxPerHour) {
            return; // throttled: stop mail-bombing a victim's inbox
        }
        // Only the newest link works.
        for (var old : tokens.findUnused(user.getId())) {
            old.setUsedAt(now);
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        var t = new PasswordResetToken();
        t.setUser(user);
        t.setTokenHash(sha256(token));
        t.setExpiresAt(now.plus(ttl));
        tokens.save(t);
        mailer.send(user.getEmail(), user.getDisplayName(), baseUrl + "/reset-password?token=" + token, (int) ttl.toMinutes());
    }

    @Transactional
    public void reset(String token, String newPassword) {
        AuthService.requireStrong(newPassword);
        var t = tokens.findByTokenHash(sha256(token == null ? "" : token.trim()))
                .filter(x -> x.isUsable(clock.instant()))
                .orElseThrow(() -> new BusinessRuleException("This reset link is invalid or has expired"));
        var user = t.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        t.setUsedAt(clock.instant());
        for (var other : tokens.findUnused(user.getId())) {
            other.setUsedAt(clock.instant());
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
