package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.generated.api.SessionsApi;
import com.glaciernotes.cloud.generated.model.SessionSummary;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import com.glaciernotes.cloud.security.CookieManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class SessionsResource implements SessionsApi {
    private final SessionRepository sessions;
    private final SecurityIdentity identity;
    private final CookieManager cookies;

    @Context
    HttpServerResponse response;

    public SessionsResource(
        SessionRepository sessions,
        SecurityIdentity identity,
        CookieManager cookies
    ) {
        this.sessions = sessions;
        this.identity = identity;
        this.cookies = cookies;
    }

    @Override
    public List<SessionSummary> listSessions() {
        var current = currentSession();
        return sessions.listActive(current.userId()).stream()
            .map(session -> AuthenticationModels.summary(session, session.id().equals(current.id())))
            .toList();
    }

    @Override
    public void revokeOtherSessions() {
        var current = currentSession();
        sessions.revokeOthers(current.userId(), current.id());
    }

    @Override
    public void revokeSession(UUID sessionId) {
        var current = currentSession();
        if (!sessions.revoke(current.userId(), sessionId)) {
            throw new NotFoundException();
        }
        if (current.id().equals(sessionId)) {
            cookies.clear(response);
        }
    }

    private SessionView currentSession() {
        var session = identity.<SessionView>getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) {
            throw AuthenticationFailure.sessionNotFound();
        }
        return session;
    }
}
