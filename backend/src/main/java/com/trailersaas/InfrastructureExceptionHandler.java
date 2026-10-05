package com.trailersaas;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InfrastructureExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedException(Exception exception) {
        // Exception messages may contain secrets; the completion log records status and correlation.
        return ProblemDetailsConfiguration.problem(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
