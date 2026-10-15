package com.trailersaas.notifications.internal;

import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(basePackageClasses=AlertController.class) @Order(10)
class AlertExceptionHandler {
 @ExceptionHandler(AlertFailure.class) ProblemDetail failure(AlertFailure f){
  var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(f.status),f.getMessage());p.setTitle(f.code);
  p.setType(java.net.URI.create("https://api.trailersaas.example/problems/"+f.code.toLowerCase(java.util.Locale.ROOT).replace('_','-')));p.setProperty("code",f.code);return p;
 }
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 ProblemDetail invalid(Exception ignored){return failure(AlertFailure.invalid());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
 ProblemDetail conflict(Exception ignored){return failure(AlertFailure.state());}
}
