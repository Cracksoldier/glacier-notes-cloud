package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.application.setup.PasswordPolicy;
import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.PasswordHistoryEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class PasswordManager {
    private final EntityManager entityManager;
    private final PasswordPolicy policy;
    private final PasswordVerifier verifier;
    private final IdGenerator ids;
    private final TimeProvider time;

    public PasswordManager(EntityManager entityManager, PasswordPolicy policy, PasswordVerifier verifier,
                           IdGenerator ids, TimeProvider time) {
        this.entityManager = entityManager;
        this.policy = policy;
        this.verifier = verifier;
        this.ids = ids;
        this.time = time;
    }

    public boolean matchesCurrent(UserEntity user, char[] password) {
        return user.passwordHash() != null && verifier.matches(password, user.passwordHash());
    }

    public void change(UserEntity user, char[] password) {
        InstanceSettingsEntity settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        policy.validate(password, settings.commonPasswordCheckEnabled());
        if (settings.passwordHistoryEnabled()) {
            boolean reused = matchesCurrent(user, password) || entityManager.createQuery(
                    "select h from PasswordHistoryEntity h where h.userId = :userId order by h.createdAt desc",
                    PasswordHistoryEntity.class)
                .setParameter("userId", user.id()).getResultStream()
                .anyMatch(history -> verifier.matches(password, history.passwordHash()));
            if (reused) throw SetupFailure.invalid(List.of(
                new SetupFailure.FieldViolation("newPassword", "Choose a password that has not been used before")));
        }
        if (user.passwordHash() != null) {
            entityManager.persist(new PasswordHistoryEntity(ids.nextId(), user.id(), user.passwordHash(), time.now()));
        }
        user.changePassword(verifier.hash(password), time.now());
    }
}
