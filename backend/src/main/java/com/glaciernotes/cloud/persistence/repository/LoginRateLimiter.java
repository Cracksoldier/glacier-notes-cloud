package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.security.LoginThrottlePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class LoginRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final EntityManager entityManager;
    private final LoginThrottlePolicy policy;

    public LoginRateLimiter(EntityManager entityManager, LoginThrottlePolicy policy) {
        this.entityManager = entityManager;
        this.policy = policy;
    }

    public void assertAllowed(String identifierKey, String ipKey, Instant now) {
        var blockedValue = entityManager.createNativeQuery("""
                select max(blocked_until)
                  from login_rate_limits
                 where ((scope = 'IDENTIFIER' and key_hash = :identifierKey)
                    or (scope = 'IP' and key_hash = :ipKey))
                   and blocked_until > :now
                """)
            .setParameter("identifierKey", identifierKey)
            .setParameter("ipKey", ipKey)
            .setParameter("now", now)
            .getSingleResult();
        var blockedUntil = asInstant(blockedValue);
        if (blockedUntil != null) {
            throw AuthenticationFailure.rateLimited(secondsUntil(blockedUntil, now));
        }
    }

    public long recordFailures(
        String identifierKey,
        String ipKey,
        Instant now,
        InstanceSettingsEntity settings
    ) {
        var identifierDelay = record("IDENTIFIER", identifierKey, now, settings);
        var ipDelay = record("IP", ipKey, now, settings);
        return Math.max(identifierDelay, ipDelay);
    }

    public void clearIdentifier(String identifierKey) {
        entityManager.createNativeQuery(
                "delete from login_rate_limits where scope = 'IDENTIFIER' and key_hash = :key"
            )
            .setParameter("key", identifierKey)
            .executeUpdate();
    }

    private long record(
        String scope,
        String key,
        Instant now,
        InstanceSettingsEntity settings
    ) {
        var count = ((Number) entityManager.createNativeQuery("""
                insert into login_rate_limits(
                    scope, key_hash, window_started_at, failure_count, blocked_until, updated_at
                ) values (:scope, :key, :now, 1, null, :now)
                on conflict (scope, key_hash) do update set
                    window_started_at = case
                        when login_rate_limits.window_started_at <= :cutoff then :now
                        else login_rate_limits.window_started_at
                    end,
                    failure_count = case
                        when login_rate_limits.window_started_at <= :cutoff then 1
                        else login_rate_limits.failure_count + 1
                    end,
                    blocked_until = null,
                    updated_at = :now
                returning failure_count
                """)
            .setParameter("scope", scope)
            .setParameter("key", key)
            .setParameter("now", now)
            .setParameter("cutoff", now.minus(WINDOW))
            .getSingleResult()).intValue();

        if (count < settings.loginDelayThreshold()) {
            return 0;
        }
        var delay = policy.delay(
            count,
            settings.loginDelayThreshold(),
            settings.loginLockThreshold(),
            settings.loginLockMinutes()
        );
        var blockedUntil = now.plus(delay);
        entityManager.createNativeQuery("""
                update login_rate_limits
                   set blocked_until = :blockedUntil, updated_at = :now
                 where scope = :scope and key_hash = :key
                """)
            .setParameter("blockedUntil", blockedUntil)
            .setParameter("now", now)
            .setParameter("scope", scope)
            .setParameter("key", key)
            .executeUpdate();
        return delay.toSeconds();
    }

    private long secondsUntil(Instant blockedUntil, Instant now) {
        return Math.max(1, Duration.between(now, blockedUntil).toSeconds());
    }

    private Instant asInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp value: " + value.getClass().getName());
    }
}
