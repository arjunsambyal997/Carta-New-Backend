package com.equigcc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development fallback when no SMTP host is configured: writes the notice to the application log. */
@Component
@ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = true, havingValue = "__unset__")
class LoggingNoticeMailer implements NoticeMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingNoticeMailer.class);

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("NOTICE (mail not configured) to {}: {}", toEmail, subject);
    }
}
