package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.persistence.entity.BackupJobEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class BackupTransactionTest {
    @Inject
    BackupService backups;

    @Inject
    EntityManager entityManager;

    @Test
    void terminalFailureCommitsIndependentlyOfTheCallingTransaction() {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager.persist(
            new BackupJobEntity(id, null, Instant.now())
        ));

        assertThrows(ExpectedRollback.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            backups.fail(id, "ExpectedFailure");
            throw new ExpectedRollback();
        }));

        String state = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                "select b.state from BackupJobEntity b where b.id=:id", String.class)
            .setParameter("id", id).getSingleResult());
        assertEquals("FAILED", state);
    }

    @Test
    void dumpWaitIsBoundedAndTerminatesTheProcess() throws Exception {
        Process process = new ProcessBuilder("sleep", "30").start();

        assertThrows(IOException.class,
            () -> backups.awaitDump(process, Duration.ofMillis(50)));
        assertFalse(process.isAlive());
    }

    private static final class ExpectedRollback extends RuntimeException {}
}
