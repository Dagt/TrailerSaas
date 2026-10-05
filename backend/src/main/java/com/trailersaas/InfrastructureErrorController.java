package com.trailersaas;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfrastructureErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    ProblemDetail error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return ProblemDetailsConfiguration.problem(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status);
    }
}
