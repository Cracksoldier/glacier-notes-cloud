package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class RequestBodyLimitFilter {
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern ADMIN_IMPORT =
        Pattern.compile("^/api/v1/admin/users/[^/]+/imports$");

    private final BodyHandler defaultHandler;
    private final BodyHandler imageHandler;
    private final BodyHandler transferHandler;

    public RequestBodyLimitFilter(GlacierConfiguration configuration) {
        var policy = RequestBodyLimitPolicy.from(configuration);
        String uploadDirectory = configuration.http().uploadsDirectory().toString();
        defaultHandler = handler(uploadDirectory, policy.defaultMaximumBodyBytes());
        imageHandler = handler(uploadDirectory, policy.imageMaximumBodyBytes());
        transferHandler = handler(uploadDirectory, policy.transferMaximumBodyBytes());
    }

    @RouteFilter(1000)
    void limit(RoutingContext context) {
        String path = context.request().path();
        if (!path.startsWith("/api/v1/") || !BODY_METHODS.contains(context.request().method().name())) {
            context.next();
            return;
        }
        if ("/api/v1/imports".equals(path) || ADMIN_IMPORT.matcher(path).matches()) {
            transferHandler.handle(context);
        } else if ("/api/v1/images".equals(path)) {
            imageHandler.handle(context);
        } else {
            defaultHandler.handle(context);
        }
    }

    @Route(regex = "^/api/v1/.*", type = Route.HandlerType.FAILURE, order = 1)
    void bodyLimitFailure(RoutingContext context) {
        if (context.statusCode() != 413 || context.response().ended()) {
            context.next();
            return;
        }
        String correlationId = CorrelationIds.resolve(
            context.request().getHeader(CorrelationId.HEADER)
        );
        var problem = new JsonObject()
            .put("type", "https://glacier-notes.example/problems/request-too-large")
            .put("title", "Request Too Large")
            .put("status", 413)
            .put("detail", "The request body exceeds the permitted size.")
            .put("instance", context.request().path())
            .put("correlationId", correlationId)
            .put("errorCode", "REQUEST_TOO_LARGE");
        context.response()
            .setStatusCode(413)
            .putHeader("Content-Type", "application/problem+json")
            .putHeader(CorrelationId.HEADER, correlationId)
            .putHeader("Cache-Control", "no-store")
            .putHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
            .putHeader("X-Frame-Options", "DENY")
            .putHeader("X-Content-Type-Options", "nosniff")
            .putHeader("Referrer-Policy", "no-referrer")
            .putHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
            .end(problem.encode());
    }

    private BodyHandler handler(String uploadDirectory, long limit) {
        return BodyHandler.create(uploadDirectory)
            .setBodyLimit(limit)
            .setHandleFileUploads(true)
            .setMergeFormAttributes(true)
            .setDeleteUploadedFilesOnEnd(true);
    }
}
