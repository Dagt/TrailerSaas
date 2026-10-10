package com.trailersaas.trips.internal;

import com.trailersaas.ProblemDetailsConfiguration;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = {
      TripController.class,
      DriverUpdatesController.class,
      DriverWorkController.class,
      DispatchBoardController.class,
      TripCouplingController.class
    })
@Order(10)
class TripExceptionHandler {
  @ExceptionHandler(TripFailure.class)
  ProblemDetail failure(TripFailure failure) {
    var body =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(failure.status), failure.getMessage());
    body.setTitle(
        switch (failure.status) {
          case 400 -> "Validation failed";
          case 403 -> "Forbidden";
          case 404 -> "Resource not found";
          default -> "Conflict";
        });
    body.setType(
        URI.create(
            "https://api.trailersaas.example/problems/"
                + failure.code.toLowerCase(Locale.ROOT).replace('_', '-')));
    body.setProperty("code", failure.code);
    if (failure.field != null) {
      body.setProperty(
          "errors", List.of(Map.of("field", failure.field, "message", "contains invalid values")));
    }
    ProblemDetailsConfiguration.addCorrelation(body);
    return body;
  }

  @ExceptionHandler({
    org.springframework.orm.ObjectOptimisticLockingFailureException.class,
    jakarta.persistence.OptimisticLockException.class
  })
  ProblemDetail stale(Exception ignored) {
    return failure(TripFailure.stale());
  }

  @ExceptionHandler(com.trailersaas.orders.OrderPlanningRejectedException.class)
  ProblemDetail planning(com.trailersaas.orders.OrderPlanningRejectedException failure) {
    return failure(
        switch (failure.reason()) {
          case MISSING -> TripFailure.missing();
          case STATE -> TripFailure.state();
          case CONFLICT -> TripFailure.conflict();
        });
  }

  @ExceptionHandler(com.trailersaas.trailers.CouplingCommandRejectedException.class)
  ProblemDetail coupling(com.trailersaas.trailers.CouplingCommandRejectedException e) {
    return failure(
        switch (e.reason()) {
          case MISSING -> TripFailure.missing();
          case STATE -> TripFailure.tripState();
          case STALE -> TripFailure.stale();
          case CONFLICT -> TripFailure.conflict();
          case TRAILER_COUPLED -> TripFailure.physical("TRAILER_ALREADY_COUPLED");
          case TRACTOR_COUPLED -> TripFailure.physical("TRACTOR_ALREADY_COUPLED");
          case SUBSTITUTION -> TripFailure.physical("TRAILER_SUBSTITUTION_NOT_AUTHORIZED");
          case KEY_REUSED -> TripFailure.physical("IDEMPOTENCY_KEY_REUSED");
        });
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail invalid(Exception ignored) {
    return failure(TripFailure.invalid("body"));
  }
}
