package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.MailMessages;

import java.time.Instant;
import java.util.UUID;

/**
 * Fired inside the security operation's transaction and observed after it commits, so that a mail
 * server that is slow, broken, or absent can never fail or roll back the operation itself.
 */
public record SecondFactorEvent(
    UUID userId,
    String recipient,
    MailMessages message,
    Instant occurredAt,
    String clientDescription
) {
}
