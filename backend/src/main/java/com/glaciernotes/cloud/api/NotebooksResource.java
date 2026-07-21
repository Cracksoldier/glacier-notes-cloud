package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.content.ContentService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.generated.api.NotebooksApi;
import com.glaciernotes.cloud.generated.model.NotebookCreate;
import com.glaciernotes.cloud.generated.model.NotebookReorder;
import com.glaciernotes.cloud.generated.model.NotebookUpdate;
import com.glaciernotes.cloud.generated.model.NotebookView;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class NotebooksResource implements NotebooksApi {
    private final ContentService content;
    private final SecurityIdentity identity;

    public NotebooksResource(ContentService content, SecurityIdentity identity) {
        this.content = content;
        this.identity = identity;
    }

    @Override
    public NotebookView createNotebook(NotebookCreate request) {
        return content.createNotebook(owner(), request);
    }

    @Override
    public void deleteNotebook(String strategy, Long version, UUID notebookId) {
        content.deleteNotebook(owner(), notebookId, strategy, version);
    }

    @Override
    public NotebookView getDefaultNotebook() {
        return content.getDefaultNotebook(owner());
    }

    @Override
    public NotebookView getNotebook(UUID notebookId) {
        return content.getNotebook(owner(), notebookId);
    }

    @Override
    public List<NotebookView> listNotebooks() {
        return content.listNotebooks(owner());
    }

    @Override
    public List<NotebookView> reorderNotebooks(NotebookReorder request) {
        return content.reorderNotebooks(owner(), request);
    }

    @Override
    public NotebookView updateNotebook(UUID notebookId, NotebookUpdate request) {
        return content.updateNotebook(owner(), notebookId, request);
    }

    private OwnerId owner() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return new OwnerId(session.userId());
    }
}
