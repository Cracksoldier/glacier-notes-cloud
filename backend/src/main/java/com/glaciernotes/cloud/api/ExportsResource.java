package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.transfer.TransferModels.ExportCommand;
import com.glaciernotes.cloud.application.transfer.TransferService;
import com.glaciernotes.cloud.generated.model.ExportRequest;
import com.glaciernotes.cloud.generated.model.TransferJob;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.io.IOException;
import java.io.InputStream;
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
    @ResponseStatus(202)
    public TransferJob create(@Valid ExportRequest request) {
        String scope = request == null || request.getScope() == null ? null : request.getScope().toString();
        UUID resourceId = request == null ? null : request.getResourceId();
        return TransferJobMapper.toModel(
            transfers.createExport(user(), new ExportCommand(scope, resourceId))
        );
    }

    @GET @Path("/{id}")
    public TransferJob get(@PathParam("id") UUID id) {
        return TransferJobMapper.toModel(transfers.get(id, user(), "EXPORT", false));
    }

    @DELETE @Path("/{id}")
    public Response cancel(@PathParam("id") UUID id) {
        transfers.cancel(id, user(), "EXPORT", false);
        return Response.noContent().build();
    }

    @GET @Path("/{id}/download")
    public Response download(@PathParam("id") UUID id) throws IOException {
        var job = transfers.downloadable(id, user());
        return downloadResponse(java.nio.file.Path.of(job.temporaryPath()), job.id());
    }

    static Response downloadResponse(java.nio.file.Path path, UUID jobId) throws IOException {
        long size = Files.size(path);
        InputStream stream = Files.newInputStream(path);
        try {
            return Response.ok(stream, MediaType.APPLICATION_JSON)
                .header("Content-Length", size)
                .header("Content-Disposition", "attachment; filename=glacier-export-" + jobId + ".glacier.json")
                .header("Cache-Control", "private, no-store").build();
        } catch (RuntimeException failure) {
            try {
                stream.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private UUID user() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return session.userId();
    }
}
