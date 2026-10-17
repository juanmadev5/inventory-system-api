# Multi-stage build - see docs/conventions/data-and-infra.md's Docker section. Builds the
# whole Maven reactor (api depends on infrastructure/application/domain as local modules,
# not published artifacts - a single-module build of api/ alone can't resolve them), then
# ships only the api module's packaged jar in a slim runtime-only image.
#
# This repo only defines this Dockerfile for the inventory-api service itself - any
# docker-compose.yml orchestrating it alongside other services lives at the
# inventory-system monorepo root, not here (see README.md).

# ---- Build stage ----
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /build

# Dependency resolution is cached in its own layer: it only re-runs when a pom.xml changes,
# never on a pure source-code change.
COPY pom.xml ./
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY api/pom.xml api/
RUN mvn -q -B -pl domain,application,infrastructure,api -am dependency:go-offline

COPY checkstyle.xml ./
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY api/src api/src
RUN mvn -q -B -o -pl api -am package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine-3.24 AS runtime
RUN addgroup -S inventory-api && adduser -S inventory-api -G inventory-api
WORKDIR /app
COPY --from=build /build/api/target/inventory-api.jar ./app.jar
USER inventory-api

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
