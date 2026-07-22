package com.glaciernotes.cloud.application.content;

import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.domain.OwnerId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContentFailure extends RuntimeException {
    private final Reason reason;
    private final Long currentVersion;
    private final Instant currentUpdatedAt;
    private final OwnerId conflictOwner;
    private final UUID conflictNoteId;
    private final List<SetupFailure.FieldViolation> violations;

    private ContentFailure(Reason reason, String message, Long currentVersion, Instant currentUpdatedAt,
                           OwnerId conflictOwner, UUID conflictNoteId,
                           List<SetupFailure.FieldViolation> violations) {
        super(message);
        this.reason = reason;
        this.currentVersion = currentVersion;
        this.currentUpdatedAt = currentUpdatedAt;
        this.conflictOwner = conflictOwner;
        this.conflictNoteId = conflictNoteId;
        this.violations = List.copyOf(violations);
    }

    public static ContentFailure notFound() {
        return new ContentFailure(Reason.NOT_FOUND, "The requested content was not found.", null, null, null, null, List.of());
    }

    public static ContentFailure conflict(String message) {
        return new ContentFailure(Reason.CONFLICT, message, null, null, null, null, List.of());
    }

    public static ContentFailure invalidState(String message) {
        return new ContentFailure(Reason.INVALID_STATE, message, null, null, null, null, List.of());
    }

    public static ContentFailure version(long currentVersion) {
        return new ContentFailure(Reason.VERSION_CONFLICT,
            "The content changed since it was read.", currentVersion, null, null, null, List.of());
    }

    public static ContentFailure noteVersion(OwnerId owner, UUID noteId, long currentVersion,
                                             Instant currentUpdatedAt) {
        return new ContentFailure(Reason.VERSION_CONFLICT, "The note changed since it was read.",
            currentVersion, currentUpdatedAt, owner, noteId, List.of());
    }

    public static ContentFailure invalid(String field, String message) {
        return new ContentFailure(Reason.INVALID, "The request is invalid.", null, null, null, null,
            List.of(new SetupFailure.FieldViolation(field, message)));
    }

    public Reason reason() { return reason; }
    public Long currentVersion() { return currentVersion; }
    public Instant currentUpdatedAt() { return currentUpdatedAt; }
    public OwnerId conflictOwner() { return conflictOwner; }
    public UUID conflictNoteId() { return conflictNoteId; }
    public List<SetupFailure.FieldViolation> violations() { return violations; }

    public enum Reason { NOT_FOUND, CONFLICT, INVALID_STATE, VERSION_CONFLICT, INVALID }
}
