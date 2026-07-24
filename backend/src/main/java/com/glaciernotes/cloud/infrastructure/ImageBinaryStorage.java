package com.glaciernotes.cloud.infrastructure;

import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class ImageBinaryStorage implements BinaryAssetStorage {
    private final GlacierConfiguration configuration;
    private final DataSource dataSource;
    private String backend;
    private S3Client s3;

    @Inject
    public ImageBinaryStorage(GlacierConfiguration configuration, DataSource dataSource) {
        this.configuration = configuration;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void initialize() {
        backend = configuration.images().backend().toUpperCase(Locale.ROOT);
        if (!backend.matches("FILESYSTEM|POSTGRESQL|S3")) {
            throw new IllegalStateException("GLACIER_IMAGE_BACKEND must be FILESYSTEM, POSTGRESQL, or S3");
        }
        if (backend.equals("FILESYSTEM")) {
            try { Files.createDirectories(configuration.images().filesystemRoot()); }
            catch (IOException exception) { throw new IllegalStateException("Could not create image storage directory", exception); }
        }
        if (backend.equals("S3")) initializeS3();
    }

    private void initializeS3() {
        var value = configuration.images().s3();
        var builder = S3Client.builder().region(Region.of(value.region()))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(value.pathStyle()).build());
        value.endpoint().filter(v -> !v.isBlank()).ifPresent(v -> builder.endpointOverride(URI.create(v)));
        String access = secret(value.accessKeyFile(), "S3 access key");
        String secret = secret(value.secretKeyFile(), "S3 secret key");
        builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)));
        s3 = builder.build();
    }

    private String secret(Optional<Path> file, String description) {
        if (file.isEmpty()) throw new IllegalStateException(description + " file is required for the S3 image backend");
        try {
            String value = Files.readString(file.orElseThrow()).strip();
            if (value.isBlank()) throw new IllegalStateException(description + " file is empty");
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + description + " file", exception);
        }
    }

    @Override
    public void store(String key, Path content, long length, String contentType) {
        switch (backend) {
            case "FILESYSTEM" -> storeFile(key, content);
            case "POSTGRESQL" -> storeDatabase(key, content, length, contentType);
            case "S3" -> storeS3(key, content, contentType);
            default -> throw new IllegalStateException("Unsupported image backend");
        }
    }

    private void storeFile(String key, Path content) {
        Path target = safePath(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { throw new ImageStorageException(exception); }
    }

    private void storeDatabase(String key, Path content, long length, String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 insert into image_asset_blobs(storage_key, content, content_length, content_type)
                 values (?, ?, ?, ?)
                 on conflict(storage_key) do update set content=excluded.content,
                   content_length=excluded.content_length,content_type=excluded.content_type
                 """ );
             InputStream input = Files.newInputStream(content)) {
            statement.setString(1, key); statement.setBinaryStream(2, input, length);
            statement.setLong(3, length); statement.setString(4, type); statement.executeUpdate();
        } catch (SQLException | IOException exception) { throw new ImageStorageException(exception); }
    }

    private void storeS3(String key, Path content, String type) {
        var builder = PutObjectRequest.builder().bucket(configuration.images().s3().bucket()).key(key).contentType(type);
        configuration.images().s3().serverSideEncryption().filter(v -> !v.isBlank())
            .ifPresent(builder::serverSideEncryption);
        try { s3.putObject(builder.build(), RequestBody.fromFile(content)); }
        catch (RuntimeException exception) { throw new ImageStorageException(exception); }
    }

    @Override
    public StoredObject load(String key) {
        try {
            return switch (backend) {
                case "FILESYSTEM" -> {
                    Path path = safePath(key);
                    yield new StoredObject(Files.newInputStream(path), Files.size(path), Files.probeContentType(path));
                }
                case "POSTGRESQL" -> loadDatabase(key);
                case "S3" -> {
                    var response = s3.getObject(GetObjectRequest.builder()
                        .bucket(configuration.images().s3().bucket()).key(key).build());
                    yield new StoredObject(response, response.response().contentLength(), response.response().contentType());
                }
                default -> throw new IllegalStateException("Unsupported image backend");
            };
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ImageStorageException storage) throw storage;
            throw new ImageStorageException(exception);
        }
    }

    private StoredObject loadDatabase(String key) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                "select content, content_length, content_type from image_asset_blobs where storage_key = ?");
            statement.setString(1, key);
            result = statement.executeQuery();
            if (!result.next()) throw new ImageStorageException("Stored image is missing");
            InputStream stream = result.getBinaryStream(1);
            Connection ownedConnection = connection;
            PreparedStatement ownedStatement = statement;
            ResultSet ownedResult = result;
            StoredObject stored = new StoredObject(new java.io.FilterInputStream(stream) {
                @Override public void close() throws IOException {
                    try { super.close(); ownedResult.close(); ownedStatement.close(); ownedConnection.close(); }
                    catch (SQLException exception) { throw new IOException(exception); }
                }
            }, result.getLong(2), result.getString(3));
            connection = null;
            statement = null;
            result = null;
            return stored;
        } catch (SQLException | RuntimeException exception) {
            close(result);
            close(statement);
            close(connection);
            if (exception instanceof ImageStorageException storageException) throw storageException;
            throw new ImageStorageException(exception);
        }
    }

    private void close(AutoCloseable resource) {
        if (resource != null) try { resource.close(); } catch (Exception ignored) {}
    }

    @Override
    public void delete(String key) {
        try {
            switch (backend) {
                case "FILESYSTEM" -> Files.deleteIfExists(safePath(key));
                case "POSTGRESQL" -> { try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("delete from image_asset_blobs where storage_key = ?")) { s.setString(1, key); s.executeUpdate(); } }
                case "S3" -> s3.deleteObject(value -> value.bucket(configuration.images().s3().bucket()).key(key));
                default -> throw new IllegalStateException("Unsupported image backend");
            }
        } catch (IOException | SQLException | RuntimeException exception) { throw new ImageStorageException(exception); }
    }

    private Path safePath(String key) {
        Path root = configuration.images().filesystemRoot().toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Unsafe image storage key");
        return target;
    }

    @Override public boolean healthy() {
        try {
            return switch (backend) {
                case "FILESYSTEM" -> Files.isWritable(configuration.images().filesystemRoot());
                case "POSTGRESQL" -> { try (Connection c = dataSource.getConnection()) { yield c.isValid(2); } }
                case "S3" -> { s3.headBucket(v -> v.bucket(configuration.images().s3().bucket())); yield true; }
                default -> false;
            };
        } catch (Exception ignored) { return false; }
    }

    @Override public String backend() { return backend; }
    @PreDestroy void close() { if (s3 != null) s3.close(); }

    public static class ImageStorageException extends RuntimeException {
        public ImageStorageException(Throwable cause) { super(cause); }
        public ImageStorageException(String message) { super(message); }
    }
}
