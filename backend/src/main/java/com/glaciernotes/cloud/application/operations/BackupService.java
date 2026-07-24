package com.glaciernotes.cloud.application.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.BackupJob;
import com.glaciernotes.cloud.generated.model.BackupJobPage;
import com.glaciernotes.cloud.generated.model.PageMetadata;
import com.glaciernotes.cloud.persistence.entity.BackupJobEntity;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class BackupService {
    private static final Logger LOG = Logger.getLogger(BackupService.class);
    private static final Duration DUMP_TIMEOUT = Duration.ofMinutes(30);
    private final EntityManager entityManager;
    private final GlacierConfiguration configuration;
    private final IdGenerator ids;
    private final TimeProvider time;
    private final LifecycleService lifecycle;
    private final BinaryAssetStorage storage;
    private final ObjectMapper objectMapper;
    private final AgroalDataSource dataSource;
    private final BackupJobStatusService status;
    private final String applicationVersion;

    public BackupService(EntityManager entityManager, GlacierConfiguration configuration,
                         IdGenerator ids, TimeProvider time, LifecycleService lifecycle,
                         BinaryAssetStorage storage, ObjectMapper objectMapper,
                         AgroalDataSource dataSource, BackupJobStatusService status,
                         @ConfigProperty(name = "quarkus.application.version", defaultValue = "0.1.0")
                         String applicationVersion) {
        this.entityManager = entityManager;
        this.configuration = configuration;
        this.ids = ids;
        this.time = time;
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.status = status;
        this.applicationVersion = applicationVersion;
    }

    public boolean enabled() { return configuration.backup().enabled(); }

    @Transactional
    public BackupJob create(UUID actor) {
        requireEnabled();
        var entity = new BackupJobEntity(ids.nextId(), actor, time.now());
        entityManager.persist(entity);
        return model(entity);
    }

    @Transactional
    public BackupJob get(UUID id) {
        requireEnabled();
        var entity = entityManager.find(BackupJobEntity.class, id);
        if (entity == null) throw OperationalFailure.notFound();
        return model(entity);
    }

    @Transactional
    public BackupJobPage list(String cursor, int limit) {
        requireEnabled();
        int offset = decode(cursor);
        List<BackupJobEntity> rows = entityManager.createQuery(
                "select b from BackupJobEntity b order by b.createdAt desc,b.id desc", BackupJobEntity.class)
            .setFirstResult(offset).setMaxResults(limit + 1).getResultList();
        boolean hasNext = rows.size() > limit;
        List<BackupJob> items = rows.stream().limit(limit).map(this::model).toList();
        return new BackupJobPage().items(items).page(new PageMetadata().size(items.size())
            .hasNext(hasNext).nextCursor(hasNext ? encode(offset + items.size()) : null));
    }

    @Transactional
    public UUID claim() {
        if (!enabled()) return null;
        List<BackupJobEntity> rows = entityManager.createQuery(
                "select b from BackupJobEntity b where b.state='QUEUED' order by b.createdAt",
                BackupJobEntity.class)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1)
            .setHint("jakarta.persistence.lock.timeout", -2).getResultList();
        if (rows.isEmpty()) return null;
        rows.getFirst().running(time.now());
        return rows.getFirst().id();
    }

    public void execute(UUID id) {
        Path temporary = null;
        try {
            Path directory = safeDirectory();
            temporary = Files.createTempDirectory(directory, ".backup-" + id + "-");
            Path dump = temporary.resolve("database.dump");
            dumpDatabase(dump);
            Path archiveTemporary = temporary.resolve("glacier-notes-" + id + ".zip");
            writeArchive(archiveTemporary, dump, id);
            Path target = directory.resolve("glacier-notes-" + id + ".zip").normalize();
            if (!target.getParent().equals(directory)) throw new IllegalStateException("Unsafe backup target");
            Files.move(archiveTemporary, target, StandardCopyOption.ATOMIC_MOVE);
            restrictive(target);
            complete(id, target.getFileName().toString(), Files.size(target), checksum(target));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.errorf(failure, "Backup job failed jobId=%s category=%s", id,
                failure.getClass().getSimpleName());
            fail(id, failure.getClass().getSimpleName());
        } finally {
            if (temporary != null) deleteTree(temporary);
        }
    }

    void complete(UUID id, String output, long size, String checksum) {
        status.complete(id, output, size, checksum);
    }

    void fail(UUID id, String category) {
        status.fail(id, category);
    }

    private void dumpDatabase(Path output) throws IOException, InterruptedException {
        var factory = dataSource.getConfiguration().connectionPoolConfiguration()
            .connectionFactoryConfiguration();
        String jdbc = factory.jdbcUrl();
        String url = jdbc.startsWith("jdbc:") ? jdbc.substring(5) : jdbc;
        var process = new ProcessBuilder("pg_dump", "--format=custom", "--no-owner", "--no-privileges",
            "--file=" + output, "--dbname=" + url, "--username=" + factory.principal().getName())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        String password = System.getenv("GLACIER_DATABASE_PASSWORD");
        if (password != null) process.environment().put("PGPASSWORD", password);
        awaitDump(process.start(), DUMP_TIMEOUT);
    }

    void awaitDump(Process running, Duration timeout) throws IOException, InterruptedException {
        try {
            if (!running.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(running);
                throw new IOException("pg_dump timed out");
            }
        } catch (InterruptedException interrupted) {
            terminate(running);
            throw interrupted;
        }
        if (running.exitValue() != 0) throw new IOException("pg_dump failed");
    }

    private void terminate(Process running) {
        running.destroy();
        try {
            if (!running.waitFor(5, TimeUnit.SECONDS)) {
                running.destroyForcibly();
                running.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            running.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void writeArchive(Path output, Path dump, UUID id) throws IOException {
        Map<String, String> checksums = new java.util.LinkedHashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            add(zip, dump, "database.dump", checksums);
            byte[] settings = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(lifecycle.settingsModel());
            add(zip, settings, "instance-settings.json", checksums);
            List<Object[]> assets = entityManager.createNativeQuery("""
                select storage_key,thumbnail_storage_key from image_assets order by owner_id,id
                """).getResultList();
            if (!"POSTGRESQL".equals(storage.backend())) {
                for (Object[] asset : assets) {
                    addStored(zip, asset[0].toString(), checksums);
                    if (asset[1] != null) addStored(zip, asset[1].toString(), checksums);
                }
            }
            Map<String, Object> manifest = Map.of(
                "backupId", id.toString(),
                "createdAt", time.now().toString(),
                "applicationVersion", applicationVersion,
                "buildIdentifier", configuration.backup().buildIdentifier(),
                "schemaVersion", 12,
                "imageStorageBackend", storage.backend(),
                "checksums", checksums
            );
            add(zip, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest),
                "manifest.json", new java.util.LinkedHashMap<>());
        }
    }

    private void addStored(ZipOutputStream zip, String key, Map<String, String> checksums) throws IOException {
        String safe = key.replace('\\', '/');
        if (safe.startsWith("/") || safe.contains("../")) throw new IOException("Unsafe image storage key");
        var object = storage.load(key);
        try (InputStream input = new BufferedInputStream(object.stream())) {
            add(zip, input.readAllBytes(), "images/" + safe, checksums);
        }
    }

    private void add(ZipOutputStream zip, Path path, String name, Map<String, String> checksums) throws IOException {
        MessageDigest digest = sha256();
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                zip.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
        }
        zip.closeEntry();
        checksums.put(name, HexFormat.of().formatHex(digest.digest()));
    }

    private void add(ZipOutputStream zip, byte[] bytes, String name, Map<String, String> checksums) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
        checksums.put(name, checksum(bytes));
    }

    private Path safeDirectory() throws IOException {
        Path directory = configuration.backup().directory().toAbsolutePath().normalize();
        Files.createDirectories(directory);
        restrictive(directory);
        return directory;
    }

    private void restrictive(Path path) {
        try {
            Files.setPosixFilePermissions(path, Files.isDirectory(path)
                ? PosixFilePermissions.fromString("rwx------")
                : PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {}
    }

    private String checksum(Path value) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(value))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private String checksum(byte[] value) {
        return HexFormat.of().formatHex(sha256().digest(value));
    }
    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private void requireEnabled() { if (!enabled()) throw OperationalFailure.featureDisabled(); }
    private String encode(int value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(value).getBytes());
    }
    private int decode(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(new String(Base64.getUrlDecoder().decode(value))); }
        catch (RuntimeException failure) { throw OperationalFailure.invalid("Backup cursor is invalid."); }
    }
    private BackupJob model(BackupJobEntity value) {
        return new BackupJob().id(value.id()).createdByUserId(value.createdBy())
            .state(BackupJob.StateEnum.fromValue(value.state()))
            .createdAt(value.createdAt().atOffset(ZoneOffset.UTC))
            .startedAt(value.startedAt() == null ? null : value.startedAt().atOffset(ZoneOffset.UTC))
            .completedAt(value.completedAt() == null ? null : value.completedAt().atOffset(ZoneOffset.UTC))
            .outputIdentifier(value.outputIdentifier()).byteSize(value.byteSize())
            .checksum(value.checksum()).errorMessage(value.errorMessage());
    }
    private void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
