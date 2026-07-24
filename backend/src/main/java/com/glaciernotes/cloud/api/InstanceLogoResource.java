package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.operations.InstanceLogoService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/api/v1/instance/logo")
public class InstanceLogoResource {
    private final InstanceLogoService logos;

    public InstanceLogoResource(InstanceLogoService logos) {
        this.logos = logos;
    }

    @GET
    @Blocking
    @Produces({"image/png", "image/jpeg", "image/webp"})
    public Response download() {
        var value = logos.download();
        return Response.ok(value.file(), value.contentType())
            .header("ETag", "\"" + value.checksum() + "\"")
            .header("Cache-Control", "public, max-age=300")
            .build();
    }
}
