package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.application.operations.RequestAuditContext;
import com.glaciernotes.cloud.application.setup.IdentityNormalizer;
import com.glaciernotes.cloud.application.setup.PasswordPolicy;
import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.generated.model.*;
import com.glaciernotes.cloud.persistence.entity.*;
import com.glaciernotes.cloud.persistence.repository.EndpointRateLimiter;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import com.glaciernotes.cloud.persistence.repository.UserUsageRepository;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import com.glaciernotes.cloud.security.SessionTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.net.IDN;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class LifecycleService {
    private final EntityManager entityManager;
    private final IdentityNormalizer identities;
    private final PasswordPolicy passwordPolicy;
    private final PasswordVerifier passwordVerifier;
    private final PasswordManager passwordManager;
    private final AccountDeletionService deletion;
    private final SessionTokenService tokens;
    private final ClientKeyHasher keyHasher;
    private final EndpointRateLimiter rateLimiter;
    private final SessionRepository sessions;
    private final UserUsageRepository userUsage;
    private final LifecycleEmailService email;
    private final GlacierConfiguration configuration;
    private final TimeProvider time;
    private final IdGenerator ids;
    private final RequestAuditContext auditContext;

    public LifecycleService(EntityManager entityManager, IdentityNormalizer identities,
                            PasswordPolicy passwordPolicy, PasswordVerifier passwordVerifier,
                            PasswordManager passwordManager, AccountDeletionService deletion,
                            SessionTokenService tokens, ClientKeyHasher keyHasher,
                            EndpointRateLimiter rateLimiter, SessionRepository sessions,
                            UserUsageRepository userUsage, LifecycleEmailService email,
                            GlacierConfiguration configuration,
                            TimeProvider time, IdGenerator ids, RequestAuditContext auditContext) {
        this.entityManager = entityManager;
        this.identities = identities;
        this.passwordPolicy = passwordPolicy;
        this.passwordVerifier = passwordVerifier;
        this.passwordManager = passwordManager;
        this.deletion = deletion;
        this.tokens = tokens;
        this.keyHasher = keyHasher;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.userUsage = userUsage;
        this.email = email;
        this.configuration = configuration;
        this.time = time;
        this.ids = ids;
        this.auditContext = auditContext;
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public InvitationDelivery createInvitation(InvitationCreateRequest request, UUID actor,
                                                String correlationId) {
        rateLimiter.record("INVITATION_ADMIN", keyHasher.hash("invite-admin:" + actor), 20, Duration.ofHours(1));
        var normalized = normalizeInvitation(request);
        var settings = settings();
        var now = time.now();
        expirePendingInvitations(now);
        enforceDomain(normalized.emailNormalized(), settings.allowedEmailDomains());
        assertIdentityAvailable(normalized.emailNormalized(), normalized.usernameNormalized(), null);
        assertPendingInvitationAvailable(normalized.emailNormalized(), normalized.usernameNormalized(), null);

        var raw = tokens.newToken();
        var invitation = new InvitationEntity(ids.nextId(), normalized.email(), normalized.emailNormalized(),
            normalized.username(), normalized.usernameNormalized(), request.getRole().toString(), normalized.displayName(),
            tokens.hashInvitationToken(raw), actor, now, now.plus(Duration.ofHours(settings.invitationExpirationHours())));
        entityManager.persist(invitation);
        audit("INVITATION_CREATED", actor, null, "INVITATION", invitation.id(), correlationId,
            Map.of("delivery", email.configured() ? "email" : "manual"));
        entityManager.flush();
        return deliver(invitation, raw);
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public InvitationDelivery resendInvitation(UUID invitationId, UUID actor, String correlationId) {
        rateLimiter.record("INVITATION_ADMIN", keyHasher.hash("invite-admin:" + actor), 20, Duration.ofHours(1));
        var invitation = invitation(invitationId, LockModeType.PESSIMISTIC_WRITE);
        invitation.expireIfNeeded(time.now());
        if (!("PENDING".equals(invitation.status()) || "EXPIRED".equals(invitation.status()))) {
            throw LifecycleFailure.invalidState("Only pending or expired invitations can be regenerated.");
        }
        var raw = tokens.newToken();
        var now = time.now();
        invitation.regenerate(tokens.hashInvitationToken(raw), now,
            now.plus(Duration.ofHours(settings().invitationExpirationHours())));
        audit("INVITATION_REGENERATED", actor, null, "INVITATION", invitation.id(), correlationId, Map.of());
        entityManager.flush();
        return deliver(invitation, raw);
    }

    @Transactional
    public void revokeInvitation(UUID invitationId, UUID actor, String correlationId) {
        var invitation = invitation(invitationId, LockModeType.PESSIMISTIC_WRITE);
        invitation.expireIfNeeded(time.now());
        if (!"PENDING".equals(invitation.status())) {
            throw LifecycleFailure.invalidState("Only pending invitations can be revoked.");
        }
        invitation.revoke(time.now());
        audit("INVITATION_REVOKED", actor, null, "INVITATION", invitation.id(), correlationId, Map.of());
    }

    @Transactional
    public InvitationPage listInvitations(String status, String cursor, int limit) {
        var now = time.now();
        expirePendingInvitations(now);
        var offset = decodeCursor(cursor);
        var query = entityManager.createQuery("select i from InvitationEntity i "
                + "where (:status is null or i.status = :status) order by i.createdAt desc, i.id",
            InvitationEntity.class).setParameter("status", blankToNull(status))
            .setFirstResult(offset).setMaxResults(limit + 1);
        var rows = query.getResultList();
        var hasNext = rows.size() > limit;
        var items = rows.stream().limit(limit).map(this::invitationModel).toList();
        return new InvitationPage().items(items).page(page(items.size(), hasNext, offset + items.size()));
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public InvitationInspection inspectInvitation(String rawToken, String address) {
        rateLimiter.record("TOKEN_IP", keyHasher.hash("token-ip:" + address), 20, Duration.ofMinutes(15));
        var invitation = findInvitation(rawToken);
        if (invitation == null || !invitation.usableAt(time.now())) throw LifecycleFailure.invalidToken();
        return new InvitationInspection()
            .emailHint(maskEmail(invitation.email()))
            .proposedUsername(invitation.proposedUsername())
            .displayName(invitation.displayName())
            .expiresAt(invitation.expiresAt().atOffset(ZoneOffset.UTC));
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void acceptInvitation(InvitationAcceptanceRequest request, String address, String correlationId) {
        rateLimiter.record("TOKEN_IP", keyHasher.hash("token-ip:" + address), 20, Duration.ofMinutes(15));
        var invitation = findInvitationForUpdate(request.getToken());
        if (invitation == null || !invitation.usableAt(time.now())) throw LifecycleFailure.invalidToken();
        var identity = identities.normalize(request.getUsername(), invitation.email(), request.getDisplayName());
        var instanceSettings = settings();
        enforceDomain(identity.emailNormalized(), instanceSettings.allowedEmailDomains());
        assertIdentityAvailable(identity.emailNormalized(), identity.usernameNormalized(), null);
        var password = request.getPassword().toCharArray();
        try {
            passwordPolicy.validate(password, instanceSettings.commonPasswordCheckEnabled());
            var now = time.now();
            var userId = ids.nextId();
            var notebookId = ids.nextId();
            var user = UserEntity.activated(userId, identity.username(), identity.usernameNormalized(),
                identity.email(), identity.emailNormalized(), identity.displayName(), invitation.role(),
                passwordVerifier.hash(password), now);
            entityManager.persist(user);
            entityManager.persist(new NotebookEntity(new Notebook(new OwnerId(userId), notebookId,
                "Notes", null, true, 0, now, now, 0)));
            entityManager.persist(new UserSettingsEntity(userId, notebookId,
                request.getLanguage() == null ? "en" : request.getLanguage().toString(),
                instanceSettings.defaultTrashRetentionDays(), now));
            invitation.accept(now);
            audit("INVITATION_ACCEPTED", userId, userId, "INVITATION", invitation.id(), correlationId, Map.of());
            entityManager.flush();
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void requestPasswordReset(String emailInput, String address, String correlationId) {
        var emailNormalized = normalizeEmail(emailInput);
        rateLimiter.record("RESET_IDENTIFIER", keyHasher.hash("reset-id:" + emailNormalized), 5, Duration.ofHours(1));
        rateLimiter.record("RESET_IP", keyHasher.hash("reset-ip:" + address), 20, Duration.ofHours(1));
        if (!email.configured()) return;
        var users = entityManager.createQuery("select u from UserEntity u where u.emailNormalized = :email",
            UserEntity.class).setParameter("email", emailNormalized).setMaxResults(1).getResultList();
        if (users.isEmpty() || !("ACTIVE".equals(users.getFirst().status()) || "LOCKED".equals(users.getFirst().status()))) return;
        var user = users.getFirst();
        revokeResetTokens(user.id());
        var issued = issueReset(user.id());
        if (!email.sendPasswordReset(user.email(), issued.url())) {
            issued.entity().revoke(time.now());
            return;
        }
        audit("PASSWORD_RESET_REQUESTED", user.id(), user.id(), "USER", user.id(), correlationId,
            Map.of("delivery", "email"));
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void completePasswordReset(PasswordResetCompletionRequest request, String address,
                                      String correlationId) {
        rateLimiter.record("TOKEN_IP", keyHasher.hash("token-ip:" + address), 20, Duration.ofMinutes(15));
        var matches = entityManager.createQuery("select t from SecurityTokenEntity t "
                + "where t.tokenHash = :hash and t.tokenType = 'PASSWORD_RESET'", SecurityTokenEntity.class)
            .setParameter("hash", tokens.hashPasswordResetToken(request.getToken()))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1).getResultList();
        if (matches.isEmpty() || !matches.getFirst().usableAt(time.now())) throw LifecycleFailure.invalidToken();
        var token = matches.getFirst();
        var user = user(token.userId(), LockModeType.PESSIMISTIC_WRITE);
        if (!("ACTIVE".equals(user.status()) || "LOCKED".equals(user.status()))) throw LifecycleFailure.invalidToken();
        var password = request.getPassword().toCharArray();
        try {
            var now = time.now();
            passwordManager.change(user, password);
            token.consume(now);
            revokeResetTokens(user.id());
            sessions.revokeAll(user.id());
            audit("PASSWORD_RESET_COMPLETED", user.id(), user.id(), "USER", user.id(), correlationId, Map.of());
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Transactional
    public ResetLink administrativeReset(UUID userId, UUID actor, String correlationId) {
        var user = user(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!("ACTIVE".equals(user.status()) || "LOCKED".equals(user.status()))) {
            throw LifecycleFailure.invalidState("Password resets require an active or locked account.");
        }
        revokeResetTokens(userId);
        var issued = issueReset(userId);
        audit("PASSWORD_RESET_LINK_CREATED", actor, userId, "USER", userId, correlationId, Map.of());
        return new ResetLink().token(issued.raw()).resetUrl(URI.create(issued.url()))
            .expiresAt(issued.entity().expiresAt().atOffset(ZoneOffset.UTC));
    }

    @Transactional
    public AdminUserPage listUsers(String queryText, String role, String status, String cursor, int limit) {
        var normalizedQuery = blankToNull(queryText == null ? null : queryText.strip().toLowerCase(Locale.ROOT));
        var offset = decodeCursor(cursor);
        var users = entityManager.createQuery("select u from UserEntity u where "
                + "(:query is null or u.usernameNormalized like :pattern or u.emailNormalized like :pattern) "
                + "and (:role is null or u.role = :role) and (:status is null or u.status = :status) "
                + "order by u.createdAt desc, u.id", UserEntity.class)
            .setParameter("query", normalizedQuery)
            .setParameter("pattern", normalizedQuery == null ? null : "%" + normalizedQuery + "%")
            .setParameter("role", blankToNull(role)).setParameter("status", blankToNull(status))
            .setFirstResult(offset).setMaxResults(limit + 1).getResultList();
        var hasNext = users.size() > limit;
        var items = users.stream().limit(limit).map(this::adminUser).toList();
        return new AdminUserPage().items(items).page(page(items.size(), hasNext, offset + items.size()));
    }

    @Transactional
    public AdminUser getUser(UUID userId) { return adminUser(user(userId, LockModeType.NONE)); }

    @Transactional
    public AdminUser updateUser(UUID userId, AdminUserUpdate request, UUID actor, String correlationId) {
        var user = user(userId, LockModeType.PESSIMISTIC_WRITE);
        var username = request.getUsername() == null ? user.username() : request.getUsername();
        var emailValue = request.getEmail() == null ? user.email() : request.getEmail();
        var display = request.getDisplayName() == null ? user.displayName() : request.getDisplayName();
        var identity = identities.normalize(username, emailValue, display);
        var newRole = request.getRole() == null ? user.role() : request.getRole().toString();
        if (request.getEmail() != null && !identity.emailNormalized().equals(normalizeEmail(user.email()))) {
            enforceDomain(identity.emailNormalized(), settings().allowedEmailDomains());
        }
        assertIdentityAvailable(identity.emailNormalized(), identity.usernameNormalized(), userId);
        if ("ADMIN".equals(user.role()) && !"ADMIN".equals(newRole) && "ACTIVE".equals(user.status())) {
            assertNotLastAdmin(userId);
        }
        var roleChanged = !user.role().equals(newRole);
        var changed = new ArrayList<String>();
        if (!user.username().equals(identity.username())) changed.add("username");
        if (!user.email().equals(identity.email())) changed.add("email");
        if (roleChanged) changed.add("role");
        user.updateIdentity(identity.username(), identity.usernameNormalized(), identity.email(),
            identity.emailNormalized(), identity.displayName(), newRole, time.now());
        if (changed.contains("email")) revokeEmailChangeTokens(userId);
        if (roleChanged) sessions.revokeAll(userId);
        audit("USER_UPDATED", actor, userId, "USER", userId, correlationId,
            Map.of("fields", String.join(",", changed)));
        return adminUser(user);
    }

    @Transactional
    public void deactivate(UUID userId, UUID actor, String correlationId) {
        var user = user(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(user.status()) && !"LOCKED".equals(user.status())) {
            throw LifecycleFailure.invalidState("Only active or locked users can be deactivated.");
        }
        if ("ADMIN".equals(user.role()) && "ACTIVE".equals(user.status())) assertNotLastAdmin(userId);
        user.deactivate(time.now());
        sessions.revokeAll(userId);
        revokeResetTokens(userId);
        audit("USER_DEACTIVATED", actor, userId, "USER", userId, correlationId, Map.of());
    }

    @Transactional
    public void activate(UUID userId, UUID actor, String correlationId) {
        var user = user(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!"DEACTIVATED".equals(user.status())) throw LifecycleFailure.invalidState("Only deactivated users can be activated.");
        user.activate(time.now());
        audit("USER_ACTIVATED", actor, userId, "USER", userId, correlationId, Map.of());
    }

    @Transactional
    public void unlock(UUID userId, UUID actor, String correlationId) {
        var user = user(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!"LOCKED".equals(user.status())) throw LifecycleFailure.invalidState("Only locked users can be unlocked.");
        user.unlock(time.now());
        audit("USER_UNLOCKED", actor, userId, "USER", userId, correlationId, Map.of());
    }

    @Transactional
    public void revokeSessions(UUID userId, UUID actor, String correlationId) {
        user(userId, LockModeType.NONE);
        sessions.revokeAll(userId);
        audit("USER_SESSIONS_REVOKED", actor, userId, "USER", userId, correlationId, Map.of());
    }

    @Transactional
    public AdminUser scheduleDeletion(UUID userId, AdminDeletionRequest request, UUID actor,
                                      String correlationId) {
        return adminUser(deletion.scheduleAdministrative(userId, request, actor, correlationId));
    }

    @Transactional
    public AdminUser restoreDeletion(UUID userId, UUID actor, String correlationId) {
        return adminUser(deletion.restore(userId, actor, correlationId));
    }

    @Transactional
    public AdminSettings settingsModel() { return settingsModel(settings()); }

    @Transactional
    public AdminSettings updateSettings(AdminSettingsUpdate update, UUID actor, String correlationId) {
        List<String> domains = update.getAllowedEmailDomains() == null
            ? null : normalizeDomains(update.getAllowedEmailDomains());
        var entity = entityManager.find(InstanceSettingsEntity.class, (short) 1, LockModeType.PESSIMISTIC_WRITE);
        int normalMinutes = update.getNormalSessionDurationMinutes() == null
            ? entity.normalSessionDurationMinutes() : update.getNormalSessionDurationMinutes();
        int rememberMinutes = update.getRememberSessionDurationMinutes() == null
            ? entity.rememberSessionDurationMinutes() : update.getRememberSessionDurationMinutes();
        int delayThreshold = update.getLoginDelayThreshold() == null
            ? entity.loginDelayThreshold() : update.getLoginDelayThreshold();
        int lockThreshold = update.getLoginLockThreshold() == null
            ? entity.loginLockThreshold() : update.getLoginLockThreshold();
        var violations = new ArrayList<SetupFailure.FieldViolation>();
        if (rememberMinutes < normalMinutes) {
            violations.add(new SetupFailure.FieldViolation("rememberSessionDurationMinutes",
                "Remember-me duration must be at least the normal session duration."));
        }
        if (lockThreshold <= delayThreshold) {
            violations.add(new SetupFailure.FieldViolation("loginLockThreshold",
                "Lock threshold must be greater than the delay threshold."));
        }
        if (update.getPublicBaseUrl() != null
            && !Set.of("http", "https").contains(update.getPublicBaseUrl().getScheme())) {
            violations.add(new SetupFailure.FieldViolation("publicBaseUrl",
                "Public base URL must use HTTP or HTTPS."));
        }
        if (!violations.isEmpty()) throw LifecycleFailure.invalid(violations);
        entity.updateLifecycle(domains, update.getInvitationExpirationHours(), update.getPasswordResetExpirationMinutes());
        entity.updateImages(
            update.getAllowedImageTypes() == null || update.getAllowedImageTypes().isEmpty()
                ? null : update.getAllowedImageTypes().stream().map(Object::toString).toList(),
            update.getMaximumImageBytes(), update.getPerUserStorageQuotaBytes(), update.getImageOrphanGraceHours()
        );
        entity.updateHistory(update.getNoteVersionMaximumCount(), update.getNoteVersionRetentionDays());
        entity.updateTransfers(update.getUserExportsEnabled());
        entity.updateAccount(update.getEmailChangeExpirationMinutes(), update.getDefaultTrashRetentionDays(),
            update.getUsersMayDisableAutoPurge(), update.getAdminDeletionRetentionDays(),
            update.getSelfDeletionEnabled(), update.getCommonPasswordCheckEnabled(),
            update.getPasswordHistoryEnabled());
        entity.updateOperations(update.getInstanceName(),
            update.getDefaultLanguage() == null ? null : update.getDefaultLanguage().toString(),
            update.getNormalSessionDurationMinutes(), update.getRememberSessionDurationMinutes(),
            update.getPublicBaseUrl() == null ? null : update.getPublicBaseUrl().toString(),
            update.getSmtpSenderName(), update.getSmtpSenderAddress(),
            update.getAuditRetentionDays(), update.getOperationalLogRetentionDays(),
            update.getLoginDelayThreshold(), update.getLoginLockThreshold(), update.getLoginLockMinutes());
        if (Boolean.FALSE.equals(update.getUsersMayDisableAutoPurge())) {
            entityManager.createNativeQuery("update user_settings set trash_auto_purge_days = :days "
                    + "where trash_auto_purge_days is null")
                .setParameter("days", entity.defaultTrashRetentionDays()).executeUpdate();
        }
        audit("INSTANCE_SETTINGS_CHANGED", actor, null, "INSTANCE_SETTINGS", null, correlationId,
            Map.of("area", "instance-settings"));
        return settingsModel(entity);
    }

    private InvitationDelivery deliver(InvitationEntity invitation, String raw) {
        var url = link("/accept-invitation?token=", raw);
        if (email.sendInvitation(invitation.email(), url)) {
            return new InvitationDelivery().invitation(invitationModel(invitation))
                .delivery(InvitationDelivery.DeliveryEnum.EMAIL_SENT);
        }
        return new InvitationDelivery().invitation(invitationModel(invitation))
            .delivery(InvitationDelivery.DeliveryEnum.MANUAL).token(raw).activationUrl(URI.create(url));
    }

    private IssuedReset issueReset(UUID userId) {
        var now = time.now();
        var raw = tokens.newToken();
        var entity = new SecurityTokenEntity(ids.nextId(), userId, tokens.hashPasswordResetToken(raw), now,
            now.plus(Duration.ofMinutes(settings().passwordResetExpirationMinutes())));
        entityManager.persist(entity);
        return new IssuedReset(entity, raw, link("/reset-password?token=", raw));
    }

    private void revokeResetTokens(UUID userId) {
        entityManager.createQuery("update SecurityTokenEntity t set t.revokedAt = :now where t.userId = :userId "
                + "and t.tokenType = 'PASSWORD_RESET' and t.consumedAt is null and t.revokedAt is null")
            .setParameter("now", time.now()).setParameter("userId", userId).executeUpdate();
    }

    private void revokeEmailChangeTokens(UUID userId) {
        entityManager.createQuery("update SecurityTokenEntity t set t.revokedAt = :now where t.userId = :userId "
                + "and t.tokenType = 'EMAIL_CHANGE' and t.consumedAt is null and t.revokedAt is null")
            .setParameter("now", time.now()).setParameter("userId", userId).executeUpdate();
    }

    private InvitationEntity findInvitation(String raw) {
        var found = entityManager.createQuery("select i from InvitationEntity i where i.tokenHash = :hash",
            InvitationEntity.class).setParameter("hash", tokens.hashInvitationToken(raw)).setMaxResults(1).getResultList();
        return found.isEmpty() ? null : found.getFirst();
    }

    private InvitationEntity findInvitationForUpdate(String raw) {
        var found = entityManager.createQuery("select i from InvitationEntity i where i.tokenHash = :hash",
            InvitationEntity.class).setParameter("hash", tokens.hashInvitationToken(raw))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1).getResultList();
        return found.isEmpty() ? null : found.getFirst();
    }

    private InvitationEntity invitation(UUID id, LockModeType lock) {
        var entity = entityManager.find(InvitationEntity.class, id, lock);
        if (entity == null) throw LifecycleFailure.notFound();
        return entity;
    }

    private UserEntity user(UUID id, LockModeType lock) {
        var entity = entityManager.find(UserEntity.class, id, lock);
        if (entity == null || "DELETED".equals(entity.status())) throw LifecycleFailure.notFound();
        return entity;
    }

    private IdentityNormalizer.NormalizedIdentity normalizeInvitation(InvitationCreateRequest request) {
        var placeholder = request.getProposedUsername() == null ? "placeholder" : request.getProposedUsername();
        var value = identities.normalize(placeholder, request.getEmail(), request.getDisplayName());
        return request.getProposedUsername() == null
            ? new IdentityNormalizer.NormalizedIdentity(null, null, value.email(), value.emailNormalized(), value.displayName())
            : value;
    }

    private void assertIdentityAvailable(String email, String username, UUID excluded) {
        var count = entityManager.createQuery("select count(u) from UserEntity u where "
                + "(u.emailNormalized = :email or (:username is not null and u.usernameNormalized = :username)) "
                + "and (:excluded is null or u.id <> :excluded)", Long.class)
            .setParameter("email", email).setParameter("username", username).setParameter("excluded", excluded)
            .getSingleResult();
        if (count > 0) throw LifecycleFailure.conflict("The email address or username is already in use.");
    }

    private void assertPendingInvitationAvailable(String email, String username, UUID excluded) {
        var count = entityManager.createQuery("select count(i) from InvitationEntity i where i.status = 'PENDING' "
                + "and (i.emailNormalized = :email or (:username is not null and i.proposedUsernameNormalized = :username)) "
                + "and (:excluded is null or i.id <> :excluded)", Long.class)
            .setParameter("email", email).setParameter("username", username).setParameter("excluded", excluded)
            .getSingleResult();
        if (count > 0) throw LifecycleFailure.conflict("A pending invitation already uses that identity.");
    }

    private void expirePendingInvitations(Instant now) {
        entityManager.createQuery("update InvitationEntity i set i.status = 'EXPIRED' "
                + "where i.status = 'PENDING' and i.expiresAt <= :now")
            .setParameter("now", now).executeUpdate();
    }

    private void assertNotLastAdmin(UUID userId) {
        entityManager.find(InstanceStateEntity.class, (short) 1, LockModeType.PESSIMISTIC_WRITE);
        var count = entityManager.createQuery("select count(u) from UserEntity u where u.role = 'ADMIN' "
            + "and u.status = 'ACTIVE' and u.id <> :userId", Long.class).setParameter("userId", userId).getSingleResult();
        if (count == 0) throw LifecycleFailure.lastAdmin();
    }

    private void enforceDomain(String emailAddress, List<String> domains) {
        if (domains.isEmpty()) return;
        var at = emailAddress.lastIndexOf('@');
        var domain = at < 0 ? "" : canonicalDomain(emailAddress.substring(at + 1));
        if (!domains.contains(domain)) throw LifecycleFailure.invalid(List.of(
            new SetupFailure.FieldViolation("email", "Email domain is not allowed")));
    }

    private List<String> normalizeDomains(java.util.Collection<String> domains) {
        try {
            return domains.stream().map(this::canonicalDomain).filter(value -> !value.isBlank()).distinct().sorted().toList();
        } catch (IllegalArgumentException invalid) {
            throw LifecycleFailure.invalid(List.of(new SetupFailure.FieldViolation(
                "allowedEmailDomains", "Use valid exact email domains without wildcards")));
        }
    }

    private String canonicalDomain(String domain) {
        var value = domain == null ? "" : domain.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("*") || value.contains("@")) throw new IllegalArgumentException();
        var ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (!ascii.contains(".") || ascii.length() > 253) throw new IllegalArgumentException();
        return ascii;
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private AdminUser adminUser(UserEntity user) {
        var usage = userUsage.summarize(new OwnerId(user.id()));
        return new AdminUser().id(user.id()).username(user.username()).email(user.email())
            .displayName(user.displayName()).role(AdminUser.RoleEnum.fromValue(user.role()))
            .status(AdminUser.StatusEnum.fromValue(user.status()))
            .createdAt(user.createdAt().atOffset(ZoneOffset.UTC))
            .lastLoginAt(user.lastLoginAt() == null ? null : user.lastLoginAt().atOffset(ZoneOffset.UTC))
            .storageBytes(usage.storageBytes()).noteCount(usage.noteCount())
            .notebookCount(usage.notebookCount()).imageCount(usage.imageCount())
            .pendingDeletionAt(user.pendingDeletionAt() == null ? null : user.pendingDeletionAt().atOffset(ZoneOffset.UTC))
            .deletionDueAt(user.deletionDueAt() == null ? null : user.deletionDueAt().atOffset(ZoneOffset.UTC));
    }

    private InvitationSummary invitationModel(InvitationEntity value) {
        return new InvitationSummary().id(value.id()).email(value.email())
            .proposedUsername(value.proposedUsername()).displayName(value.displayName())
            .role(InvitationSummary.RoleEnum.fromValue(value.role()))
            .status(InvitationSummary.StatusEnum.fromValue(value.status()))
            .createdAt(value.createdAt().atOffset(ZoneOffset.UTC))
            .expiresAt(value.expiresAt().atOffset(ZoneOffset.UTC))
            .acceptedAt(value.acceptedAt() == null ? null : value.acceptedAt().atOffset(ZoneOffset.UTC))
            .revokedAt(value.revokedAt() == null ? null : value.revokedAt().atOffset(ZoneOffset.UTC));
    }

    private AdminSettings settingsModel(InstanceSettingsEntity value) {
        String senderName = value.smtpSenderName() == null
            ? configuration.smtp().senderName().orElse("Glacier Notes") : value.smtpSenderName();
        String senderAddress = value.smtpSenderAddress() == null
            ? configuration.smtp().senderAddress().orElse("noreply@localhost") : value.smtpSenderAddress();
        String baseUrl = value.publicBaseUrl() == null
            ? configuration.publicBaseUrl().orElse("http://localhost:8080") : value.publicBaseUrl();
        Number logos = (Number) entityManager.createNativeQuery("select count(*) from instance_logo").getSingleResult();
        return new AdminSettings().instanceName(value.instanceName())
            .instanceLogoUrl(logos.longValue() == 0 ? null : "/api/v1/instance/logo")
            .defaultLanguage(AdminSettings.DefaultLanguageEnum.fromValue(value.defaultLanguage()))
            .allowedEmailDomains(value.allowedEmailDomains())
            .invitationExpirationHours(value.invitationExpirationHours())
            .passwordResetExpirationMinutes(value.passwordResetExpirationMinutes())
            .normalSessionDurationMinutes(value.normalSessionDurationMinutes())
            .rememberSessionDurationMinutes(value.rememberSessionDurationMinutes())
            .allowedImageTypes(value.allowedUploadTypes().stream().map(AdminSettings.AllowedImageTypesEnum::fromValue).toList())
            .maximumImageBytes(value.maximumImageBytes())
            .perUserStorageQuotaBytes(value.perUserStorageQuotaBytes())
            .imageOrphanGraceHours(value.imageOrphanGraceHours())
            .noteVersionMaximumCount(value.noteVersionMaximumCount())
            .noteVersionRetentionDays(value.noteVersionRetentionDays())
            .userExportsEnabled(value.userExportsEnabled())
            .emailChangeExpirationMinutes(value.emailChangeExpirationMinutes())
            .defaultTrashRetentionDays(value.defaultTrashRetentionDays())
            .usersMayDisableAutoPurge(value.usersMayDisableAutoPurge())
            .adminDeletionRetentionDays(value.adminDeletionRetentionDays())
            .selfDeletionEnabled(value.selfDeletionEnabled())
            .publicBaseUrl(URI.create(baseUrl))
            .smtpSenderName(senderName)
            .smtpSenderAddress(senderAddress)
            .auditRetentionDays(value.auditRetentionDays())
            .operationalLogRetentionDays(value.operationalLogRetentionDays())
            .loginDelayThreshold(value.loginDelayThreshold())
            .loginLockThreshold(value.loginLockThreshold())
            .loginLockMinutes(value.loginLockMinutes())
            .commonPasswordCheckEnabled(value.commonPasswordCheckEnabled())
            .passwordHistoryEnabled(value.passwordHistoryEnabled())
            .restartRequiredSettings(List.of(
                AdminSettings.RestartRequiredSettingsEnum.IMAGE_STORAGE_BACKEND,
                AdminSettings.RestartRequiredSettingsEnum.SMTP_ENABLED,
                AdminSettings.RestartRequiredSettingsEnum.BACKUP_ENABLED,
                AdminSettings.RestartRequiredSettingsEnum.METRICS_ENABLED
            ));
    }

    private InstanceSettingsEntity settings() {
        return entityManager.find(InstanceSettingsEntity.class, (short) 1);
    }

    private PageMetadata page(int size, boolean hasNext, int next) {
        return new PageMetadata().size(size).hasNext(hasNext)
            .nextCursor(hasNext ? Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(next).getBytes()) : null);
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            var offset = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor)));
            if (offset < 0 || offset > 1_000_000) throw new IllegalArgumentException();
            return offset;
        } catch (RuntimeException invalid) {
            throw LifecycleFailure.invalid(List.of(new SetupFailure.FieldViolation("cursor", "Cursor is invalid")));
        }
    }

    private String link(String path, String raw) {
        var configured = settings().publicBaseUrl();
        var base = configured == null || configured.isBlank() ? configuration.publicBaseUrl().orElse("http://localhost:8080") : configured;
        return base.replaceAll("/+$", "") + path + raw;
    }

    private String maskEmail(String address) {
        var at = address.indexOf('@');
        if (at <= 1) return "***" + address.substring(Math.max(0, at));
        return address.charAt(0) + "***" + address.substring(at);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private void audit(String type, UUID actor, UUID target, String entityType, UUID entityId,
                       String correlationId, Map<String, String> metadata) {
        entityManager.persist(AuditEventEntity.administrative(ids.nextId(), type, actor, target,
            entityType, entityId, time.now(), correlationId, metadata,
            auditContext.address(), auditContext.clientDescription(), "SUCCESS"));
    }

    private record IssuedReset(SecurityTokenEntity entity, String raw, String url) {}
}
