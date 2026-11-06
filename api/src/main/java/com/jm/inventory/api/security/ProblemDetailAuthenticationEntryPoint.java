package com.jm.inventory.api.security;

import com.jm.inventory.application.common.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A missing, malformed or expired access token is rejected inside the Spring Security
 * filter chain, before any controller - so {@code GlobalExceptionHandler} never sees it.
 * Keeps the standard Bearer behavior ({@code 401} + {@code WWW-Authenticate}) and adds the
 * same {@code ProblemDetail} body, with code {@code UNAUTHENTICATED}, every other error has.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authenticationException);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "A valid access token is required.");
        problem.setProperty("code", ErrorCode.UNAUTHENTICATED.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
