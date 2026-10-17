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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Keyed by the caller's own user id - a noisy or misbehaving account's traffic never
 * starves another's. Must run after Spring Security's bearer-token authentication filter
 * (wired via {@code addFilterAfter} in {@link SecurityConfig}) so the authenticated token is
 * already available; skips entirely when there is none (an anonymous/failed-auth request,
 * which {@code AuthorizationFilter} rejects later in the chain regardless).
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(
            RedisRateLimiter redisRateLimiter, RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        this.redisRateLimiter = redisRateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String userId = currentUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Limit limit = rateLimitProperties.apiWide();
        String key = RateLimitKeys.API_WIDE_PREFIX + userId;

        if (redisRateLimiter.tryAcquire(key, limit.maxRequests(), Duration.ofSeconds(limit.windowSeconds()))) {
            filterChain.doFilter(request, response);
            return;
        }
        writeRateLimitExceeded(response);
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwtAuthenticationToken
                ? jwtAuthenticationToken.getToken().getSubject()
                : null;
    }

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/problem+json");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many requests - try again later.");
        problem.setProperty("code", ErrorCode.RATE_LIMIT_EXCEEDED.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
