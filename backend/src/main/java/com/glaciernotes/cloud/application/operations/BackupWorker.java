package com.glaciernotes.cloud.application.operations;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.MDC;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class BackupWorker {
    private final BackupService backups;
    private final AuditService audit;

    public BackupWorker(BackupService backups, AuditService audit) {
        this.backups = backups;
        this.audit = audit;
    }

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void process() {
        UUID id = backups.claim();
        if (id == null) return;
        String correlationId = "backup-" + id;
        MDC.put("correlationId", correlationId);
        MDC.put("jobId", id.toString());
        try {
            backups.execute(id);
            var result = backups.get(id);
            audit.recordBackground(result.getState() == com.glaciernotes.cloud.generated.model.BackupJob.StateEnum.SUCCEEDED
                    ? "BACKUP_COMPLETED" : "BACKUP_FAILED",
                result.getCreatedByUserId(), "BACKUP", id,
                result.getState() == com.glaciernotes.cloud.generated.model.BackupJob.StateEnum.SUCCEEDED
                    ? "SUCCESS" : "FAILURE",
                correlationId, Map.of());
        } finally {
            MDC.remove("jobId");
            MDC.remove("correlationId");
        }
    }
}
