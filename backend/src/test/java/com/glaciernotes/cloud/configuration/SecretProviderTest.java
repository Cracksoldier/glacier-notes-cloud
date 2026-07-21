package com.glaciernotes.cloud.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretProviderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mountedFileTakesPrecedenceAndTerminalNewlinesAreRemoved() throws IOException {
        var tokenFile = temporaryDirectory.resolve("bootstrap-token");
        Files.writeString(tokenFile, "file-bootstrap-secret-value\n");

        var provider = new SecretProvider(configuration(
            Optional.of(tokenFile),
            Optional.of("environment-bootstrap-secret-value")
        ));

        assertEquals("file-bootstrap-secret-value", provider.bootstrapToken().orElseThrow());
    }

    @Test
    void configuredValueIsUsedWhenNoFileIsConfigured() {
        var provider = new SecretProvider(configuration(
            Optional.empty(),
            Optional.of("environment-bootstrap-secret-value")
        ));

        assertEquals("environment-bootstrap-secret-value", provider.bootstrapToken().orElseThrow());
    }

    private GlacierConfiguration configuration(Optional<Path> tokenFile, Optional<String> token) {
        return new GlacierConfiguration() {
            @Override
            public Optional<String> publicBaseUrl() {
                return Optional.empty();
            }

            @Override
            public Bootstrap bootstrap() {
                return new Bootstrap() {
                    @Override
                    public Optional<String> token() {
                        return token;
                    }

                    @Override
                    public Optional<Path> tokenFile() {
                        return tokenFile;
                    }

                    @Override
                    public int failureLimit() {
                        return 5;
                    }

                    @Override
                    public long windowSeconds() {
                        return 900;
                    }

                    @Override
                    public long blockSeconds() {
                        return 900;
                    }
                };
            }

            @Override
            public Security security() {
                return new Security() {
                    @Override
                    public Optional<String> sessionSecret() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Path> sessionSecretFile() {
                        return Optional.empty();
                    }
                };
            }

            @Override
            public Password password() {
                return new Password() {
                    @Override
                    public Argon2 argon2() {
                        return new Argon2() {
                            @Override
                            public int memoryKib() {
                                return 19_456;
                            }

                            @Override
                            public int iterations() {
                                return 2;
                            }

                            @Override
                            public int parallelism() {
                                return 1;
                            }

                            @Override
                            public int hashLength() {
                                return 32;
                            }
                        };
                    }

                    @Override
                    public int saltLength() {
                        return 16;
                    }
                };
            }

            @Override
            public Smtp smtp() {
                return new Smtp() {
                    @Override public boolean enabled() { return false; }
                    @Override public Optional<String> senderName() { return Optional.empty(); }
                    @Override public Optional<String> senderAddress() { return Optional.empty(); }
                };
            }
        };
    }
}
