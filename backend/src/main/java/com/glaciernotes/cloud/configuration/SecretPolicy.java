package com.glaciernotes.cloud.configuration;

public final class SecretPolicy {
    public static final int MINIMUM_LENGTH = 32;
    public static final int MAXIMUM_LENGTH = 512;

    private SecretPolicy() {
    }

    public static boolean valid(String value) {
        return value != null
            && value.length() >= MINIMUM_LENGTH
            && value.length() <= MAXIMUM_LENGTH
            && value.codePoints().noneMatch(Character::isWhitespace);
    }
}
