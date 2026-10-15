package com.trailersaas.customers.internal;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.OptimisticLockException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = MasterDataController.class)
@Order(10)
class MasterDataExceptionHandler {
    @ExceptionHandler(MasterDataFailure.class)
    ProblemDetail failure(MasterDataFailure failure) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(failure.status), failure.detail);
        body.setTitle(switch (failure.status) {
            case 400 -> "Validation failed";
            case 403 -> "Forbidden";
            case 404 -> "Resource not found";
            default -> "Conflict";
        });
        body.setType(URI.create("https://api.trailersaas.example/problems/"
                + failure.code.toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("code", failure.code);
        if (failure.field != null) {
            body.setProperty("errors", List.of(Map.of("field", failure.field, "message", "must be a valid value")));
        }
        return body;
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail stale(Exception ignored) { return failure(MasterDataFailure.stale()); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(Exception ignored) { return failure(MasterDataFailure.conflict()); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) { return failure(MasterDataFailure.invalid("body")); }
}
