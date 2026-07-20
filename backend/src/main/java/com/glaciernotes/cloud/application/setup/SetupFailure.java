package com.glaciernotes.cloud.application.setup;

import java.util.List;

public class SetupFailure extends RuntimeException {
    private final Reason reason;
    private final List<FieldViolation> violations;
    private final long retryAfterSeconds;

    private SetupFailure(
        Reason reason,
        String message,
        List<FieldViolation> violations,
        long retryAfterSeconds
    ) {
        super(message);
        this.reason = reason;
        this.violations = List.copyOf(violations);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static SetupFailure alreadyInitialized() {
        return new SetupFailure(Reason.ALREADY_INITIALIZED, "Setup is not available", List.of(), 0);
    }

    public static SetupFailure denied() {
        return new SetupFailure(Reason.DENIED, "Setup could not be completed", List.of(), 0);
    }

    public static SetupFailure unavailable() {
        return new SetupFailure(Reason.UNAVAILABLE, "Setup is temporarily unavailable", List.of(), 0);
    }

    public static SetupFailure invalid(List<FieldViolation> violations) {
        return new SetupFailure(Reason.INVALID_INPUT, "The setup details are invalid", violations, 0);
    }

    public static SetupFailure rateLimited(long retryAfterSeconds) {
        return new SetupFailure(
            Reason.RATE_LIMITED,
            "Too many setup attempts",
            List.of(),
            Math.max(1, retryAfterSeconds)
        );
    }

    public static SetupFailure conflict() {
        return new SetupFailure(Reason.CONFLICT, "Setup could not be completed", List.of(), 0);
    }

    public Reason reason() {
        return reason;
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Reason {
        ALREADY_INITIALIZED,
        DENIED,
        UNAVAILABLE,
        INVALID_INPUT,
        RATE_LIMITED,
        CONFLICT
    }

    public record FieldViolation(String field, String message) {
    }
}
