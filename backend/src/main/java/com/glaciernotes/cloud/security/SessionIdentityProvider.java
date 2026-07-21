package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<SessionAuthenticationRequest> {
    private final SessionRepository sessions;
    private final SessionTokenService tokens;

    public SessionIdentityProvider(SessionRepository sessions, SessionTokenService tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @Override
    public Class<SessionAuthenticationRequest> getRequestType() {
        return SessionAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
        SessionAuthenticationRequest request,
        AuthenticationRequestContext context
    ) {
        return context.runBlocking(() -> sessions.authenticate(tokens.hashToken(request.token()))
            .map(session -> QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> session.userId().toString())
                .addRole(session.role())
                .addAttribute(AuthenticationIdentity.SESSION, session)
                .addAttribute(AuthenticationIdentity.RAW_TOKEN, request.token())
                .build())
            .orElse(null));
    }
}
