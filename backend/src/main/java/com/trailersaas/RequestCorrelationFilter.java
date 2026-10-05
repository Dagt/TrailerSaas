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
        // Micrometer's outer observation filter owns traceId/spanId. Keep a safe fallback.
        if (MDC.get("traceId") == null) {
            MDC.put("traceId", requestId);
        }
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
                if (!request.isAsyncStarted()) {
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
