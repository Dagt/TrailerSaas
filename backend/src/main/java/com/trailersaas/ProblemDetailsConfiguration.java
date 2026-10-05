package com.trailersaas;

import java.util.List;
import java.util.UUID;
import java.net.URI;

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
        interceptors.add((body, error) -> sanitize(body));
    }

    static ProblemDetail problem(HttpStatusCode status) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        sanitize(body);
        return body;
    }

    private static void sanitize(ProblemDetail body) {
        HttpStatus status = HttpStatus.resolve(body.getStatus());
        body.setType(URI.create("about:blank"));
        body.setInstance(null);
        body.setTitle(status == null ? "HTTP error" : status.getReasonPhrase());
        body.setDetail(body.getStatus() >= 500
                ? "The server could not complete the request."
                : "The request could not be completed.");
        body.setProperties(null);
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = MDC.get("requestId");
        }
        body.setProperty("traceId", traceId == null ? UUID.randomUUID().toString() : traceId);
    }
}
