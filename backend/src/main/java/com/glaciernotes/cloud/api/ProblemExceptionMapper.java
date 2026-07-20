package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.generated.model.ProblemDetails;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.Objects;

@Provider
public class ProblemExceptionMapper implements ExceptionMapper<Throwable> {
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        var status = exception instanceof WebApplicationException webException
            ? webException.getResponse().getStatus()
            : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        var title = status == 404 ? "Not Found" : status >= 500 ? "Internal Server Error" : "Request Failed";
        var errorCode = status == 404 ? "ENTITY_NOT_FOUND" : "INTERNAL_ERROR";
        var detail = status >= 500
            ? "The request could not be completed."
            : "The requested resource is unavailable.";
        var correlationId = Objects.toString(MDC.get("correlationId"), "unavailable");

        var problem = new ProblemDetails()
            .type("https://glacier-notes.example/problems/" + errorCode.toLowerCase().replace('_', '-'))
            .title(title)
            .status(status)
            .detail(detail)
            .instance(uriInfo.getRequestUri().getPath())
            .correlationId(correlationId)
            .errorCode(errorCode);

        return Response.status(status)
            .type("application/problem+json")
            .entity(problem)
            .build();
    }
}
