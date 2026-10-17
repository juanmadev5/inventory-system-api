# Sources

Entries not already covered by `03-backend/spring-boot/sources.md` in the author's
conventions repository, consulted while building this project's foundation.

## Spring Boot 4 package reorganization

Spring Boot 4 moved several auto-configuration types out of `org.springframework.boot.autoconfigure.*`
into dedicated packages. Confirmed against the official Javadoc (docs.spring.io) and, where
noted, by actually resolving the dependency and inspecting the jar:

- `HibernatePropertiesCustomizer`: `org.springframework.boot.hibernate.autoconfigure` (was
  `org.springframework.boot.autoconfigure.orm.jpa` in Boot 3).
- `EntityScan`: `org.springframework.boot.persistence.autoconfigure` (was
  `org.springframework.boot.autoconfigure.domain`).
- `RedisCacheManagerBuilderCustomizer`: `org.springframework.boot.cache.autoconfigure` (was
  `org.springframework.boot.autoconfigure.cache`).
- `BearerTokenAuthenticationFilter`: confirmed by inspecting the resolved
  `spring-security-oauth2-resource-server-7.1.1.jar` directly -
  `org.springframework.security.oauth2.server.resource.web.authentication`, not
  `...resource.web` as the class name alone might suggest.
- `AutoConfigureMockMvc`: confirmed by inspecting the resolved `spring-boot-webmvc-test-4.1.1.jar` -
  `org.springframework.boot.webmvc.test.autoconfigure` (was
  `org.springframework.boot.test.autoconfigure.web.servlet` in Boot 3).
- JSON/Jackson support is its own starter in Boot 4, `spring-boot-starter-jackson` - not
  pulled in automatically by `spring-boot-starter-webmvc` alone; its absence surfaced as
  `NoSuchBeanDefinitionException` for `ObjectMapper` in a `@SpringBootTest`, not a compile
  error, so it's easy to miss.
- That starter auto-configures **Jackson 3**, not Jackson 2 - confirmed by resolving
  `spring-boot-starter-jackson:4.1.1`'s dependency tree, which pulls
  `tools.jackson.core:jackson-databind:3.1.5`, a different group id/package
  (`tools.jackson.databind.ObjectMapper`) from the classic `com.fasterxml.jackson.core`
  artifact. A module (e.g. `application`, for `AuditLogService`) that needs `ObjectMapper`
  on its own compile classpath must depend on `tools.jackson.core:jackson-databind`, not
  `com.fasterxml.jackson.core:jackson-databind` - the latter resolves fine (something else
  in the tree still pulls Jackson 2 transitively, e.g. YAML config parsing) and compiles
  fine, but produces a *different* `ObjectMapper` type than the one Spring Boot actually
  registers as a bean, surfacing as the same `NoSuchBeanDefinitionException` all over again
  at `@SpringBootTest` context load - a second, more subtle instance of the exact gotcha
  above, caught by `./mvnw test`, not by `./mvnw compile`.

## springdoc-openapi / Spring Boot 4 compatibility

- springdoc-openapi v3.x targets Spring Boot 4; pinned to `3.1.1` (latest release at the
  time, confirmed against Maven Central's `maven-metadata.xml`).

## Maven plugin versions

- `maven-checkstyle-plugin`: `3.6.0` (Maven Central `maven-metadata.xml`, latest at time of
  writing).
- Testcontainers: `testcontainers-bom:2.0.5` exists but does **not** manage the
  `org.testcontainers:postgresql`/`junit-jupiter` artifact versions - those are still on the
  `1.21.4` line (confirmed via each artifact's own `maven-metadata.xml`). Pinned directly,
  not via that BOM.

## Spring Security Test - mocking a JWT

- `SecurityMockMvcRequestPostProcessors.jwt()` (`spring-security-test`, pulled transitively
  by `spring-boot-starter-security-oauth2-resource-server-test`) lets a `MockMvc` test
  inject an authenticated `Jwt` directly into the security context, bypassing real
  signature validation entirely - the correct, documented way to test authorization logic
  without a real signed token. See `CategoryControllerIntegrationTest`.

## Spring Security - issuing a JWT with `NimbusJwtEncoder`

- `NimbusJwtEncoder` constructed from an `ImmutableSecret<SecurityContext>` wrapping a raw
  `SecretKey`, for an application that signs and validates its own HS256 tokens with no
  external authorization server:
  <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>
  (the resource-server side, `NimbusJwtDecoder.withSecretKey(...)`) and the `nimbus-jose-jwt`
  Javadoc for `ImmutableSecret`.
