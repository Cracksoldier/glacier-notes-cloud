package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backup_jobs")
public class BackupJobEntity {
    @Id private UUID id;
    @Column(name = "created_by") private UUID createdBy;
    private String state;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "output_identifier") private String outputIdentifier;
    @Column(name = "byte_size") private Long byteSize;
    private String checksum;
    @Column(name = "error_code") private String errorCode;
    @Column(name = "error_message") private String errorMessage;
    private long version;

    protected BackupJobEntity() {}

    public BackupJobEntity(UUID id, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        state = "QUEUED";
    }

    public UUID id() { return id; }
    public UUID createdBy() { return createdBy; }
    public String state() { return state; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public String outputIdentifier() { return outputIdentifier; }
    public Long byteSize() { return byteSize; }
    public String checksum() { return checksum; }
    public String errorMessage() { return errorMessage; }

    public void running(Instant now) { state = "RUNNING"; startedAt = now; }
    public void succeeded(Instant now, String output, long size, String hash) {
        state = "SUCCEEDED"; completedAt = now; outputIdentifier = output; byteSize = size;
        checksum = hash; errorCode = null; errorMessage = null;
    }
    public void failed(Instant now, String category) {
        state = "FAILED"; completedAt = now; errorCode = category;
        errorMessage = "The backup could not be completed. Review server logs using the job ID.";
    }
}
