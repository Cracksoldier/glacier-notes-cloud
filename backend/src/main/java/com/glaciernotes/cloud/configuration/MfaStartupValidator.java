package com.glaciernotes.cloud.configuration;

import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.EnrollmentSecretCipher;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class MfaStartupValidator {
    private static final Logger LOG = Logger.getLogger(MfaStartupValidator.class);

    private final GlacierConfiguration configuration;
    private final SecretProvider secretProvider;
    private final EnrollmentSecretCipher cipher;
    private final MfaRepository mfaRepository;

    public MfaStartupValidator(
        GlacierConfiguration configuration,
        SecretProvider secretProvider,
        EnrollmentSecretCipher cipher,
        MfaRepository mfaRepository
    ) {
        this.configuration = configuration;
        this.secretProvider = secretProvider;
        this.cipher = cipher;
        this.mfaRepository = mfaRepository;
    }

    void validate(@Observes StartupEvent ignored) {
        validate(configuration.mfa().enabled(), secretProvider::enrollmentSecret);
        if (configuration.mfa().enabled()) {
            reportStaleEnrollments();
        }
    }

    /**
     * Rotating the enrollment secret does not re-encrypt anything, so an account enrolled under the
     * previous key can no longer complete the second login stage. Saying so at startup beats
     * discovering it one sign-in at a time.
     */
    private void reportStaleEnrollments() {
        long stale = mfaRepository.countEnrollmentsUnderOtherKeys(cipher.keyId());
        if (stale > 0) {
            LOG.warnf(
                "%d second-factor enrollment(s) were encrypted under a different enrollment secret "
                    + "and can no longer be decrypted; those accounts must re-enroll. "
                    + "See docs/BACKUP_RESTORE.md.",
                stale
            );
        }
    }

    /**
     * The secret is supplied lazily because resolving it reads a configured file, which throws when
     * the path is stale. An instance that has switched the feature off must still start.
     */
    static void validate(boolean enabled, Supplier<Optional<String>> enrollmentSecret) {
        if (!enabled) {
            return;
        }
        if (enrollmentSecret.get().filter(SecretPolicy::valid).isEmpty()) {
            throw new IllegalStateException(
                "Second-factor support requires GLACIER_MFA_ENCRYPTION_SECRET_FILE or "
                    + "GLACIER_MFA_ENCRYPTION_SECRET to hold at least "
                    + SecretPolicy.MINIMUM_LENGTH
                    + " non-whitespace characters"
            );
        }
    }
}
