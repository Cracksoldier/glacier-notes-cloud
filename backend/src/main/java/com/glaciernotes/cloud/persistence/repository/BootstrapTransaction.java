package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.application.setup.SetupCommand;
import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceStateEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.NotebookEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.entity.UserSettingsEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class BootstrapTransaction {
    private final EntityManager entityManager;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final PasswordVerifier passwordVerifier;

    public BootstrapTransaction(
        EntityManager entityManager,
        IdGenerator idGenerator,
        TimeProvider timeProvider,
        PasswordVerifier passwordVerifier
    ) {
        this.entityManager = entityManager;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
        this.passwordVerifier = passwordVerifier;
    }

    public State state() {
        var instance = entityManager.find(InstanceStateEntity.class, (short) 1);
        var userCount = entityManager.createQuery("select count(u) from UserEntity u", Long.class)
            .getSingleResult();
        return new State(instance.initialized(), userCount);
    }

    @Transactional
    public Instant initialize(SetupCommand command, String correlationId) {
        var instance = entityManager.find(
            InstanceStateEntity.class,
            (short) 1,
            LockModeType.PESSIMISTIC_WRITE
        );
        var userCount = entityManager.createQuery("select count(u) from UserEntity u", Long.class)
            .getSingleResult();
        if (instance.initialized() || userCount != 0) {
            throw SetupFailure.alreadyInitialized();
        }

        var now = timeProvider.now();
        var administratorId = idGenerator.nextId();
        var notebookId = idGenerator.nextId();
        var passwordHash = passwordVerifier.hash(command.password());

        entityManager.persist(UserEntity.initialAdministrator(
            administratorId,
            command.username(),
            command.usernameNormalized(),
            command.email(),
            command.emailNormalized(),
            command.displayName(),
            passwordHash,
            now
        ));
        entityManager.persist(new NotebookEntity(new Notebook(
            new OwnerId(administratorId), notebookId, "Notes", null, true, 0, now, now, 0
        )));
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        entityManager.persist(new UserSettingsEntity(administratorId, notebookId, command.language(),
            settings.defaultTrashRetentionDays(), now));
        instance.initialize(administratorId, now);
        entityManager.persist(AuditEventEntity.instanceBootstrapped(
            idGenerator.nextId(), administratorId, now, correlationId
        ));
        entityManager.flush();
        return now;
    }

    public record State(boolean initialized, long userCount) {
        public boolean setupRequired() {
            return !initialized && userCount == 0;
        }

        public boolean inconsistent() {
            return initialized != (userCount > 0);
        }
    }
}
