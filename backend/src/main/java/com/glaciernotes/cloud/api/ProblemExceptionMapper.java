package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.application.content.ContentFailure;
import com.glaciernotes.cloud.application.image.ImageFailure;
import com.glaciernotes.cloud.generated.model.ProblemDetails;
import com.glaciernotes.cloud.generated.model.ValidationError;
import jakarta.validation.ConstraintViolationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;

@Provider
public class ProblemExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(ProblemExceptionMapper.class);

    @Context
    UriInfo uriInfo;
    @Inject
    com.glaciernotes.cloud.application.content.ContentService content;

    @Override
    public Response toResponse(Throwable exception) {
        var correlationId = Objects.toString(MDC.get("correlationId"), "unavailable");
        var description = describe(exception);
        if (exception instanceof ContentFailure failure && failure.conflictOwner() != null) {
            try { content.recordConflictSnapshot(failure.conflictOwner(), failure.conflictNoteId()); }
            catch (RuntimeException snapshotFailure) {
                LOG.warnf(
                    snapshotFailure,
                    "Could not record conflict snapshot correlationId=%s",
                    correlationId
                );
            }
        }
        if (description.status() >= 500 && !(exception instanceof SetupFailure)
            && !(exception instanceof AuthenticationFailure)
            && !(exception instanceof LifecycleFailure)
            && !(exception instanceof ContentFailure)
            && !(exception instanceof ImageFailure)) {
            LOG.errorf(
                exception,
                "Unhandled request failure correlationId=%s",
                correlationId
            );
        }

        var problem = new ProblemDetails()
            .type("https://glacier-notes.example/problems/"
                + description.errorCode().toLowerCase().replace('_', '-'))
            .title(description.title())
            .status(description.status())
            .detail(description.detail())
            .instance(uriInfo.getRequestUri().getPath())
            .correlationId(correlationId)
            .errorCode(description.errorCode())
            .validationErrors(description.validationErrors());
        if (exception instanceof ContentFailure contentFailure
            && contentFailure.currentVersion() != null) {
            problem.currentVersion(contentFailure.currentVersion());
            if (contentFailure.currentUpdatedAt() != null) {
                problem.currentUpdatedAt(contentFailure.currentUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
            }
        }

        var response = Response.status(description.status())
            .type("application/problem+json")
            .entity(problem);
        if (description.retryAfterSeconds() > 0) {
            response.header("Retry-After", description.retryAfterSeconds());
        }
        return response.build();
    }

    private Description describe(Throwable exception) {
        if (exception instanceof SetupFailure setupFailure) {
            return describeSetupFailure(setupFailure);
        }
        if (exception instanceof AuthenticationFailure authenticationFailure) {
            return describeAuthenticationFailure(authenticationFailure);
        }
        if (exception instanceof LifecycleFailure lifecycleFailure) {
            return describeLifecycleFailure(lifecycleFailure);
        }
        if (exception instanceof ContentFailure contentFailure) {
            return describeContentFailure(contentFailure);
        }
        if (exception instanceof ImageFailure imageFailure) {
            int status = switch (imageFailure.reason()) {
                case INVALID -> 422;
                case TOO_LARGE -> 413;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case UNAVAILABLE -> 503;
            };
            return new Description(status, status == 413 ? "Image Too Large" : status == 422 ? "Invalid Image" :
                status == 404 ? "Not Found" : status == 409 ? "Image Still Referenced" : "Image Storage Unavailable",
                imageFailure.code(), imageFailure.getMessage(), List.of(), 0);
        }
        if (exception instanceof ConstraintViolationException violations) {
            var validationErrors = violations.getConstraintViolations().stream()
                .map(violation -> new ValidationError()
                    .field(lastNode(violation.getPropertyPath().toString()))
                    .message(violation.getMessage()))
                .toList();
            return new Description(
                422, "Validation Failed", "VALIDATION_FAILED",
                "The request contains invalid values.", validationErrors, 0
            );
        }
        var status = exception instanceof WebApplicationException webException
            ? webException.getResponse().getStatus()
            : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (status == 401) {
            return new Description(
                401, "Authentication Required", "AUTH_SESSION_EXPIRED",
                "A valid session is required.", List.of(), 0
            );
        }
        if (status == 403) {
            return new Description(
                403, "Forbidden", "AUTH_FORBIDDEN",
                "You are not permitted to perform this action.", List.of(), 0
            );
        }
        if (status >= 400 && status < 500) {
            return describeClientError(status);
        }
        return new Description(
            status,
            "Internal Server Error",
            "INTERNAL_ERROR",
            "The request could not be completed.",
            List.of(),
            0
        );
    }

    private Description describeClientError(int status) {
        return switch (status) {
            case 400 -> clientError(status, "Bad Request", "REQUEST_INVALID",
                "The request could not be understood.");
            case 404 -> clientError(status, "Not Found", "ENTITY_NOT_FOUND",
                "The requested resource is unavailable.");
            case 405 -> clientError(status, "Method Not Allowed", "METHOD_NOT_ALLOWED",
                "The requested method is not supported for this resource.");
            case 406 -> clientError(status, "Not Acceptable", "NOT_ACCEPTABLE",
                "The requested response format is not available.");
            case 409 -> clientError(status, "Conflict", "REQUEST_CONFLICT",
                "The request conflicts with the current resource state.");
            case 413 -> clientError(status, "Request Too Large", "REQUEST_TOO_LARGE",
                "The request body exceeds the permitted size.");
            case 415 -> clientError(status, "Unsupported Media Type", "UNSUPPORTED_MEDIA_TYPE",
                "The request media type is not supported.");
            case 422 -> clientError(status, "Validation Failed", "VALIDATION_FAILED",
                "The request contains invalid values.");
            case 429 -> clientError(status, "Too Many Requests", "RATE_LIMITED",
                "Too many requests were received.");
            default -> clientError(status, "Request Rejected", "REQUEST_REJECTED",
                "The request was rejected.");
        };
    }

    private Description clientError(int status, String title, String code, String detail) {
        return new Description(status, title, code, detail, List.of(), 0);
    }

    private Description describeSetupFailure(SetupFailure failure) {
        return switch (failure.reason()) {
            case ALREADY_INITIALIZED -> description(404, "Not Found", "ENTITY_NOT_FOUND", failure);
            case DENIED -> description(403, "Setup Denied", "SETUP_DENIED", failure);
            case UNAVAILABLE -> description(503, "Setup Unavailable", "SETUP_UNAVAILABLE", failure);
            case INVALID_INPUT -> new Description(
                422,
                "Validation Failed",
                "VALIDATION_FAILED",
                failure.getMessage(),
                failure.violations().stream()
                    .map(violation -> new ValidationError()
                        .field(violation.field())
                        .message(violation.message()))
                    .toList(),
                0
            );
            case RATE_LIMITED -> new Description(
                429, "Too Many Requests", "SETUP_RATE_LIMITED", failure.getMessage(),
                List.of(), failure.retryAfterSeconds()
            );
            case CONFLICT -> description(409, "Conflict", "SETUP_CONFLICT", failure);
        };
    }

    private Description description(int status, String title, String code, SetupFailure failure) {
        return new Description(status, title, code, failure.getMessage(), List.of(), 0);
    }

    private Description describeAuthenticationFailure(AuthenticationFailure failure) {
        return switch (failure.reason()) {
            case INVALID_CREDENTIALS -> new Description(
                401, "Invalid Credentials", "AUTH_INVALID_CREDENTIALS",
                failure.getMessage(), List.of(), 0
            );
            case RATE_LIMITED -> new Description(
                429, "Too Many Requests", "AUTH_RATE_LIMITED",
                failure.getMessage(), List.of(), failure.retryAfterSeconds()
            );
            case SESSION_NOT_FOUND -> new Description(
                401, "Authentication Required", "AUTH_SESSION_EXPIRED",
                failure.getMessage(), List.of(), 0
            );
            case CSRF_INVALID -> new Description(
                403, "Request Verification Failed", "CSRF_INVALID",
                failure.getMessage(), List.of(), 0
            );
        };
    }

    private Description describeLifecycleFailure(LifecycleFailure failure) {
        return switch (failure.reason()) {
            case NOT_FOUND -> new Description(404, "Not Found", "ENTITY_NOT_FOUND",
                failure.getMessage(), List.of(), 0);
            case INVALID_TOKEN -> new Description(404, "Invalid Token", "TOKEN_INVALID_OR_EXPIRED",
                failure.getMessage(), List.of(), 0);
            case CONFLICT -> new Description(409, "Identity Conflict", "IDENTITY_CONFLICT",
                failure.getMessage(), List.of(), 0);
            case INVALID_STATE -> new Description(409, "Invalid Account State", "ACCOUNT_STATE_CONFLICT",
                failure.getMessage(), List.of(), 0);
            case LAST_ADMIN -> new Description(409, "Last Administrator Required", "LAST_ADMIN_REQUIRED",
                failure.getMessage(), List.of(), 0);
            case INVALID_CREDENTIALS -> new Description(403, "Current Password Required", "CURRENT_PASSWORD_INVALID",
                failure.getMessage(), List.of(), 0);
            case UNAVAILABLE -> new Description(503, "Service Unavailable", "LIFECYCLE_UNAVAILABLE",
                failure.getMessage(), List.of(), 0);
            case INVALID_INPUT -> new Description(422, "Validation Failed", "VALIDATION_FAILED",
                failure.getMessage(), failure.violations().stream()
                    .map(value -> new ValidationError().field(value.field()).message(value.message())).toList(), 0);
            case RATE_LIMITED -> new Description(429, "Too Many Requests", "LIFECYCLE_RATE_LIMITED",
                failure.getMessage(), List.of(), failure.retryAfterSeconds());
        };
    }

    private Description describeContentFailure(ContentFailure failure) {
        return switch (failure.reason()) {
            case NOT_FOUND -> new Description(404, "Not Found", "ENTITY_NOT_FOUND",
                failure.getMessage(), List.of(), 0);
            case CONFLICT -> new Description(409, "Content Conflict", "CONTENT_CONFLICT",
                failure.getMessage(), List.of(), 0);
            case INVALID_STATE -> new Description(409, "Invalid Content State", "CONTENT_STATE_CONFLICT",
                failure.getMessage(), List.of(), 0);
            case VERSION_CONFLICT -> new Description(409, "Content Changed", "CONTENT_VERSION_CONFLICT",
                failure.getMessage(), List.of(), 0);
            case INVALID -> new Description(422, "Validation Failed", "VALIDATION_FAILED",
                failure.getMessage(), failure.violations().stream()
                    .map(value -> new ValidationError().field(value.field()).message(value.message())).toList(), 0);
        };
    }

    private String lastNode(String path) {
        var lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    private record Description(
        int status,
        String title,
        String errorCode,
        String detail,
        List<ValidationError> validationErrors,
        long retryAfterSeconds
    ) {
    }
}
