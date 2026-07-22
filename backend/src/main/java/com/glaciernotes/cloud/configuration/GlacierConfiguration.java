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

    Smtp smtp();

    Images images();

    Transfer transfer();

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

    interface Smtp {
        @WithDefault("false")
        boolean enabled();

        Optional<String> senderName();

        Optional<String> senderAddress();
    }

    interface Images {
        @WithDefault("FILESYSTEM") String backend();
        @WithDefault("/var/lib/glacier-notes/images") Path filesystemRoot();
        @WithDefault("41943040") long maximumUploadBytes();
        @WithDefault("40000000") long maximumPixels();
        @WithDefault("2560") int maximumEdge();
        @WithDefault("480") int thumbnailEdge();
        @WithDefault("10") int processingTimeoutSeconds();
        S3 s3();

        interface S3 {
            Optional<String> endpoint();
            @WithDefault("us-east-1") String region();
            @WithDefault("glacier-notes") String bucket();
            @WithDefault("true") boolean pathStyle();
            Optional<Path> accessKeyFile();
            Optional<Path> secretKeyFile();
            Optional<String> serverSideEncryption();
        }
    }

    interface Transfer {
        @WithDefault("/var/lib/glacier-notes/transfers") Path temporaryRoot();
        @WithDefault("1610612736") long maximumUploadBytes();
        @WithDefault("1073741824") long maximumDecodedImageBytes();
        @WithDefault("10485760") long maximumImageBytes();
        @WithDefault("10000") int maximumNotebooks();
        @WithDefault("100000") int maximumNotes();
        @WithDefault("10000") int maximumLabels();
        @WithDefault("100000") int maximumImages();
        @WithDefault("1000000") int maximumChecklistItems();
        @WithDefault("32") int maximumJsonDepth();
        @WithDefault("16777216") int maximumStringLength();
        @WithDefault("24") int retentionHours();
    }
}
