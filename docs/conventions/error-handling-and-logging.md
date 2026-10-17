# Error handling and logging

## Exceptions

One place maps exceptions to HTTP responses: `GlobalExceptionHandler`
(`api/.../error/GlobalExceptionHandler.java`), a `@RestControllerAdvice` extending
`ResponseEntityExceptionHandler` so Spring MVC's own exceptions already get a
`ProblemDetail` shape for free. Never a try/catch scattered per controller method. Every
application-specific exception (`InvalidCredentialsException` today) is a dedicated type,
never a generic `RuntimeException` caught and string-matched.

Every error response is RFC 7807 `ProblemDetail` with a stable `code` property from
`ErrorCode` (`application/.../common/ErrorCode.java`) or a feature-specific enum added
alongside it - never a raw exception message surfaced to the caller.

## Logging

SLF4J + Logback (Spring Boot's default), structured via parameterized messages and MDC -
never string-concatenated log lines. Two MDC keys are attached for the lifetime of every
request, both cleared in a `finally` block so neither leaks into a different request on a
pooled thread:

- `correlationId` - `CorrelationIdFilter` (`api/.../logging/CorrelationIdFilter.java`),
  ordered first (`Ordered.HIGHEST_PRECEDENCE`) so even an authentication failure is logged
  with it. Reuses the caller's `X-Correlation-Id` header if present, generates one
  otherwise, always echoes it on the response.
- `userId` - `AuthenticatedUserMdcFilter`, once a caller is authenticated (see
  [docs/security.md](../security.md)).

Levels: `ERROR` for failures needing attention, `WARN` for recoverable/unexpected
situations (e.g. a failed login attempt), `INFO` for business-relevant events,
`DEBUG` for development detail only (`application-dev.yml` raises
`com.jm.inventory` to `DEBUG`; the base `application.yml` does not).

Never log a secret, token, or password - not even at `DEBUG`.
