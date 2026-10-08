package com.trailersaas;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingTests {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/test","/api/v1/trailer-qr/synthetic-public-qr-token"})
    void nativeJsonCompletionLogHasCorrelationAndNeverCopiesHeadersBodiesOrQuery(String path) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
        var appender = new ListAppender<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        var encoder = new StructuredLogEncoder();
        var encoderContext = new LoggerContext();
        encoderContext.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(encoderContext);
        encoder.setFormat("logstash");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();
        try {
            var request = new MockHttpServletRequest(path.startsWith("/api/v1/trailer-qr/")?"GET":"POST", path);
            request.addHeader(RequestCorrelationFilter.HEADER, "privacy-test");
            request.addHeader("Authorization", "Bearer synthetic-secret-token");
            request.addHeader("Cookie", "synthetic-secret-token");
            request.setQueryString("signature=synthetic-secret-token");
            request.setContent("synthetic-secret-token".getBytes(StandardCharsets.UTF_8));
            new RequestCorrelationFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            assertThat(appender.list).hasSize(1);
            String json = new String(encoder.encode(appender.list.getFirst()), StandardCharsets.UTF_8);
            Map<String, Object> fields = JsonPath.parse(json).read("$");
            assertThat(fields).containsEntry("requestId", "privacy-test")
                    .containsEntry("traceId", "privacy-test")
                    .containsEntry("http.path", path.startsWith("/api/v1/trailer-qr/")?"/api/v1/trailer-qr/redacted":"/test")
                    .containsEntry("http.method", request.getMethod())
                    .containsEntry("http.status", 200)
                    .containsKeys("@timestamp", "level", "logger_name", "durationMs");
            assertThat(json).doesNotContain("synthetic-secret-token","synthetic-public-qr-token", "Authorization", "Cookie", "signature");
            assertThat(json.strip()).doesNotContain("\n", "\r");
        } finally {
            encoder.stop();
            encoderContext.stop();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
