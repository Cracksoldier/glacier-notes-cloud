package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transfer_jobs")
public class TransferJobEntity {
    @Id private UUID id;
    @Column(name = "job_kind") private String kind;
    private String phase;
    private String state;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "target_user_id") private UUID targetUserId;
    @Column(name = "blind_admin") private boolean blindAdmin;
    @Column(name = "scope_kind") private String scopeKind;
    @Column(name = "scope_entity_id") private UUID scopeEntityId;
    @Column(name = "import_strategy") private String importStrategy;
    @Column(name = "temporary_path") private String temporaryPath;
    @Column(name = "original_file_name") private String originalFileName;
    @Column(name = "byte_size") private Long byteSize;
    @Column(name = "counts_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Long> counts;
    @Column(name = "has_conflicts") private Boolean hasConflicts;
    @Column(name = "quota_impact_bytes") private Long quotaImpactBytes;
    @Column(name = "errors_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON) private List<String> errors;
    @Column(name = "cancel_requested") private boolean cancelRequested;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Version private long version;

    protected TransferJobEntity() {}

    public static TransferJobEntity export(UUID id, UUID userId, String scope, UUID scopeId,
                                           String path, Instant now, Instant expiresAt) {
        if (scope == null
            || !List.of("ALL", "NOTEBOOK", "NOTE").contains(scope)
            || ("ALL".equals(scope) && scopeId != null)
            || (!"ALL".equals(scope) && scopeId == null)) {
            throw new IllegalArgumentException("Export scope and resource id do not match");
        }
        var job = base(id, "EXPORT", "GENERATE", userId, userId, false, path, now, expiresAt);
        job.scopeKind = scope;
        job.scopeEntityId = scopeId;
        return job;
    }

    public static TransferJobEntity imported(UUID id, UUID actor, UUID target, boolean blind,
                                             String path, String fileName, long size,
                                             Instant now, Instant expiresAt) {
        var job = base(id, "IMPORT", "INSPECT", actor, target, blind, path, now, expiresAt);
        job.originalFileName = fileName;
        job.byteSize = size;
        return job;
    }

    private static TransferJobEntity base(UUID id, String kind, String phase, UUID actor, UUID target,
                                          boolean blind, String path, Instant now, Instant expiresAt) {
        var job = new TransferJobEntity();
        job.id = id; job.kind = kind; job.phase = phase; job.state = "QUEUED";
        job.requestedBy = actor; job.targetUserId = target; job.blindAdmin = blind;
        job.temporaryPath = path; job.createdAt = now; job.expiresAt = expiresAt;
        job.errors = List.of();
        return job;
    }

    public void running(Instant now) { state = "RUNNING"; startedAt = now; }
    public void inspected(Map<String, Long> counts, boolean conflicts, long quotaBytes) {
        this.counts = Map.copyOf(counts); hasConflicts = conflicts; quotaImpactBytes = quotaBytes;
        state = "READY"; completedAt = null;
    }
    public void apply(String strategy) {
        importStrategy = strategy; phase = "APPLY"; state = "QUEUED";
        startedAt = null; completedAt = null; cancelRequested = false; errors = List.of();
    }
    public void succeeded(long size, Instant now) { byteSize = size; state = "SUCCEEDED"; completedAt = now; }
    public void succeeded(long size, Map<String, Long> counts, Instant now) {
        this.counts = Map.copyOf(counts); succeeded(size, now);
    }
    public void failed(List<String> failures, Instant now) {
        errors = List.copyOf(failures.stream().limit(100).toList()); state = "FAILED"; completedAt = now;
    }
    public void requestCancel(Instant now) {
        cancelRequested = true;
        if (state.equals("QUEUED") || state.equals("READY")) { state = "CANCELED"; completedAt = now; }
    }
    public void canceled(Instant now) { cancelRequested = true; state = "CANCELED"; completedAt = now; }
    public void expired(Instant now) { state = "EXPIRED"; completedAt = now; }
    public void requeue() { if (state.equals("RUNNING")) state = "QUEUED"; }

    public UUID id() { return id; }
    public String kind() { return kind; }
    public String phase() { return phase; }
    public String state() { return state; }
    public UUID requestedBy() { return requestedBy; }
    public UUID targetUserId() { return targetUserId; }
    public boolean blindAdmin() { return blindAdmin; }
    public String scopeKind() { return scopeKind; }
    public UUID scopeEntityId() { return scopeEntityId; }
    public String importStrategy() { return importStrategy; }
    public String temporaryPath() { return temporaryPath; }
    public String originalFileName() { return originalFileName; }
    public Long byteSize() { return byteSize; }
    public Map<String, Long> counts() { return counts; }
    public Boolean hasConflicts() { return hasConflicts; }
    public Long quotaImpactBytes() { return quotaImpactBytes; }
    public List<String> errors() { return errors == null ? List.of() : errors; }
    public boolean cancelRequested() { return cancelRequested; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public Instant expiresAt() { return expiresAt; }
}
