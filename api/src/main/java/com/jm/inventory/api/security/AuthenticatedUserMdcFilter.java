package com.jm.inventory.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attaches the authenticated caller's user id to SLF4J's MDC for the lifetime of the
 * request, cleared in {@code finally} - same discipline as {@code CorrelationIdFilter}, so
 * it never leaks into a different request on a pooled thread. Runs after Spring Security's
 * bearer-token authentication filter (wired via {@code addFilterAfter} in
 * {@link SecurityConfig}).
 */
@Component
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

    private static final String USER_ID_MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String userId = currentUserId();
            if (userId != null) {
                MDC.put(USER_ID_MDC_KEY, userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwtAuthenticationToken
                ? jwtAuthenticationToken.getToken().getSubject()
                : null;
    }
}
