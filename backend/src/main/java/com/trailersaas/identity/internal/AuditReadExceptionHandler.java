package com.trailersaas.identity.internal;

import com.trailersaas.audit.AuditQueryFailure;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=AuditReadController.class) @Order(5)
class AuditReadExceptionHandler {
    @ExceptionHandler(AuditQueryFailure.class) ProblemDetail failure(AuditQueryFailure e) {
        var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status),e.status==404?"The resource was not found.":"The request contains invalid values.");
        p.setTitle(e.status==404?"Resource not found":"Validation failed");p.setType(URI.create("https://api.trailersaas.example/problems/"+e.code.toLowerCase(Locale.ROOT).replace('_','-')));p.setProperty("code",e.code);return p;
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) ProblemDetail invalid(Exception e){return failure(AuditQueryFailure.invalid());}
}
