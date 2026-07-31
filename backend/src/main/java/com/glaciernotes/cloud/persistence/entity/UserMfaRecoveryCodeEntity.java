package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_recovery_codes")
public class UserMfaRecoveryCodeEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "code_hash")
    private String codeHash;
    @Column(name = "generated_at")
    private Instant generatedAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected UserMfaRecoveryCodeEntity() {
    }

    public UserMfaRecoveryCodeEntity(UUID id, UUID userId, String codeHash, Instant generatedAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.generatedAt = generatedAt;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public Instant usedAt() { return usedAt; }

    public boolean unused() {
        return usedAt == null;
    }

    public void use(Instant now) {
        usedAt = now;
    }
}
