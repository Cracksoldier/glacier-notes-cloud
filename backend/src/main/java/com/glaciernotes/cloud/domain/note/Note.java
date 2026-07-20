package com.glaciernotes.cloud.domain.note;

import com.glaciernotes.cloud.domain.OwnerId;

import java.time.Instant;
import java.util.UUID;

public record Note(
    OwnerId ownerId,
    UUID id,
    UUID notebookId,
    String type,
    String title,
    String content,
    boolean pinned,
    boolean archived,
    String color,
    Instant deletedAt,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
}

