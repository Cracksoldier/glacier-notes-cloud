package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
public class UserSettingsEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    private String theme;
    private String language;
    @Column(name = "move_checked_to_bottom")
    private boolean moveCheckedToBottom;
    @Column(name = "last_selected_notebook_id")
    private UUID lastSelectedNotebookId;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private long version;

    protected UserSettingsEntity() {
    }

    public UserSettingsEntity(UUID userId, UUID defaultNotebookId, Instant now) {
        this.userId = userId;
        theme = "dark";
        language = "en";
        moveCheckedToBottom = false;
        lastSelectedNotebookId = defaultNotebookId;
        updatedAt = now;
    }
}
