package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "labels")
public class LabelEntity extends OwnedMutableEntity {
    @Column(nullable = false)
    private String name;
    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    protected LabelEntity() {
    }

    public LabelEntity(UUID ownerId, UUID id, String name, String normalizedName, Instant now) {
        this.key = new OwnedEntityId(ownerId, id);
        this.name = name;
        this.nameNormalized = normalizedName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() { return key.id(); }
    public String name() { return name; }
    public String nameNormalized() { return nameNormalized; }

    public void rename(String name, String normalizedName, Instant now) {
        this.name = name;
        this.nameNormalized = normalizedName;
        this.updatedAt = now;
    }
}
