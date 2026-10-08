package com.trailersaas;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ProblemDetailsConfiguration implements WebMvcConfigurer {
    @Override
    public void addErrorResponseInterceptors(List<ErrorResponse.Interceptor> interceptors) {
        interceptors.add((body, error) -> addCorrelation(body));
    }

    static ProblemDetail safeInfrastructureProblem(HttpStatusCode status) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setType(URI.create("about:blank"));
        HttpStatus standardStatus = HttpStatus.resolve(body.getStatus());
        body.setTitle(standardStatus == null ? "HTTP error" : standardStatus.getReasonPhrase());
        body.setDetail(body.getStatus() >= 500
                ? "The server could not complete the request."
                : "The request could not be completed.");
        addCorrelation(body);
        return body;
    }

    public static void addCorrelation(ProblemDetail body) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                && servlet.getRequest().getRequestURI().startsWith("/api/v1/trailer-qr/")) {
            body.setInstance(URI.create("/api/v1/trailer-qr/redacted"));
        }
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = MDC.get("requestId");
        }
        body.setProperty("traceId", traceId == null ? UUID.randomUUID().toString() : traceId);
    }

    static String safeRequestPath(String path) {
        return path.startsWith("/api/v1/trailer-qr/") ? "/api/v1/trailer-qr/redacted" : path;
    }
}
