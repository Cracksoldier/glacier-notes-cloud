package com.glaciernotes.cloud.application.transfer;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.TransferJobEntity;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.glaciernotes.cloud.application.transfer.TransferModels.*;

@ApplicationScoped
public class TransferService {
    private final TransferJobStore jobs;
    private final GlacierConfiguration configuration;
    private final IdGenerator ids;
    private final TimeProvider clock;
    private final EntityManager em;

    public TransferService(TransferJobStore jobs, GlacierConfiguration configuration,
                           IdGenerator ids, TimeProvider clock, EntityManager em) {
        this.jobs = jobs; this.configuration = configuration; this.ids = ids; this.clock = clock; this.em = em;
    }

    @PostConstruct
    void initialize() {
        try { Files.createDirectories(configuration.transfer().temporaryRoot()); }
        catch (IOException failure) { throw new IllegalStateException("Could not create transfer directory", failure); }
    }

    @Transactional
    public JobView createExport(UUID actor, ExportCommand request) {
        if (!exportsEnabled()) throw new ForbiddenException();
        String scope = request == null || request.scope() == null ? "" : request.scope().toUpperCase();
        if (!List.of("ALL", "NOTEBOOK", "NOTE").contains(scope)) throw new ClientErrorException(422);
        if (scope.equals("ALL") && request.resourceId() != null || !scope.equals("ALL") && request.resourceId() == null)
            throw new ClientErrorException(422);
        if (scope.equals("NOTEBOOK") && count("notebooks", actor, request.resourceId()) == 0) throw new NotFoundException();
        if (scope.equals("NOTE") && count("notes", actor, request.resourceId()) == 0) throw new NotFoundException();
        UUID id = ids.nextId();
        Path output = path(id, ".glacier.json");
        var now = clock.now();
        var job = TransferJobEntity.export(id, actor, scope, request.resourceId(), output.toString(), now,
            now.plus(Duration.ofHours(configuration.transfer().retentionHours())));
        em.persist(job);
        return view(job);
    }

    public JobView createImport(UUID actor, UUID target, boolean blind, FileUpload upload) {
        if (upload == null) throw new ClientErrorException(422);
        Path input = null;
        try {
            long size = Files.size(upload.uploadedFile());
            if (size <= 0) throw new ClientErrorException(422);
            if (size > configuration.transfer().maximumUploadBytes()) throw new ClientErrorException(413);
            requireTarget(target);
            UUID id = ids.nextId();
            input = path(id, ".upload");
            Files.copy(upload.uploadedFile(), input, StandardCopyOption.REPLACE_EXISTING);
            var now = clock.now();
            var job = TransferJobEntity.imported(id, actor, target, blind, input.toString(),
                cleanName(upload.fileName()), size, now,
                now.plus(Duration.ofHours(configuration.transfer().retentionHours())));
            jobs.persist(job);
            return view(job);
        } catch (ClientErrorException failure) {
            deletePath(input);
            throw failure;
        } catch (IOException failure) {
            deletePath(input);
            throw new jakarta.ws.rs.ServiceUnavailableException();
        } catch (RuntimeException failure) {
            deletePath(input);
            throw failure;
        }
    }

    public JobView createImport(UUID actor, UUID target, boolean blind, InputStream upload, String fileName) {
        if (upload == null) throw new ClientErrorException(422);
        requireTarget(target);
        UUID id = ids.nextId();
        Path input = path(id, ".upload");
        long size = 0;
        try (upload; OutputStream output = Files.newOutputStream(input)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = upload.read(buffer)) != -1) {
                size += read;
                if (size > configuration.transfer().maximumUploadBytes()) {
                    delete(input.toString());
                    throw new ClientErrorException(413);
                }
                output.write(buffer, 0, read);
            }
            if (size == 0) {
                delete(input.toString());
                throw new ClientErrorException(422);
            }
            var now = clock.now();
            var job = TransferJobEntity.imported(id, actor, target, blind, input.toString(),
                cleanName(fileName), size, now, now.plus(Duration.ofHours(configuration.transfer().retentionHours())));
            jobs.persist(job);
            return view(job);
        } catch (ClientErrorException failure) {
            throw failure;
        } catch (IOException failure) {
            delete(input.toString());
            throw new jakarta.ws.rs.ServiceUnavailableException();
        } catch (RuntimeException failure) {
            delete(input.toString());
            throw failure;
        }
    }

    public JobView get(UUID id, UUID actor, String kind, boolean admin) {
        return view(jobs.requireForUser(id, actor, kind, admin));
    }

    public JobView apply(UUID id, UUID actor, boolean admin, ApplyCommand request) {
        if (request == null || request.strategy() == null) throw new ClientErrorException(422);
        jobs.queueApply(id, actor, admin, request.strategy().toUpperCase());
        return get(id, actor, "IMPORT", admin);
    }

    public void cancel(UUID id, UUID actor, String kind, boolean admin) {
        var job = jobs.requestCancel(id, actor, kind, admin);
        if (!job.state().equals("RUNNING")) delete(job.temporaryPath());
    }

    public TransferJobEntity downloadable(UUID id, UUID actor) {
        var job = jobs.requireForUser(id, actor, "EXPORT", false);
        if (!job.state().equals("SUCCEEDED")) throw new ClientErrorException(409);
        if (!Files.isRegularFile(Path.of(job.temporaryPath()))) throw new NotFoundException();
        return job;
    }

    public JobView view(TransferJobEntity job) {
        Map<String, Long> value = job.counts();
        Counts counts = value == null ? null : new Counts(value.getOrDefault("notebooks", 0L),
            value.getOrDefault("notes", 0L), value.getOrDefault("labels", 0L),
            value.getOrDefault("images", 0L), value.getOrDefault("checklistItems", 0L));
        String download = job.kind().equals("EXPORT") && job.state().equals("SUCCEEDED")
            ? "/api/v1/exports/" + job.id() + "/download" : null;
        return new JobView(job.id(), job.kind(), job.state(), job.phase(), counts,
            job.hasConflicts(), job.quotaImpactBytes(), job.errors(), download,
            utc(job.createdAt()), utc(job.completedAt()), utc(job.expiresAt()));
    }

    private boolean exportsEnabled() {
        return (Boolean) em.createNativeQuery("select user_exports_enabled from instance_settings where singleton_key = 1")
            .getSingleResult();
    }
    private long count(String table, UUID owner, UUID id) {
        return ((Number) em.createNativeQuery("select count(*) from " + table + " where owner_id = :owner and id = :id")
            .setParameter("owner", owner).setParameter("id", id).getSingleResult()).longValue();
    }
    @Transactional void requireTarget(UUID target) {
        long count = em.createQuery("select count(u) from UserEntity u where u.id = :id and u.status <> 'DELETED'", Long.class)
            .setParameter("id", target).getSingleResult();
        if (count == 0) throw new NotFoundException();
    }
    private Path path(UUID id, String suffix) { return configuration.transfer().temporaryRoot().resolve(id + suffix); }
    private String cleanName(String value) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\r\\n]", "");
        return clean.substring(0, Math.min(512, clean.length()));
    }
    private OffsetDateTime utc(java.time.Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private void deletePath(Path value) { if (value != null) delete(value.toString()); }
    void delete(String value) { if (value != null) try { Files.deleteIfExists(Path.of(value)); } catch (IOException ignored) {} }
}
