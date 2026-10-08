package com.trailersaas.trailers.internal;

import java.net.URI;
import java.util.Locale;
import com.trailersaas.ProblemDetailsConfiguration;

import jakarta.persistence.OptimisticLockException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={TrailerController.class,TrailerQrController.class})
@Order(10)
class TrailerExceptionHandler {
    @ExceptionHandler(TrailerFailure.class)
    ProblemDetail failure(TrailerFailure failure) {
        var body=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(failure.status),failure.getMessage());
        body.setTitle(switch(failure.status) { case 400 -> "Validation failed"; case 404 -> "Resource not found"; default -> "Conflict"; });
        body.setType(URI.create("https://api.trailersaas.example/problems/"+failure.code.toLowerCase(Locale.ROOT).replace('_','-')));
        body.setProperty("code",failure.code);
        ProblemDetailsConfiguration.addCorrelation(body);
        return body;
    }
    @ExceptionHandler({OptimisticLockingFailureException.class,OptimisticLockException.class})
    ProblemDetail stale(Exception ignored) { return failure(TrailerFailure.stale()); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) { return failure(TrailerFailure.invalid()); }
}
