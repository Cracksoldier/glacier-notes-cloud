package com.glaciernotes.cloud.application.auth;

/**
 * No factory accepts a submitted code, an enrollment secret, or a challenge token: these messages
 * reach problem details, and second-factor material must never appear there.
 */
public class MfaFailure extends RuntimeException {
    private final Reason reason;
    private final long retryAfterSeconds;

    private MfaFailure(Reason reason, String message, long retryAfterSeconds) {
        super(message);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Covers a wrong authenticator code, an unknown recovery code, and an already-used recovery code
     * alike. Distinguishing them would disclose which was attempted.
     */
    public static MfaFailure invalidCode() {
        return new MfaFailure(Reason.INVALID_CODE, "The verification code is incorrect.", 0);
    }

    /**
     * Also the response when the account was deactivated, locked, deleted, or had its password
     * changed between the two login steps.
     */
    public static MfaFailure challengeInvalid() {
        return new MfaFailure(
            Reason.CHALLENGE_INVALID,
            "The verification request is unknown, already used, or expired.",
            0
        );
    }

    public static MfaFailure attemptsExhausted(long retryAfterSeconds) {
        return new MfaFailure(
            Reason.ATTEMPTS_EXHAUSTED,
            "Too many verification attempts. Sign in again to start over.",
            Math.max(1, retryAfterSeconds)
        );
    }

    public static MfaFailure alreadyEnrolled() {
        return new MfaFailure(
            Reason.ALREADY_ENROLLED,
            "A second factor is already active for this account.",
            0
        );
    }

    public static MfaFailure notEnrolled() {
        return new MfaFailure(
            Reason.NOT_ENROLLED,
            "No second factor is active for this account.",
            0
        );
    }

    /**
     * The actor holds an active second factor and the operation is outside the session's step-up
     * grace window. A client cannot decide this locally, so it submits the password alone and is
     * told here that a code is also needed.
     */
    public static MfaFailure stepUpRequired() {
        return new MfaFailure(
            Reason.STEP_UP_REQUIRED,
            "This operation requires a current verification code.",
            0
        );
    }

    /** Raised by operations whose request body carried no password to re-authenticate with. */
    public static MfaFailure stepUpPasswordRequired() {
        return new MfaFailure(
            Reason.STEP_UP_PASSWORD_REQUIRED,
            "This operation requires your current password.",
            0
        );
    }

    /** The instance was started without {@code GLACIER_MFA_ENABLED}, so no enrollment can be made. */
    public static MfaFailure unavailable() {
        return new MfaFailure(
            Reason.UNAVAILABLE,
            "Second-factor authentication is not enabled on this instance.",
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
        INVALID_CODE,
        CHALLENGE_INVALID,
        ATTEMPTS_EXHAUSTED,
        ALREADY_ENROLLED,
        NOT_ENROLLED,
        STEP_UP_REQUIRED,
        STEP_UP_PASSWORD_REQUIRED,
        UNAVAILABLE
    }
}
