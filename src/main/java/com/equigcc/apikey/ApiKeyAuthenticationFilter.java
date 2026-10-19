package com.equigcc.apikey;

import com.equigcc.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Authenticates {@code X-API-Key} requests. A key acts as an auditor of exactly one organization and can only read:
 * anything but GET or HEAD is refused here, before any controller runs.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService keys;

    public ApiKeyAuthenticationFilter(ApiKeyService keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null) {
            chain.doFilter(request, response);
            return;
        }
        var key = keys.authenticate(presented.trim());
        if (key.isEmpty()) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "API keys are read-only");
            return;
        }
        // The key is a machine credential: there is no person to ask for a second factor, so it counts as verified.
        var principal = new AuthenticatedUser("apikey:" + key.get().getKeyPrefix(), Set.of(key.get().getOrganizationId()), true, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_LEGAL_AUDITOR"))));
        chain.doFilter(request, response);
    }
}
