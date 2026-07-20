package com.glaciernotes.cloud.domain.note;

import com.glaciernotes.cloud.domain.OwnerId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository {
    Optional<Note> findById(OwnerId ownerId, UUID id);

    List<Note> listByNotebook(OwnerId ownerId, UUID notebookId, int limit);

    Note save(Note note);
}

