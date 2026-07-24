package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class OwnedEntityId implements Serializable {
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    protected OwnedEntityId() {
    }

    public OwnedEntityId(UUID ownerId, UUID id) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof OwnedEntityId that
            && Objects.equals(ownerId, that.ownerId) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, id);
    }
}
