package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.generated.api.AdministrationApi;
import com.glaciernotes.cloud.generated.model.AdminStatus;
import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
import com.glaciernotes.cloud.application.lifecycle.LifecycleEmailService;
import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.application.operations.BackupService;
import com.glaciernotes.cloud.application.operations.InstanceLogoService;
import com.glaciernotes.cloud.application.operations.JobLeaseRepository;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.application.transfer.TransferModels.ApplyCommand;
import com.glaciernotes.cloud.application.transfer.TransferService;
import com.glaciernotes.cloud.generated.model.*;
import com.glaciernotes.cloud.security.CookieManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.core.Context;
import org.jboss.logging.MDC;

import java.io.InputStream;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@RolesAllowed("ADMIN")
public class AdministrationResource implements AdministrationApi {
    private final LifecycleService lifecycle;
    private final SecurityIdentity identity;
    private final CookieManager cookies;
    private final BinaryAssetStorage imageStorage;
    private final TransferService transfers;
    private final AuditService audit;
    private final BackupService backups;
    private final InstanceLogoService logo;
    private final LifecycleEmailService email;
    private final JobLeaseRepository jobs;
    private final EntityManager entityManager;
    private final GlacierConfiguration configuration;
    private final String applicationVersion;
    private final boolean metricsEnabled;

    @Context
    HttpServerResponse response;

    public AdministrationResource(LifecycleService lifecycle, SecurityIdentity identity, CookieManager cookies,
                                  BinaryAssetStorage imageStorage, TransferService transfers,
                                  AuditService audit, BackupService backups, InstanceLogoService logo,
                                  LifecycleEmailService email, JobLeaseRepository jobs,
                                  EntityManager entityManager, GlacierConfiguration configuration,
                                  @ConfigProperty(name = "quarkus.application.version", defaultValue = "0.1.0")
                                  String applicationVersion,
                                  @ConfigProperty(name = "quarkus.micrometer.enabled", defaultValue = "true")
                                  boolean metricsEnabled) {
        this.lifecycle = lifecycle;
        this.identity = identity;
        this.cookies = cookies;
        this.imageStorage = imageStorage;
        this.transfers = transfers;
        this.audit = audit;
        this.backups = backups;
        this.logo = logo;
        this.email = email;
        this.jobs = jobs;
        this.entityManager = entityManager;
        this.configuration = configuration;
        this.applicationVersion = applicationVersion;
        this.metricsEnabled = metricsEnabled;
    }

    @Override
    public AdminStatus getAdminStatus() {
        return new AdminStatus()
            .service(AdminStatus.ServiceEnum.GLACIER_NOTES_CLOUD)
            .status(AdminStatus.StatusEnum.OK)
            .apiVersion(AdminStatus.ApiVersionEnum.V1)
            .applicationVersion(applicationVersion)
            .buildIdentifier(configuration.backup().buildIdentifier())
            .database(AdminStatus.DatabaseEnum.UP)
            .imageStorageBackend(AdminStatus.ImageStorageBackendEnum.fromValue(imageStorage.backend()))
            .imageStorage(imageStorage.healthy() ? AdminStatus.ImageStorageEnum.UP : AdminStatus.ImageStorageEnum.DOWN)
            .smtp(email.status()).backupEnabled(backups.enabled()).metricsEnabled(metricsEnabled)
            .jobsHealthy(jobs.healthy());
    }

    @Override
    public AdminUserPage listUsers(String query, String role, String status, String cursor, Integer limit) {
        return lifecycle.listUsers(query, role, status, cursor, limit);
    }

    @Override
    public AdminUser getUser(UUID userId) { return lifecycle.getUser(userId); }

    @Override
    public AdminUser updateUser(UUID userId, AdminUserUpdate update) {
        var result = lifecycle.updateUser(userId, update, actor(), correlationId());
        if (userId.equals(actor()) && !"ADMIN".equals(result.getRole().toString())) cookies.clear(response);
        return result;
    }

    @Override
    public void activateUser(UUID userId) { lifecycle.activate(userId, actor(), correlationId()); }

    @Override
    public void deactivateUser(UUID userId) {
        lifecycle.deactivate(userId, actor(), correlationId());
        if (userId.equals(actor())) cookies.clear(response);
    }

    @Override
    public void unlockUser(UUID userId) { lifecycle.unlock(userId, actor(), correlationId()); }

    @Override
    public ResetLink createAdministrativePasswordReset(UUID userId) {
        return lifecycle.administrativeReset(userId, actor(), correlationId());
    }

    @Override
    public void revokeUserSessions(UUID userId) {
        lifecycle.revokeSessions(userId, actor(), correlationId());
        if (userId.equals(actor())) cookies.clear(response);
    }

