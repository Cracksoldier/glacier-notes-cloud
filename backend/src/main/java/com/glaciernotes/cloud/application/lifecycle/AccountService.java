package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.setup.IdentityNormalizer;
import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.*;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.SecurityTokenEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.entity.UserSettingsEntity;
import com.glaciernotes.cloud.persistence.repository.EndpointRateLimiter;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import com.glaciernotes.cloud.security.SessionTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.net.IDN;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AccountService {
    private final EntityManager entityManager;
    private final IdentityNormalizer identities;
    private final PasswordManager passwords;
    private final SessionRepository sessions;
    private final SessionTokenService tokens;
    private final EndpointRateLimiter rateLimiter;
    private final ClientKeyHasher keyHasher;
    private final LifecycleEmailService email;
    private final AccountDeletionService deletion;
    private final GlacierConfiguration configuration;
    private final TimeProvider time;
    private final IdGenerator ids;

    public AccountService(EntityManager entityManager, IdentityNormalizer identities, PasswordManager passwords,
                          SessionRepository sessions, SessionTokenService tokens, EndpointRateLimiter rateLimiter,
                          ClientKeyHasher keyHasher, LifecycleEmailService email, AccountDeletionService deletion,
                          GlacierConfiguration configuration, TimeProvider time, IdGenerator ids) {
        this.entityManager = entityManager;
        this.identities = identities;
        this.passwords = passwords;
        this.sessions = sessions;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.keyHasher = keyHasher;
        this.email = email;
        this.deletion = deletion;
        this.configuration = configuration;
        this.time = time;
        this.ids = ids;
    }

    @Transactional
    public UserProfile profile(UUID userId) {
        return profileModel(require(userId, LockModeType.NONE));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UserProfileUpdate update, String correlationId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        var identity = identities.normalize(update.getUsername() == null ? user.username() : update.getUsername(),
            user.email(), update.getDisplayName() == null ? user.displayName() : update.getDisplayName());
        assertIdentityAvailable(identity.emailNormalized(), identity.usernameNormalized(), userId);
        user.updateIdentity(identity.username(), identity.usernameNormalized(), identity.email(),
            identity.emailNormalized(), identity.displayName(), user.role(), time.now());
        audit("PROFILE_UPDATED", userId, userId, correlationId, Map.of());
        return profileModel(user);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request, String correlationId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        var current = request.getCurrentPassword().toCharArray();
        var replacement = request.getNewPassword().toCharArray();
        try {
            if (!passwords.matchesCurrent(user, current)) throw LifecycleFailure.invalidCredentials();
            passwords.change(user, replacement);
            sessions.revokeAll(userId);
            revokeTokens(userId);
            audit("PASSWORD_CHANGED", userId, userId, correlationId, Map.of());
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void requestEmailChange(UUID userId, EmailChangeRequest request, String address, String correlationId) {
        if (!email.configured()) {
            throw LifecycleFailure.unavailable(
                "Email changes require SMTP to be configured by the administrator.");
        }
        rateLimiter.record("EMAIL_CHANGE_USER", keyHasher.hash("email-change-user:" + userId), 5,
            Duration.ofHours(1));
        rateLimiter.record("EMAIL_CHANGE_IP", keyHasher.hash("email-change-ip:" + address), 20,
            Duration.ofHours(1));
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        var current = request.getCurrentPassword().toCharArray();
        try {
            if (!passwords.matchesCurrent(user, current)) throw LifecycleFailure.invalidCredentials();
        } finally {
            Arrays.fill(current, '\0');
        }
        var identity = identities.normalize(user.username(), request.getNewEmail(), user.displayName());
        if (identity.emailNormalized().equals(user.email().strip().toLowerCase(Locale.ROOT))) {
            throw LifecycleFailure.conflict("The new email address must be different from the current address.");
        }
        enforceDomain(identity.emailNormalized());
        assertIdentityAvailable(identity.emailNormalized(), null, userId);
        revokeEmailChangeTokens(userId);
        var now = time.now();
        var raw = tokens.newToken();
        var token = SecurityTokenEntity.emailChange(ids.nextId(), userId, tokens.hashEmailChangeToken(raw),
            identity.email(), identity.emailNormalized(), now,
            now.plus(Duration.ofMinutes(settings().emailChangeExpirationMinutes())));
        entityManager.persist(token);
        entityManager.flush();
        if (!email.sendEmailChangeVerification(identity.email(), link("/verify-email-change?token=", raw))) {
            token.revoke(time.now());
            throw LifecycleFailure.unavailable("The verification email could not be sent. Try again later.");
        }
        audit("EMAIL_CHANGE_REQUESTED", userId, userId, correlationId, Map.of());
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void completeEmailChange(String rawToken, String address, String correlationId) {
        rateLimiter.record("EMAIL_CHANGE_IP", keyHasher.hash("email-change-ip:" + address), 20,
            Duration.ofHours(1));
        var matches = entityManager.createQuery("select t from SecurityTokenEntity t where t.tokenHash = :hash "
                + "and t.tokenType = 'EMAIL_CHANGE'", SecurityTokenEntity.class)
            .setParameter("hash", tokens.hashEmailChangeToken(rawToken))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1).getResultList();
        if (matches.isEmpty() || !matches.getFirst().usableAt(time.now())) throw LifecycleFailure.invalidToken();
        var token = matches.getFirst();
        var user = require(token.userId(), LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(user.status()) && !"LOCKED".equals(user.status())) {
            throw LifecycleFailure.invalidToken();
        }
        enforceDomain(token.targetEmailNormalized());
        assertIdentityAvailable(token.targetEmailNormalized(), null, user.id());
        var oldEmail = user.email();
        user.updateIdentity(user.username(), normalizedUsername(user.username()), token.targetEmail(),
            token.targetEmailNormalized(), user.displayName(), user.role(), time.now());
        token.consume(time.now());
        revokeEmailChangeTokens(user.id());
        sessions.revokeAll(user.id());
        audit("EMAIL_CHANGED", user.id(), user.id(), correlationId, Map.of());
        email.sendEmailChangedNotice(oldEmail);
    }

    @Transactional
    public UserSettings settings(UUID userId) {
        return settingsModel(requireSettings(userId), settings());
    }

    @Transactional
    public UserSettings updateSettings(UUID userId, UserSettingsUpdate update, String correlationId) {
        require(userId, LockModeType.NONE);
        var instance = settings();
        Integer trash = update.getTrashAutoPurgeDays();
        boolean updateTrash = trash != null;
        if (updateTrash && trash == 0 && !instance.usersMayDisableAutoPurge()) {
            throw LifecycleFailure.invalid(List.of(new SetupFailure.FieldViolation("trashAutoPurgeDays",
                "The administrator requires automatic trash cleanup")));
        }
        var userSettings = entityManager.find(UserSettingsEntity.class, userId, LockModeType.PESSIMISTIC_WRITE);
        userSettings.update(update.getTheme() == null ? null : update.getTheme().toString(),
            update.getLanguage() == null ? null : update.getLanguage().toString(), update.getMoveCheckedToBottom(),
            trash != null && trash == 0 ? null : trash, updateTrash, time.now());
        audit("USER_SETTINGS_CHANGED", userId, userId, correlationId, Map.of());
        return settingsModel(userSettings, instance);
    }

    @Transactional
    public void deleteSelf(UUID userId, SelfDeletionRequest request, String correlationId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        var current = request.getCurrentPassword().toCharArray();
        try {
            if (!passwords.matchesCurrent(user, current)) throw LifecycleFailure.invalidCredentials();
        } finally {
            Arrays.fill(current, '\0');
        }
        deletion.scheduleSelf(userId, correlationId);
    }

    private UserProfile profileModel(UserEntity user) {
        return new UserProfile().id(user.id()).username(user.username()).email(user.email())
            .displayName(user.displayName()).role(UserProfile.RoleEnum.fromValue(user.role()))
            .emailChangeAvailable(email.configured()).selfDeletionEnabled(settings().selfDeletionEnabled());
    }

    private UserSettings settingsModel(UserSettingsEntity value, InstanceSettingsEntity instance) {
        return new UserSettings().theme(UserSettings.ThemeEnum.fromValue(value.theme()))
            .language(UserSettings.LanguageEnum.fromValue(value.language()))
            .moveCheckedToBottom(value.moveCheckedToBottom())
            .trashAutoPurgeDays(value.trashAutoPurgeDays() == null ? 0 : value.trashAutoPurgeDays())
            .trashAutoPurgeMayBeDisabled(instance.usersMayDisableAutoPurge());
    }

    private UserEntity require(UUID id, LockModeType lock) {
        var user = entityManager.find(UserEntity.class, id, lock);
        if (user == null || "DELETED".equals(user.status())) throw LifecycleFailure.notFound();
        return user;
    }

    private UserSettingsEntity requireSettings(UUID userId) {
        var value = entityManager.find(UserSettingsEntity.class, userId);
        if (value == null) throw LifecycleFailure.notFound();
        return value;
    }

    private void assertIdentityAvailable(String email, String username, UUID excluded) {
        long count = entityManager.createQuery("select count(u) from UserEntity u where "
                + "(:email is not null and u.emailNormalized = :email or :username is not null "
                + "and u.usernameNormalized = :username) and u.id <> :excluded", Long.class)
            .setParameter("email", email).setParameter("username", username).setParameter("excluded", excluded)
            .getSingleResult();
        if (count > 0) throw LifecycleFailure.conflict("The email address or username is already in use.");
    }

    private void enforceDomain(String emailAddress) {
        var domains = settings().allowedEmailDomains();
        if (domains.isEmpty()) return;
        int at = emailAddress.lastIndexOf('@');
        String domain = at < 0 ? "" : canonicalDomain(emailAddress.substring(at + 1));
        if (!domains.contains(domain)) throw LifecycleFailure.invalid(List.of(
            new SetupFailure.FieldViolation("newEmail", "Email domain is not allowed")));
    }

    private String canonicalDomain(String value) {
        return IDN.toASCII(value.strip().toLowerCase(Locale.ROOT), IDN.USE_STD3_ASCII_RULES)
            .toLowerCase(Locale.ROOT);
    }

    private String normalizedUsername(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private void revokeTokens(UUID userId) {
        entityManager.createQuery("update SecurityTokenEntity t set t.revokedAt = :now where t.userId = :userId "
                + "and t.consumedAt is null and t.revokedAt is null")
            .setParameter("now", time.now()).setParameter("userId", userId).executeUpdate();
    }

    private void revokeEmailChangeTokens(UUID userId) {
        entityManager.createQuery("update SecurityTokenEntity t set t.revokedAt = :now where t.userId = :userId "
                + "and t.tokenType = 'EMAIL_CHANGE' and t.consumedAt is null and t.revokedAt is null")
            .setParameter("now", time.now()).setParameter("userId", userId).executeUpdate();
    }

    private InstanceSettingsEntity settings() {
        return entityManager.find(InstanceSettingsEntity.class, (short) 1);
    }

    private String link(String path, String raw) {
        var configured = settings().publicBaseUrl();
        var base = configured == null || configured.isBlank()
            ? configuration.publicBaseUrl().orElse("http://localhost:8080") : configured;
        return base.replaceAll("/+$", "") + path + raw;
    }

    private void audit(String type, UUID actor, UUID target, String correlationId, Map<String, String> metadata) {
        entityManager.persist(AuditEventEntity.administrative(ids.nextId(), type, actor, target,
            "USER", target, time.now(), correlationId, metadata));
    }
}
