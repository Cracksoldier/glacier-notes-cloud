package com.glaciernotes.cloud.configuration;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaStartupValidatorTest {
    private static final String VALID_SECRET = "a-perfectly-adequate-enrollment-encryption-secret";

    @Test
    void disabledSecondFactorSupportNeedsNoSecret() {
        assertDoesNotThrow(() -> MfaStartupValidator.validate(false, Optional.empty()));
    }

    @Test
    void enabledSecondFactorSupportRequiresASecretSatisfyingThePolicy() {
        assertThrows(
            IllegalStateException.class,
            () -> MfaStartupValidator.validate(true, Optional.empty())
        );
        assertThrows(
            IllegalStateException.class,
            () -> MfaStartupValidator.validate(true, Optional.of("too-short"))
        );
        assertThrows(
            IllegalStateException.class,
            () -> MfaStartupValidator.validate(true, Optional.of("secret with whitespace padded out to length"))
        );
        assertDoesNotThrow(() -> MfaStartupValidator.validate(true, Optional.of(VALID_SECRET)));
    }

    @Test
    void theFailureMessageNamesThePropertyWithoutEchoingTheSecret() {
        var rejected = "this rejected secret must never reach a log line";
        var failure = assertThrows(
            IllegalStateException.class,
            () -> MfaStartupValidator.validate(true, Optional.of(rejected))
        );

        assertTrue(failure.getMessage().contains("GLACIER_MFA_ENCRYPTION_SECRET"));
        assertFalse(failure.getMessage().contains(rejected));
    }
}
