package com.glaciernotes.cloud;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@QuarkusTest
@TestProfile(S3ImageResourceTest.Profile.class)
@QuarkusTestResource(value = S3ImageResourceTest.Minio.class, restrictToAnnotatedClass = true)
public class S3ImageResourceTest extends ImageResourceTest {
    public static class Profile implements QuarkusTestProfile {}

    public static class Minio implements QuarkusTestResourceLifecycleManager {
        private static final String ACCESS = "glacier-test-access";
        private static final String SECRET = "glacier-test-secret-2026";
        private GenericContainer<?> container;
        private Path accessFile;
        private Path secretFile;

        @Override
        public Map<String, String> start() {
            container = new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
                .withExposedPorts(9000)
                .withEnv("MINIO_ROOT_USER", ACCESS)
                .withEnv("MINIO_ROOT_PASSWORD", SECRET)
                .withCommand("server", "/data")
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));
            container.start();
            String endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(9000);
            try {
                accessFile = Files.createTempFile("glacier-s3-access-", ".secret");
                secretFile = Files.createTempFile("glacier-s3-secret-", ".secret");
                Files.writeString(accessFile, ACCESS);
                Files.writeString(secretFile, SECRET);
            } catch (java.io.IOException exception) {
                container.stop();
                throw new IllegalStateException("Could not create S3 test credentials", exception);
            }
            try (S3Client client = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS, SECRET))).build()) {
                client.createBucket(value -> value.bucket("glacier-notes-test"));
            }
            return Map.of(
                "glacier.images.backend", "S3",
                "glacier.images.s3.endpoint", endpoint,
                "glacier.images.s3.region", "us-east-1",
                "glacier.images.s3.bucket", "glacier-notes-test",
                "glacier.images.s3.path-style", "true",
                "glacier.images.s3.access-key-file", accessFile.toString(),
                "glacier.images.s3.secret-key-file", secretFile.toString()
            );
        }

        @Override public void stop() {
            if (container != null) container.stop();
            try {
                if (accessFile != null) Files.deleteIfExists(accessFile);
                if (secretFile != null) Files.deleteIfExists(secretFile);
            } catch (java.io.IOException ignored) { /* temporary files are also removed by the host */ }
        }
    }
}
