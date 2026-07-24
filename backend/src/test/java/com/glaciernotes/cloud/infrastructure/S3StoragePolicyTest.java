package com.glaciernotes.cloud.infrastructure;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3StoragePolicyTest {
    @Test
    void createsBoundedClientConfigurationAndEncryption() {
        var policy = S3StoragePolicy.from(configuration(30, 10, "AES256"));
        var override = policy.clientOverrideConfiguration();

        assertEquals(Duration.ofSeconds(30), override.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofSeconds(10), override.apiCallAttemptTimeout().orElseThrow());
        assertEquals("AES256", policy.serverSideEncryption().orElseThrow());
        var request = PutObjectRequest.builder().bucket("bucket").key("key");
        policy.applyEncryption(request);
        assertEquals("AES256", request.build().serverSideEncryptionAsString());
    }

    @Test
    void rejectsInvalidTimeoutsAndEncryption() {
        assertThrows(
            IllegalStateException.class,
            () -> S3StoragePolicy.from(configuration(10, 11, "AES256"))
        );
        assertThrows(
            IllegalStateException.class,
            () -> S3StoragePolicy.from(configuration(30, 10, "invalid"))
        );
    }

    private GlacierConfiguration.Images.S3 configuration(int call, int attempt, String encryption) {
        return new GlacierConfiguration.Images.S3() {
            @Override public Optional<String> endpoint() { return Optional.empty(); }
            @Override public String region() { return "us-east-1"; }
            @Override public String bucket() { return "bucket"; }
            @Override public boolean pathStyle() { return true; }
            @Override public Optional<Path> accessKeyFile() { return Optional.empty(); }
            @Override public Optional<Path> secretKeyFile() { return Optional.empty(); }
            @Override public Optional<String> serverSideEncryption() {
                return Optional.ofNullable(encryption);
            }
            @Override public int apiCallTimeoutSeconds() { return call; }
            @Override public int apiCallAttemptTimeoutSeconds() { return attempt; }
        };
    }
}
