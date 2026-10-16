package com.equigcc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client request limits, in memory. Sign-in, sign-up, second-factor, password and refresh calls get a tight limit
 * (on top of the per-account lockout); everything else gets a generous one that only stops runaway clients.
 * Counts live in this process only: with several backend instances each one counts on its own.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final Set<String> SENSITIVE = Set.of("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/mfa/verify",
            "/api/v1/auth/mfa/confirm", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/auth/password/change",
            "/api/v1/auth/refresh");

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;
    private final int authPerMinute;
    private final int apiPerMinute;
    private final boolean trustForwarded;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(@Value("${equigcc.rate-limit.enabled:true}") boolean enabled,
                           @Value("${equigcc.rate-limit.auth-per-minute:30}") int authPerMinute,
                           @Value("${equigcc.rate-limit.api-per-minute:600}") int apiPerMinute,
                           @Value("${equigcc.rate-limit.trust-forwarded:false}") boolean trustForwarded) {
        this(Clock.systemUTC(), enabled, authPerMinute, apiPerMinute, trustForwarded);
    }

    RateLimitFilter(Clock clock,
                           boolean enabled,
                           int authPerMinute,
                           int apiPerMinute,
                           boolean trustForwarded) {
        this.clock = clock;
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
        this.trustForwarded = trustForwarded;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean sensitive = SENSITIVE.contains(request.getRequestURI());
        int limit = sensitive ? authPerMinute : apiPerMinute;
        long retryAfter = hit((sensitive ? "auth:" : "api:") + client(request), limit);
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Too many requests. Try again in "
                    + retryAfter + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Counts one request; returns 0 if allowed, otherwise the seconds until the window resets. */
    long hit(String key, int limit) {
        long now = clock.millis();
        if (windows.size() > 20_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
        Window w = windows.merge(key, new Window(now, 1), (old, fresh) -> now - old.start() >= WINDOW_MS ? fresh : new Window(old.start(), old.count() + 1));
        if (w.count() > limit) {
            return Math.max(1, (WINDOW_MS - (now - w.start()) + 999) / 1000);
        }
        return 0;
    }

    private String client(HttpServletRequest request) {
        if (trustForwarded) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
