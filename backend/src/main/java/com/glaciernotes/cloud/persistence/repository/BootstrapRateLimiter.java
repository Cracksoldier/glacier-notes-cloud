package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@ApplicationScoped
public class BootstrapRateLimiter {
    private final EntityManager entityManager;
    private final TimeProvider timeProvider;
    private final GlacierConfiguration.Bootstrap configuration;

    public BootstrapRateLimiter(
        EntityManager entityManager,
        TimeProvider timeProvider,
        GlacierConfiguration configuration
    ) {
        this.entityManager = entityManager;
        this.timeProvider = timeProvider;
        this.configuration = configuration.bootstrap();
    }

    public void checkAllowed(String clientKey) {
        var values = entityManager.createNativeQuery(
                "select blocked_until from bootstrap_rate_limits where client_key = ?1"
            )
            .setParameter(1, clientKey)
            .getResultList();
        if (!values.isEmpty() && values.getFirst() != null) {
            var blockedUntil = instant(values.getFirst());
            if (blockedUntil.isAfter(timeProvider.now())) {
                throw SetupFailure.rateLimited(secondsUntil(blockedUntil));
            }
        }
    }

    @Transactional(value = REQUIRES_NEW, dontRollbackOn = SetupFailure.class)
    public void recordFailure(String clientKey) {
        var now = timeProvider.now();
        var cutoff = now.minusSeconds(configuration.windowSeconds());
        var blockedUntil = now.plusSeconds(configuration.blockSeconds());
        @SuppressWarnings("unchecked")
        var result = (Object[]) entityManager.createNativeQuery("""
                INSERT INTO bootstrap_rate_limits(
                    client_key, window_started_at, failure_count, blocked_until, updated_at
                ) VALUES (?1, ?2, 1, CAST(NULL AS timestamptz), ?2)
                ON CONFLICT (client_key) DO UPDATE SET
                    window_started_at = CASE
                        WHEN bootstrap_rate_limits.window_started_at <= ?3 THEN ?2
                        ELSE bootstrap_rate_limits.window_started_at
                    END,
                    failure_count = CASE
                        WHEN bootstrap_rate_limits.window_started_at <= ?3 THEN 1
                        ELSE bootstrap_rate_limits.failure_count + 1
                    END,
                    blocked_until = CASE
                        WHEN (CASE
                            WHEN bootstrap_rate_limits.window_started_at <= ?3 THEN 1
                            ELSE bootstrap_rate_limits.failure_count + 1
                        END) >= ?4 THEN CAST(?5 AS timestamptz)
                        ELSE CAST(NULL AS timestamptz)
                    END,
                    updated_at = ?2
                RETURNING failure_count, blocked_until
                """)
            .setParameter(1, clientKey)
            .setParameter(2, now)
            .setParameter(3, cutoff)
            .setParameter(4, configuration.failureLimit())
            .setParameter(5, blockedUntil)
            .getSingleResult();
        if (((Number) result[0]).intValue() >= configuration.failureLimit()) {
            throw SetupFailure.rateLimited(secondsUntil(instant(result[1])));
        }
    }

    @Transactional(REQUIRES_NEW)
    public void clear(String clientKey) {
        entityManager.createNativeQuery("delete from bootstrap_rate_limits where client_key = ?1")
            .setParameter(1, clientKey)
            .executeUpdate();
    }

    private long secondsUntil(Instant blockedUntil) {
        var milliseconds = Math.max(1, Duration.between(timeProvider.now(), blockedUntil).toMillis());
        return Math.max(1, (milliseconds + 999) / 1000);
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return OffsetDateTime.parse(value.toString()).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
    }
}
