package com.trailersaas;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class SafeInfrastructureDiagnosticsTests {
    @ParameterizedTest @CsvSource({"08006,db_connection_failure","57014,db_timeout","23505,integrity_failure","P0001,unexpected_failure"})
    void causeTraversalOnlyExtractsSafeClassesStateAndCategory(String state,String category) {
        var failure=new RuntimeException("Bearer synthetic-secret-bearer",new SQLException("synthetic-public-qr-token",state));
        var facts=SafeInfrastructureDiagnostics.facts(failure);
        assertThat(facts.category()).isEqualTo(category);
        assertThat(facts.sqlState()).isEqualTo(state);
        assertThat(facts.databaseFailure()).isTrue();
        assertThat(facts.exceptionType()).isEqualTo(RuntimeException.class.getName());
        assertThat(facts.rootCauseType()).isEqualTo(SQLException.class.getName());
        assertThat(facts.toString()).doesNotContain("synthetic-secret-bearer","synthetic-public-qr-token");
    }
    @Test void rollbackFailurePreservesOriginalSqlState() {
        var failure=new org.springframework.transaction.TransactionSystemException("rollback private",
                new SQLException("connection closed","08003"));
        failure.initApplicationException(new RuntimeException(new SQLException("original private","08006")));
        assertThat(SafeInfrastructureDiagnostics.facts(failure).sqlState()).isEqualTo("08006");
    }
    @Test void poolAndTransactionFailuresWithoutSqlStateAreStillClassified() {
        assertThat(SafeInfrastructureDiagnostics.facts(new org.springframework.dao.DataAccessResourceFailureException("pool private")).category()).isEqualTo("db_connection_failure");
        assertThat(SafeInfrastructureDiagnostics.facts(new org.springframework.transaction.TransactionSystemException("transaction private")).category()).isEqualTo("transaction_failure");
        assertThat(SafeInfrastructureDiagnostics.facts(new java.sql.SQLTimeoutException("timeout private")).category()).isEqualTo("db_timeout");
    }
    @Test void cyclicCausesAndInvalidSqlStateNeverExposeArbitraryValuesOrLoop() {
        var a=new RuntimeException("private");var b=new RuntimeException("private",a);a.initCause(b);
        assertThat(SafeInfrastructureDiagnostics.facts(a).category()).isEqualTo("unexpected_failure");
        assertThat(SafeInfrastructureDiagnostics.facts(new SQLException("private","synthetic-secret")).sqlState()).isNull();
    }
    @Test void structuredDiagnosticAtQrEndpointHasSafeFactsCorrelationAndRedactedPath() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger(SafeInfrastructureDiagnostics.class);
        var appender=new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing();super.append(event); }
        };
        appender.start();logger.addAppender(appender);
        var encoder=new StructuredLogEncoder();var encoderContext=new LoggerContext();
        encoderContext.putObject(Environment.class.getName(),new MockEnvironment());
        encoder.setContext(encoderContext);encoder.setFormat("logstash");encoder.setCharset(StandardCharsets.UTF_8);encoder.start();
        try {
            var request=new MockHttpServletRequest("GET","/api/v1/trailer-qr/synthetic-public-qr-token");
            request.addHeader("X-Request-Id","qr-diagnostic-test");
            request.addHeader("Authorization","Bearer synthetic-secret-bearer");
            new RequestCorrelationFilter().doFilter(request,new MockHttpServletResponse(),(req,res)->{
                SafeInfrastructureDiagnostics.record(new RuntimeException("raw SQL and synthetic-public-qr-token",
                        new SQLException("synthetic-secret-bearer password=private","08006")));
            });
            assertThat(appender.list).hasSize(1);
            String json=new String(encoder.encode(appender.list.getFirst()),StandardCharsets.UTF_8);
            Map<String,Object> fields=JsonPath.read(json,"$");
            assertThat(fields).containsEntry("http.path","/api/v1/trailer-qr/redacted").containsEntry("traceId","qr-diagnostic-test")
                    .containsEntry("requestId","qr-diagnostic-test").containsEntry("databaseFailure",true)
                    .containsEntry("failureCategory","db_connection_failure").containsEntry("sqlState","08006")
                    .containsEntry("exceptionType",RuntimeException.class.getName()).containsEntry("rootCauseType",SQLException.class.getName());
            assertThat(json).doesNotContain("synthetic-public-qr-token","synthetic-secret-bearer","raw SQL","password","stack_trace");
        } finally { encoder.stop();encoderContext.stop();logger.detachAppender(appender);appender.stop(); }
    }
}
