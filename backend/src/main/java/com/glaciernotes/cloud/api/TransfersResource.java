package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.transfer.TransferModels.ApplyCommand;
import com.glaciernotes.cloud.application.transfer.TransferService;
import com.glaciernotes.cloud.generated.model.ImportApplyRequest;
import com.glaciernotes.cloud.generated.model.TransferJob;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;
import java.util.UUID;

@Path("/api/v1/imports")
@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
public class TransfersResource {
    private final TransferService transfers;
    private final SecurityIdentity identity;

    public TransfersResource(TransferService transfers, SecurityIdentity identity) {
        this.transfers = transfers; this.identity = identity;
    }

    @POST @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(202)
    public TransferJob createImport(@RestForm("file") FileUpload file) {
        UUID user = user();
        return TransferJobMapper.toModel(transfers.createImport(user, user, false, file));
    }
    @GET @Path("/{id}")
    public TransferJob imported(@PathParam("id") UUID id) {
        return TransferJobMapper.toModel(transfers.get(id, user(), "IMPORT", false));
    }
    @POST @Path("/{id}/apply") @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(202)
    public TransferJob apply(@PathParam("id") UUID id, @Valid ImportApplyRequest request) {
        String strategy = request == null || request.getStrategy() == null
            ? null : request.getStrategy().toString();
        return TransferJobMapper.toModel(
            transfers.apply(id, user(), false, new ApplyCommand(strategy))
        );
    }
    @DELETE @Path("/{id}")
    public Response cancelImport(@PathParam("id") UUID id) {
        transfers.cancel(id, user(), "IMPORT", false); return Response.noContent().build();
    }

    private UUID user() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return session.userId();
    }
}
