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
    @Column(name = "trash_auto_purge_days")
    private Integer trashAutoPurgeDays;
    @Column(name = "last_selected_notebook_id")
    private UUID lastSelectedNotebookId;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private long version;

    protected UserSettingsEntity() {
    }

    public UserSettingsEntity(UUID userId, UUID defaultNotebookId, Instant now) {
        this(userId, defaultNotebookId, "en", 30, now);
    }

    public UserSettingsEntity(UUID userId, UUID defaultNotebookId, String language,
                              int trashAutoPurgeDays, Instant now) {
        this.userId = userId;
        theme = "dark";
        this.language = "de".equals(language) ? "de" : "en";
        moveCheckedToBottom = false;
        this.trashAutoPurgeDays = trashAutoPurgeDays;
        lastSelectedNotebookId = defaultNotebookId;
        updatedAt = now;
    }

    public String theme() { return theme; }
    public String language() { return language; }
    public boolean moveCheckedToBottom() { return moveCheckedToBottom; }
    public Integer trashAutoPurgeDays() { return trashAutoPurgeDays; }

    public void update(String theme, String language, Boolean moveChecked, Integer trashDays,
                       boolean updateTrash, Instant now) {
        if (theme != null) this.theme = theme;
        if (language != null) this.language = language;
        if (moveChecked != null) moveCheckedToBottom = moveChecked;
        if (updateTrash) trashAutoPurgeDays = trashDays;
        updatedAt = now;
    }
}
