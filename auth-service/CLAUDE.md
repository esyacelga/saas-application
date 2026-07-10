# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

For architecture, JWT design, RBAC, route prefixes, and business rules, see [README.md](README.md) — this file only covers conventions not already documented there. API endpoint documentation lives in [../docs/auth-service/](../docs/auth-service/INDEX.md).

**When you add or change an endpoint, route, business rule, or JWT/permission behavior, update the matching file in `../docs/auth-service/api/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-08):

| Área | Documento | Estado |
|------|-----------|--------|
| API — login, refresh, reset | [../docs/auth-service/api/auth.md](../docs/auth-service/api/auth.md) | ✅ Refleja el código |
| API — personas (identidad global) | [../docs/auth-service/api/personas.md](../docs/auth-service/api/personas.md) | ✅ Refleja el código |
| API — usuarios staff + roles/permisos | [../docs/auth-service/api/usuarios-staff.md](../docs/auth-service/api/usuarios-staff.md) | ✅ Refleja el código |
| API — usuarios app (cliente PWA) | [../docs/auth-service/api/app-usuarios.md](../docs/auth-service/api/app-usuarios.md) | ✅ Refleja el código |
| API — usuarios plataforma SaaS | [../docs/auth-service/api/platform.md](../docs/auth-service/api/platform.md) | ✅ Refleja el código |
| API — bitácora de auditoría | [../docs/auth-service/api/bitacora.md](../docs/auth-service/api/bitacora.md) | ✅ Refleja el código |

Fuente de verdad del enrutamiento: `infrastructure/adapter/in/web/ApiRouter.java`. Si un doc y el código divergen, el código gana — corrige el doc en el mismo commit.

## Commands

```bash
mvn clean package -DskipTests        # build jar, skip tests
mvn test                             # all unit tests
mvn test -Dtest=ClassName            # single unit test class
mvn verify -P integration-tests      # all integration tests (*IT.java) via Testcontainers
mvn verify -P integration-tests -Dit.test=AuthIT  # single IT class
mvn test -P fulltest                 # all repository integration tests against real PostgreSQL (Testcontainers)
docker-compose up -d --build         # full stack (PostgreSQL + auth-service)
```

Local run (requires DB). On Windows/PowerShell, load the `.env` first:
```powershell
Get-Content .env | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } |
  ForEach-Object { $p = $_ -split '=', 2; [System.Environment]::SetEnvironmentVariable($p[0].Trim(), $p[1].Trim(), 'Process') }
mvn spring-boot:run
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

All methods across every layer return `Mono<T>` or `Flux<T>` — there are no blocking calls anywhere. Route beans in `ApiRouter` are declared as `RouterFunction<ServerResponse>` `@Bean` methods; each handler method is `Mono<ServerResponse>`.

## Token types

Three **non-interchangeable** JWT user types (claim `tipo`):

| `tipo`       | Table                       | Notable claims                                   |
|--------------|-----------------------------|--------------------------------------------------|
| `plataforma` | `saas.usuarios_plataforma`  | `rolPlataforma`; no `idCompania` → global access |
| `staff`      | `seguridad.usuarios`        | `idCompania`, `idSucursal`, `idRol`, `permisos[]`|
| `cliente`    | `identidad.usuarios_app`    | `idCompania`, `idPersona`                        |

`SecurityUtils` enforces type: `requireStaff()`, `requirePlataforma()`, `requireStaffWithPermiso("modulo:accion")`. Permissions are embedded in the JWT at login — no downstream DB query.

## Persistence conventions

- **Centralized identity via `identidad.personas`:** all three user tables (`UsuarioStaff`, `UsuarioPlataforma`, `UsuarioApp`) reference `personas` via `id_persona` FK and never store `nombre`/`foto_url` directly — always obtained via `LEFT/INNER JOIN identidad.personas p`, exposed as `nombre_persona` / `foto_url_persona` column aliases. The `Persona` must already exist before creating any user.
- `correo` on user tables is the login/corporate email and may differ from `personas.correo` (personal email); `nombre`/`foto_url` are updated only via the `Persona` API.
- All JSON request/response bodies use **snake_case** (`spring.jackson.property-naming-strategy: SNAKE_CASE`). DTO field names must match this convention.

## Key classes

- `infrastructure/security/JwtService` — JJWT 0.12.6 generate/parse (implements `TokenGeneratorPort`)
- `infrastructure/security/JwtAuthWebFilter`, `SecurityUtils` — auth pipeline (see README "Pipeline de seguridad")
- `application/service/AuthApplicationService` — all three login flows, token refresh, password reset
- `infrastructure/config/GlobalExceptionHandler` — maps domain exceptions to `ApiError` JSON
- `RateLimiterPort` — key format `"tipo:idCompania:login"`, platform uses `"platform:email"`
- `BitacoraPort` — every POST/PUT/DELETE must write an audit entry via this port

## Testing

Integration tests live in `src/test/`, use **Testcontainers** (real PostgreSQL container), extend `IntegrationTestBase`:

- `@SpringBootTest` + `@Transactional` — auto-rollback per test, no manual cleanup
- Seed helpers: `seedPlatform()`, `seedStaff()`, `seedPersona()`, `seedAppUser()`, `seedRol()`, `seedPermiso()`
- Bearer token builders: `platformBearer()`, `staffBearer(String... permisos)`, `staffBearerWithRol(int idRol, String... permisos)`
- Rate limiter reset before each test; all test data uses `id_compania = 99999` for isolation

Test files follow `*IT.java` (e.g. `AuthIT.java`, `UsuarioStaffIT.java`). When adding an endpoint, seed required entities in `@BeforeEach`, then test happy path plus auth/permission failures.

## Configuration

Sensitive values come from environment variables (see README "Variables de entorno" for the full list). Local overrides go in `application-local.yml` (git-ignored).
