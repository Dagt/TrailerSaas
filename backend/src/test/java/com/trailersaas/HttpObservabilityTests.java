package com.trailersaas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
@Import(HttpObservabilityTests.TestEndpoints.class)
class HttpObservabilityTests extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private Tracer tracer;

    @ParameterizedTest
    @CsvSource({"/test/failure,500", "/test/parameter,400", "/not-found,404", "/actuator/metrics,404",
            "/test/parameter?value=bad,400", "/test/error-response,422",
            "/test/parameter?value=synthetic-secret-token,400",
            "/test/failure?signature=synthetic-secret-token,500"})
    void httpErrorsUseRealTraceAndSafeProblemDetails(String path, int status, CapturedOutput output) throws Exception {
        String requestId = "http-error-" + status;
        var response = get(path, requestId);
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue(RequestCorrelationFilter.HEADER)).contains(requestId);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        String traceId = JsonPath.read(response.body(), "$.traceId");
        assertThat(traceId).matches("[a-f0-9]{32}").isNotEqualTo(requestId);
        assertThat(response.body()).doesNotContain("synthetic-secret-token", "SQL", "Exception", "password",
                "components", "details");
        List<Map<String, Object>> logs = output.getOut().lines()
                .filter(line -> line.startsWith("{") && line.contains("HTTP request completed"))
                .map(line -> JsonPath.<Map<String, Object>>read(line, "$")).toList();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log).containsEntry("requestId", requestId)
                    .containsEntry("traceId", traceId)
                    .containsEntry("http.status", status);
            assertThat(log.get("spanId")).asString().matches("[a-f0-9]{16}");
        });
        assertThat(output.getOut()).doesNotContain("synthetic-secret-token");
    }

    @Test
    void domainAdviceWinsAndItsProblemDetailsRemainCorrelated(CapturedOutput output) throws Exception {
        var response = get("/test/domain-error", "domain-error");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        Map<String, Object> problem = JsonPath.read(response.body(), "$");
        assertThat(problem).containsEntry("type", "https://api.trailersaas.example/problems/test-domain-error")
                .containsEntry("title", "Test domain error").containsEntry("status", 409)
                .containsEntry("detail", "Safe domain detail").containsEntry("code", "TEST_DOMAIN_ERROR")
                .containsEntry("errors", List.of(Map.of("field", "exampleField", "message", "safe validation message")));
        String traceId = (String) problem.get("traceId");
        assertThat(traceId).matches("[a-f0-9]{32}");
        assertThat(output.getOut().lines()
                .filter(line -> line.startsWith("{") && line.contains("HTTP request completed"))
                .map(line -> JsonPath.<Map<String, Object>>read(line, "$")))
                .anySatisfy(log -> assertThat(log).containsEntry("requestId", "domain-error")
                        .containsEntry("traceId", traceId).containsEntry("http.status", 409));
        assertThat(response.body()).doesNotContain("synthetic-secret-token");
        assertThat(output.getOut()).doesNotContain("synthetic-secret-token");
    }

    @Test
    void recordsStandardHttpMetricsAndTracingWithoutExporters() throws Exception {
        get("/test/ok", "metrics-test");
        assertThat(tracer).isNotNull();
        // The client can receive the body before the server closes its outer observation.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(meters.find("http.server.requests").tag("uri", "/test/ok").timer()).isNotNull();
            assertThat(meters.find("http.server.requests").tag("uri", "/test/ok").timer().count()).isPositive();
        });
        assertThat(meters.find("jvm.memory.used").meters()).isNotEmpty();
    }

    @Test
    void errorDispatcherUsesSafeProblemDetails(CapturedOutput output) throws Exception {
        var response = get("/test/send-error", "dispatcher-error");
        assertThat(response.statusCode()).isEqualTo(503);
        String traceId = JsonPath.read(response.body(), "$.traceId");
        assertThat(traceId).matches("[a-f0-9]{32}");
        List<Map<String, Object>> logs = output.getOut().lines()
                .filter(line -> line.startsWith("{") && line.contains("HTTP request completed")
                        && line.contains("\"requestId\":\"dispatcher-error\""))
                .map(line -> JsonPath.<Map<String, Object>>read(line, "$")).toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).containsEntry("traceId", traceId);
        assertThat(response.body()).doesNotContain("synthetic-secret-token");
        assertThat(output.getOut()).doesNotContain("synthetic-secret-token");
    }

    private HttpResponse<String> get(String path, String requestId) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header(RequestCorrelationFilter.HEADER, requestId)
                    .header("Authorization", "Bearer synthetic-secret-token")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {
        @Bean
        Controller testController() { return new Controller(); }
        @Bean
        DomainAdvice testDomainAdvice() { return new DomainAdvice(); }
    }

    static class TestDomainException extends RuntimeException {}

    @RestControllerAdvice
    @Order(0)
    @org.springframework.boot.test.context.TestComponent
    static class DomainAdvice {
        @ExceptionHandler(TestDomainException.class)
        ProblemDetail domainError(TestDomainException exception) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Safe domain detail");
            problem.setType(URI.create("https://api.trailersaas.example/problems/test-domain-error"));
            problem.setTitle("Test domain error");
            problem.setProperty("code", "TEST_DOMAIN_ERROR");
            problem.setProperty("errors", List.of(Map.of("field", "exampleField", "message", "safe validation message")));
            return problem;
        }
    }

    @RestController
    @org.springframework.boot.test.context.TestComponent
    static class Controller {
        @GetMapping("/test/ok")
        String ok() { return "ok"; }
        @GetMapping("/test/domain-error")
        String domainError() { throw new TestDomainException(); }
        @GetMapping("/test/failure")
        String failure() { throw new IllegalStateException("SQL password=synthetic-secret-token"); }
        @GetMapping("/test/parameter")
        String parameter(@RequestParam int value) { return "ok"; }
        @GetMapping("/test/error-response")
        String errorResponse() {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "synthetic-secret-token");
        }
        @GetMapping("/test/send-error")
        void sendError(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.sendError(503, "synthetic-secret-token");
        }
    }
}
