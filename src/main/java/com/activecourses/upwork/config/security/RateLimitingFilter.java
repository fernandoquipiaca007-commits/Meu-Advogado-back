package com.activecourses.upwork.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private final ConcurrentHashMap<String, UserRequestCount> requestCounts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String clientIp = getClientIp(request);
        String key = clientIp; // Rate limit per IP across all endpoints

        UserRequestCount count = requestCounts.compute(key, (k, v) -> {
            if (v == null || v.isExpired()) {
                return new UserRequestCount();
            }
            v.increment();
            return v;
        });

        if (count.getCount() > MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"success\":false,\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class UserRequestCount {
        private int count;
        private final long windowStart;

        UserRequestCount() {
            this.count = 1;
            this.windowStart = System.currentTimeMillis();
        }

        synchronized void increment() {
            this.count++;
        }

        synchronized int getCount() {
            return count;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - windowStart > TimeUnit.MINUTES.toMillis(1);
        }
    }
}
