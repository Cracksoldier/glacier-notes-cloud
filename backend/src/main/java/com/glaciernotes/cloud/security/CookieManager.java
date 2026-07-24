package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@ApplicationScoped
public class CookieManager {
    public static final String SESSION_COOKIE = "GLACIER_SESSION";
    public static final String CSRF_COOKIE = "GLACIER_CSRF";

    private final EntityManager entityManager;
    private final String configuredPublicBaseUrl;
    private final SessionTokenService tokenService;

    public CookieManager(
        EntityManager entityManager,
        @ConfigProperty(name = "glacier.public-base-url", defaultValue = "http://localhost:8080")
        String configuredPublicBaseUrl,
        SessionTokenService tokenService
    ) {
        this.entityManager = entityManager;
        this.configuredPublicBaseUrl = configuredPublicBaseUrl;
        this.tokenService = tokenService;
    }

    @Transactional
    public void issue(HttpServerResponse response, String token, boolean rememberMe, long maxAgeSeconds) {
        var secure = secureCookies();
        var sessionCookie = baseCookie(SESSION_COOKIE, token, secure).setHttpOnly(true);
        var csrfCookie = baseCookie(CSRF_COOKIE, tokenService.csrfToken(token), secure).setHttpOnly(false);
        if (rememberMe) {
            sessionCookie.setMaxAge(maxAgeSeconds);
            csrfCookie.setMaxAge(maxAgeSeconds);
        }
        response.addCookie(sessionCookie);
        response.addCookie(csrfCookie);
    }

    @Transactional
    public void clear(HttpServerResponse response) {
        var secure = secureCookies();
        response.addCookie(baseCookie(SESSION_COOKIE, "", secure).setHttpOnly(true).setMaxAge(0));
        response.addCookie(baseCookie(CSRF_COOKIE, "", secure).setHttpOnly(false).setMaxAge(0));
    }

    private Cookie baseCookie(String name, String value, boolean secure) {
        return Cookie.cookie(name, value)
            .setPath("/")
            .setSameSite(CookieSameSite.LAX)
            .setSecure(secure);
    }

    private boolean secureCookies() {
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        var configured = settings.publicBaseUrl() == null || settings.publicBaseUrl().isBlank()
            ? configuredPublicBaseUrl
            : settings.publicBaseUrl();
        final String scheme;
        try {
            scheme = URI.create(configured).getScheme();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("The public base URL must be a valid HTTP or HTTPS URI", invalid);
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("The public base URL must use HTTP or HTTPS");
        }
        return "https".equalsIgnoreCase(scheme);
    }
}
