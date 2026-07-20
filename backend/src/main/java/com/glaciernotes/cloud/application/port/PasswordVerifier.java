package com.glaciernotes.cloud.application.port;

/** Replaceable password hashing and verification boundary for authentication tests. */
public interface PasswordVerifier {
    String hash(char[] password);

    boolean matches(char[] password, String encodedHash);
}
