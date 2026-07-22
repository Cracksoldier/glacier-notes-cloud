package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.transfer.TransferModels.ExportCommand;
import com.glaciernotes.cloud.application.transfer.TransferService;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.file.Files;
import java.util.UUID;

@Path("/api/v1/exports")
@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
public class ExportsResource {
    private final TransferService transfers;
    private final SecurityIdentity identity;

    public ExportsResource(TransferService transfers, SecurityIdentity identity) {
        this.transfers = transfers;
        this.identity = identity;
    }

    @POST @Consumes(MediaType.APPLICATION_JSON)
    public Response create(ExportCommand request) {
        return Response.accepted(transfers.createExport(user(), request)).build();
    }

    @GET @Path("/{id}")
    public Object get(@PathParam("id") UUID id) { return transfers.get(id, user(), "EXPORT", false); }

    @DELETE @Path("/{id}")
    public Response cancel(@PathParam("id") UUID id) {
        transfers.cancel(id, user(), "EXPORT", false);
        return Response.noContent().build();
    }

    @GET @Path("/{id}/download")
    public Response download(@PathParam("id") UUID id) throws java.io.IOException {
        var job = transfers.downloadable(id, user());
        java.nio.file.Path path = java.nio.file.Path.of(job.temporaryPath());
        return Response.ok(Files.newInputStream(path), MediaType.APPLICATION_JSON)
            .header("Content-Length", Files.size(path))
            .header("Content-Disposition", "attachment; filename=glacier-export-" + job.id() + ".glacier.json")
            .header("Cache-Control", "private, no-store").build();
    }

    private UUID user() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return session.userId();
    }
}
