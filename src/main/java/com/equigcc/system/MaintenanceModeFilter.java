package com.equigcc.system;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

/**
 * During a read-only maintenance window, reads keep working (cap tables, certificate downloads) and every
 * write is answered with 503 and a Retry-After. The decision is cached for a few seconds so it costs no query per request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
    static final long CACHE_MS = 5_000;

    private final SystemService system;
    private final Clock clock;
    private volatile boolean readOnly;
    private volatile Long checkedAt;

    /** Providers, not hard dependencies: web slice tests load filters but not services, and must keep working. */
    @Autowired
    public MaintenanceModeFilter(ObjectProvider<SystemService> system, ObjectProvider<Clock> clock) {
        this(system.getIfAvailable(), clock.getIfAvailable(Clock::systemUTC));
    }

    MaintenanceModeFilter(SystemService system, Clock clock) {
        this.system = system;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SAFE.contains(request.getMethod()) || path.startsWith("/api/v1/system/")
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (system != null && isReadOnly()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "300");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":503,\"title\":\"Scheduled maintenance\","
                    + "\"detail\":\"The platform is read-only during scheduled maintenance. Please retry shortly.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isReadOnly() {
        long now = clock.millis();
        Long last = checkedAt;
        if (last == null || now - last > CACHE_MS) {
            try {
                readOnly = system.isReadOnlyNow();
            } catch (RuntimeException e) {
                readOnly = false; // never lock clients out because the status lookup failed
            }
            checkedAt = now;
        }
        return readOnly;
    }
}
