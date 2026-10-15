package com.equigcc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** HS256 JWT signing and verification. Swap for an OIDC provider's JWKS in production if preferred. */
@Component
public class JwtService {

    public static final String ROLES = "roles";
    public static final String ORG_IDS = "org_ids";
    public static final String AMR = "amr";
    public static final String SHAREHOLDER_ID = "shareholder_id";

    private final SecretKey key;

    public JwtService(@Value("${equigcc.security.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String issue(String subject, Collection<String> roles, Collection<UUID> orgIds,
                        boolean mfa, Duration ttl) {
        return issue(subject, roles, orgIds, mfa, null, ttl);
    }

    public String issue(String subject, Collection<String> roles, Collection<UUID> orgIds,
                        boolean mfa, UUID shareholderId, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder();
        if (shareholderId != null) {
            builder.claim(SHAREHOLDER_ID, shareholderId.toString());
        }
        return builder
                .subject(subject)
                .claim(ROLES, List.copyOf(roles))
                .claim(ORG_IDS, orgIds.stream().map(UUID::toString).toList())
                .claim(AMR, mfa ? List.of("pwd", "otp") : List.of("pwd"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
