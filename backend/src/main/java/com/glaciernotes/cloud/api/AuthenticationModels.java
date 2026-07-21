package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.generated.model.AuthenticatedUser;
import com.glaciernotes.cloud.generated.model.SessionContext;
import com.glaciernotes.cloud.generated.model.SessionSummary;

import java.time.ZoneOffset;

final class AuthenticationModels {
    private AuthenticationModels() {
    }

    static SessionContext context(SessionView session) {
        return new SessionContext()
            .user(new AuthenticatedUser()
                .id(session.userId())
                .username(session.username())
                .email(session.email())
                .displayName(session.displayName())
                .role(AuthenticatedUser.RoleEnum.fromValue(session.role())))
            .session(summary(session, true));
    }

    static SessionSummary summary(SessionView session, boolean current) {
        return new SessionSummary()
            .id(session.id())
            .current(current)
            .rememberMe(session.rememberMe())
            .createdAt(session.createdAt().atOffset(ZoneOffset.UTC))
            .lastSeenAt(session.lastSeenAt().atOffset(ZoneOffset.UTC))
            .expiresAt(session.expiresAt().atOffset(ZoneOffset.UTC))
            .clientDescription(session.clientDescription());
    }
}
