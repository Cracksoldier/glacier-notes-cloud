package com.glaciernotes.cloud.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.nio.file.Path;
import java.util.Optional;

@ConfigMapping(prefix = "glacier")
public interface GlacierConfiguration {
    Bootstrap bootstrap();

    Security security();

    Optional<String> publicBaseUrl();

    Password password();

    interface Bootstrap {
        Optional<String> token();

        Optional<Path> tokenFile();

        @WithDefault("5")
        int failureLimit();

        @WithDefault("900")
        long windowSeconds();

        @WithDefault("900")
        long blockSeconds();
    }

    interface Security {
        Optional<String> sessionSecret();

        Optional<Path> sessionSecretFile();
    }

    interface Password {
        Argon2 argon2();

        @WithDefault("16")
        int saltLength();

        interface Argon2 {
            @WithDefault("19456")
            int memoryKib();

            @WithDefault("2")
            int iterations();

            @WithDefault("1")
            int parallelism();

            @WithDefault("32")
            int hashLength();
        }
    }
}
