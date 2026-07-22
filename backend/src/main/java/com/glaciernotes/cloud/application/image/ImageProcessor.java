package com.glaciernotes.cloud.application.image;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ImageProcessor {
    private static final Set<String> FORMATS = Set.of("png", "jpeg", "jpg", "webp");
    private final GlacierConfiguration config;
    private final Semaphore processingSlots = new Semaphore(2, true);

    public ImageProcessor(GlacierConfiguration config) { this.config = config; }

    public ProcessedImage process(Path upload, long storedLimit) {
        boolean acquired = false;
        try {
            acquired = processingSlots.tryAcquire(config.images().processingTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) throw ImageFailure.invalid("IMAGE_PROCESSING_TIMEOUT", "Image processing is currently busy; retry the upload.");
            return processInternal(upload, storedLimit);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ImageFailure.invalid("IMAGE_PROCESSING_TIMEOUT", "Image processing was interrupted.");
        } finally {
            if (acquired) processingSlots.release();
        }
    }

    private ProcessedImage processInternal(Path upload, long storedLimit) {
        try {
            long inputSize = Files.size(upload);
            if (inputSize <= 0) throw ImageFailure.invalid("IMAGE_INVALID", "The uploaded file is empty.");
            if (inputSize > config.images().maximumUploadBytes())
                throw ImageFailure.tooLarge("IMAGE_TOO_LARGE", "The upload exceeds the 40 MB processing limit.");
            BufferedImage source;
            String inputFormat;
            try (ImageInputStream input = ImageIO.createImageInputStream(upload.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw ImageFailure.invalid("IMAGE_TYPE_UNSUPPORTED", "Only PNG, JPEG, and WebP images are supported.");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    inputFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!FORMATS.contains(inputFormat)) throw ImageFailure.invalid("IMAGE_TYPE_UNSUPPORTED", "Only PNG, JPEG, and WebP images are supported.");
                    int width = reader.getWidth(0); int height = reader.getHeight(0);
                    if ((long) width * height > config.images().maximumPixels())
                        throw ImageFailure.invalid("IMAGE_DIMENSIONS_EXCEEDED", "The image exceeds the 40 megapixel processing limit.");
                    source = reader.read(0);
                } finally { reader.dispose(); }
            }
            if (source == null) throw ImageFailure.invalid("IMAGE_INVALID", "The image could not be decoded.");
            boolean alpha = source.getColorModel().hasAlpha();
            String sourceMimeType = switch (inputFormat) {
                case "png" -> "image/png";
                case "jpeg", "jpg" -> "image/jpeg";
                case "webp" -> "image/webp";
                default -> throw ImageFailure.invalid("IMAGE_TYPE_UNSUPPORTED", "Only PNG, JPEG, and WebP images are supported.");
            };
            String format = alpha ? "png" : "jpeg";
            String mime = alpha ? "image/png" : "image/jpeg";
            BufferedImage normalized = scaleToEdge(source, config.images().maximumEdge(), alpha);
            Path main = Files.createTempFile("glacier-image-", "." + format);
            Path thumbnail = Files.createTempFile("glacier-thumbnail-", "." + format);
            try {
                write(normalized, format, main);
                while (Files.size(main) > storedLimit && Math.max(normalized.getWidth(), normalized.getHeight()) > 320) {
                    normalized = scale(normalized, Math.max(320, (int) (Math.max(normalized.getWidth(), normalized.getHeight()) * 0.82)), alpha);
                    write(normalized, format, main);
                }
                if (Files.size(main) > storedLimit) throw ImageFailure.tooLarge("IMAGE_TOO_LARGE", "The normalized image exceeds the configured image limit.");
                BufferedImage thumb = scaleToEdge(normalized, config.images().thumbnailEdge(), alpha);
                write(thumb, format, thumbnail);
                return new ProcessedImage(main, thumbnail, sourceMimeType, mime, Files.size(main), normalized.getWidth(), normalized.getHeight(),
                    Files.size(thumbnail), thumb.getWidth(), thumb.getHeight(), sha256(main));
            } catch (RuntimeException | IOException failure) {
                Files.deleteIfExists(main); Files.deleteIfExists(thumbnail); throw failure;
            }
        } catch (ImageFailure failure) { throw failure; }
        catch (IOException failure) { throw ImageFailure.invalid("IMAGE_INVALID", "The image could not be processed."); }
    }

    private BufferedImage scaleToEdge(BufferedImage source, int edge, boolean alpha) {
        if (Math.max(source.getWidth(), source.getHeight()) <= edge) return convert(source, alpha);
        return scale(source, edge, alpha);
    }

    private BufferedImage scale(BufferedImage source, int edge, boolean alpha) {
        double ratio = (double) edge / Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            if (!alpha) { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height); }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return target;
    }

    private BufferedImage convert(BufferedImage source, boolean alpha) {
        return scale(source, Math.max(source.getWidth(), source.getHeight()), alpha);
    }

    private void write(BufferedImage image, String format, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("No image writer for " + format);
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg") && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); params.setCompressionQuality(0.85f);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally { writer.dispose(); }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record ProcessedImage(Path main, Path thumbnail, String sourceMimeType, String mimeType, long byteSize,
                                 int width, int height, long thumbnailByteSize,
                                 int thumbnailWidth, int thumbnailHeight, String hash) implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(main); } catch (IOException ignored) {}
            try { Files.deleteIfExists(thumbnail); } catch (IOException ignored) {}
        }
    }
}
