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
}
