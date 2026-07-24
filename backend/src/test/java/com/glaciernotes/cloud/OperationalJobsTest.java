package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.application.operations.CleanupService;
import com.glaciernotes.cloud.application.operations.JobLeaseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OperationalJobsTest {
    @Inject
    JobLeaseRepository leases;

    @Inject
    EntityManager entityManager;

    @Inject
    AuditService audit;

    @Inject
    CleanupService cleanup;

    @BeforeEach
    @Transactional
    void reset() {
        entityManager.createNativeQuery("delete from scheduled_job_runs").executeUpdate();
        entityManager.createNativeQuery("delete from job_locks").executeUpdate();
    }

    @Test
    void exclusiveLeasePreventsDuplicateExecutionAndCanBeRetriedAfterCompletion() {
        var firstRun = leases.acquire("exclusive-test-job", Duration.ofMinutes(5));
        assertNotNull(firstRun);
        assertNull(leases.acquire("exclusive-test-job", Duration.ofMinutes(5)));

        leases.finish("exclusive-test-job", firstRun, null);
        var retry = leases.acquire("exclusive-test-job", Duration.ofMinutes(5));
        assertNotNull(retry);
        leases.finish("exclusive-test-job", retry, null);

        assertTrue(leases.healthy());
    }

    @Test
    void staleRunCannotReleaseANewerLeaseFromTheSameInstance() {
        var firstRun = leases.acquire("fenced-test-job", Duration.ofMinutes(5));
        assertNotNull(firstRun);
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
            update job_locks set locked_until=:expired where job_name='fenced-test-job'
            """).setParameter("expired", Instant.EPOCH).executeUpdate());

        var secondRun = leases.acquire("fenced-test-job", Duration.ofMinutes(5));
        assertNotNull(secondRun);
        leases.finish("fenced-test-job", firstRun, null);

        assertNull(leases.acquire("fenced-test-job", Duration.ofMinutes(5)));
        leases.finish("fenced-test-job", secondRun, null);
    }

    @Test
    void matchingRunCanRenewItsLease() {
        var run = leases.acquire("renewed-test-job", Duration.ofMinutes(5));
        assertNotNull(run);

        assertTrue(leases.renew("renewed-test-job", run, Duration.ofMinutes(5)));
        assertFalse(leases.renew("renewed-test-job", UUID.randomUUID(), Duration.ofMinutes(5)));
        leases.finish("renewed-test-job", run, null);
    }

    @Test
    void historicalFailureDoesNotMakeTheJobSubsystemUnavailable() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
            insert into scheduled_job_runs(
              job_name,run_id,state,started_at,completed_at,error_category,updated_at
            ) values ('failed-test-job',:run,'FAILED',:now,:now,'ExpectedFailure',:now)
            """).setParameter("run", UUID.randomUUID()).setParameter("now", Instant.now())
            .executeUpdate());

        assertTrue(leases.healthy());
        assertEquals(1, leases.recentFailureCount());
    }

    @Test
    void backgroundAuditEventsExcludeSensitiveMetadata() {
        audit.recordBackground("BACKGROUND_TEST", null, "job", UUID.randomUUID(), "SUCCESS",
            "background-test", Map.of("password", "do-not-store", "detail", "completed"));

        var events = audit.list("BACKGROUND_TEST", null, null, null, null, 10);

        assertEquals(1, events.getItems().size());
        assertEquals("completed", events.getItems().getFirst().getMetadata().get("detail"));
        assertFalse(events.getItems().getFirst().getMetadata().containsKey("password"));
    }

    @Test
    void auditCleanupUsesTheConfiguredRetentionCutoff() {
        audit.recordBackground("EXPIRED_BACKGROUND_TEST", null, "job", UUID.randomUUID(),
            "SUCCESS", "expired-background-test", Map.of());
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
            update audit_events set occurred_at = :occurredAt
            where event_type = 'EXPIRED_BACKGROUND_TEST'
            """).setParameter("occurredAt", Instant.EPOCH).executeUpdate());

        cleanup.removeAuditEvents();

        assertTrue(audit.list("EXPIRED_BACKGROUND_TEST", null, null, null, null, 10)
            .getItems().isEmpty());
    }

    @Test
    void csvAuditExportNeutralizesSpreadsheetFormulas() throws Exception {
        audit.recordBackground("=2+2", null, "job", UUID.randomUUID(), "SUCCESS",
            "formula-export-test", Map.of());

        var export = audit.export("csv", "=2+2", null, null, null);

        assertTrue(Files.readString(export.toPath()).contains("\"'=2+2\""));
    }
}
