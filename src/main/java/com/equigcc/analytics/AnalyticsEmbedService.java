package com.equigcc.analytics;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed links to the Metabase analytics dashboard. The token locks the dashboard's organization_id filter to the
 * caller's company, and Metabase refuses a link whose signature or expiry does not check out, so changing the URL in
 * the browser cannot show another company's numbers.
 */
@Service
public class AnalyticsEmbedService {

    public record Embed(String url, Instant expiresAt) {
    }

    static final Duration TTL = Duration.ofHours(1);

    private final String siteUrl;
    private final long dashboardId;
    private final SecretKey key;
    private final Clock clock;

    public AnalyticsEmbedService(@Value("${equigcc.analytics.metabase-site-url:}") String siteUrl,
                                 @Value("${equigcc.analytics.dashboard-id:0}") long dashboardId,
                                 @Value("${equigcc.analytics.embedding-secret:}") String secret,
                                 Clock clock) {
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.dashboardId = dashboardId;
        // Metabase's secret is 64 hex characters and is used as-is (its UTF-8 bytes), not hex-decoded.
        this.key = secret.length() >= 32 ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)) : null;
        this.clock = clock;
    }

    public boolean configured() {
        return !siteUrl.isEmpty() && dashboardId > 0 && key != null;
    }

    public Optional<Embed> embed(UUID orgId) {
        if (!configured()) {
            return Optional.empty();
        }
        Instant expires = clock.instant().plus(TTL);
        String token = Jwts.builder()
                .claim("resource", Map.of("dashboard", dashboardId))
                .claim("params", Map.of("organization_id", orgId.toString()))
                .expiration(Date.from(expires))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return Optional.of(new Embed(siteUrl + "/embed/dashboard/" + token + "#bordered=false&titled=false", expires));
    }
}
