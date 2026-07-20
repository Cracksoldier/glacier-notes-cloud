package com.glaciernotes.cloud.domain.notebook;

import com.glaciernotes.cloud.domain.OwnerId;

import java.time.Instant;
import java.util.UUID;

public record Notebook(
    OwnerId ownerId,
    UUID id,
    String name,
    String color,
    boolean defaultNotebook,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
}

