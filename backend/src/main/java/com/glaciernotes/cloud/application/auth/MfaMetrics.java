package com.glaciernotes.cloud.application.auth;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Counters carry outcome and factor labels only. A per-account label would turn the metrics endpoint
 * into a directory of who has a second factor.
 */
@ApplicationScoped
public class MfaMetrics {
    private final MeterRegistry registry;

    public MfaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void challengeIssued() {
        registry.counter("glacier_mfa_challenges", "outcome", "issued").increment();
    }

    public void challengeConsumed() {
        registry.counter("glacier_mfa_challenges", "outcome", "consumed").increment();
    }

    public void challengeExhausted() {
        registry.counter("glacier_mfa_challenges", "outcome", "exhausted").increment();
    }

    public void verificationSucceeded(String factor) {
        registry.counter("glacier_mfa_verifications", "outcome", "success", "factor", factor)
            .increment();
    }

    public void verificationFailed() {
        registry.counter("glacier_mfa_verifications", "outcome", "failure", "factor", "unknown")
            .increment();
    }

    public void enrollmentActivated() {
        registry.counter("glacier_mfa_enrollments", "outcome", "activated").increment();
    }

    public void enrollmentDisabled() {
        registry.counter("glacier_mfa_enrollments", "outcome", "disabled").increment();
    }

    public void recoveryCodesIssued() {
        registry.counter("glacier_mfa_recovery_codes", "outcome", "issued").increment();
    }

    public void recoveryCodesConsumed() {
        registry.counter("glacier_mfa_recovery_codes", "outcome", "consumed").increment();
    }

    public void challengesExpired(long count) {
        registry.counter("glacier_mfa_challenges", "outcome", "expired").increment(count);
    }

    public void operatorReset() {
        registry.counter("glacier_mfa_operator_resets").increment();
    }

    public void administrativeClear() {
        registry.counter("glacier_mfa_administrative_clears").increment();
    }
}
