package com.equigcc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the reset email through SMTP; active when {@code spring.mail.host} is set. */
@Component
@ConditionalOnProperty("spring.mail.host")
class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender sender;
    private final String from;

    SmtpPasswordResetMailer(JavaMailSender sender, @Value("${equigcc.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String displayName, String resetLink, int validMinutes) {
        var m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(toEmail);
        m.setSubject("Reset your EquiGCC password / إعادة تعيين كلمة المرور");
        m.setText("Hello " + displayName + ",\n\nUse this link to choose a new password. It works once and expires in "
                + validMinutes + " minutes:\n" + resetLink + "\n\nIf you did not ask for this, ignore this email; your password is unchanged.\n\n"
                + "مرحبًا،\nاستخدم الرابط أعلاه لاختيار كلمة مرور جديدة. يعمل مرة واحدة وينتهي خلال " + validMinutes
                + " دقيقة. إذا لم تطلب ذلك فتجاهل هذه الرسالة.\n");
        sender.send(m);
    }
}
