package com.glaciernotes.cloud.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class SessionAuthenticationMechanism implements HttpAuthenticationMechanism {
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    @Override
    public Uni<SecurityIdentity> authenticate(
        RoutingContext context,
        IdentityProviderManager identityProviderManager
    ) {
        var cookie = context.request().getCookie(CookieManager.SESSION_COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return identityProviderManager.authenticate(new SessionAuthenticationRequest(cookie.getValue()));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401));
    }

    @Override
    public Uni<Boolean> sendChallenge(RoutingContext context) {
        var incoming = context.request().getHeader("X-Correlation-ID");
        var correlationId = incoming != null && VALID_CORRELATION_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
        var problem = new JsonObject()
            .put("type", "https://glacier-notes.example/problems/auth-session-expired")
            .put("title", "Authentication Required")
            .put("status", 401)
            .put("detail", "A valid session is required.")
            .put("instance", context.request().path())
            .put("correlationId", correlationId)
            .put("errorCode", "AUTH_SESSION_EXPIRED");
        context.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/problem+json")
            .putHeader("X-Correlation-ID", correlationId)
            .putHeader("Cache-Control", "no-store")
            .putHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
            .putHeader("X-Frame-Options", "DENY")
            .putHeader("X-Content-Type-Options", "nosniff")
            .putHeader("Referrer-Policy", "no-referrer")
            .putHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
            .end(problem.encode());
        return Uni.createFrom().item(true);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(SessionAuthenticationRequest.class);
    }
}
