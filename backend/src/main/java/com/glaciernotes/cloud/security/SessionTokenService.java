package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import com.glaciernotes.cloud.configuration.SecretPolicy;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@ApplicationScoped
public class SessionTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] secret;

    public SessionTokenService(SecretProvider secretProvider) {
        secret = secretProvider.sessionSecret()
            .filter(SecretPolicy::valid)
            .orElseThrow(() -> new IllegalStateException(
                "A 32-512 character session secret without whitespace is required"
            ))
            .getBytes(StandardCharsets.UTF_8);
    }

    public String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        return HexFormat.of().formatHex(hmac("session:" + token));
    }

    public String hashInvitationToken(String token) {
        return HexFormat.of().formatHex(hmac("invitation:" + token));
    }

    public String hashPasswordResetToken(String token) {
        return HexFormat.of().formatHex(hmac("password-reset:" + token));
    }

    public String hashEmailChangeToken(String token) {
        return HexFormat.of().formatHex(hmac("email-change:" + token));
    }

    public String csrfToken(String token) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac("csrf:" + token));
    }

    public boolean matches(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.UTF_8),
            right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] hmac(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
