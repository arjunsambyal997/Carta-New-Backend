package com.equigcc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends notices through SMTP; active when {@code spring.mail.host} is set. */
@Component
@ConditionalOnProperty("spring.mail.host")
class SmtpNoticeMailer implements NoticeMailer {

    private final JavaMailSender sender;
    private final String from;

    SmtpNoticeMailer(JavaMailSender sender, @Value("${equigcc.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        var m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(toEmail);
        m.setSubject(subject);
        m.setText(body);
        sender.send(m);
    }
}
