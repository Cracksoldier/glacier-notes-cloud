package com.glaciernotes.cloud.application.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionView(
    UUID id,
    UUID userId,
    String username,
    String email,
    String displayName,
    String role,
    boolean rememberMe,
    Instant createdAt,
    Instant lastSeenAt,
    Instant expiresAt,
    String clientDescription
) {
}
