package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.api.SystemApi;
import com.glaciernotes.cloud.generated.model.PingResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.ZoneOffset;

@ApplicationScoped
public class PingResource implements SystemApi {
    private final TimeProvider timeProvider;

    @Inject
    public PingResource(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public PingResponse ping() {
        return new PingResponse()
            .service(PingResponse.ServiceEnum.GLACIER_NOTES_CLOUD)
            .status(PingResponse.StatusEnum.OK)
            .apiVersion(PingResponse.ApiVersionEnum.V1)
            .serverTime(timeProvider.now().atOffset(ZoneOffset.UTC));
    }
}
