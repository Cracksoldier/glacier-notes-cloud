package com.glaciernotes.cloud.configuration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.Optional;

@ApplicationScoped
public class MfaStartupValidator {
    private final GlacierConfiguration configuration;
    private final SecretProvider secretProvider;

    public MfaStartupValidator(GlacierConfiguration configuration, SecretProvider secretProvider) {
        this.configuration = configuration;
        this.secretProvider = secretProvider;
    }

    void validate(@Observes StartupEvent ignored) {
        validate(configuration.mfa().enabled(), secretProvider.enrollmentSecret());
    }

    static void validate(boolean enabled, Optional<String> enrollmentSecret) {
        if (!enabled) {
            return;
        }
        if (enrollmentSecret.filter(SecretPolicy::valid).isEmpty()) {
            throw new IllegalStateException(
                "Second-factor support requires GLACIER_MFA_ENCRYPTION_SECRET_FILE or "
                    + "GLACIER_MFA_ENCRYPTION_SECRET to hold at least "
                    + SecretPolicy.MINIMUM_LENGTH
                    + " non-whitespace characters"
            );
        }
    }
}
