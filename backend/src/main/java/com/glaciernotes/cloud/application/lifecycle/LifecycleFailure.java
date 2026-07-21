package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.setup.SetupFailure;

import java.util.List;

public class LifecycleFailure extends RuntimeException {
    public enum Reason { NOT_FOUND, INVALID_TOKEN, CONFLICT, INVALID_INPUT, INVALID_STATE, LAST_ADMIN, RATE_LIMITED }

    private final Reason reason;
    private final List<SetupFailure.FieldViolation> violations;
    private final long retryAfterSeconds;

    private LifecycleFailure(Reason reason, String message, List<SetupFailure.FieldViolation> violations,
                             long retryAfterSeconds) {
        super(message);
        this.reason = reason;
        this.violations = violations;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static LifecycleFailure notFound() {
        return new LifecycleFailure(Reason.NOT_FOUND, "The requested resource is unavailable.", List.of(), 0);
    }
    public static LifecycleFailure invalidToken() {
        return new LifecycleFailure(Reason.INVALID_TOKEN, "The token is invalid or has expired.", List.of(), 0);
    }
    public static LifecycleFailure conflict(String message) {
        return new LifecycleFailure(Reason.CONFLICT, message, List.of(), 0);
    }
    public static LifecycleFailure invalidState(String message) {
        return new LifecycleFailure(Reason.INVALID_STATE, message, List.of(), 0);
    }
    public static LifecycleFailure lastAdmin() {
        return new LifecycleFailure(Reason.LAST_ADMIN,
            "The last active administrator must remain active and retain the ADMIN role.", List.of(), 0);
    }
    public static LifecycleFailure invalid(List<SetupFailure.FieldViolation> violations) {
        return new LifecycleFailure(Reason.INVALID_INPUT, "The request contains invalid values.", violations, 0);
    }
    public static LifecycleFailure rateLimited(long seconds) {
        return new LifecycleFailure(Reason.RATE_LIMITED, "Too many requests. Try again later.", List.of(), seconds);
    }

    public Reason reason() { return reason; }
    public List<SetupFailure.FieldViolation> violations() { return violations; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
