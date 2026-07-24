package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.generated.model.ProblemDetails;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.stream.Collectors;

@Provider
@Priority(Priorities.USER)
public class RequestCompletionLogFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOG = Logger.getLogger(RequestCompletionLogFilter.class);
    private static final String STARTED = RequestCompletionLogFilter.class.getName() + ".started";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(STARTED, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object started = request.getProperty(STARTED);
        long duration = started instanceof Long value ? (System.nanoTime() - value) / 1_000_000 : 0;
        UriInfo uri = request.getUriInfo();
        String template = uri.getMatchedURIs().stream().findFirst().orElse(uri.getPath());
        String correlation = String.valueOf(request.getProperty(CorrelationId.PROPERTY));
        String error = response.getEntity() instanceof ProblemDetails problem
            ? problem.getErrorCode() : "none";
        LOG.infof("request_completed method=%s pathTemplate=%s status=%d durationMs=%d correlationId=%s errorCode=%s",
            request.getMethod(), template, response.getStatus(), duration, correlation, error);
    }
}
