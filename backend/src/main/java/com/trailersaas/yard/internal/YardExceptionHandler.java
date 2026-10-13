package com.trailersaas.yard.internal;

import com.trailersaas.ProblemDetailsConfiguration;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=YardController.class)
@Order(10)
class YardExceptionHandler {
  @ExceptionHandler(YardFailure.class)
  ProblemDetail failure(YardFailure e) {
    var body=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status),e.getMessage());
    body.setTitle(e.status==400?"Validation failed":e.status==404?"Resource not found":"Conflict");
    body.setType(URI.create("https://api.trailersaas.example/problems/"+e.code.toLowerCase(Locale.ROOT).replace('_','-')));
    body.setProperty("code",e.code);
    if(e.currentTrailer!=null)body.setProperty("currentTrailer",e.currentTrailer);
    ProblemDetailsConfiguration.addCorrelation(body);return body;
  }
  @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
  ProblemDetail invalid(Exception ignored) { return failure(YardFailure.invalid()); }
}
