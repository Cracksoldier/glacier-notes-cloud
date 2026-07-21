package com.glaciernotes.cloud.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class SessionAuthenticationRequest extends BaseAuthenticationRequest {
    private final String token;

    public SessionAuthenticationRequest(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
