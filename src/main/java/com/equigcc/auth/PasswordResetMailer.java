package com.equigcc.auth;

/** Delivers a password reset link to a user. The link carries the only copy of the token. */
public interface PasswordResetMailer {

    void send(String toEmail, String displayName, String resetLink, int validMinutes);
}
