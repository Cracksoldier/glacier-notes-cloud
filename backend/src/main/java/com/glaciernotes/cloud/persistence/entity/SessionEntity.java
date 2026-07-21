package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.net.InetAddress;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class SessionEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "remember_me")
    private boolean rememberMe;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;
    @Column(name = "client_description")
    private String clientDescription;

    protected SessionEntity() {
    }

    public SessionEntity(
        UUID id,
        UserEntity user,
        String tokenHash,
        boolean rememberMe,
        Instant createdAt,
        Instant expiresAt,
        InetAddress ipAddress,
        String clientDescription
    ) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.rememberMe = rememberMe;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.clientDescription = clientDescription;
    }

    public UUID id() {
        return id;
    }

    public UserEntity user() {
        return user;
    }

    public boolean rememberMe() {
        return rememberMe;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String clientDescription() {
        return clientDescription;
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        lastSeenAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
