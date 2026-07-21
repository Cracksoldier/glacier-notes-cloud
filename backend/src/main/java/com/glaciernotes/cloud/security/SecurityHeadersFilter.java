package com.glaciernotes.cloud.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var headers = response.getHeaders();
        headers.putSingle(
            "Content-Security-Policy",
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                + "form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "connect-src 'self'; img-src 'self' blob: data:; font-src 'self'; worker-src 'self' blob:"
        );
        headers.putSingle("X-Frame-Options", "DENY");
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("Referrer-Policy", "no-referrer");
        headers.putSingle("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (request.getUriInfo().getPath().startsWith("api/v1/auth/")) {
            headers.putSingle("Cache-Control", "no-store");
        }
    }
}
