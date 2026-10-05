package com.trailersaas;

import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class InfrastructureExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        // Only errors reaching this fallback are untrusted; domain advice runs before it.
        return createResponseEntity(ProblemDetailsConfiguration.safeInfrastructureProblem(status),
                headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedException(Exception exception) {
        // Exception messages may contain secrets; the completion log records status and correlation.
        return ProblemDetailsConfiguration.safeInfrastructureProblem(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
