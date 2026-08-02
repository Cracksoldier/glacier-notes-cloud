package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.api.CorrelationIds;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class SessionAuthenticationMechanism implements HttpAuthenticationMechanism {
    /**
     * Login must never fail because a stale or attacker-supplied session cookie happens to be
     * present: Quarkus's proactive authentication would otherwise reject the whole request with 401
     * before {@code AuthenticationResource#login} ever runs. Treat this path like no cookie was sent.
     */
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    /**
     * Nothing outside the API is authenticated — the Angular entry point and the static bundle are
     * public. For the same reason as above, a stale cookie must not turn into a 401 on the very page
     * the user needs in order to sign in again, which would leave the browser showing raw
     * problem+json with no way forward but clearing cookies by hand.
     */
    private static final String API_PREFIX = "/api/";

    @Override
    public Uni<SecurityIdentity> authenticate(
        RoutingContext context,
        IdentityProviderManager identityProviderManager
    ) {
        var cookie = context.request().getCookie(CookieManager.SESSION_COOKIE);
        var path = context.normalizedPath();
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()
            || !path.startsWith(API_PREFIX)
            || (context.request().method() == HttpMethod.POST && LOGIN_PATH.equals(path))) {
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
        var correlationId = CorrelationIds.resolve(incoming);
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
