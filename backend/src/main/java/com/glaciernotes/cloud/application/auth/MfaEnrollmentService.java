package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.application.lifecycle.PasswordManager;
import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.MfaEnrollmentStart;
import com.glaciernotes.cloud.generated.model.MfaRecoveryCodes;
import com.glaciernotes.cloud.generated.model.MfaStatus;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.entity.UserMfaTotpEntity;
import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.Base32Codec;
import com.glaciernotes.cloud.security.EnrollmentSecretCipher;
import com.glaciernotes.cloud.security.MfaTokenService;
import com.glaciernotes.cloud.security.TotpProvisioningUri;
import com.glaciernotes.cloud.security.TotpVerifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MfaEnrollmentService {
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int SECRET_GROUP_SIZE = 4;

    private final EntityManager entityManager;
    private final MfaRepository mfaRepository;
    private final MfaTokenService mfaTokens;
    private final EnrollmentSecretCipher cipher;
    private final TotpVerifier totp;
    private final PasswordManager passwords;
    private final TimeProvider timeProvider;
    private final AuditService audit;
    private final MfaMetrics metrics;
    private final boolean enabled;

    public MfaEnrollmentService(
        EntityManager entityManager,
        MfaRepository mfaRepository,
        MfaTokenService mfaTokens,
        EnrollmentSecretCipher cipher,
        TotpVerifier totp,
        PasswordManager passwords,
        TimeProvider timeProvider,
        AuditService audit,
        MfaMetrics metrics,
        GlacierConfiguration configuration
    ) {
        this.entityManager = entityManager;
        this.mfaRepository = mfaRepository;
        this.mfaTokens = mfaTokens;
        this.cipher = cipher;
        this.totp = totp;
        this.passwords = passwords;
        this.timeProvider = timeProvider;
        this.audit = audit;
        this.metrics = metrics;
        this.enabled = configuration.mfa().enabled();
    }

    @Transactional
    public MfaStatus status(UUID userId) {
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment == null) {
            return new MfaStatus().status(MfaStatus.StatusEnum.NONE);
        }
        if (!enrollment.active()) {
            return new MfaStatus().status(MfaStatus.StatusEnum.PENDING);
        }
        return new MfaStatus()
            .status(MfaStatus.StatusEnum.ACTIVE)
            .confirmedAt(enrollment.confirmedAt().atOffset(ZoneOffset.UTC))
            .recoveryCodesRemaining((int) mfaRepository.countUnusedRecoveryCodes(userId));
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public MfaEnrollmentStart start(UUID userId, char[] currentPassword, String correlationId) {
        requireEnabled();
        var user = authenticate(userId, currentPassword, correlationId);
        var existing = mfaRepository.findEnrollment(userId).orElse(null);
        if (existing != null && existing.active()) {
            throw MfaFailure.alreadyEnrolled();
        }
        if (existing != null) {
            entityManager.remove(existing);
            entityManager.flush();
        }

        var now = timeProvider.now();
        var secret = mfaTokens.newTotpSecret();
        try {
            var encrypted = cipher.encrypt(secret);
            mfaRepository.saveEnrollment(new UserMfaTotpEntity(
                userId, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId(),
                DIGITS, PERIOD_SECONDS, now
            ));
            audit.record(
                MfaAuditEvents.ENROLLMENT_STARTED, userId, userId, "USER", userId, "SUCCESS",
                correlationId, Map.of()
            );
            return new MfaEnrollmentStart()
                .secret(groupForManualEntry(Base32Codec.encode(secret).replace("=", "")))
                .provisioningUri(TotpProvisioningUri.build(
                    issuer(), user.username(), secret, DIGITS, PERIOD_SECONDS
                ))
                .expiresAt(pendingExpiry(now).atOffset(ZoneOffset.UTC))
                .digits(DIGITS)
                .periodSeconds(PERIOD_SECONDS);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Transactional
    public MfaRecoveryCodes confirm(UUID userId, String code, String correlationId) {
        requireEnabled();
        var now = timeProvider.now();
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment != null && enrollment.active()) {
            throw MfaFailure.alreadyEnrolled();
        }
        if (enrollment == null || !pendingExpiry(enrollment.createdAt()).isAfter(now)) {
            if (enrollment != null) {
                entityManager.remove(enrollment);
            }
            throw MfaFailure.notEnrolled();
        }

        var secret = cipher.decrypt(new EnrollmentSecretCipher.EncryptedSecret(
            enrollment.secretCiphertext(), enrollment.secretNonce(), enrollment.keyId()
        ));
        long acceptedStep;
        try {
            var step = totp.verify(
                secret, code, enrollment.digits(), enrollment.periodSeconds(), null
            );
            if (step.isEmpty()) {
                throw MfaFailure.invalidCode();
            }
            acceptedStep = step.getAsLong();
        } finally {
            Arrays.fill(secret, (byte) 0);
        }

        enrollment.confirm(now);
        // Burns the confirming step so the very code just typed cannot also complete a login.
        enrollment.recordAcceptedStep(acceptedStep, now);
        metrics.enrollmentActivated();
        audit.record(
            MfaAuditEvents.ENROLLMENT_CONFIRMED, userId, userId, "USER", userId, "SUCCESS",
            correlationId, Map.of()
        );
        return issueRecoveryCodes(userId, now);
    }

    @Transactional
    public void cancel(UUID userId, String correlationId) {
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment == null || enrollment.active()) {
            return;
        }
        entityManager.remove(enrollment);
        audit.record(
            MfaAuditEvents.ENROLLMENT_ABANDONED, userId, userId, "USER", userId, "SUCCESS",
            correlationId, Map.of()
        );
    }

    /**
     * Deliberately not gated on the feature flag: turning the flag off must not trap an account that
     * already carries an enrollment.
     */
    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public void disable(UUID userId, char[] currentPassword, String correlationId) {
        authenticate(userId, currentPassword, correlationId);
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment == null || !enrollment.active()) {
            throw MfaFailure.notEnrolled();
        }
        mfaRepository.deleteEnrollment(userId);
        metrics.enrollmentDisabled();
        audit.record(
            MfaAuditEvents.DISABLED, userId, userId, "USER", userId, "SUCCESS",
            correlationId, Map.of()
        );
    }

    @Transactional(dontRollbackOn = LifecycleFailure.class)
    public MfaRecoveryCodes regenerate(UUID userId, char[] currentPassword, String correlationId) {
        requireEnabled();
        authenticate(userId, currentPassword, correlationId);
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment == null || !enrollment.active()) {
            throw MfaFailure.notEnrolled();
        }
        audit.record(
            MfaAuditEvents.RECOVERY_CODES_REGENERATED, userId, userId, "USER", userId, "SUCCESS",
            correlationId, Map.of()
        );
        return issueRecoveryCodes(userId, timeProvider.now());
    }

    private MfaRecoveryCodes issueRecoveryCodes(UUID userId, Instant now) {
        var codes = mfaTokens.newRecoveryCodes(RECOVERY_CODE_COUNT);
        mfaRepository.replaceRecoveryCodes(userId, codes.stream().map(mfaTokens::hashRecoveryCode).toList());
        metrics.recoveryCodesIssued();
        return new MfaRecoveryCodes().codes(codes).generatedAt(now.atOffset(ZoneOffset.UTC));
    }

    private UserEntity authenticate(UUID userId, char[] currentPassword, String correlationId) {
        try {
            var user = entityManager.find(UserEntity.class, userId);
            if (user == null || !passwords.matchesCurrent(user, currentPassword)) {
                audit.record(
                    MfaAuditEvents.REAUTHENTICATION_FAILED, userId, userId, "USER", userId,
                    "FAILURE", correlationId, Map.of()
                );
                throw LifecycleFailure.invalidCredentials();
            }
            return user;
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw MfaFailure.unavailable();
        }
    }

    private Instant pendingExpiry(Instant createdAt) {
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        return createdAt.plusSeconds(settings.mfaPendingEnrollmentMinutes() * 60L);
    }

    private String issuer() {
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        var name = settings.instanceName();
        return name == null || name.isBlank() ? "Glacier Notes" : name;
    }

    private String groupForManualEntry(String base32) {
        var grouped = new StringBuilder(base32.length() + base32.length() / SECRET_GROUP_SIZE);
        for (int index = 0; index < base32.length(); index++) {
            if (index > 0 && index % SECRET_GROUP_SIZE == 0) {
                grouped.append(' ');
            }
            grouped.append(base32.charAt(index));
        }
        return grouped.toString();
    }
}
