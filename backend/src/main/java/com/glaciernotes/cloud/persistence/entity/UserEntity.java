package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserEntity {
    @Id
    private UUID id;
    private String username;
    @Column(name = "username_normalized")
    private String usernameNormalized;
    private String email;
    @Column(name = "email_normalized")
    private String emailNormalized;
    @Column(name = "display_name")
    private String displayName;
    private String role;
    private String status;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "failed_login_count")
    private int failedLoginCount;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Version
    private long version;

    protected UserEntity() {
    }

    public UserEntity(
        UUID id,
        String username,
        String usernameNormalized,
        String email,
        String emailNormalized,
        String role,
        String status,
        Instant now
    ) {
        this.id = id;
        this.username = username;
        this.usernameNormalized = usernameNormalized;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.role = role;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserEntity initialAdministrator(
        UUID id,
        String username,
        String usernameNormalized,
        String email,
        String emailNormalized,
        String displayName,
        String passwordHash,
        Instant now
    ) {
        var user = new UserEntity(
            id, username, usernameNormalized, email, emailNormalized, "ADMIN", "ACTIVE", now
        );
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.passwordChangedAt = now;
        user.activatedAt = now;
        return user;
    }

    public UUID id() {
        return id;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }

    public String status() {
        return status;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public int registerFailedLogin(int lockThreshold, Instant now, long lockMinutes) {
        failedLoginCount++;
        updatedAt = now;
        if (failedLoginCount >= lockThreshold && "ACTIVE".equals(status)) {
            status = "LOCKED";
            lockedUntil = now.plusSeconds(lockMinutes * 60);
        }
        return failedLoginCount;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        status = "ACTIVE";
        lastLoginAt = now;
        updatedAt = now;
    }

    public void unlockIfTemporaryLockExpired(Instant now) {
        if ("LOCKED".equals(status) && lockedUntil != null && !lockedUntil.isAfter(now)) {
            status = "ACTIVE";
            failedLoginCount = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }
}
