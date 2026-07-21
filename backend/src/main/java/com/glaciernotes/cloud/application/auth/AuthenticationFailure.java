package com.glaciernotes.cloud.application.auth;

public class AuthenticationFailure extends RuntimeException {
    private final Reason reason;
    private final long retryAfterSeconds;

    private AuthenticationFailure(Reason reason, String message, long retryAfterSeconds) {
        super(message);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static AuthenticationFailure invalidCredentials() {
        return new AuthenticationFailure(
            Reason.INVALID_CREDENTIALS,
            "The username, email, or password is incorrect.",
            0
        );
    }

    public static AuthenticationFailure rateLimited(long retryAfterSeconds) {
        return new AuthenticationFailure(
            Reason.RATE_LIMITED,
            "Too many login attempts. Try again later.",
            Math.max(1, retryAfterSeconds)
        );
    }

    public static AuthenticationFailure sessionNotFound() {
        return new AuthenticationFailure(
            Reason.SESSION_NOT_FOUND,
            "The session is missing, expired, or revoked.",
            0
        );
    }

    public static AuthenticationFailure csrfInvalid() {
        return new AuthenticationFailure(
            Reason.CSRF_INVALID,
            "The request could not be verified.",
            0
        );
    }

    public Reason reason() {
        return reason;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Reason {
        INVALID_CREDENTIALS,
        RATE_LIMITED,
        SESSION_NOT_FOUND,
        CSRF_INVALID
    }
}
