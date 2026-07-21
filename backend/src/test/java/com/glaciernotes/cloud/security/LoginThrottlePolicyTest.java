package com.glaciernotes.cloud.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginThrottlePolicyTest {
    private final LoginThrottlePolicy policy = new LoginThrottlePolicy();

    @Test
    void appliesDeterministicProgressiveDelaysAndLockout() {
        var now = Instant.parse("2026-07-21T12:00:00Z");

        assertEquals(Duration.ZERO, policy.delay(4, 5, 10, 15));
        assertEquals(Duration.ofSeconds(1), policy.delay(5, 5, 10, 15));
        assertEquals(Duration.ofSeconds(2), policy.delay(6, 5, 10, 15));
        assertEquals(Duration.ofSeconds(4), policy.delay(7, 5, 10, 15));
        assertEquals(Duration.ofSeconds(8), policy.delay(8, 5, 10, 15));
        assertEquals(Duration.ofSeconds(16), policy.delay(9, 5, 10, 15));
        assertEquals(Duration.ofMinutes(15), policy.delay(10, 5, 10, 15));
        assertEquals(
            Instant.parse("2026-07-21T12:15:00Z"),
            policy.blockedUntil(now, 10, 5, 10, 15)
        );
    }
}
