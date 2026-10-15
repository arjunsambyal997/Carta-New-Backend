package com.equigcc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final java.util.function.Predicate<UUID> sessionActive;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this(jwtService, id -> true);
    }

    /** {@code sessionActive} says whether a session id is still signed in; signing out makes it false. */
    public JwtAuthenticationFilter(JwtService jwtService, java.util.function.Predicate<UUID> sessionActive) {
        this.jwtService = jwtService;
        this.sessionActive = sessionActive;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                if (!JwtService.TYPE_ACCESS.equals(claims.get(JwtService.TYPE, String.class))) {
                    throw new io.jsonwebtoken.JwtException("Not an access token");
                }
                Set<UUID> orgIds = stringList(claims, JwtService.ORG_IDS).stream()
                        .map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
                boolean mfa = stringList(claims, JwtService.AMR).contains("otp");
                String sh = claims.get(JwtService.SHAREHOLDER_ID, String.class);
                String sid = claims.get(JwtService.SESSION_ID, String.class);
                UUID sessionId = sid == null ? null : UUID.fromString(sid);
                if (sessionId != null && !sessionActive.test(sessionId)) {
                    throw new io.jsonwebtoken.JwtException("Session ended");
                }
                var principal = new AuthenticatedUser(claims.getSubject(), orgIds, mfa, sh == null ? null : UUID.fromString(sh), sessionId);
                var authorities = stringList(claims, JwtService.ROLES).stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static List<String> stringList(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
