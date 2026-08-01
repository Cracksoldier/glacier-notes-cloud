package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class EndpointRateLimiter {
    private final EntityManager entityManager;
    private final TimeProvider timeProvider;

    public EndpointRateLimiter(EntityManager entityManager, TimeProvider timeProvider) {
        this.entityManager = entityManager;
        this.timeProvider = timeProvider;
    }

    /**
     * Rejects a request that is still inside a block window, without counting it as an attempt.
     * Callers that only want to count failures pair this with {@link #record} on the failure path.
     */
    public void assertAllowed(Instant now, ScopedKey... keys) {
        for (var key : keys) {
            var seconds = ((Number) entityManager.createNativeQuery("""
                    select coalesce(max(extract(epoch from blocked_until - :now)), 0)
                      from endpoint_rate_limits
                     where scope = :scope and key_hash = :keyHash and blocked_until > :now
                    """)
                .setParameter("scope", key.scope())
                .setParameter("keyHash", key.keyHash())
                .setParameter("now", now)
                .getSingleResult()).longValue();
            if (seconds > 0) {
                throw LifecycleFailure.rateLimited(Math.max(1, seconds));
            }
        }
    }

    /**
     * The count and the block must outlive the rejection: callers that count failures keep their
     * transaction, and rolling this back would reset the attacker's budget on every ceiling hit.
     */
    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = LifecycleFailure.class
    )
    public void record(String scope, String keyHash, int maximum, Duration window) {
        var now = timeProvider.now();
        var cutoff = now.minus(window);
        var count = ((Number) entityManager.createNativeQuery("""
                insert into endpoint_rate_limits(
                    scope, key_hash, window_started_at, attempt_count, blocked_until, updated_at
                ) values (:scope, :keyHash, :now, 1, null, :now)
                on conflict (scope, key_hash) do update set
                    attempt_count = case
                        when endpoint_rate_limits.window_started_at <= :cutoff then 1
                        else endpoint_rate_limits.attempt_count + 1
                    end,
                    window_started_at = case
                        when endpoint_rate_limits.window_started_at <= :cutoff then :now
                        else endpoint_rate_limits.window_started_at
                    end,
                    blocked_until = case
                        when endpoint_rate_limits.window_started_at <= :cutoff then null
                        else endpoint_rate_limits.blocked_until
                    end,
                    updated_at = :now
                returning attempt_count
                """)
            .setParameter("scope", scope)
            .setParameter("keyHash", keyHash)
            .setParameter("now", now)
            .setParameter("cutoff", cutoff)
            .getSingleResult()).intValue();
        if (count > maximum) {
            var blockedUntil = now.plus(window);
            entityManager.createNativeQuery("""
                    update endpoint_rate_limits set blocked_until = :blockedUntil, updated_at = :now
                    where scope = :scope and key_hash = :keyHash
                    """)
                .setParameter("blockedUntil", blockedUntil)
                .setParameter("now", now)
                .setParameter("scope", scope)
                .setParameter("keyHash", keyHash)
                .executeUpdate();
            throw LifecycleFailure.rateLimited(Math.max(1, Duration.between(now, blockedUntil).toSeconds()));
        }
    }

    public record ScopedKey(String scope, String keyHash) {
    }
}
