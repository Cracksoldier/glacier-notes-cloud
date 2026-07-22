package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_tokens")
public class SecurityTokenEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "token_type")
    private String tokenType;
    @Column(name = "target_email")
    private String targetEmail;
    @Column(name = "target_email_normalized")
    private String targetEmailNormalized;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SecurityTokenEntity() {
    }

    public SecurityTokenEntity(UUID id, UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.tokenType = "PASSWORD_RESET";
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public static SecurityTokenEntity emailChange(UUID id, UUID userId, String tokenHash,
                                                  String email, String normalizedEmail,
                                                  Instant now, Instant expiresAt) {
        var token = new SecurityTokenEntity(id, userId, tokenHash, now, expiresAt);
        token.tokenType = "EMAIL_CHANGE";
        token.targetEmail = email;
        token.targetEmailNormalized = normalizedEmail;
        return token;
    }

    public UUID userId() { return userId; }
    public String tokenType() { return tokenType; }
    public String targetEmail() { return targetEmail; }
    public String targetEmailNormalized() { return targetEmailNormalized; }
    public Instant expiresAt() { return expiresAt; }
    public boolean usableAt(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
    public void consume(Instant now) { consumedAt = now; }
    public void revoke(Instant now) { revokedAt = now; }
}
