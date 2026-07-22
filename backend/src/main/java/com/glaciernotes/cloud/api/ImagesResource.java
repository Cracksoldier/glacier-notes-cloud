package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.image.ImageService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/api/v1")
@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class ImagesResource {
    private final ImageService images;
    private final SecurityIdentity identity;
    public ImagesResource(ImageService images, SecurityIdentity identity) { this.images = images; this.identity = identity; }

    @POST @Path("/images") @Consumes(MediaType.MULTIPART_FORM_DATA) @Produces(MediaType.APPLICATION_JSON)
    public Response upload(@RestForm("file") FileUpload file) {
        if (file == null) throw com.glaciernotes.cloud.application.image.ImageFailure.invalid("IMAGE_INVALID", "A file part is required.");
        return Response.status(201).entity(images.upload(owner(), file.uploadedFile(), file.fileName())).build();
    }

    @GET @Path("/images/{id}/metadata") @Produces(MediaType.APPLICATION_JSON)
    public Object metadata(@PathParam("id") UUID id) { return images.metadata(owner(), id); }

    @GET @Path("/images/{id}")
    public Response download(@PathParam("id") UUID id) { return response(images.download(owner(), id, false)); }

    @GET @Path("/images/{id}/thumbnail")
    public Response thumbnail(@PathParam("id") UUID id) { return response(images.download(owner(), id, true)); }

    @DELETE @Path("/images/{id}")
    public Response delete(@PathParam("id") UUID id) { images.delete(owner(), id); return Response.noContent().build(); }

    private Response response(ImageService.Download download) {
        return Response.ok(download.object().stream(), download.mimeType())
            .header("Content-Length", download.object().contentLength())
            .header("ETag", '"' + download.etag() + '"')
            .header("Cache-Control", "private, max-age=86400")
            .header("Content-Disposition", "inline")
            .build();
    }

    private OwnerId owner() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return new OwnerId(session.userId());
    }
}
