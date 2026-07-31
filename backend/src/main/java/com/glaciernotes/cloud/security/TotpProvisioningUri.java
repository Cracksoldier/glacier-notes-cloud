package com.glaciernotes.cloud.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class TotpProvisioningUri {
    private TotpProvisioningUri() {
    }

    public static String build(String issuer, String account, byte[] secret, int digits, int periodSeconds) {
        var label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label
            + "?secret=" + Base32Codec.encode(secret).replace("=", "")
            + "&issuer=" + encode(issuer)
            + "&algorithm=SHA1"
            + "&digits=" + digits
            + "&period=" + periodSeconds;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
