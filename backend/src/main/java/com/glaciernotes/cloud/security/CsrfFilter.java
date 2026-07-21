package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

@Provider
@Priority(Priorities.AUTHORIZATION - 10)
public class CsrfFilter implements ContainerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Inject
    SecurityIdentity identity;

    @Inject
    SessionTokenService tokens;

    @Override
    public void filter(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod()) || identity.isAnonymous()) {
            return;
        }
        var path = request.getUriInfo().getPath();
        if ("api/v1/auth/login".equals(path) || path.startsWith("api/v1/setup/")) {
            return;
        }
        var rawToken = identity.<String>getAttribute(AuthenticationIdentity.RAW_TOKEN);
        var cookie = request.getCookies().get(CookieManager.CSRF_COOKIE);
        var header = request.getHeaderString("X-CSRF-Token");
        var expected = rawToken == null ? null : tokens.csrfToken(rawToken);
        if (cookie == null || !tokens.matches(cookie.getValue(), header)
            || !tokens.matches(expected, header)) {
            throw AuthenticationFailure.csrfInvalid();
        }
    }
}
