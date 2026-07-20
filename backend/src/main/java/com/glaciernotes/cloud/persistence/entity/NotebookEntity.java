package com.glaciernotes.cloud.persistence.entity;

import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.domain.OwnerId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "notebooks")
public class NotebookEntity extends OwnedMutableEntity {
    @Column(nullable = false)
    private String name;
    private String color;
    @Column(name = "is_default", nullable = false)
    private boolean defaultNotebook;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected NotebookEntity() {
    }

    public NotebookEntity(Notebook notebook) {
        key = new OwnedEntityId(notebook.ownerId().value(), notebook.id());
        update(notebook);
        createdAt = notebook.createdAt();
        version = notebook.version();
    }

    public void update(Notebook notebook) {
        name = notebook.name();
        color = notebook.color();
        defaultNotebook = notebook.defaultNotebook();
        sortOrder = notebook.sortOrder();
        updatedAt = notebook.updatedAt();
    }

    public Notebook toDomain() {
        return new Notebook(
            new OwnerId(key.ownerId()), key.id(), name, color, defaultNotebook, sortOrder,
            createdAt, updatedAt, version
        );
    }
}

