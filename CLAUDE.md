# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules (skip tests)
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew :bootstrap:test --tests "com.project.auth.LayerDependencyArchitectureTest"

# Run tests in a specific module
./gradlew :application:test

# Boot the application (local profile with H2)
./gradlew :bootstrap:bootRun

# Build executable jar
./gradlew :bootstrap:bootJar

# Build migration job jar
./gradlew :bootstrap:migrationBootJar

# Build Docker image
docker build -f deploy/docker/application/Dockerfile -t project-auth-server:local .
```

Modules: `domain`, `application`, `presentation`, `infrastructure`, `bootstrap`.

## Architecture

This is a **Clean/Hexagonal Architecture** auth server with strict layer enforcement via ArchUnit.

### Layer rules (enforced by tests)

- **domain** — Pure domain models and value objects. No Spring, JPA, or Servlet dependencies allowed.
- **application** — Use cases, ports (in/out), commands, results. Must not depend on presentation or infrastructure.
- **presentation** — Controllers, request/response DTOs, error-to-HTTP mapping. Must not depend on infrastructure.
- **infrastructure** — JPA adapters, security adapters (Bcrypt, JWT/Nimbus, Vault Transit), Flyway migrations.
- **bootstrap** — Spring Boot entry point, wires all modules together, owns all `@Configuration` classes. Only module allowed to depend on config packages.

### Port/Adapter pattern

Use cases define **port interfaces** (e.g., `LoginUseCase` as in-port, `LoadLoginUserPort` as out-port). Infrastructure provides adapters that implement out-ports. Presentation calls in-ports. Bootstrap wires ports to adapters in `AuthCoreConfiguration`.

### Error handling across layers

- **Domain**: throws `DomainException` subclasses
- **Application**: throws `BusinessException` with `ErrorCode` (code + message only, no HTTP status)
- **Presentation**: `GlobalExceptionHandler` catches exceptions; `ApiErrorHttpStatusMapper` maps `ErrorCode` → HTTP status; responses wrapped in `ApiResult<T>`

### Database migrations

Flyway SQL migrations live in `infrastructure/src/main/resources/db/migration/`. In K8s they are run as an `initContainer` using the official `flyway/flyway` image — there is no in-repo migration entry point or migration image.

## Tech Stack

- Java 21, Spring Boot 4.0.3, Gradle (centralized version catalog in `settings.gradle`)
- PostgreSQL + H2 (test), Flyway, Spring Data JPA
- Spring Security + OAuth2 (Keycloak-mediated Google/GitHub federation)
- JWT signing via Nimbus — supports both local file keys and Vault Transit
- ArchUnit for architecture tests, JUnit 5 + AssertJ

## Local Development

`deploy/docker/docker-compose.yml` provides PostgreSQL, Keycloak, Vault, and vault-init services. The `local` profile uses H2, so Docker is optional for basic development.

## Documentation

Before any documentation task, read `AGENTS.md` and `docs/documentation-guide.md` first. Documentation follows the Why → What → How → Result narrative order. Architecture docs must include Mermaid diagrams.
