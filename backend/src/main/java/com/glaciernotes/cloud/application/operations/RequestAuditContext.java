package com.glaciernotes.cloud.application.operations;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.net.InetAddress;
import java.util.Locale;

@RequestScoped
public class RequestAuditContext {
    @Inject
    HttpServerRequest request;

    public InetAddress address() {
        try {
            var remote = request.remoteAddress();
            return remote == null ? null : InetAddress.getByName(remote.hostAddress());
        } catch (Exception ignored) {
            return null;
        }
    }

    public String clientDescription() {
        try {
            String value = request.getHeader("User-Agent");
            if (value == null || value.isBlank()) return null;
            String lower = value.toLowerCase(Locale.ROOT);
            String browser = lower.contains("edg/") ? "Edge"
                : lower.contains("firefox/") ? "Firefox"
                : lower.contains("chrome/") ? "Chrome"
                : lower.contains("safari/") ? "Safari" : "Other browser";
            String platform = lower.contains("android") ? "Android"
                : lower.contains("iphone") || lower.contains("ipad") ? "iOS"
                : lower.contains("windows") ? "Windows"
                : lower.contains("mac os") || lower.contains("macintosh") ? "macOS"
                : lower.contains("linux") ? "Linux" : "Other platform";
            return browser + " / " + platform;
        } catch (RuntimeException noHttpRequest) {
            return null;
        }
    }
}
