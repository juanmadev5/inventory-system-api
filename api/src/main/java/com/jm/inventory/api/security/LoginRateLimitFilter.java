package com.jm.inventory.api.security;

import com.jm.inventory.application.common.ErrorCode;
import com.jm.inventory.infrastructure.platform.RateLimitKeys;
import com.jm.inventory.infrastructure.platform.RateLimitProperties;
import com.jm.inventory.infrastructure.platform.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Keyed by remote IP, scoped to {@code POST /api/v1/auth/login} only (checked on the
 * request path itself, since this is a plain {@code OncePerRequestFilter} on the one and
 * only security filter chain, not a separate chain) - the threat model here is
 * brute-forcing a password, not a single legitimate client's own traffic.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(
            RedisRateLimiter redisRateLimiter, RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        this.redisRateLimiter = redisRateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!LOGIN_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Limit limit = rateLimitProperties.login();
        String key = RateLimitKeys.LOGIN_PREFIX + request.getRemoteAddr();

        if (redisRateLimiter.tryAcquire(key, limit.maxRequests(), Duration.ofSeconds(limit.windowSeconds()))) {
            filterChain.doFilter(request, response);
            return;
        }
        writeRateLimitExceeded(response);
    }

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/problem+json");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts - try again later.");
        problem.setProperty("code", ErrorCode.RATE_LIMIT_EXCEEDED.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
