package com.glaciernotes.cloud.application.port;

/** Replaceable outbound-email boundary; messages contain no user note content. */
public interface EmailSender {
    void send(EmailMessage message);

    record EmailMessage(String recipient, String subject, String textBody) {
    }
}
