package com.trailersaas;

import java.util.ArrayList;
import java.util.UUID;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCorrelationFilterTests {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void qrTokenIsRedactedFromMdcAndProblemInstance() throws Exception {
        String token="synthetic-secret-qr-token";
        var request=new MockHttpServletRequest("GET","/api/v1/trailer-qr/"+token);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
                assertThat(MDC.get("http.path")).isEqualTo("/api/v1/trailer-qr/redacted");
                var problem=ProblemDetail.forStatus(404);
                ProblemDetailsConfiguration.addCorrelation(problem);
                assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/trailer-qr/redacted");
                assertThat(MDC.getCopyOfContextMap().toString()).doesNotContain(token);
            });
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void errorRedispatchRetainsOriginalTraceAfterOuterScopeClosed() throws Exception {
        var request = new MockHttpServletRequest("GET", "/test");
        var response = new MockHttpServletResponse();
        MDC.put("traceId", "original-technical-trace");
        MDC.put("spanId", "original-span");
        try {
            filter.doFilter(request, response, (req, res) -> {});
        } finally {
            MDC.clear();
        }
        String requestId = response.getHeader(RequestCorrelationFilter.HEADER);
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/test");
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo(requestId);
            assertThat(MDC.get("traceId")).isEqualTo("original-technical-trace");
            assertThat(MDC.get("spanId")).isEqualTo("original-span");
        });
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void validRequestIdIsReturnedAndScopedToRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestCorrelationFilter.HEADER, "client_request-123");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("client_request-123");
            assertThat(MDC.get("traceId")).isEqualTo("client_request-123");
            assertThat(MDC.get("http.path")).isEqualTo("/test");
        });
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("client_request-123");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad\r\nInjected: header", "bad\u0000value", "has spaces",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidRequestIdIsReplaced(String input) throws Exception {
        var request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestCorrelationFilter.HEADER, input);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(UUID.fromString(response.getHeader(RequestCorrelationFilter.HEADER))).isNotNull();
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isNotEqualTo(input);
    }

    @Test
    void missingRequestIdGeneratesDifferentIdsWithoutThreadContamination() throws Exception {
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/first"), first, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest("GET", "/second"), second, (req, res) -> {});
        assertThat(UUID.fromString(first.getHeader(RequestCorrelationFilter.HEADER))).isNotNull();
        assertThat(first.getHeader(RequestCorrelationFilter.HEADER))
                .isNotEqualTo(second.getHeader(RequestCorrelationFilter.HEADER));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void restoresOuterTraceAndCleansContextEvenWhenChainThrows() {
        MDC.put("traceId", "outer-trace");
        MDC.put("spanId", "outer-span");
        try {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/test"),
                    new MockHttpServletResponse(), (req, res) -> {
                        assertThat(MDC.get("traceId")).isEqualTo("outer-trace");
                        throw new ServletException("synthetic-secret-token");
                    })).isInstanceOf(ServletException.class);
            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("http.path")).isNull();
            assertThat(MDC.get("traceId")).isEqualTo("outer-trace");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void infrastructureProblemsAreSafeAndCorrelatable() throws Exception {
        MDC.put("requestId", "fallback-request");
        try {
            var exception = new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "SQL password=synthetic-secret-token");
            exception.getBody().setProperty("body", "synthetic-secret-token");
            exception.getBody().setType(java.net.URI.create("https://example.test/error?secret=synthetic-secret-token"));
            exception.getBody().setInstance(java.net.URI.create("https://example.test/path?secret=synthetic-secret-token"));
            var response = new InfrastructureExceptionHandler().handleException(exception,
                    new org.springframework.web.context.request.ServletWebRequest(new MockHttpServletRequest()));
            var problem = (ProblemDetail) response.getBody();
            assertThat(problem.getDetail()).doesNotContain("synthetic-secret-token", "SQL");
            assertThat(problem.getType().toString()).isEqualTo("about:blank");
            assertThat(problem.getInstance()).isNull();
            assertThat(problem.getProperties()).containsOnlyKeys("traceId")
                    .containsEntry("traceId", "fallback-request");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void correlationInterceptorPreservesApprovedProblemSemanticsAndExtensions() {
        MDC.put("requestId", "fallback-request");
        try {
            var interceptors = new ArrayList<ErrorResponse.Interceptor>();
            new ProblemDetailsConfiguration().addErrorResponseInterceptors(interceptors);
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Safe domain detail");
            var type = java.net.URI.create("https://api.trailersaas.example/problems/test-domain-error");
            var instance = java.net.URI.create("/safe-instance");
            problem.setType(type);
            problem.setInstance(instance);
            problem.setTitle("Test domain error");
            problem.setProperty("code", "TEST_DOMAIN_ERROR");
            var errors = java.util.List.of(java.util.Map.of("field", "exampleField", "message", "safe validation message"));
            problem.setProperty("errors", errors);
            problem.setProperty("safeExtension", "safe metadata");
            problem.setProperty("traceId", "stale-trace");
            interceptors.getFirst().handleError(problem, null);
            assertThat(problem.getProperties()).containsEntry("traceId", "fallback-request");
            MDC.put("traceId", "real-trace");
            interceptors.getFirst().handleError(problem, null);
            assertThat(problem.getType()).isEqualTo(type);
            assertThat(problem.getInstance()).isEqualTo(instance);
            assertThat(problem.getTitle()).isEqualTo("Test domain error");
            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getDetail()).isEqualTo("Safe domain detail");
            assertThat(problem.getProperties()).containsOnlyKeys("code", "errors", "safeExtension", "traceId")
                    .containsEntry("code", "TEST_DOMAIN_ERROR").containsEntry("errors", errors)
                    .containsEntry("safeExtension", "safe metadata").containsEntry("traceId", "real-trace");
        } finally {
            MDC.clear();
        }
    }
}
