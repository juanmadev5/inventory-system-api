# Security

## Decision

- **This application is its own identity provider.** No external authorization server, no
  per-tenant realm, no third-party identity service of any kind. A single flat `users`
  table (`id`, `email`, `password_hash`, `role`, `active`, `created_at`) holds every
  account - see [docs/specs/11-user-management/](specs/11-user-management/requirements.md).
- **Authentication**: `POST /api/v1/auth/login` (`AuthController`) takes `{ email,
  password }`, verifies the password with BCrypt (`BCryptPasswordHasher`), and on success
  issues a signed JWT access token (`JwtAccessTokenIssuer`, Spring Security's
  `NimbusJwtEncoder`, HMAC-SHA256). An unknown email, a wrong password, and a deactivated
  account all fail identically - `401`, code `UNAUTHENTICATED` - never revealing which.
- **The token's claims**: `sub` (the user's id), `email`, `role` (exactly one of
  `STAFF`/`MANAGER`/`ADMIN` - see
  [06-roles-and-permissions](specs/06-roles-and-permissions/requirements.md)), plus the
  standard `iss`/`iat`/`exp`. Every other endpoint is a Spring Security OAuth2 resource
  server validating that same signature (`JwtDecoder` in `SecurityConfig`, keyed off
  `app.security.jwt.secret`) - there is only ever one issuer, this application itself, so
  there is no multi-issuer resolution to do.
- **`RoleJwtAuthenticationConverter`** maps the `role` claim to a `ROLE_*`
  `GrantedAuthority`; `@RequireStaff`/`@RequireManager`/`@RequireAdmin`
  (`@PreAuthorize`-backed composed annotations) declare the minimum role per controller
  method.
- **Bootstrapping the first `ADMIN`**: `AdminBootstrapRunner` runs at startup in every
  profile. If the `users` table is empty and `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` are
  both set, it creates one `ADMIN` with those credentials. Either one blank just skips
  bootstrapping (logged, never a startup failure) - there is no hardcoded fallback
  credential. Every teammate after that is created by an `ADMIN` through
  [11-user-management](specs/11-user-management/requirements.md), never through this
  runner again (it is a no-op once any user exists).
- **No multitenancy.** Every table is a single, shared set of rows - there is no `tenant_id`
  column, no Row-Level Security, no per-request tenant resolution anywhere in this
  codebase. One deployment of this API serves one organization.

## Rate limiting

Two independent limiters, both built on the same `RedisRateLimiter` (fixed-window counter -
`INCR` + `EXPIRE` on first hit) - see
[data-and-infra.md](conventions/data-and-infra.md)'s "Rate limiting" section for the
mechanism itself:

- `LoginRateLimitFilter` - `POST /api/v1/auth/login` only, keyed by caller IP
  (`app.rate-limit.login.*`). Tight by default - the threat model is brute-forcing a
  password.
- `ApiRateLimitFilter` - every authenticated business endpoint, keyed by the caller's own
  user id (`app.rate-limit.api-wide.*`). Generous by default - it exists to cap abuse, not
  throttle normal use.

## Logging

`AuthenticatedUserMdcFilter` attaches the authenticated caller's user id to SLF4J's MDC
(`userId` key) for the lifetime of the request, cleared in `finally` - see
[error-handling-and-logging.md](conventions/error-handling-and-logging.md).
