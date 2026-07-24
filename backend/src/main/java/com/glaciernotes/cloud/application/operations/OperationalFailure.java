package com.glaciernotes.cloud.application.operations;

public class OperationalFailure extends RuntimeException {
    public enum Reason { FEATURE_DISABLED, SMTP_NOT_CONFIGURED, NOT_FOUND, INVALID }

    private final Reason reason;

    private OperationalFailure(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static OperationalFailure featureDisabled() {
        return new OperationalFailure(Reason.FEATURE_DISABLED,
            "Server backup operations are not enabled for this instance.");
    }

    public static OperationalFailure smtpNotConfigured() {
        return new OperationalFailure(Reason.SMTP_NOT_CONFIGURED,
            "SMTP is not configured for this instance.");
    }

    public static OperationalFailure notFound() {
        return new OperationalFailure(Reason.NOT_FOUND, "The requested resource is unavailable.");
    }

    public static OperationalFailure invalid(String message) {
        return new OperationalFailure(Reason.INVALID, message);
    }

    public Reason reason() {
        return reason;
    }
}
