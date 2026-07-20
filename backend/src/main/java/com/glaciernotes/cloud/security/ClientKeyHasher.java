package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@ApplicationScoped
public class ClientKeyHasher {
    private final SecretProvider secretProvider;

    public ClientKeyHasher(SecretProvider secretProvider) {
        this.secretProvider = secretProvider;
    }

    public String hash(String clientAddress) {
        var secret = secretProvider.sessionSecret()
            .orElseThrow(() -> new IllegalStateException("A session secret is required"));
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(clientAddress.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
