package com.equigcc.analytics;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsEmbedServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.now().plusSeconds(60);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ORG = UUID.randomUUID();

    @Test
    void signsALinkLockedToTheCompanyThatExpiresInTenMinutes() {
        var service = new AnalyticsEmbedService("https://analytics.example.com/", 7, SECRET, CLOCK);
        var embed = service.embed(ORG).orElseThrow();

        assertThat(embed.url()).startsWith("https://analytics.example.com/embed/dashboard/").endsWith("#bordered=false&titled=false");
        assertThat(embed.expiresAt()).isEqualTo(NOW.plus(AnalyticsEmbedService.TTL));

        String token = embed.url().substring("https://analytics.example.com/embed/dashboard/".length(), embed.url().indexOf('#'));
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload();
        assertThat(claims.get("resource", Map.class)).containsEntry("dashboard", 7);
        assertThat(claims.get("params", Map.class)).containsEntry("organization_id", ORG.toString());
        assertThat(claims.getExpiration().toInstant().getEpochSecond()).isEqualTo(embed.expiresAt().getEpochSecond());
    }

    @Test
    void isOffUntilTheSiteDashboardAndSecretAreAllSet() {
        assertThat(new AnalyticsEmbedService("", 7, SECRET, CLOCK).embed(ORG)).isEmpty();
        assertThat(new AnalyticsEmbedService("https://a.example.com", 0, SECRET, CLOCK).embed(ORG)).isEmpty();
        assertThat(new AnalyticsEmbedService("https://a.example.com", 7, "", CLOCK).embed(ORG)).isEmpty();
        assertThat(new AnalyticsEmbedService("https://a.example.com", 7, "short", CLOCK).configured()).isFalse();
    }
}
