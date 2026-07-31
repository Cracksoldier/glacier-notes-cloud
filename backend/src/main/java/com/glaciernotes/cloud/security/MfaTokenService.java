package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Keyed hashing for second-factor material, under the dedicated enrollment secret rather than the
 * session secret so that rotating session keys does not invalidate stored recovery codes.
 */
@ApplicationScoped
public class MfaTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    private static final int RECOVERY_CODE_CHARACTERS = 12;
    private static final int RECOVERY_GROUP_SIZE = 4;

    private final byte[] secret;

    public MfaTokenService(SecretProvider secretProvider) {
        secret = EnrollmentSecret.resolve(secretProvider);
    }

    public String newChallengeToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 160 bits, the width RFC 4226 specifies for an HMAC-SHA1 shared secret. */
    public byte[] newTotpSecret() {
        var bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public String hashChallengeToken(String token) {
        return HexFormat.of().formatHex(hmac("mfa-challenge:" + token));
    }

    public List<String> newRecoveryCodes(int count) {
        var codes = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            codes.add(newRecoveryCode());
        }
        return List.copyOf(codes);
    }

    public String hashRecoveryCode(String code) {
        return HexFormat.of().formatHex(hmac("mfa-recovery:" + normalizeRecoveryCode(code)));
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

    private String newRecoveryCode() {
        var code = new StringBuilder(RECOVERY_CODE_CHARACTERS + RECOVERY_CODE_CHARACTERS / RECOVERY_GROUP_SIZE);
        for (int index = 0; index < RECOVERY_CODE_CHARACTERS; index++) {
            if (index > 0 && index % RECOVERY_GROUP_SIZE == 0) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    private String normalizeRecoveryCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
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
