package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.CharBuffer;

@ApplicationScoped
public class Argon2PasswordVerifier implements PasswordVerifier {
    private final Argon2Function function;
    private final int saltLength;

    public Argon2PasswordVerifier(GlacierConfiguration configuration) {
        var argon2 = configuration.password().argon2();
        validateParameters(argon2, configuration.password().saltLength());
        function = Argon2Function.getInstance(
            argon2.memoryKib(),
            argon2.iterations(),
            argon2.parallelism(),
            argon2.hashLength(),
            Argon2.ID,
            Argon2Function.ARGON2_VERSION_13
        );
        saltLength = configuration.password().saltLength();
    }

    @Override
    public String hash(char[] password) {
        return Password.hash(CharBuffer.wrap(password))
            .addRandomSalt(saltLength)
            .with(function)
            .getResult();
    }

    @Override
    public boolean matches(char[] password, String encodedHash) {
        var hashFunction = Argon2Function.getInstanceFromHash(encodedHash);
        return Password.check(CharBuffer.wrap(password), encodedHash).with(hashFunction);
    }

    private void validateParameters(GlacierConfiguration.Password.Argon2 argon2, int configuredSaltLength) {
        if (argon2.memoryKib() < 19_456 || argon2.iterations() < 2 || argon2.parallelism() < 1
            || argon2.hashLength() < 32 || configuredSaltLength < 16) {
            throw new IllegalStateException("Argon2id configuration is below the supported security baseline");
        }
    }
}
