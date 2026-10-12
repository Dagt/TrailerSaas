package com.trailersaas.inspections.internal;

import com.trailersaas.ProblemDetailsConfiguration;
import com.trailersaas.identity.IdempotencyKeyReusedException;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice(assignableTypes={ShiftController.class,RoadOperationsController.class}) @Order(10)
class ShiftExceptionHandler {
  @ExceptionHandler(ShiftFailure.class) ProblemDetail failure(ShiftFailure e) {return problem(e.status,e.code);}
  static ProblemDetail problem(int status,String code) {
    var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),"The Driver operation could not be completed.");p.setTitle(status==400?"Validation failed":status==404?"Resource not found":"Conflict");
    p.setType(URI.create("https://api.trailersaas.example/problems/"+code.toLowerCase(Locale.ROOT).replace('_','-')));p.setProperty("code",code);ProblemDetailsConfiguration.addCorrelation(p);return p;
  }
  @ExceptionHandler(IdempotencyKeyReusedException.class) ProblemDetail reused(Exception e) {return problem(409,"IDEMPOTENCY_KEY_REUSED");}
  @ExceptionHandler(com.trailersaas.trips.DriverOperationRejectedException.class) ProblemDetail execution(com.trailersaas.trips.DriverOperationRejectedException e){return problem(e.status(),e.code());}
  @ExceptionHandler(com.trailersaas.orders.OrderPlanningRejectedException.class) ProblemDetail fulfillment(com.trailersaas.orders.OrderPlanningRejectedException e){return problem(e.reason()==com.trailersaas.orders.OrderPlanningRejectedException.Reason.MISSING?404:409,e.reason()==com.trailersaas.orders.OrderPlanningRejectedException.Reason.MISSING?"RESOURCE_NOT_FOUND":e.reason()==com.trailersaas.orders.OrderPlanningRejectedException.Reason.STATE?"INVALID_STATE_TRANSITION":"RESOURCE_CONFLICT");}
  @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingRequestHeaderException.class}) ProblemDetail invalid(Exception e) {return problem(400,"VALIDATION_FAILED");}
}
