package com.trailersaas.documents.internal;

import com.trailersaas.ProblemDetailsConfiguration;
import com.trailersaas.identity.IdempotencyKeyReusedException;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes={DocumentController.class,TrailerDamageUploadController.class})
@Order(10)
class DocumentExceptionHandler {
  @ExceptionHandler(DocumentFailure.class) ProblemDetail failure(DocumentFailure e) {
    var body=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status),e.getMessage());
    body.setTitle(e.status==400?"Validation failed":e.status==403?"Forbidden":e.status==404?"Resource not found":e.status==503?"Storage unavailable":"Conflict");
    body.setType(URI.create("https://api.trailersaas.example/problems/"+e.code.toLowerCase(Locale.ROOT).replace('_','-')));
    body.setProperty("code",e.code);ProblemDetailsConfiguration.addCorrelation(body);return body;
  }
  @ExceptionHandler(IdempotencyKeyReusedException.class) ProblemDetail reused(Exception e) { return failure(DocumentFailure.conflict("IDEMPOTENCY_KEY_REUSED")); }
  @ExceptionHandler({MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
  ProblemDetail invalid(Exception e) { return failure(DocumentFailure.invalid()); }
}
