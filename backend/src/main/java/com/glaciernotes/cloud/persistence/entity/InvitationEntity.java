package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
public class InvitationEntity {
    @Id
    private UUID id;
    private String email;
    @Column(name = "email_normalized")
    private String emailNormalized;
    @Column(name = "proposed_username")
    private String proposedUsername;
    @Column(name = "proposed_username_normalized")
    private String proposedUsernameNormalized;
    private String role;
    @Column(name = "display_name")
    private String displayName;
    @Column(name = "token_hash")
    private String tokenHash;
    private String status;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected InvitationEntity() {
    }

    public InvitationEntity(UUID id, String email, String emailNormalized, String proposedUsername,
                            String proposedUsernameNormalized, String role, String displayName,
                            String tokenHash, UUID createdBy, Instant now, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.proposedUsername = proposedUsername;
        this.proposedUsernameNormalized = proposedUsernameNormalized;
        this.role = role;
        this.displayName = displayName;
        this.tokenHash = tokenHash;
        this.status = "PENDING";
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String emailNormalized() { return emailNormalized; }
    public String proposedUsername() { return proposedUsername; }
    public String role() { return role; }
    public String displayName() { return displayName; }
    public String status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant revokedAt() { return revokedAt; }

    public boolean usableAt(Instant now) {
        return "PENDING".equals(status) && expiresAt.isAfter(now);
    }

    public void expireIfNeeded(Instant now) {
        if ("PENDING".equals(status) && !expiresAt.isAfter(now)) status = "EXPIRED";
    }

    public void regenerate(String replacementHash, Instant now, Instant replacementExpiry) {
        tokenHash = replacementHash;
        status = "PENDING";
        createdAt = now;
        expiresAt = replacementExpiry;
        acceptedAt = null;
        revokedAt = null;
    }

    public void accept(Instant now) { status = "ACCEPTED"; acceptedAt = now; }
    public void revoke(Instant now) { status = "REVOKED"; revokedAt = now; }
}
