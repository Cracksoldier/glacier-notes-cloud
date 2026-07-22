package com.glaciernotes.cloud.application.setup;

import com.glaciernotes.cloud.configuration.SecretProvider;
import com.glaciernotes.cloud.persistence.repository.BootstrapRateLimiter;
import com.glaciernotes.cloud.persistence.repository.BootstrapTransaction;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;

@ApplicationScoped
public class BootstrapService {
    private final BootstrapTransaction transaction;
    private final BootstrapRateLimiter rateLimiter;
    private final SecretProvider secretProvider;
    private final ClientKeyHasher clientKeyHasher;
    private final IdentityNormalizer identityNormalizer;
    private final PasswordPolicy passwordPolicy;

    public BootstrapService(
        BootstrapTransaction transaction,
        BootstrapRateLimiter rateLimiter,
        SecretProvider secretProvider,
        ClientKeyHasher clientKeyHasher,
        IdentityNormalizer identityNormalizer,
        PasswordPolicy passwordPolicy
    ) {
        this.transaction = transaction;
        this.rateLimiter = rateLimiter;
        this.secretProvider = secretProvider;
        this.clientKeyHasher = clientKeyHasher;
        this.identityNormalizer = identityNormalizer;
        this.passwordPolicy = passwordPolicy;
    }

    public boolean setupRequired() {
        var state = transaction.state();
        if (state.inconsistent()) {
            throw SetupFailure.unavailable();
        }
        return state.setupRequired();
    }

    public Instant initialize(
        String suppliedToken,
        String username,
        String email,
        String displayName,
        String language,
        String passwordValue,
        String clientAddress,
        String correlationId
    ) {
        if (!setupRequired()) {
            throw SetupFailure.alreadyInitialized();
        }
        var configuredToken = validSecret(secretProvider.bootstrapToken().orElse(null));
        validSecret(secretProvider.sessionSecret().orElse(null));
        var clientKey = clientKeyHasher.hash(clientAddress);
        rateLimiter.checkAllowed(clientKey);
        if (!matches(configuredToken, suppliedToken)) {
            rateLimiter.recordFailure(clientKey);
            throw SetupFailure.denied();
        }

        var identity = identityNormalizer.normalize(username, email, displayName);
        var password = passwordValue == null ? new char[0] : passwordValue.toCharArray();
        try {
            passwordPolicy.validate(password);
            var initializedAt = transaction.initialize(new SetupCommand(
                identity.username(),
                identity.usernameNormalized(),
                identity.email(),
                identity.emailNormalized(),
                identity.displayName(),
                "de".equals(language) ? "de" : "en",
                password
            ), correlationId);
            rateLimiter.clear(clientKey);
            return initializedAt;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void validateProductionSecrets() {
        validSecret(secretProvider.bootstrapToken().orElse(null));
        validSecret(secretProvider.sessionSecret().orElse(null));
    }

    private String validSecret(String secret) {
        if (secret == null || secret.length() < 32 || secret.length() > 512
            || secret.codePoints().anyMatch(Character::isWhitespace)) {
            throw SetupFailure.unavailable();
        }
        return secret;
    }

    private boolean matches(String expected, String supplied) {
        var expectedDigest = digest(expected);
        var suppliedDigest = digest(supplied == null ? "" : supplied);
        return MessageDigest.isEqual(expectedDigest, suppliedDigest);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
