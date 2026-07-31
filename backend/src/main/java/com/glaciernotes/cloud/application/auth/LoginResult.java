package com.glaciernotes.cloud.application.auth;

import java.time.Instant;

/**
 * Sealed so that a caller cannot treat a password step that only produced a challenge as if it had
 * produced a session.
 */
public sealed interface LoginResult {
    record SessionIssued(String token, SessionView session, long cookieMaxAgeSeconds)
        implements LoginResult {
    }

    record SecondFactorRequired(String challengeToken, Instant expiresAt, int attemptsRemaining)
        implements LoginResult {
    }
}
