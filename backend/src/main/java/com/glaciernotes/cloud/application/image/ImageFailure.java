package com.glaciernotes.cloud.application.image;

public final class ImageFailure extends RuntimeException {
    private final Reason reason;
    private final String code;

    private ImageFailure(Reason reason, String code, String message) {
        super(message); this.reason = reason; this.code = code;
    }

    public static ImageFailure invalid(String code, String message) { return new ImageFailure(Reason.INVALID, code, message); }
    public static ImageFailure tooLarge(String code, String message) { return new ImageFailure(Reason.TOO_LARGE, code, message); }
    public static ImageFailure quota() { return new ImageFailure(Reason.TOO_LARGE, "IMAGE_QUOTA_EXCEEDED", "The image storage quota would be exceeded."); }
    public static ImageFailure notFound() { return new ImageFailure(Reason.NOT_FOUND, "ENTITY_NOT_FOUND", "The requested image was not found."); }
    public static ImageFailure referenced() { return new ImageFailure(Reason.CONFLICT, "IMAGE_STILL_REFERENCED", "The image is still referenced by a note or retained version."); }
    public static ImageFailure unavailable() { return new ImageFailure(Reason.UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE", "Image storage is temporarily unavailable."); }
    public Reason reason() { return reason; }
    public String code() { return code; }
    public enum Reason { INVALID, TOO_LARGE, NOT_FOUND, CONFLICT, UNAVAILABLE }
}
