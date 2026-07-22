package com.glaciernotes.cloud.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

/** Serves the Angular entry point for routes handled by the client-side router. */
@Path("/")
public class SpaResource {
    private static final String INDEX_RESOURCE = "META-INF/resources/index.html";

    @GET
    @Path("{path: login|accept-invitation|forgot-password|reset-password|sessions|notes(?:/.*)?|admin(?:/.*)?}")
    @Produces(MediaType.TEXT_HTML)
    public Response index() throws IOException {
        var classLoader = Thread.currentThread().getContextClassLoader();
        try (var stream = classLoader.getResourceAsStream(INDEX_RESOURCE)) {
            if (stream == null) {
                throw new NotFoundException();
            }

            var cacheControl = new CacheControl();
            cacheControl.setNoCache(true);
            return Response.ok(stream.readAllBytes(), MediaType.TEXT_HTML_TYPE)
                .cacheControl(cacheControl)
                .build();
        }
    }
}
