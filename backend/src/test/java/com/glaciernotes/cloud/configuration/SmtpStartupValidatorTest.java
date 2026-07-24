package com.glaciernotes.cloud.configuration;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpStartupValidatorTest {
    @Test
    void allowsDisabledOrUnauthenticatedSmtp() {
        assertDoesNotThrow(() -> SmtpStartupValidator.validate(smtp(false, "user", "")));
        assertDoesNotThrow(() -> SmtpStartupValidator.validate(smtp(true, "", "")));
    }

    @Test
    void requiresUsernameAndPasswordTogether() {
        assertThrows(
            IllegalStateException.class,
            () -> SmtpStartupValidator.validate(smtp(true, "user", ""))
        );
        assertThrows(
            IllegalStateException.class,
            () -> SmtpStartupValidator.validate(smtp(true, "", "password"))
        );
        assertDoesNotThrow(() -> SmtpStartupValidator.validate(smtp(true, "user", " password ")));
    }

    private GlacierConfiguration.Smtp smtp(boolean enabled, String username, String password) {
        return new GlacierConfiguration.Smtp() {
            @Override public boolean enabled() { return enabled; }
            @Override public Optional<String> username() { return Optional.of(username); }
            @Override public Optional<String> password() { return Optional.of(password); }
            @Override public Optional<String> senderName() { return Optional.empty(); }
            @Override public Optional<String> senderAddress() { return Optional.empty(); }
        };
    }
}
