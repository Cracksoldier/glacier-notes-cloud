package com.glaciernotes.cloud.domain.notebook;

import com.glaciernotes.cloud.domain.OwnerId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotebookRepository {
    Optional<Notebook> findById(OwnerId ownerId, UUID id);

    List<Notebook> list(OwnerId ownerId);

    Notebook save(Notebook notebook);
}

