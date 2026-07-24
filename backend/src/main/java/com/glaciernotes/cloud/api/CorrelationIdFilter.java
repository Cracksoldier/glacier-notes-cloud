package com.glaciernotes.cloud.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) {
        var incoming = requestContext.getHeaderString(CorrelationId.HEADER);
        var correlationId = CorrelationIds.resolve(incoming);
        requestContext.setProperty(CorrelationId.PROPERTY, correlationId);
        MDC.put("correlationId", correlationId);
    }

    @Override
    public void filter(
        ContainerRequestContext requestContext,
        ContainerResponseContext responseContext
    ) {
        var correlationId = requestContext.getProperty(CorrelationId.PROPERTY);
        if (correlationId != null) {
            responseContext.getHeaders().putSingle(CorrelationId.HEADER, correlationId.toString());
        }
        MDC.remove("correlationId");
    }
}
