package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.persistence.entity.UserMfaTotpEntity;
import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.EnrollmentSecretCipher;
import com.glaciernotes.cloud.security.MfaTokenService;
import com.glaciernotes.cloud.security.TotpVerifier;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * The single place a submitted second-factor code is checked. Login and step-up must share it, or
 * the accepted-step replay protection diverges between them and a code spent on one path could be
 * replayed on the other.
 */
@ApplicationScoped
public class MfaCodeVerifier {
    private final MfaRepository mfaRepository;
    private final MfaTokenService mfaTokens;
    private final TotpVerifier totp;
    private final EnrollmentSecretCipher cipher;
    private final MfaMetrics metrics;

    public MfaCodeVerifier(
        MfaRepository mfaRepository,
        MfaTokenService mfaTokens,
        TotpVerifier totp,
        EnrollmentSecretCipher cipher,
        MfaMetrics metrics
    ) {
        this.mfaRepository = mfaRepository;
        this.mfaTokens = mfaTokens;
        this.totp = totp;
        this.cipher = cipher;
        this.metrics = metrics;
    }

    /** Returns the accepted factor name, or null when neither a TOTP nor a recovery code matched. */
    public String verify(UserMfaTotpEntity enrollment, UUID userId, String code, Instant now) {
        var secret = cipher.decrypt(new EnrollmentSecretCipher.EncryptedSecret(
            enrollment.secretCiphertext(), enrollment.secretNonce(), enrollment.keyId()
        ));
        try {
            var step = totp.verify(
                secret, code, enrollment.digits(), enrollment.periodSeconds(),
                enrollment.lastAcceptedStep()
            );
            if (step.isPresent()) {
                enrollment.recordAcceptedStep(step.getAsLong(), now);
                return "TOTP";
            }
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        if (mfaRepository.consumeRecoveryCode(userId, mfaTokens.hashRecoveryCode(code))) {
            metrics.recoveryCodesConsumed();
            return "RECOVERY_CODE";
        }
        return null;
    }
}
