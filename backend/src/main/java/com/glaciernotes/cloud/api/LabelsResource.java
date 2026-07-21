package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.content.ContentService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.generated.api.LabelsApi;
import com.glaciernotes.cloud.generated.model.LabelCreate;
import com.glaciernotes.cloud.generated.model.LabelUpdate;
import com.glaciernotes.cloud.generated.model.LabelView;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class LabelsResource implements LabelsApi {
    private final ContentService content;
    private final SecurityIdentity identity;

    public LabelsResource(ContentService content, SecurityIdentity identity) {
        this.content = content;
        this.identity = identity;
    }

    @Override
    public LabelView createLabel(LabelCreate request) {
        return content.createLabel(owner(), request);
    }

    @Override
    public void deleteLabel(Long version, UUID labelId) {
        content.deleteLabel(owner(), labelId, version);
    }

    @Override
    public List<LabelView> listLabels() {
        return content.listLabels(owner());
    }

    @Override
    public LabelView updateLabel(UUID labelId, LabelUpdate request) {
        return content.updateLabel(owner(), labelId, request);
    }

    private OwnerId owner() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return new OwnerId(session.userId());
    }
}
