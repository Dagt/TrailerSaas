package com.trailersaas.identity.internal;

import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses=DeviceController.class) @Order(10)
class DeviceExceptionHandler {
 @ExceptionHandler(DeviceFailure.class) ProblemDetail failure(DeviceFailure f){var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(f.status),f.getMessage());p.setTitle("Device registration");p.setProperty("code",f.status==400?"VALIDATION_FAILED":f.status==404?"RESOURCE_NOT_FOUND":"RESOURCE_CONFLICT");return p;}
}
