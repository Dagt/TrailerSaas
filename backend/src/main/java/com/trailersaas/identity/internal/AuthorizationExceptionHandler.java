package com.trailersaas.identity.internal;

import java.net.URI;
import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
class AuthorizationExceptionHandler {
    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail denied(AuthorizationDeniedException exception) {
        var reason = exception.reason();
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, reason.detail());
        problem.setType(URI.create("https://api.trailersaas.example/problems/"
                + reason.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle("Forbidden");
        problem.setProperty("code", reason.name());
        return problem;
    }
}
