package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_totp")
public class UserMfaTotpEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "status")
    private String status;
    @Column(name = "secret_ciphertext")
    private byte[] secretCiphertext;
    @Column(name = "secret_nonce")
    private byte[] secretNonce;
    @Column(name = "key_id")
    private String keyId;
    @Column(name = "algorithm")
    private String algorithm;
    @Column(name = "digits")
    private int digits;
    @Column(name = "period_seconds")
    private int periodSeconds;
    @Column(name = "last_accepted_step")
    private Long lastAcceptedStep;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Version
    @Column(name = "version")
    private long version;

    protected UserMfaTotpEntity() {
    }

    public UserMfaTotpEntity(
        UUID userId,
        byte[] secretCiphertext,
        byte[] secretNonce,
        String keyId,
        int digits,
        int periodSeconds,
        Instant now
    ) {
        this.userId = userId;
        this.status = "PENDING";
        this.secretCiphertext = secretCiphertext;
        this.secretNonce = secretNonce;
        this.keyId = keyId;
        this.algorithm = "SHA1";
        this.digits = digits;
        this.periodSeconds = periodSeconds;
        this.createdAt = now;
    }

    public UUID userId() { return userId; }
    public String status() { return status; }
    public byte[] secretCiphertext() { return secretCiphertext; }
    public byte[] secretNonce() { return secretNonce; }
    public String keyId() { return keyId; }
    public int digits() { return digits; }
    public int periodSeconds() { return periodSeconds; }
    public Long lastAcceptedStep() { return lastAcceptedStep; }
    public Instant createdAt() { return createdAt; }

    public boolean active() {
        return "ACTIVE".equals(status);
    }

    public void confirm(Instant now) {
        status = "ACTIVE";
        confirmedAt = now;
    }

    public void recordAcceptedStep(long step, Instant now) {
        lastAcceptedStep = step;
        lastUsedAt = now;
    }
}
