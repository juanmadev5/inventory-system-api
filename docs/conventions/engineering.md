# Engineering conventions

Distilled for this project from the author's own cross-project conventions. If a rule here
ever looks wrong for this project, that is a question for the developer, not something to
silently override.

## Language

All code, identifiers, comments, commit messages, and documentation: English. The only
exception is end-user-facing text (there is none in this API yet - it returns JSON, not
rendered UI). Route paths, error codes, and constant names are always English.

## Naming

Every identifier - including stream/lambda parameters - must be understandable at first
glance, with no need to trace back through the code. Never a bare placeholder (`e`, `x`,
`data`, `result`) when a descriptive name is one keystroke away; the sole accepted
exception is a raw loop index (`i`).

```java
// Bad
var errors = validationErrors.stream()
    .collect(Collectors.groupingBy(e -> e.getField()));

// Good
var errorsByField = validationErrors.stream()
    .collect(Collectors.groupingBy(validationError -> validationError.getField()));
```

## No magic values

Status codes, role/claim names, config keys, cache key prefixes, route paths: always named
constants, or an `enum` for a closed set. An enum is always persisted/transmitted by
**name**, never by ordinal (`@Enumerated(EnumType.STRING)`, never `ORDINAL`).

## DRY and SOLID

Duplication of *logic* (not merely similar-looking code) is a defect - extract it. SOLID is
applied pragmatically: dependencies are injected behind an interface **from the first
implementation** (testability alone justifies it - see
[architecture.md](architecture.md)), but that is about layer boundaries, not an excuse to
split a class into pieces with no real reason to change independently.

## Comments

Default to none. Code should read clearly from naming alone. Write a comment only for a
non-obvious **why** - a hidden constraint, a workaround, a business rule the code itself
can't express (see e.g. the Javadoc on `JwtSigningKey` explaining *why*
`set_config(..., true)` and not plain `SET`). Doc-comment format: `/** ... */` Javadoc,
reserved for genuinely non-obvious *what* (complex generics, multi-step stream pipelines,
Strategy-style abstractions) - never a mechanical restatement of a getter or a well-named
method.

## Architecture

Packages and Maven modules are created the moment they get their first real file, never
pre-scaffolded "for later." The one deliberate, explicit exception: the four Clean
Architecture modules themselves (`domain`/`application`/`infrastructure`/`api`) are
mandated structure per [architecture.md](architecture.md), not a per-feature package.

## Error handling

Validate and handle errors at system boundaries only (HTTP request body/params, JWT claims,
external calls) - no defensive checks against states the type system or Bean Validation
already rules out internally. See
[error-handling-and-logging.md](error-handling-and-logging.md) for the mechanism.

## Definition of done

Before calling any change finished:

- `mvn compile` and `mvn checkstyle:check` (root) both pass clean - see each module's
  `pom.xml`.
- Tests pass locally; new tests were added if the change touches logic
  [testing.md](testing.md) calls mandatory (delicate business logic, authorization,
  concurrency, anything where a silent bug corrupts data or leaks it to the wrong caller).
- The full diff was read start to finish before calling it done.
- `README.md` was updated if the change affects how the project runs; `docs/SOURCES.md` was
  updated if a source outside the stack's own `sources.md` list was consulted (see
  [data-and-infra.md](data-and-infra.md) and the Spring Boot 4 package-location notes
  already recorded there as an example).
- Scope matches exactly what was asked - no unrelated edits, no speculative flexibility, no
  half-finished feature.

## Ask vs. assume

Ask, and do not proceed, when a decision is business/domain-specific and not already
decided by this file or `docs/security.md` (e.g. what a new feature's exact validation
rules should be). Never guess an API, annotation, or dependency's package/behavior - verify
it against the library's own official docs first (this project has already been burned once
by Spring Boot 4's package reshuffle - see `docs/SOURCES.md`).
