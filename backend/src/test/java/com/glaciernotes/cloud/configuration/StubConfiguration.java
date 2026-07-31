package com.glaciernotes.cloud.configuration;

import java.nio.file.Path;
import java.util.Optional;

public final class StubConfiguration implements GlacierConfiguration {
    private Optional<Path> bootstrapTokenFile = Optional.empty();
    private Optional<String> bootstrapToken = Optional.empty();
    private Optional<Path> sessionSecretFile = Optional.empty();
    private Optional<String> sessionSecret = Optional.empty();
    private Optional<Path> mfaSecretFile = Optional.empty();
    private Optional<String> mfaSecret = Optional.empty();
    private boolean mfaEnabled;

    public StubConfiguration bootstrapToken(Optional<Path> file, Optional<String> value) {
        bootstrapTokenFile = file;
        bootstrapToken = value;
        return this;
    }

    public StubConfiguration sessionSecret(Optional<Path> file, Optional<String> value) {
        sessionSecretFile = file;
        sessionSecret = value;
        return this;
    }

    public StubConfiguration mfa(boolean enabled, Optional<Path> file, Optional<String> value) {
        mfaEnabled = enabled;
        mfaSecretFile = file;
        mfaSecret = value;
        return this;
    }

    @Override
    public Bootstrap bootstrap() {
        return new Bootstrap() {
            @Override public Optional<String> token() { return bootstrapToken; }
            @Override public Optional<Path> tokenFile() { return bootstrapTokenFile; }
            @Override public int failureLimit() { return 5; }
            @Override public long windowSeconds() { return 900; }
            @Override public long blockSeconds() { return 900; }
        };
    }

    @Override
    public Security security() {
        return new Security() {
            @Override public Optional<String> sessionSecret() { return sessionSecret; }
            @Override public Optional<Path> sessionSecretFile() { return sessionSecretFile; }
        };
    }

    @Override
    public Mfa mfa() {
        return new Mfa() {
            @Override public boolean enabled() { return mfaEnabled; }
            @Override public Optional<String> encryptionSecret() { return mfaSecret; }
            @Override public Optional<Path> encryptionSecretFile() { return mfaSecretFile; }
        };
    }

    @Override
    public Optional<String> publicBaseUrl() {
        return Optional.empty();
    }

    @Override
    public Password password() {
        return new Password() {
            @Override
            public Argon2 argon2() {
                return new Argon2() {
                    @Override public int memoryKib() { return 19_456; }
                    @Override public int iterations() { return 2; }
                    @Override public int parallelism() { return 1; }
                    @Override public int hashLength() { return 32; }
                };
            }

            @Override public int saltLength() { return 16; }
        };
    }

    @Override
    public Smtp smtp() {
        return new Smtp() {
            @Override public boolean enabled() { return false; }
            @Override public Optional<String> username() { return Optional.empty(); }
            @Override public Optional<String> password() { return Optional.empty(); }
            @Override public Optional<String> senderName() { return Optional.empty(); }
            @Override public Optional<String> senderAddress() { return Optional.empty(); }
        };
    }

    @Override
    public HttpLimits http() { return null; }

    @Override
    public Images images() { return null; }

    @Override
    public Transfer transfer() { return null; }

    @Override
    public Backup backup() { return null; }
}
