# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

For architecture, JWT design, RBAC, route prefixes, and business rules, see [README.md](README.md) — this file only covers conventions not already documented there. API endpoint documentation lives in [../docs/auth-service/](../docs/auth-service/INDEX.md).

**When you add or change an endpoint, route, business rule, or JWT/permission behavior, update the matching file in `../docs/auth-service/api/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Commands

```bash
mvn clean package -DskipTests   # skip tests
mvn test -Dtest=ClassName       # single test class
docker-compose up -d --build    # full stack (PostgreSQL + auth-service)
```

No linter is configured. The Postman collection `auth-service.postman_collection.json` covers all endpoints.

## Code layout (Hexagonal / Ports & Adapters)

```
infrastructure/adapter/in/web/    ApiRouter (functional RouterFunctions, no @Controller), *Handler
domain/port/in/                  use-case interfaces (AuthUseCase, UsuarioStaffUseCase, …)
application/service/             *ApplicationService — implements the use-case interface
domain/port/out/                 repository/service interfaces (UsuarioStaffPort, TokenGeneratorPort, …)
infrastructure/adapter/out/persistence/   *PersistenceAdapter — R2DBC repositories + raw DatabaseClient for complex queries
```

`domain/` (model, port/in, port/out, exception) has zero infrastructure dependencies. Adding a feature touches all four layers: domain port → application service → persistence adapter → handler + router.

## Persistence conventions

- **Centralized identity via `identidad.personas`:** all three user tables (`UsuarioStaff`, `UsuarioPlataforma`, `UsuarioApp`) reference `personas` via `id_persona` FK and never store `nombre`/`foto_url` directly — always obtained via `LEFT/INNER JOIN identidad.personas p`, exposed as `nombre_persona` / `foto_url_persona` column aliases. The `Persona` must already exist before creating any user.
- `correo` on user tables is the login/corporate email and may differ from `personas.correo` (personal email); `nombre`/`foto_url` are updated only via the `Persona` API.

## Key classes

- `infrastructure/security/JwtTokenProvider` — JJWT 0.12.6 generate/parse
- `infrastructure/security/JwtAuthWebFilter`, `SecurityUtils` — auth pipeline (see README "Pipeline de seguridad")
- `application/service/AuthApplicationService` — all three login flows, token refresh, password reset
- `infrastructure/config/GlobalExceptionHandler` — maps domain exceptions to `ApiError` JSON
- `RateLimiterPort` — key format `"tipo:idCompania:login"`, platform uses `"platform:email"`
- `BitacoraPort` — every POST/PUT/DELETE must write an audit entry via this port

## Testing

Integration tests live in `src/test/`, use **Testcontainers** (real PostgreSQL container), extend `IntegrationTestBase`:

- `@SpringBootTest` + `@Transactional` — auto-rollback per test, no manual cleanup
- Seed helpers: `seedPlatform()`, `seedStaff()`, `seedPersona()`, `seedAppUser()`, `seedRol()`, `seedPermiso()`
- Bearer token builders: `bearerPlatform()`, `bearerStaff()`, `bearerCliente()`
- Rate limiter reset before each test; all test data uses `id_compania = 99999` for isolation

Test files follow `*IT.java` (e.g. `AuthIT.java`, `UsuarioStaffIT.java`). When adding an endpoint, seed required entities in `@BeforeEach`, then test happy path plus auth/permission failures.

## Configuration

Sensitive values come from environment variables (see README "Variables de entorno" for the full list). Local overrides go in `application-local.yml` (git-ignored).
