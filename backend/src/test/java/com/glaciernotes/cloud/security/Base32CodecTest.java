package com.glaciernotes.cloud.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base32CodecTest {
    @ParameterizedTest
    @CsvSource({
        "'',''",
        "f,MY======",
        "fo,MZXQ====",
        "foo,MZXW6===",
        "foob,MZXW6YQ=",
        "fooba,MZXW6YTB",
        "foobar,MZXW6YTBOI======"
    })
    void encodesThePublishedRfc4648Vectors(String plain, String encoded) {
        assertEquals(encoded, Base32Codec.encode(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @CsvSource({
        "'',''",
        "f,MY======",
        "fo,MZXQ====",
        "foo,MZXW6===",
        "foob,MZXW6YQ=",
        "fooba,MZXW6YTB",
        "foobar,MZXW6YTBOI======"
    })
    void decodesThePublishedRfc4648Vectors(String plain, String encoded) {
        assertArrayEquals(plain.getBytes(StandardCharsets.UTF_8), Base32Codec.decode(encoded));
    }

    @Test
    void decodingToleratesLowercaseMissingPaddingAndGroupingSeparators() {
        assertArrayEquals(
            "foobar".getBytes(StandardCharsets.UTF_8),
            Base32Codec.decode("mzxw6ytboi")
        );
        assertArrayEquals(
            "foobar".getBytes(StandardCharsets.UTF_8),
            Base32Codec.decode("MZXW 6YTB OI")
        );
    }

    @Test
    void arbitraryBytesRoundTrip() {
        var secret = new byte[]{0, 1, 2, 3, (byte) 0x80, (byte) 0xff, 42, -7, 99, 17};

        assertArrayEquals(secret, Base32Codec.decode(Base32Codec.encode(secret)));
    }

    @Test
    void charactersOutsideTheAlphabetAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("MZXW6YT1"));
    }
}
