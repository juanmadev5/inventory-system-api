package com.jm.inventory.api.error;

import com.jm.inventory.application.common.ErrorCode;
import com.jm.inventory.application.common.FieldError;
import com.jm.inventory.domain.common.ConflictException;
import com.jm.inventory.domain.common.InvalidReferenceException;
import com.jm.inventory.domain.common.NotFoundException;
import com.jm.inventory.domain.user.InvalidCredentialsException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place exceptions get mapped to HTTP responses - every response follows RFC 7807
 * {@code ProblemDetail}, with a stable {@code code} property a client can branch on.
 * Extends {@code ResponseEntityExceptionHandler} so Spring MVC's own exceptions (malformed
 * body, unknown route, wrong method, ...) keep Spring's status and shape; this class adds
 * the {@code code} those lack, the field-level errors Bean Validation failures carry, and
 * the handlers for this application's own exception types.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE_PROPERTY = "code";
    private static final String ERRORS_PROPERTY = "errors";
    private static final String INVALID_FIELDS_DETAIL = "The request contains invalid fields.";

    /** Codes for the framework-raised statuses {@code ResponseEntityExceptionHandler} can produce. */
    private static final Map<HttpStatus, ErrorCode> FRAMEWORK_ERROR_CODES = Map.of(
            HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
            HttpStatus.NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE,
            HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Login failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setProperty(CODE_PROPERTY, ErrorCode.UNAUTHENTICATED.name());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setProperty(CODE_PROPERTY, ex.code());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty(CODE_PROPERTY, ex.code());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "The caller's role does not permit this operation.");
        problem.setProperty(CODE_PROPERTY, ErrorCode.FORBIDDEN.name());
        return problem;
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ProblemDetail handleInvalidReference(InvalidReferenceException ex) {
        return validationFailed(HttpStatus.UNPROCESSABLE_ENTITY, List.of(new FieldError(ex.field(), ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setProperty(CODE_PROPERTY, ErrorCode.INTERNAL_ERROR.name());
        return problem;
    }

    /** A request body failing Bean Validation - {@code 400}, per every feature spec that names the case. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage())),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(globalError -> new FieldError(globalError.getObjectName(), globalError.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationFailed(HttpStatus.BAD_REQUEST, errors), headers, status, request);
    }

    /** A constrained {@code @RequestParam}/{@code @PathVariable} failing Bean Validation - same shape as above. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(parameterResult -> parameterResult.getResolvableErrors().stream()
                        .map(error -> new FieldError(parameterResult.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationFailed(HttpStatus.BAD_REQUEST, errors), headers, status, request);
    }

    /** Every framework-raised error passes through here - adds the {@code code} Spring's own body lacks. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty(CODE_PROPERTY, frameworkErrorCode(response.getStatusCode()).name());
        }
        return response;
    }

    private static ProblemDetail validationFailed(HttpStatus status, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, INVALID_FIELDS_DETAIL);
        problem.setProperty(CODE_PROPERTY, ErrorCode.VALIDATION_FAILED.name());
        problem.setProperty(ERRORS_PROPERTY, errors);
        return problem;
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey(CODE_PROPERTY);
    }

    private static ErrorCode frameworkErrorCode(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? ErrorCode.MALFORMED_REQUEST : FRAMEWORK_ERROR_CODES.getOrDefault(status, ErrorCode.MALFORMED_REQUEST);
    }
}
