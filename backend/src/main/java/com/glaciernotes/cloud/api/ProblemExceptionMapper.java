package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.setup.SetupFailure;
import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.application.content.ContentFailure;
import com.glaciernotes.cloud.application.image.ImageFailure;
import com.glaciernotes.cloud.generated.model.ProblemDetails;
import com.glaciernotes.cloud.generated.model.ValidationError;
import jakarta.validation.ConstraintViolationException;
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

    @Override
    public Response toResponse(Throwable exception) {
        var correlationId = Objects.toString(MDC.get("correlationId"), "unavailable");
        var description = describe(exception);
        if (description.status() >= 500 && !(exception instanceof SetupFailure)
            && !(exception instanceof AuthenticationFailure)
            && !(exception instanceof LifecycleFailure)
            && !(exception instanceof ContentFailure)) {
            LOG.errorf(
                "Unhandled request failure correlationId=%s exception=%s",
                correlationId,
                exception.getClass().getName()
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
        return new Description(
            status,
            status == 404 ? "Not Found" : status >= 500 ? "Internal Server Error" : "Request Failed",
            status == 404 ? "ENTITY_NOT_FOUND" : "INTERNAL_ERROR",
            status >= 500 ? "The request could not be completed." : "The requested resource is unavailable.",
            List.of(),
            0
        );
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
