package com.glaciernotes.cloud.configuration;

import com.glaciernotes.cloud.application.setup.BootstrapService;
import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.persistence.repository.BootstrapTransaction;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BootstrapStartupValidator {
    private static final Logger LOG = Logger.getLogger(BootstrapStartupValidator.class);

    private final BootstrapTransaction transaction;
    private final BootstrapService bootstrapService;

    public BootstrapStartupValidator(
        BootstrapTransaction transaction,
        BootstrapService bootstrapService
    ) {
        this.transaction = transaction;
        this.bootstrapService = bootstrapService;
    }

    void validate(@Observes StartupEvent ignored) {
        var state = transaction.state();
        if (state.inconsistent()) {
            throw new IllegalStateException("Instance initialization state is inconsistent");
        }
        if (!state.setupRequired()) {
            return;
        }
        try {
            bootstrapService.validateProductionSecrets();
        } catch (SetupFailure exception) {
            if (LaunchMode.current() == LaunchMode.NORMAL) {
                throw new IllegalStateException(
                    "An uninitialized production instance requires valid bootstrap and session secrets"
                );
            }
            LOG.warn("Instance setup is required, but bootstrap or session secrets are unavailable");
        }
    }
}
