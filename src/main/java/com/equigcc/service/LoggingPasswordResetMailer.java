package com.equigcc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development fallback when no SMTP host is configured: writes the link to the application log. Never use in prod. */
@Component
@ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = true, havingValue = "__unset__")
class LoggingPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

    @Override
    public void send(String toEmail, String displayName, String resetLink, int validMinutes) {
        log.warn("MAIL NOT CONFIGURED (dev only). Password reset link for {} (valid {} min): {}", toEmail, validMinutes, resetLink);
    }
}
