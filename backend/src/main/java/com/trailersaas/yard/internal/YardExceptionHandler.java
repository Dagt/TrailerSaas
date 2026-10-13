package com.trailersaas.yard.internal;

import com.trailersaas.ProblemDetailsConfiguration;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={YardController.class,YardMoveController.class,YardActionController.class,YardReportController.class})
@Order(10)
class YardExceptionHandler {
  @ExceptionHandler(YardFailure.class)
  ProblemDetail failure(YardFailure e) {
    var body=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status),e.getMessage());
    body.setTitle(e.status==400?"Validation failed":e.status==403?"Forbidden":e.status==404?"Resource not found":"Conflict");
    body.setType(URI.create("https://api.trailersaas.example/problems/"+e.code.toLowerCase(Locale.ROOT).replace('_','-')));
    body.setProperty("code",e.code);
    if(e.currentTrailer!=null)body.setProperty("currentTrailer",e.currentTrailer);
    ProblemDetailsConfiguration.addCorrelation(body);return body;
  }
  @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
  ProblemDetail invalid(Exception ignored) { return failure(YardFailure.invalid()); }
  @ExceptionHandler(com.trailersaas.identity.IdempotencyKeyReusedException.class)
  ProblemDetail reused(Exception ignored) { return failure(new YardFailure(409,"IDEMPOTENCY_KEY_REUSED","The operation identity was already used for a different request.")); }
  @ExceptionHandler(com.trailersaas.trailers.TrailerMovementRejectedException.class)
  ProblemDetail movement(com.trailersaas.trailers.TrailerMovementRejectedException e) {
    return failure(switch(e.reason()) {
      case VALIDATION_FAILED -> YardFailure.invalid();
      case RESOURCE_NOT_FOUND -> YardFailure.missing();
      case STALE_VERSION -> new YardFailure(409,"STALE_VERSION","The Trailer changed. Refresh before retrying.");
      case INVALID_STATE_TRANSITION -> new YardFailure(409,"INVALID_STATE_TRANSITION","The Trailer cannot perform this move in its current state.");
      case RESOURCE_CONFLICT -> YardFailure.conflict();
    });
  }
}
