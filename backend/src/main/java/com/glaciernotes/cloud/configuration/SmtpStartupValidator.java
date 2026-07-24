package com.glaciernotes.cloud.configuration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SmtpStartupValidator {
    private final GlacierConfiguration configuration;

    public SmtpStartupValidator(GlacierConfiguration configuration) {
        this.configuration = configuration;
    }

    void validate(@Observes StartupEvent ignored) {
        validate(configuration.smtp());
    }

    static void validate(GlacierConfiguration.Smtp smtp) {
        if (!smtp.enabled()) {
            return;
        }
        boolean hasUsername = smtp.username().filter(value -> !value.isBlank()).isPresent();
        boolean hasPassword = smtp.password().filter(value -> !value.isEmpty()).isPresent();
        if (hasUsername != hasPassword) {
            throw new IllegalStateException(
                "Authenticated SMTP requires both GLACIER_SMTP_USERNAME and GLACIER_SMTP_PASSWORD_FILE"
            );
        }
    }
}
