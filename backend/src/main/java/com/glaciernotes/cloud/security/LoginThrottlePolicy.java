package com.glaciernotes.cloud.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class LoginThrottlePolicy {
    public Duration delay(
        int failureCount,
        int delayThreshold,
        int lockThreshold,
        int lockMinutes
    ) {
        if (failureCount < delayThreshold) {
            return Duration.ZERO;
        }
        if (failureCount >= lockThreshold) {
            return Duration.ofMinutes(lockMinutes);
        }
        return Duration.ofSeconds(1L << Math.min(20, failureCount - delayThreshold));
    }

    public Instant blockedUntil(
        Instant now,
        int failureCount,
        int delayThreshold,
        int lockThreshold,
        int lockMinutes
    ) {
        return now.plus(delay(failureCount, delayThreshold, lockThreshold, lockMinutes));
    }
}
