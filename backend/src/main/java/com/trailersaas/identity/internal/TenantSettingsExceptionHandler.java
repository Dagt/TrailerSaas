package com.trailersaas.identity.internal;

import java.net.URI;
import java.util.List;
import java.util.Map;
import jakarta.persistence.OptimisticLockException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TenantSettingsController.class) @Order(5)
class TenantSettingsExceptionHandler {
    @ExceptionHandler(TenantSettingsFailure.class)
    ProblemDetail failure(TenantSettingsFailure error) {
        String detail = switch (error.code) {
            case "STALE_VERSION" -> "The resource changed; reload the latest version before retrying.";
            case "RESOURCE_NOT_FOUND" -> "The resource was not found.";
            default -> "The request contains invalid values.";
        };
        var result = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(error.status), detail);
        result.setTitle(switch (error.status) { case 400 -> "Validation failed"; case 404 -> "Resource not found"; default -> "Conflict"; });
        result.setType(URI.create("https://api.trailersaas.example/problems/" + error.code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        result.setProperty("code", error.code);
        if (error.field != null) { result.setProperty("errors", List.of(Map.of("field", error.field, "message", "must be a valid value"))); }
        return result;
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail stale(Exception ignored) { return failure(TenantSettingsFailure.stale()); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) { return failure(TenantSettingsFailure.invalid("body")); }
}