    @Override
    public AdminUser scheduleUserDeletion(UUID userId, AdminDeletionRequest request) {
        var result = lifecycle.scheduleDeletion(userId, request, actor(), correlationId());
        if (userId.equals(actor())) cookies.clear(response);
        return result;
    }

    @Override
    public AdminUser restoreUserDeletion(UUID userId) {
        return lifecycle.restoreDeletion(userId, actor(), correlationId());
    }

    @Override
    public InvitationPage listInvitations(String status, String cursor, Integer limit) {
        return lifecycle.listInvitations(status, cursor, limit);
    }

    @Override
    public InvitationDelivery createInvitation(InvitationCreateRequest request) {
        return lifecycle.createInvitation(request, actor(), correlationId());
    }

    @Override
    public InvitationDelivery resendInvitation(UUID invitationId) {
        return lifecycle.resendInvitation(invitationId, actor(), correlationId());
    }

    @Override
    public void revokeInvitation(UUID invitationId) {
        lifecycle.revokeInvitation(invitationId, actor(), correlationId());
    }

    @Override
    public AdminSettings getAdminSettings() { return lifecycle.settingsModel(); }

    @Override
    public AdminSettings updateAdminSettings(AdminSettingsUpdate update) {
        return lifecycle.updateSettings(update, actor(), correlationId());
    }

    @Override
    public AdminSettings updateInstanceLogo(InputStream file) {
        logo.replace(file);
        audit.record("INSTANCE_LOGO_CHANGED", actor(), null, "INSTANCE_SETTINGS", null,
            "SUCCESS", correlationId(), java.util.Map.of("action", "replace"));
        return lifecycle.settingsModel();
    }

    @Override
    public void deleteInstanceLogo() {
        logo.delete();
        audit.record("INSTANCE_LOGO_CHANGED", actor(), null, "INSTANCE_SETTINGS", null,
            "SUCCESS", correlationId(), java.util.Map.of("action", "remove"));
    }

    @Override
    public SmtpStatus testSmtp() {
        if (!email.configured()) throw com.glaciernotes.cloud.application.operations.OperationalFailure.smtpNotConfigured();
        String recipient = entityManager.createQuery(
                "select u.email from UserEntity u where u.id=:id", String.class)
            .setParameter("id", actor()).getSingleResult();
        boolean sent = email.sendTest(recipient);
        audit.record("SMTP_TEST", actor(), null, "SMTP", null, sent ? "SUCCESS" : "FAILURE",
            correlationId(), java.util.Map.of());
        return email.status();
    }

    @Override
    public AuditEventPage listAuditEvents(String eventType, String result, OffsetDateTime from,
                                          OffsetDateTime to, String cursor, Integer limit) {
        return audit.list(eventType, result, from, to, cursor, limit);
    }

    @Override
    public File exportAuditEvents(String format, String eventType, String result,
                                  OffsetDateTime from, OffsetDateTime to) {
        File file = audit.export(format, eventType, result, from, to);
        response.putHeader("Content-Type", "csv".equals(format) ? "text/csv" : "application/json");
        response.putHeader("Content-Disposition", "attachment; filename=\"glacier-audit." + format + "\"");
        return file;
    }

    @Override
    public BackupJob createBackup() {
        BackupJob job = backups.create(actor());
        audit.record("BACKUP_INITIATED", actor(), null, "BACKUP", job.getId(), "SUCCESS",
            correlationId(), java.util.Map.of());
        return job;
    }

    @Override
    public BackupJobPage listBackups(String cursor, Integer limit) {
        return backups.list(cursor, limit);
    }

    @Override
    public BackupJob getBackup(UUID backupId) {
        return backups.get(backupId);
    }

    @Override
    public TransferJob createAdminImport(UUID target, InputStream file) {
        return TransferJobMapper.toModel(
            transfers.createImport(actor(), target, true, file, "import.glacier.json")
        );
    }

    @Override
    public TransferJob getAdminImport(UUID id) {
        return TransferJobMapper.toModel(transfers.get(id, actor(), "IMPORT", true));
    }

    @Override
    public TransferJob applyAdminImport(UUID id, ImportApplyRequest request) {
        String strategy = request == null || request.getStrategy() == null ? null : request.getStrategy().toString();
        return TransferJobMapper.toModel(transfers.apply(id, actor(), true, new ApplyCommand(strategy)));
    }

    @Override
    public void cancelAdminImport(UUID id) {
        transfers.cancel(id, actor(), "IMPORT", true);
    }

    private UUID actor() { return UUID.fromString(identity.getPrincipal().getName()); }
    private String correlationId() { return Objects.toString(MDC.get("correlationId"), "unavailable"); }
}
