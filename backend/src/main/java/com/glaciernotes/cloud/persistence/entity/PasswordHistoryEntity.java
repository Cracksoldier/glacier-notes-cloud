package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_password_history")
public class PasswordHistoryEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordHistoryEntity() {}

    public PasswordHistoryEntity(UUID id, UUID userId, String passwordHash, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public String passwordHash() { return passwordHash; }
}
