package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_challenges")
public class MfaChallengeEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "remember_me")
    private boolean rememberMe;
    @Column(name = "attempt_count")
    private int attemptCount;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;
    @Column(name = "client_description")
    private String clientDescription;

    protected MfaChallengeEntity() {
    }

    public MfaChallengeEntity(
        UUID id,
        UUID userId,
        String tokenHash,
        boolean rememberMe,
        Instant createdAt,
        Instant expiresAt,
        InetAddress ipAddress,
        String clientDescription
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.rememberMe = rememberMe;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.clientDescription = clientDescription;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public boolean rememberMe() { return rememberMe; }
    public int attemptCount() { return attemptCount; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public InetAddress ipAddress() { return ipAddress; }
    public String clientDescription() { return clientDescription; }

    public boolean usableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public int recordFailure() {
        return ++attemptCount;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
