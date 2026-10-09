package com.trailersaas.orders.internal;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.trailersaas.ProblemDetailsConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = OrderController.class) @Order(10)
class OrderExceptionHandler {
    @ExceptionHandler(OrderFailure.class)
    ProblemDetail failure(OrderFailure failure) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(failure.status), failure.getMessage());
        body.setTitle(switch (failure.status) { case 400 -> "Validation failed"; case 403 -> "Forbidden"; case 404 -> "Resource not found"; default -> "Conflict"; });
        body.setType(URI.create("https://api.trailersaas.example/problems/" + failure.code.toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("code", failure.code);
        if (failure.field != null) { body.setProperty("errors", List.of(Map.of("field", failure.field, "message", "contains invalid values"))); }
        ProblemDetailsConfiguration.addCorrelation(body);
        return body;
    }
    @ExceptionHandler(com.trailersaas.orders.OrderPlanningRejectedException.class)
    ProblemDetail planning(com.trailersaas.orders.OrderPlanningRejectedException failure) {
        return failure(switch(failure.reason()) {case MISSING -> OrderFailure.missing();case STATE -> OrderFailure.transition();case CONFLICT -> OrderFailure.conflict();});
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) { return failure(OrderFailure.invalid("body")); }
}
