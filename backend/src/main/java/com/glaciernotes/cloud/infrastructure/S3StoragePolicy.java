package com.glaciernotes.cloud.infrastructure;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public record S3StoragePolicy(
    Duration apiCallTimeout,
    Duration apiCallAttemptTimeout,
    Optional<String> serverSideEncryption
) {
    private static final Set<String> SUPPORTED_ENCRYPTION = Set.of("AES256", "aws:kms");

    public static S3StoragePolicy from(GlacierConfiguration.Images.S3 configuration) {
        int callSeconds = configuration.apiCallTimeoutSeconds();
        int attemptSeconds = configuration.apiCallAttemptTimeoutSeconds();
        if (callSeconds <= 0 || attemptSeconds <= 0 || attemptSeconds > callSeconds) {
            throw new IllegalStateException(
                "S3 timeouts must be positive and the attempt timeout must not exceed the call timeout"
            );
        }
        var encryption = configuration.serverSideEncryption()
            .map(String::trim)
            .filter(value -> !value.isEmpty());
        if (encryption.isPresent() && !SUPPORTED_ENCRYPTION.contains(encryption.orElseThrow())) {
            throw new IllegalStateException(
                "GLACIER_S3_SERVER_SIDE_ENCRYPTION must be AES256 or aws:kms"
            );
        }
        return new S3StoragePolicy(
            Duration.ofSeconds(callSeconds),
            Duration.ofSeconds(attemptSeconds),
            encryption
        );
    }

    public ClientOverrideConfiguration clientOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
            .apiCallTimeout(apiCallTimeout)
            .apiCallAttemptTimeout(apiCallAttemptTimeout)
            .build();
    }

    public void applyEncryption(PutObjectRequest.Builder request) {
        serverSideEncryption.ifPresent(request::serverSideEncryption);
    }
}
