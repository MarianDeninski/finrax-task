package com.finrax.interview_task.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * One line in and one line out for every HTTP request, plus a correlation id that every log
 * statement made while handling that request carries. Without it, concurrent requests interleave
 * in the log and there is no way to tell which line belongs to which caller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID = "correlationId";
    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);

        String target = request.getMethod() + " " + request.getRequestURI();
        long startedAt = System.nanoTime();
        log.info("--> {}", target);
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("<-- {} {} ({} ms)", target, status, millis);
            } else if (status >= 400) {
                log.warn("<-- {} {} ({} ms)", target, status, millis);
            } else {
                log.info("<-- {} {} ({} ms)", target, status, millis);
            }
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Swagger and the H2 console are developer tools, not traffic worth logging.
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/h2-console");
    }
}
