package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.AuthenticationService;
import com.glaciernotes.cloud.application.auth.LoginResult;
import com.glaciernotes.cloud.application.auth.MfaChallengeService;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.generated.api.AuthenticationApi;
import com.glaciernotes.cloud.generated.model.LoginOutcome;
import com.glaciernotes.cloud.generated.model.LoginRequest;
import com.glaciernotes.cloud.generated.model.MfaChallenge;
import com.glaciernotes.cloud.generated.model.MfaLoginRequest;
import com.glaciernotes.cloud.generated.model.InvitationAcceptanceRequest;
import com.glaciernotes.cloud.generated.model.InvitationInspection;
import com.glaciernotes.cloud.generated.model.PasswordResetCompletionRequest;
import com.glaciernotes.cloud.generated.model.PasswordResetRequest;
import com.glaciernotes.cloud.generated.model.SessionContext;
import com.glaciernotes.cloud.generated.model.TokenRequest;
import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
import com.glaciernotes.cloud.application.lifecycle.AccountService;
import com.glaciernotes.cloud.application.operations.RequestAuditContext;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import com.glaciernotes.cloud.security.CookieManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import org.jboss.logging.MDC;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class AuthenticationResource implements AuthenticationApi {
    private final AuthenticationService authentication;
    private final MfaChallengeService challenges;
    private final SessionRepository sessions;
    private final CookieManager cookies;
    private final SecurityIdentity identity;
    private final LifecycleService lifecycle;
    private final AccountService accounts;
    private final RequestAuditContext auditContext;

    @Context
    HttpServerRequest request;

    @Context
    HttpServerResponse response;

    public AuthenticationResource(
        AuthenticationService authentication,
        MfaChallengeService challenges,
        SessionRepository sessions,
        CookieManager cookies,
        SecurityIdentity identity,
        LifecycleService lifecycle,
        AccountService accounts,
        RequestAuditContext auditContext
    ) {
        this.authentication = authentication;
        this.challenges = challenges;
        this.sessions = sessions;
        this.cookies = cookies;
        this.identity = identity;
        this.lifecycle = lifecycle;
        this.accounts = accounts;
        this.auditContext = auditContext;
    }

    @Override
    @RolesAllowed({"USER", "ADMIN"})
    public SessionContext getCurrentSession() {
        return AuthenticationModels.context(currentSession());
    }

    @Override
    public LoginOutcome login(LoginRequest loginRequest) {
        var result = authentication.login(
            loginRequest.getIdentifier(),
            loginRequest.getPassword().toCharArray(),
            Boolean.TRUE.equals(loginRequest.getRememberMe()),
            clientAddress(),
            auditContext.clientDescription(),
            correlationId()
        );
        return switch (result) {
            case LoginResult.SessionIssued issued -> {
                cookies.issue(
                    response, issued.token(), issued.session().rememberMe(),
                    issued.cookieMaxAgeSeconds()
                );
                yield new LoginOutcome()
                    .result(LoginOutcome.ResultEnum.SESSION)
                    .context(AuthenticationModels.context(issued.session()));
            }
            case LoginResult.SecondFactorRequired challenge -> new LoginOutcome()
                .result(LoginOutcome.ResultEnum.MFA_REQUIRED)
                .challenge(new MfaChallenge()
                    .token(challenge.challengeToken())
                    .expiresAt(challenge.expiresAt().atOffset(ZoneOffset.UTC))
                    .attemptsRemaining(challenge.attemptsRemaining())
                    .acceptedFactors(List.of(
                        MfaChallenge.AcceptedFactorsEnum.TOTP,
                        MfaChallenge.AcceptedFactorsEnum.RECOVERY_CODE
                    )));
        };
    }

    @Override
    public SessionContext completeMfaLogin(MfaLoginRequest mfaLoginRequest) {
        var issued = challenges.complete(
            mfaLoginRequest.getChallengeToken(),
            mfaLoginRequest.getCode(),
            clientAddress(),
            auditContext.clientDescription(),
            correlationId()
        );
        cookies.issue(
            response, issued.token(), issued.session().rememberMe(), issued.cookieMaxAgeSeconds()
        );
        return AuthenticationModels.context(issued.session());
    }

    @Override
    @RolesAllowed({"USER", "ADMIN"})
    public void logout() {
        var session = currentSession();
        sessions.revokeCurrent(session.userId(), session.id());
        cookies.clear(response);
    }

    @Override
    public InvitationInspection inspectInvitation(TokenRequest tokenRequest) {
        return lifecycle.inspectInvitation(tokenRequest.getToken(), clientAddress());
    }

    @Override
    public void acceptInvitation(InvitationAcceptanceRequest request) {
        lifecycle.acceptInvitation(request, clientAddress(), correlationId());
    }

    @Override
    public void requestPasswordReset(PasswordResetRequest request) {
        lifecycle.requestPasswordReset(request.getEmail(), clientAddress(), correlationId());
    }

    @Override
    public void completePasswordReset(PasswordResetCompletionRequest request) {
        lifecycle.completePasswordReset(request, clientAddress(), correlationId());
    }

    @Override
    public void completeEmailChange(TokenRequest tokenRequest) {
        accounts.completeEmailChange(tokenRequest.getToken(), clientAddress(), correlationId());
    }

    private SessionView currentSession() {
        var session = identity.<SessionView>getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) {
            throw AuthenticationFailure.sessionNotFound();
        }
        return session;
    }

    private String clientAddress() {
        var address = request.remoteAddress();
        return address == null ? "0.0.0.0" : address.hostAddress();
    }

    private String correlationId() {
        return Objects.toString(MDC.get("correlationId"), "unavailable");
    }
}
