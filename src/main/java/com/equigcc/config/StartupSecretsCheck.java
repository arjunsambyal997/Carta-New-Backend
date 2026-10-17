package com.equigcc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Refuses to start in the "prod" profile with the development default secrets. */
@Component
public class StartupSecretsCheck {

    public static final String DEV_JWT_SECRET = "change-me-change-me-change-me-change-me-0123456789";
    public static final String DEV_ENCRYPTION_KEY = "ZGV2LW9ubHktYWVzMjU2LWtleS0wMTIzNDU2Nzg5YWI=";

    private final Environment env;
    private final String jwtSecret;
    private final String encryptionKey;

    public StartupSecretsCheck(Environment env,
                               @Value("${equigcc.security.jwt-secret}") String jwtSecret,
                               @Value("${equigcc.security.encryption-key}") String encryptionKey) {
        this.env = env;
        this.jwtSecret = jwtSecret;
        this.encryptionKey = encryptionKey;
    }

    @EventListener(ApplicationReadyEvent.class)
    void verify() {
        if (env.acceptsProfiles(Profiles.of("prod"))
                && (DEV_JWT_SECRET.equals(jwtSecret) || DEV_ENCRYPTION_KEY.equals(encryptionKey))) {
            throw new IllegalStateException("Refusing to run in prod with the development JWT secret or encryption key");
        }
        if (env.acceptsProfiles(Profiles.of("prod")) && !org.springframework.util.StringUtils.hasText(env.getProperty("spring.mail.host"))) {
            throw new IllegalStateException("Refusing to run in prod without spring.mail.host: password reset emails could not be delivered");
        }
    }
}
