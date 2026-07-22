package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.setup.BootstrapService;
import com.glaciernotes.cloud.generated.api.SetupApi;
import com.glaciernotes.cloud.generated.model.InitialAdministratorRequest;
import com.glaciernotes.cloud.generated.model.SetupCompletion;
import com.glaciernotes.cloud.generated.model.SetupStatus;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import org.jboss.logging.MDC;

import java.time.ZoneOffset;
import java.util.Objects;

@ApplicationScoped
public class SetupResource implements SetupApi {
    private final BootstrapService bootstrapService;

    @Context
    HttpServerRequest httpRequest;

    public SetupResource(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public SetupCompletion createInitialAdministrator(
        String bootstrapToken,
        InitialAdministratorRequest request
    ) {
        var initializedAt = bootstrapService.initialize(
            bootstrapToken,
            request.getUsername(),
            request.getEmail(),
            request.getDisplayName(),
            request.getLanguage() == null ? "en" : request.getLanguage().toString(),
            request.getPassword(),
            clientAddress(),
            Objects.toString(MDC.get("correlationId"), "unavailable")
        );
        return new SetupCompletion()
            .initialized(true)
            .initializedAt(initializedAt.atOffset(ZoneOffset.UTC));
    }

    @Override
    public SetupStatus getSetupStatus() {
        return new SetupStatus().setupRequired(bootstrapService.setupRequired());
    }

    private String clientAddress() {
        var remoteAddress = httpRequest.remoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.hostAddress();
    }
}
