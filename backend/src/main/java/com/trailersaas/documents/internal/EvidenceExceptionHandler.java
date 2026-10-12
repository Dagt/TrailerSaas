package com.trailersaas.documents.internal;

import com.trailersaas.documents.EvidenceValidationException;
import com.trailersaas.ProblemDetailsConfiguration;
import java.net.URI;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Applies to the future workflow controllers as well as the Documents transport. */
@RestControllerAdvice @Order(9)
class EvidenceExceptionHandler {
  @ExceptionHandler(EvidenceValidationException.class)
  ProblemDetail evidence(EvidenceValidationException e) {
    var body=ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(e.status()),e.getMessage());
    body.setTitle(e.status()==400?"Validation failed":e.status()==403?"Forbidden":e.status()==404?"Resource not found":"Conflict");
    body.setType(URI.create("https://api.trailersaas.example/problems/"+e.code().name().toLowerCase(java.util.Locale.ROOT).replace('_','-')));
    body.setProperty("code",e.code().name());ProblemDetailsConfiguration.addCorrelation(body);return body;
  }
}
