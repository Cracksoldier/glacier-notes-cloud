package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instance_state")
public class InstanceStateEntity {
    @Id
    @Column(name = "singleton_key")
    private short key;
    private boolean initialized;
    @Column(name = "initialized_at")
    private Instant initializedAt;
    @Column(name = "initialized_by")
    private UUID initializedBy;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private long version;
    @Column(name = "image_storage_backend")
    private String imageStorageBackend;

    protected InstanceStateEntity() {
    }

    public boolean initialized() {
        return initialized;
    }

    public Instant initializedAt() {
        return initializedAt;
    }

    public String imageStorageBackend() { return imageStorageBackend; }
    public void selectImageStorageBackend(String backend, Instant now) {
        imageStorageBackend = backend;
        updatedAt = now;
    }

    public void initialize(UUID administratorId, Instant now) {
        initialized = true;
        initializedAt = now;
        initializedBy = administratorId;
        updatedAt = now;
    }
}
