package com.equigcc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                           org.springframework.beans.factory.ObjectProvider<com.equigcc.service.SessionService> sessions) throws Exception {
        var sessionService = sessions.getIfAvailable();
        return http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/certificates/*/verify").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(sessionService == null ? new JwtAuthenticationFilter(jwtService) : new JwtAuthenticationFilter(jwtService, sessionService::isActive), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
