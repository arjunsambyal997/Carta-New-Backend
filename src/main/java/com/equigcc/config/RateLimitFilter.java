package com.equigcc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

/**
 * Per-client request limits, in memory. Sign-in, sign-up, second-factor, password and refresh calls get a tight limit
 * (on top of the per-account lockout); everything else gets a generous one that only stops runaway clients.
 * Sign-in style calls count in the database when RATE_LIMIT_STORE=database, so several backend instances share one limit; the
 * general limit always counts in this process, which keeps every ordinary request off the database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE = Set.of("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/mfa/verify",
            "/api/v1/auth/mfa/confirm", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/auth/password/change",
            "/api/v1/auth/refresh");

    private final RateLimitStore apiStore;
    private final RateLimitStore authStore;
    private final boolean enabled;
    private final int authPerMinute;
    private final int apiPerMinute;
    private final boolean trustForwarded;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(@Value("${equigcc.rate-limit.enabled:true}") boolean enabled,
                           @Value("${equigcc.rate-limit.auth-per-minute:30}") int authPerMinute,
                           @Value("${equigcc.rate-limit.api-per-minute:600}") int apiPerMinute,
                           @Value("${equigcc.rate-limit.trust-forwarded:false}") boolean trustForwarded,
                           @Value("${equigcc.rate-limit.store:memory}") String store,
                           ObjectProvider<JdbcTemplate> jdbc) {
        this(enabled, authPerMinute, apiPerMinute, trustForwarded, new MemoryRateLimitStore(Clock.systemUTC()),
                "database".equalsIgnoreCase(store) ? new JdbcRateLimitStore(jdbc.getObject(), Clock.systemUTC())
                        : new MemoryRateLimitStore(Clock.systemUTC()));
    }

    RateLimitFilter(Clock clock, boolean enabled, int authPerMinute, int apiPerMinute, boolean trustForwarded) {
        this(enabled, authPerMinute, apiPerMinute, trustForwarded, new MemoryRateLimitStore(clock), new MemoryRateLimitStore(clock));
    }

    RateLimitFilter(boolean enabled, int authPerMinute, int apiPerMinute, boolean trustForwarded,
                    RateLimitStore apiStore, RateLimitStore authStore) {
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
        this.trustForwarded = trustForwarded;
        this.apiStore = apiStore;
        this.authStore = authStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !RequestPaths.of(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean sensitive = SENSITIVE.contains(RequestPaths.of(request));
        int limit = sensitive ? authPerMinute : apiPerMinute;
        long retryAfter = sensitive ? authStore.hit("auth:" + client(request), limit) : apiStore.hit("api:" + client(request), limit);
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

    /** Counts one request against the general (per-process) limit; returns 0 if allowed, otherwise the seconds until the window resets. */
    long hit(String key, int limit) {
        return apiStore.hit(key, limit);
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
