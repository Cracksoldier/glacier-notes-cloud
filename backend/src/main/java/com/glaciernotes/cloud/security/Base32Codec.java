package com.glaciernotes.cloud.security;

import java.io.ByteArrayOutputStream;

public final class Base32Codec {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS_PER_CHARACTER = 5;
    private static final int BITS_PER_BYTE = 8;
    private static final int BLOCK_CHARACTERS = 8;

    private Base32Codec() {
    }

    public static String encode(byte[] value) {
        var encoded = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte current : value) {
            buffer = (buffer << BITS_PER_BYTE) | (current & 0xff);
            bits += BITS_PER_BYTE;
            while (bits >= BITS_PER_CHARACTER) {
                bits -= BITS_PER_CHARACTER;
                encoded.append(ALPHABET.charAt((buffer >>> bits) & 0x1f));
            }
        }
        if (bits > 0) {
            encoded.append(ALPHABET.charAt((buffer << (BITS_PER_CHARACTER - bits)) & 0x1f));
        }
        while (encoded.length() % BLOCK_CHARACTERS != 0) {
            encoded.append('=');
        }
        return encoded.toString();
    }

    public static byte[] decode(String value) {
        var decoded = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = Character.toUpperCase(value.charAt(index));
            if (current == '=' || current == ' ' || current == '-') {
                continue;
            }
            int symbol = ALPHABET.indexOf(current);
            if (symbol < 0) {
                throw new IllegalArgumentException("Value is not Base32 encoded");
            }
            buffer = (buffer << BITS_PER_CHARACTER) | symbol;
            bits += BITS_PER_CHARACTER;
            if (bits >= BITS_PER_BYTE) {
                bits -= BITS_PER_BYTE;
                decoded.write((buffer >>> bits) & 0xff);
            }
        }
        return decoded.toByteArray();
    }
}
