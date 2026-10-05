package com.trailersaas;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";
    private static final String TRACE_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".traceId";
    private static final String SPAN_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".spanId";
    private static final String LOGGED_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".logged";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Logger LOG = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String requestId = (String) request.getAttribute(ATTRIBUTE);
        if (requestId == null) {
            String supplied = request.getHeader(HEADER);
            requestId = supplied != null && SAFE_ID.matcher(supplied).matches()
                    ? supplied : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, requestId);
        }
        response.setHeader(HEADER, requestId);
        MDC.put("requestId", requestId);
        // An error redispatch may occur after the outer observation closed. Retain its real IDs.
        if (MDC.get("traceId") == null) {
            Object traceId = request.getAttribute(TRACE_ATTRIBUTE);
            MDC.put("traceId", traceId == null ? requestId : traceId.toString());
            Object spanId = request.getAttribute(SPAN_ATTRIBUTE);
            if (spanId != null) {
                MDC.put("spanId", spanId.toString());
            }
        }
        request.setAttribute(TRACE_ATTRIBUTE, MDC.get("traceId"));
        request.setAttribute(SPAN_ATTRIBUTE, MDC.get("spanId"));
        MDC.put("http.method", request.getMethod());
        MDC.put("http.path", request.getRequestURI());
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            try {
                if (!request.isAsyncStarted() && request.getAttribute(LOGGED_ATTRIBUTE) == null) {
                    request.setAttribute(LOGGED_ATTRIBUTE, true);
                    LOG.atInfo()
                            .addKeyValue("http.status", failed ? 500 : response.getStatus())
                            .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000.0)
                            .log("HTTP request completed");
                }
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        }
    }
}
