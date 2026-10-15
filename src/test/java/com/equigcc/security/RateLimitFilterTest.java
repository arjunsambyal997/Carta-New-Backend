package com.equigcc.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private MockHttpServletResponse call(RateLimitFilter f, String path, String ip, String xff) throws Exception {
        var req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void sensitiveEndpointsAreLimitedPerClientAndOthersAreNot() throws Exception {
        var f = new RateLimitFilter(Clock.fixed(NOW, ZoneOffset.UTC), true, 3, 100, false);
        for (int i = 0; i < 3; i++) {
            assertThat(call(f, "/api/v1/auth/login", "1.1.1.1", null).getStatus()).isEqualTo(200);
        }
        var blocked = call(f, "/api/v1/auth/login", "1.1.1.1", null);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(call(f, "/api/v1/auth/login", "2.2.2.2", null).getStatus()).isEqualTo(200);
        assertThat(call(f, "/api/v1/organizations/x/cap-table", "1.1.1.1", null).getStatus()).isEqualTo(200);
        assertThat(call(f, "/api/v1/auth/me", "1.1.1.1", null).getStatus()).isEqualTo(200);
    }

    @Test
    void windowResets() {
        var clock = new java.util.concurrent.atomic.AtomicLong(NOW.toEpochMilli());
        var f = new RateLimitFilter(new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(clock.get()); }
        }, true, 2, 100, false);
        assertThat(f.hit("k", 2)).isZero();
        assertThat(f.hit("k", 2)).isZero();
        assertThat(f.hit("k", 2)).isPositive();
        clock.addAndGet(61_000);
        assertThat(f.hit("k", 2)).isZero();
    }

    @Test
    void forwardedHeaderIsOnlyTrustedWhenConfigured() throws Exception {
        var plain = new RateLimitFilter(Clock.fixed(NOW, ZoneOffset.UTC), true, 1, 100, false);
        call(plain, "/api/v1/auth/login", "9.9.9.9", "5.5.5.5");
        assertThat(call(plain, "/api/v1/auth/login", "9.9.9.9", "6.6.6.6").getStatus()).isEqualTo(429);
        var proxied = new RateLimitFilter(Clock.fixed(NOW, ZoneOffset.UTC), true, 1, 100, true);
        call(proxied, "/api/v1/auth/login", "9.9.9.9", "5.5.5.5");
        assertThat(call(proxied, "/api/v1/auth/login", "9.9.9.9", "6.6.6.6").getStatus()).isEqualTo(200);
    }
}
