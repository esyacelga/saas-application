# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

API endpoint documentation lives in [../docs/core-service/](../docs/core-service/INDEX.md).

**When you add or change an endpoint or business rule (e.g. membership modes), update the matching file in `../docs/core-service/api/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Commands

```bash
# Build
mvn clean package

# Run (requires PostgreSQL and Redis)
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClienteIntegrationTest

# Run a single test method
mvn test -Dtest=ClienteIntegrationTest#methodName
```

## Architecture

This is a **Spring Boot WebFlux microservice** (reactive/non-blocking) for a Gym Administrator SaaS platform. It manages clients, memberships, and gym access control. Runs on port **8083**.

The project follows **Hexagonal Architecture** with three layers:

- **`domain/`** — Pure business logic, no framework dependencies. Contains domain models (`model/`) and port interfaces (`port/in/` for use cases, `port/out/` for repository contracts).
- **`application/service/`** — Use case implementations that depend only on domain ports.
- **`infrastructure/`** — All framework/external concerns:
  - `adapter/in/web/` — WebFlux REST controllers + DTOs
  - `adapter/out/persistence/` — R2DBC adapters implementing repository ports
  - `config/` — Security, JWT filter, CORS, R2DBC config, global exception handler

**Reactive paradigm**: All service and repository methods return `Mono<T>` or `Flux<T>`. Never use blocking calls.

**Mapping**: MapStruct mappers live alongside the infrastructure adapters. They use `componentModel = "spring"`.

**Authentication**: JWT Bearer tokens. The `JwtAuthenticationFilter` parses claims (userId, userType `staff`/`platform`, role, companyId) into a `JwtPrincipal` stored in the `SecurityContext`. Controllers access it via `ReactiveSecurityContextHolder`.

## Key Tech

- Java 21, Spring Boot 3.3.5, Spring WebFlux, Spring Security WebFlux
- Spring Data R2DBC + PostgreSQL (r2dbc-postgresql)
- Redis reactive for caching
- JJWT 0.12.6 for JWT
- Lombok + MapStruct
- Jackson with snake_case naming strategy

## Configuration

Environment variables (loaded from `.env` in dev):
- `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` — PostgreSQL
- `JWT_SECRET` — Base64-encoded secret
- `PLATFORM_SERVICE_URL` — URL for the platform service (default: `http://localhost:8081`)

Redis defaults to `localhost:6379`. Jackson is configured globally to use `snake_case`.

## Testing

Tests are integration tests using `WebTestClient` + a real PostgreSQL + Redis (configured in `application-test.yml`). `BaseIntegrationTest` provides shared setup. `DotEnvInitializer` loads `.env` for test context. There are no unit tests — only integration tests.

## Membership Control Modes

Memberships have two distinct billing/tracking modes:
- **Calendar-based**: Fixed-duration (e.g., 1 month from start date).
- **Access-based**: Limited daily visits with an overall expiration date.

This distinction drives branching logic in `MembresiaService` and the access validation endpoint (`GET /api/v1/membresias/validar-acceso` — unauthenticated).