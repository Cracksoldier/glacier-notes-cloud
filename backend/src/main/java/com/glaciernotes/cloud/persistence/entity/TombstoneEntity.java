package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tombstones")
public class TombstoneEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id")
    private UUID ownerId;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private UUID entityId;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "last_version")
    private long lastVersion;

    protected TombstoneEntity() {}

    public TombstoneEntity(UUID id, UUID ownerId, String entityType, UUID entityId,
                           Instant deletedAt, Instant expiresAt, long lastVersion) {
        this.id = id;
        this.ownerId = ownerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.deletedAt = deletedAt;
        this.expiresAt = expiresAt;
        this.lastVersion = lastVersion;
    }

    public UUID ownerId() {
        return ownerId;
    }
}
