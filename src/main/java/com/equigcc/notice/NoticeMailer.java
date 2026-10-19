package com.equigcc.notice;

/** Delivers a plain-text notice to one recipient. Failures are the caller's to log; they never undo register changes. */
public interface NoticeMailer {

    void send(String toEmail, String subject, String body);
}
