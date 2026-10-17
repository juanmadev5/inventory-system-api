# API design

## Resources, versioning, routes

- URLs are nouns, plural, never a verb (`POST /api/v1/products`, never `POST /createProduct`).
- Every endpoint's path is versioned (`/api/v1/...`), baked into the controller's
  `@RequestMapping` - see `UserController`'s `/api/v1/users`. A breaking change
  gets a new controller class on `/api/v2/...`, never a branch inside the same one.
- Every `@GetMapping`/`@PostMapping`/etc. has its own explicit path - never left bare,
  relying only on the class-level mapping.
- Query parameters (never path segments) for anything optional: filtering, sorting, paging.

## Status codes and errors

Standard HTTP status semantics (`201` + `Location` on create, `204` on a side-effect-only
success, `404`, `409`, `422` for semantically-invalid-but-well-formed input). Every error
response is an RFC 7807 `ProblemDetail`, produced by the single `GlobalExceptionHandler`
(`api/.../error/GlobalExceptionHandler.java`) - see
[error-handling-and-logging.md](error-handling-and-logging.md) - with a stable `code`
property (`ErrorCode`, or a feature-specific enum) a client can branch on without parsing
`detail`.

## Pagination

Every endpoint returning a collection is paginated - no exceptions, no "it's a short list
for now." `page` is 1-based in the API surface; Spring Data's 0-based `Pageable` is an
internal detail, never leaked. The envelope (`application/.../common/PageResponse.java`)
and its defaults (`PaginationDefaults.DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`) are
defined once and reused by every feature - never redefined per endpoint.

```json
{ "items": [...], "page": 1, "pageSize": 20, "totalCount": 137, "totalPages": 7 }
```

## OpenAPI

Every endpoint is documented - no exceptions. springdoc-openapi serves the live spec at
`/v3/api-docs` and Swagger UI at `/swagger-ui.html` (both public, see
[multitenancy.md](../multitenancy.md)'s `SecurityConfig` notes). Every `@RestController`
gets `@Tag`; every method gets `@Operation` plus one `@ApiResponse` per status it can
actually return, success and error alike. `OpenApiConfig`
(`api/.../config/OpenApiConfig.java`) declares the bearer-JWT security scheme once, applied
globally.

Per [Spec Driven Development](../../AGENTS.md), the hand-written `openapi.yaml` under each
`docs/specs/<nn>-<feature>/` is the contract agreed *before* implementation - the generated
`/v3/api-docs` is cross-checked against it, not the other way around.
