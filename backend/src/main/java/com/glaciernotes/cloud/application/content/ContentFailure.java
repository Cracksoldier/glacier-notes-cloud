package com.glaciernotes.cloud.application.content;

import com.glaciernotes.cloud.application.setup.SetupFailure;

import java.util.List;

public final class ContentFailure extends RuntimeException {
    private final Reason reason;
    private final Long currentVersion;
    private final List<SetupFailure.FieldViolation> violations;

    private ContentFailure(Reason reason, String message, Long currentVersion,
                           List<SetupFailure.FieldViolation> violations) {
        super(message);
        this.reason = reason;
        this.currentVersion = currentVersion;
        this.violations = List.copyOf(violations);
    }

    public static ContentFailure notFound() {
        return new ContentFailure(Reason.NOT_FOUND, "The requested content was not found.", null, List.of());
    }

    public static ContentFailure conflict(String message) {
        return new ContentFailure(Reason.CONFLICT, message, null, List.of());
    }

    public static ContentFailure invalidState(String message) {
        return new ContentFailure(Reason.INVALID_STATE, message, null, List.of());
    }

    public static ContentFailure version(long currentVersion) {
        return new ContentFailure(Reason.VERSION_CONFLICT,
            "The content changed since it was read.", currentVersion, List.of());
    }

    public static ContentFailure invalid(String field, String message) {
        return new ContentFailure(Reason.INVALID, "The request is invalid.", null,
            List.of(new SetupFailure.FieldViolation(field, message)));
    }

    public Reason reason() { return reason; }
    public Long currentVersion() { return currentVersion; }
    public List<SetupFailure.FieldViolation> violations() { return violations; }

    public enum Reason { NOT_FOUND, CONFLICT, INVALID_STATE, VERSION_CONFLICT, INVALID }
}
