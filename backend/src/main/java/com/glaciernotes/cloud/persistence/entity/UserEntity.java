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
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
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

    public UUID id() {
        return id;
    }
}

