package com.example.day3.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(String requestedTraceId) {
        if (requestedTraceId != null && SAFE_TRACE_ID.matcher(requestedTraceId).matches()) {
            return requestedTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
