package com.glaciernotes.cloud.application.auth;

public final class MfaAuditEvents {
    public static final String ENROLLMENT_STARTED = "MFA_ENROLLMENT_STARTED";
    public static final String ENROLLMENT_CONFIRMED = "MFA_ENROLLMENT_CONFIRMED";
    public static final String ENROLLMENT_ABANDONED = "MFA_ENROLLMENT_ABANDONED";
    public static final String DISABLED = "MFA_DISABLED";
    public static final String CHALLENGE_FAILED = "MFA_CHALLENGE_FAILED";
    public static final String REAUTHENTICATION_FAILED = "MFA_REAUTHENTICATION_FAILED";
    public static final String CHALLENGE_COMPLETED = "MFA_CHALLENGE_COMPLETED";
    public static final String RECOVERY_CODE_USED = "MFA_RECOVERY_CODE_USED";
    public static final String RECOVERY_CODES_REGENERATED = "MFA_RECOVERY_CODES_REGENERATED";
    public static final String OPERATOR_RESET = "MFA_OPERATOR_RESET";
    public static final String STEP_UP_SUCCEEDED = "MFA_STEP_UP_SUCCEEDED";
    public static final String STEP_UP_FAILED = "MFA_STEP_UP_FAILED";

    private MfaAuditEvents() {
    }
}
