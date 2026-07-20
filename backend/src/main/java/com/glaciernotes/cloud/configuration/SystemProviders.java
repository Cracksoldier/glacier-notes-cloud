package com.glaciernotes.cloud.configuration;

import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SystemProviders {
    @Produces
    @ApplicationScoped
    TimeProvider timeProvider() {
        return Instant::now;
    }

    @Produces
    @ApplicationScoped
    IdGenerator idGenerator() {
        return UUID::randomUUID;
    }
}

