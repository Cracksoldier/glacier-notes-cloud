package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;

public record RequestBodyLimitPolicy(
    long defaultMaximumBodyBytes,
    long imageMaximumBodyBytes,
    long transferMaximumBodyBytes
) {
    public static RequestBodyLimitPolicy from(GlacierConfiguration configuration) {
        var http = configuration.http();
        return from(
            http.defaultMaximumBodyBytes(),
            http.multipartOverheadBytes(),
            http.absoluteMaximumBodyBytes(),
            configuration.images().maximumUploadBytes(),
            configuration.transfer().maximumUploadBytes()
        );
    }

    static RequestBodyLimitPolicy from(
        long defaultBodyBytes,
        long multipartOverheadBytes,
        long absoluteBodyBytes,
        long imageUploadBytes,
        long transferUploadBytes
    ) {
        long overhead = positive(multipartOverheadBytes, "multipart overhead");
        long defaultMaximum = positive(defaultBodyBytes, "default request-body limit");
        long imageMaximum = add(
            positive(imageUploadBytes, "image upload limit"),
            overhead
        );
        long transferMaximum = add(
            positive(transferUploadBytes, "transfer upload limit"),
            overhead
        );
        long absoluteMaximum = positive(absoluteBodyBytes, "absolute request-body limit");
        if (absoluteMaximum < defaultMaximum
            || absoluteMaximum < imageMaximum
            || absoluteMaximum < transferMaximum) {
            throw new IllegalStateException(
                "The absolute request-body limit must cover default, image, and transfer request limits"
            );
        }
        return new RequestBodyLimitPolicy(defaultMaximum, imageMaximum, transferMaximum);
    }

    private static long positive(long value, String description) {
        if (value <= 0) {
            throw new IllegalStateException(description + " must be positive");
        }
        return value;
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Configured request-body limit is too large", exception);
        }
    }
}
