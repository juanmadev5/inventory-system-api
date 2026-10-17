# Testing

Testing is not a blanket requirement for every line of code - it is mandatory for business
logic that is genuinely delicate: authorization, concurrency, and money. That class of
logic always gets a test, no judgment call.

## Stack

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`), plus each feature-specific
`-test` starter Spring Boot 4 ships alongside its production starter (e.g.
`spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test` - see
`docs/SOURCES.md`). Testcontainers (the `postgresql` module) spins up a fresh Postgres per
`@SpringBootTest` class for integration tests. The two rate-limit integration tests
(`LoginRateLimitIntegrationTest`/`ApiRateLimitIntegrationTest`) are the one deliberate
exception - they need a real `INCR`/`EXPIRE` round trip against the shared
`local-infrastructure` Redis instance, since Testcontainers has no equivalent module worth
the overhead for that.

## Structure

Given-When-Then, both as commented blocks and in the method name:
`given<Context>_when<Action>_then<Outcome>`.

```java
@Test
void given_tenOnHand_when_twoReservationsOfSixRaceConcurrently_then_exactlyOneSucceeds() {
    // Given
    ...
    // When
    ...
    // Then
    ...
}
```

## Mocking

Unit tests mock every injected dependency (`@Mock` + `@InjectMocks`, or
`Mockito.mock(...)`) - never a real database or Redis call. Integration tests
(`@SpringBootTest`, Testcontainers) are the ones that exercise real infrastructure; the two
styles are never mixed in the same test class.

## Test isolation

A `@SpringBootTest` class's `@Container` Postgres is shared across every `@Test` method in
that class (started once, not per test) - fast, but it means one test's row (a category
named "Electronics") is still there for the next. Every controller integration test that
creates named/unique resources is annotated
`@ExtendWith(TruncateBusinessTablesExtension.class)`
(`api/src/test/java/.../support/TruncateBusinessTablesExtension.java`), which truncates
every business table before each test method - the reusable fix, not a per-test workaround
of unique-ifying every literal name.

## Worked example

`CategoryControllerIntegrationTest` (`api/src/test/java/.../catalog/`) is the reference for
exercising the HTTP layer itself (JSON, Bean Validation, exception -> `ProblemDetail`
mapping, the `@ExtendWith` above) with a mocked JWT
(`SecurityMockMvcRequestPostProcessors.jwt()`) - a real signed token is deliberately out of
scope for that kind of test; see its Javadoc. `AuthControllerIntegrationTest`
(`api/src/test/java/.../user/`) is the one exception that exercises a real, signed,
decoded token end to end, since that is exactly what it is testing.
