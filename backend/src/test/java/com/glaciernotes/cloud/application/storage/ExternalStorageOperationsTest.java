package com.glaciernotes.cloud.application.storage;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.OwnerId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ExternalStorageOperationsTest.Profile.class)
class ExternalStorageOperationsTest {
    @Inject
    ExternalStorageOperations operations;

    @Inject
    ExternalStorageWorker worker;

    @Inject
    GlacierConfiguration configuration;

    @Inject
    DataSource dataSource;

    private Path outside;

    @AfterEach
    void clean() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from external_storage_operations");
        }
        if (outside != null) Files.deleteIfExists(outside);
    }

    @Test
    void activeReservationCountsAgainstQuotaAndIsRecoveredAfterFailure() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        UUID reservation = UUID.randomUUID();
        operations.reserveBinary(owner, reservation, null, "FILESYSTEM",
            "reserved/main", "reserved/thumbnail", 42);

        assertEquals(42, operations.pendingBytes(owner));
        assertTrue(operations.claimNext().isEmpty());

        operations.expedite(reservation);
        worker.execute(operations.claimNext().orElseThrow());

        assertEquals(0, operations.pendingBytes(owner));
        assertEquals(0, operationCount());
    }

    @Test
    void workerDeletesOnlyTransferFilesBelowTheConfiguredRoot() throws Exception {
        Path root = configuration.transfer().temporaryRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path safe = Files.createTempFile(root, "journal-", ".tmp");
        operations.enqueueTransferFileDelete(new OwnerId(UUID.randomUUID()), UUID.randomUUID(),
            safe.toString());

        worker.execute(operations.claimNext().orElseThrow());

        assertFalse(Files.exists(safe));
        assertEquals(0, operationCount());
    }

    @Test
    void unsafeTransferPathBecomesATerminalFailure() throws Exception {
        outside = Files.createTempFile("glacier-outside-transfer-root-", ".tmp");
        operations.enqueueTransferFileDelete(new OwnerId(UUID.randomUUID()), UUID.randomUUID(),
            outside.toString());

        worker.execute(operations.claimNext().orElseThrow());

        assertTrue(Files.exists(outside));
        assertEquals("FAILED", operationState());
    }

    private long operationCount() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select count(*) from external_storage_operations");
             var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String operationState() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select state from external_storage_operations");
             var result = statement.executeQuery()) {
            result.next();
            return result.getString(1);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "false");
        }
    }
}
