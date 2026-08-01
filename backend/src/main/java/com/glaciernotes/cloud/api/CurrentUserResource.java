package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.MfaEnrollmentService;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.image.ImageService;
import com.glaciernotes.cloud.application.lifecycle.AccountService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.generated.api.CurrentUserApi;
import com.glaciernotes.cloud.generated.model.*;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import com.glaciernotes.cloud.security.CookieManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
@Path("/api/v1/me")
@RolesAllowed({"USER", "ADMIN"})
public class CurrentUserResource implements CurrentUserApi {
    private final AccountService accounts;
    private final ImageService images;
    private final MfaEnrollmentService mfa;
    private final SecurityIdentity identity;
    private final CookieManager cookies;

    @Context
    HttpServerRequest request;

    @Context
    HttpServerResponse response;

    public CurrentUserResource(AccountService accounts, ImageService images,
                               MfaEnrollmentService mfa, SecurityIdentity identity,
                               CookieManager cookies) {
        this.accounts = accounts;
        this.images = images;
        this.mfa = mfa;
        this.identity = identity;
        this.cookies = cookies;
    }

    @Override
    @GET
    @Path("/profile")
    @Produces(MediaType.APPLICATION_JSON)
    public UserProfile getCurrentUserProfile() { return accounts.profile(userId()); }

    @Override
    @PATCH
    @Path("/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserProfile updateCurrentUserProfile(UserProfileUpdate update) {
        return accounts.updateProfile(userId(), update, correlationId());
    }

    @Override
    @PUT
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void changeCurrentUserPassword(PasswordChangeRequest passwordChangeRequest) {
        accounts.changePassword(userId(), passwordChangeRequest, correlationId());
        cookies.clear(response);
    }

    @Override
    @POST
    @Path("/email-change")
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(202)
    public void requestCurrentUserEmailChange(EmailChangeRequest emailChangeRequest) {
        var session = session();
        accounts.requestEmailChange(session.userId(), session.id(), emailChangeRequest, clientAddress(),
            correlationId());
    }

    @Override
    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    public UserSettings getCurrentUserSettings() { return accounts.settings(userId()); }

    @Override
    @PATCH
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserSettings updateCurrentUserSettings(UserSettingsUpdate update) {
        return accounts.updateSettings(userId(), update, correlationId());
    }

    @Override
    @GET
    @Path("/storage")
    @Produces(MediaType.APPLICATION_JSON)
    public StorageUsage getCurrentUserStorage() { return images.usage(new OwnerId(userId())); }

    @Override
    @POST
    @Path("/deletion")
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(202)
    public void deleteCurrentUser(SelfDeletionRequest selfDeletionRequest) {
        var session = session();
        accounts.deleteSelf(session.userId(), session.id(), selfDeletionRequest, clientAddress(),
            correlationId());
        cookies.clear(response);
    }

    @Override
    @GET
    @Path("/mfa")
    @Produces(MediaType.APPLICATION_JSON)
    public MfaStatus getMfaStatus() { return mfa.status(userId()); }

    @Override
    @POST
    @Path("/mfa/totp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MfaEnrollmentStart startTotpEnrollment(MfaEnrollmentStartRequest request) {
        var session = session();
        return mfa.start(session.userId(), session.id(),
            request.getCurrentPassword().toCharArray(), correlationId());
    }

    @Override
    @POST
    @Path("/mfa/totp/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MfaRecoveryCodes confirmTotpEnrollment(MfaEnrollmentConfirmRequest request) {
        var session = session();
        return mfa.confirm(session.userId(), session.id(), request.getCode(), correlationId());
    }

    @Override
    @DELETE
    @Path("/mfa/totp/pending")
    public void cancelTotpEnrollment() { mfa.cancel(userId(), correlationId()); }

    @Override
    @POST
    @Path("/mfa/totp/disable")
    @Consumes(MediaType.APPLICATION_JSON)
    public void disableTotp(MfaDisableRequest request) {
        var session = session();
        mfa.disable(session.userId(), session.id(), request.getCurrentPassword().toCharArray(),
            request.getCode(), clientAddress(), correlationId());
    }

    @Override
    @POST
    @Path("/mfa/recovery-codes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MfaRecoveryCodes regenerateRecoveryCodes(MfaRecoveryCodesRequest request) {
        var session = session();
        return mfa.regenerate(session.userId(), session.id(), request.getCurrentPassword().toCharArray(),
            request.getCode(), clientAddress(), correlationId());
    }

    private SessionView session() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return session;
    }

    private UUID userId() {
        return session().userId();
    }

    private String clientAddress() {
        var address = request.remoteAddress();
        return address == null ? "0.0.0.0" : address.hostAddress();
    }

    private String correlationId() {
        return Objects.toString(MDC.get("correlationId"), "unavailable");
    }
}
