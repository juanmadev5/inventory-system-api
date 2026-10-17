# Architecture

## Clean Architecture, enforced by the build

Four Maven modules, dependencies pointing inward, enforced physically by `pom.xml` (not
just by convention - `domain`'s `pom.xml`, once it exists, will declare no dependency on
`infrastructure`, so the build itself fails if `domain` code ever imports from it):

```
pom.xml (parent, packaging=pom)
  application/    # depends on: domain (once it exists)
  infrastructure/ # depends on: application, domain
  api/             # depends on: infrastructure, application, domain - the only runnable module
```

`domain/` holds pure Java business entities/value objects, repository interfaces, and the
`NotFoundException`/`ConflictException`/`InvalidReferenceException` base hierarchy
(`GlobalExceptionHandler` maps each to its HTTP status in one place) - zero Spring/JPA on
its classpath. Added with the catalog/stock feature
(`docs/specs/01-catalog-and-stock/`), the first to need one.

- **`application/`**: use cases, application-owned contracts other layers implement
  (`CurrentActor`, `PasswordHasher`, `AccessTokenIssuer`), cross-endpoint shared DTOs
  (`PageResponse<T>`, `PaginationDefaults`, `ErrorCode`). No Spring dependency on its
  compile classpath beyond what's needed to be unit-testable in isolation.
- **`infrastructure/`**: Spring Data JPA entities/repositories, Redis-backed caches,
  Flyway's runtime dependency (the migration *scripts* live in `api/`, since Flyway needs
  them on the classpath of whichever module actually boots - see
  [data-and-infra.md](data-and-infra.md)).
- **`api/`**: `@RestController`s, Spring Security config, the global exception handler,
  filters, `application.yml`, Flyway scripts, the `InventoryApiApplication` main class.

## Dependency direction in practice

A JPA `@Entity` is never the same class as the domain model it represents. `infrastructure`
defines `ProductJpaEntity` (`@Entity`) and a repository adapter implementing a
`ProductRepository` interface that `domain` owns; `domain`'s own `Product` class never has
a single JPA annotation on it.

Always depend on an interface at a layer boundary, from the first implementation -
`CurrentActor` (owned by `application`) is implemented by `JwtCurrentActor` (in `api`,
since it's tied to the authenticated request) precisely so `application`'s audit-logging
code can depend on the abstraction without knowing how the caller is actually
authenticated. `AccessTokenIssuer`/`PasswordHasher` (also owned by `application`) follow
the same pattern - see [docs/security.md](../security.md).

## Component scanning across modules

Spring Boot's default component scan only covers the main class's own package and its
sub-packages. Because `api`'s base package (`com.jm.inventory.api`) is a *sibling* of
`application`'s and `infrastructure`'s, `InventoryApiApplication` explicitly widens this
with `@SpringBootApplication(scanBasePackages = "com.jm.inventory")`, plus `@EntityScan` and
`@EnableJpaRepositories` pointed at `com.jm.inventory.infrastructure`. Any new module added
later under `com.jm.inventory.*` is picked up automatically; a module placed outside that
root package namespace would not be.
