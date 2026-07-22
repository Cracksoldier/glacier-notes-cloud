package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.generated.api.AdministrationApi;
import com.glaciernotes.cloud.generated.model.AdminStatus;
import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.application.transfer.TransferModels.ApplyCommand;
import com.glaciernotes.cloud.application.transfer.TransferModels.JobView;
import com.glaciernotes.cloud.application.transfer.TransferService;
import com.glaciernotes.cloud.generated.model.*;
import com.glaciernotes.cloud.security.CookieManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.core.Context;
import org.jboss.logging.MDC;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RolesAllowed("ADMIN")
public class AdministrationResource implements AdministrationApi {
    private final LifecycleService lifecycle;
    private final SecurityIdentity identity;
    private final CookieManager cookies;
    private final BinaryAssetStorage imageStorage;
    private final TransferService transfers;

    @Context
    HttpServerResponse response;

    public AdministrationResource(LifecycleService lifecycle, SecurityIdentity identity, CookieManager cookies,
                                  BinaryAssetStorage imageStorage, TransferService transfers) {
        this.lifecycle = lifecycle;
        this.identity = identity;
        this.cookies = cookies;
        this.imageStorage = imageStorage;
        this.transfers = transfers;
    }

    @Override
    public AdminStatus getAdminStatus() {
        return new AdminStatus()
            .service(AdminStatus.ServiceEnum.GLACIER_NOTES_CLOUD)
            .status(AdminStatus.StatusEnum.OK)
            .apiVersion(AdminStatus.ApiVersionEnum.V1)
            .database(AdminStatus.DatabaseEnum.UP)
            .imageStorageBackend(AdminStatus.ImageStorageBackendEnum.fromValue(imageStorage.backend()))
            .imageStorage(imageStorage.healthy() ? AdminStatus.ImageStorageEnum.UP : AdminStatus.ImageStorageEnum.DOWN);
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
    public TransferJob createAdminImport(UUID target, InputStream file) {
        return transferJob(transfers.createImport(actor(), target, true, file, "import.glacier.json"));
    }

    @Override
    public TransferJob getAdminImport(UUID id) {
        return transferJob(transfers.get(id, actor(), "IMPORT", true));
    }

    @Override
    public TransferJob applyAdminImport(UUID id, ImportApplyRequest request) {
        String strategy = request == null || request.getStrategy() == null ? null : request.getStrategy().toString();
        return transferJob(transfers.apply(id, actor(), true, new ApplyCommand(strategy)));
    }

    @Override
    public void cancelAdminImport(UUID id) {
        transfers.cancel(id, actor(), "IMPORT", true);
    }

    private TransferJob transferJob(JobView view) {
        TransferCounts counts = view.counts() == null ? null : new TransferCounts(
            view.counts().notebooks(), view.counts().notes(), view.counts().labels(),
            view.counts().images(), view.counts().checklistItems());
        return new TransferJob(view.id(), TransferJob.KindEnum.fromValue(view.kind()),
            TransferJob.StateEnum.fromValue(view.state()), view.createdAt(), view.expiresAt())
            .phase(view.phase() == null ? null : TransferJob.PhaseEnum.fromValue(view.phase()))
            .counts(counts)
            .hasConflicts(view.hasConflicts())
            .quotaImpactBytes(view.quotaImpactBytes())
            .errors(view.errors())
            .downloadUrl(view.downloadUrl())
            .completedAt(view.completedAt());
    }

    private UUID actor() { return UUID.fromString(identity.getPrincipal().getName()); }
    private String correlationId() { return Objects.toString(MDC.get("correlationId"), "unavailable"); }
}
