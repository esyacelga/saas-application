# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Auth Service** — the single authentication and authorization entry point for a multi-tenant Gym Administrator SaaS platform. Built with **Spring Boot 3.3.5 / Java 21 / Spring WebFlux (reactive) / R2DBC / PostgreSQL**. All service methods return `Mono<T>` or `Flux<T>`; there are no blocking calls.

## Commands

```bash
# Build
mvn clean package
mvn clean package -DskipTests   # skip tests

# Run locally
mvn spring-boot:run

# Test
mvn test
mvn test -Dtest=ClassName       # single class

# Docker (full stack: PostgreSQL + auth-service)
docker-compose up -d
docker-compose up -d --build    # rebuild images
docker-compose logs -f auth-service
docker-compose down -v          # remove volumes too
```

No linter is configured. The Postman collection `auth-service.postman_collection.json` covers all endpoints.

## Architecture

The codebase follows **Hexagonal Architecture (Ports & Adapters)**:

```
infrastructure/adapter/in/web/
  ApiRouter        ← defines all routes (functional RouterFunctions, no @Controller)
  Handler          ← one per domain (AuthHandler, UsuarioStaffHandler, …)
        │
        │  domain/port/in/  (input ports — interfaces)
        ▼
application/service/
  *ApplicationService   ← implements the use case interface
        │
        │  domain/port/out/  (output ports — interfaces)
        ▼
infrastructure/adapter/out/persistence/
  *PersistenceAdapter   ← implements output ports; uses R2DBC repositories + DatabaseClient
```

**Domain** (`domain/`) is the center and has zero infrastructure dependencies:
- `model/` — plain Java records/classes for all entities
- `port/in/` — use-case interfaces (`AuthUseCase`, `UsuarioStaffUseCase`, …)
- `port/out/` — repository/service interfaces (`UsuarioStaffPort`, `TokenGeneratorPort`, …)
- `exception/` — domain exception classes

When adding a feature, the change must touch all four layers: domain port → application service → persistence adapter → handler + router.

### Three Independent User Hierarchies

Each hierarchy has its own entity, repository, service, and JWT type. Tokens are **never interchangeable**:

| Level | Entity | Schema.Table | JWT `tipo` | Key claims |
|-------|--------|-------------|------------|------------|
| Platform operator | `UsuarioPlataforma` | `saas.usuarios_plataforma` | `"plataforma"` | `rol_plataforma` — **no** `id_compania` |
| Gym staff | `UsuarioStaff` | `seguridad.usuarios` | `"staff"` | `id_compania`, `id_sucursal`, `id_rol`, `permisos[]` |
| App client | `UsuarioApp` | `identidad.usuarios_app` | `"cliente"` | `id_compania`, `id_persona` |

Absence of `id_compania` in a platform token signals global (cross-company) admin access.

### Database Schemas

Four PostgreSQL schemas: `saas` (platform users), `identidad` (persons, app users, refresh tokens), `seguridad` (staff, roles, permissions, audit log), `tenant` (companies — `tenant.companias`).

Key entity relationships:
- `Persona` (identidad) ← 1:N → `UsuarioApp`: a person can have one app account per company
- `Persona` (identidad) ← 1:N → `UsuarioStaff`: a person can have one staff account per company (`UNIQUE (id_persona, id_compania)`)
- `Persona` (identidad) ← 1:1 → `UsuarioPlataforma`: a person can have one platform operator account (`UNIQUE (id_persona)`)
- `Rol` ← N:N (via `RolPermiso` composite key) → `Permiso`
- `RefreshToken` links to all three user types via nullable `id_compania` (null = platform token)

**Centralized identity via `identidad.personas`:** All three user types reference `personas` via `id_persona FK`. `nombre` and `foto_url` are never stored in the user tables — they are always obtained via JOIN. This means:
- Before creating any user (`UsuarioStaff`, `UsuarioPlataforma`, `UsuarioApp`), the `Persona` must already exist.
- All persistence adapters use `LEFT/INNER JOIN identidad.personas p ON *.id_persona = p.id` and expose `nombre_persona` / `foto_url_persona` as column aliases.
- The `correo` on user tables is the login/corporate email and may differ from `personas.correo` (personal email).
- `nombre` and `foto_url` are updated via the `Persona` API, not via user endpoints.

Unique constraints to be aware of: `UsuarioStaff.correo` is unique per company, `UsuarioApp.login` is unique per company, `Persona.ci` is globally unique and immutable after creation.

### Security Pipeline

```
Request → JwtAuthWebFilter (extract Bearer, build UserPrincipal)
        → SecurityConfig (stateless, per-path rules)
        → ApiRouter → Handler → ApplicationService (via UseCase port)
        → PersistenceAdapter
```

`UserPrincipal` carries `tipo`, `id_compania`, and `permisos[]` so services can enforce multi-tenant boundaries without extra DB queries.

Authorization is enforced by calling `SecurityUtils` helpers inside each application service method:
- `SecurityUtils.requirePlataforma(principal)` — platform-only endpoints
- `SecurityUtils.requireStaff(principal)` — staff-only endpoints
- `SecurityUtils.requirePermiso(principal, "modulo:accion")` — fine-grained permission check

### Key Modules

- **`infrastructure/security/`** — `JwtTokenProvider` (JJWT 0.12.6 generate/parse), `JwtAuthWebFilter`, `SecurityUtils`
- **`application/service/AuthApplicationService`** — implements `AuthUseCase`; all three login flows, token refresh, password reset (1-hour reset tokens)
- **`infrastructure/adapter/out/persistence/`** — R2DBC adapters; complex queries use raw `DatabaseClient`, simple queries use repository interfaces
- **`infrastructure/config/`** — `SecurityConfig` (stateless Spring Security), `JwtProperties`, `AppProperties`, `R2dbcConfig`
- **`infrastructure/config/GlobalExceptionHandler`** — maps all domain exceptions to `ApiError` JSON responses

Rate limiting is handled via `RateLimiterPort` (key format: `"tipo:idCompania:login"`, platform uses `"platform:email"`). Every POST/PUT/DELETE writes an audit entry to `seguridad.bitacora_accesos` via `BitacoraPort`.

### Token Lifetimes (configurable via env vars)

| Token | Default |
|-------|---------|
| Staff JWT | 8 hours |
| Platform JWT | 8 hours |
| Client JWT | 7 days |
| Refresh token | 30 days (single-use, stored in DB) |

### RBAC

Permissions follow the pattern `modulo:accion` (e.g., `socios:leer`, `pagos:escribir`). Staff JWTs embed the full resolved permission list at login time, so downstream services can verify access without calling auth-service again.

### API Route Prefixes

All routes are defined as functional `RouterFunction` beans in `ApiRouter.java`.

| Prefix | Who can access |
|--------|---------------|
| `/api/v1/auth/*` | Public (login, refresh, password reset, companies-by-email) |
| `/api/v1/personas/*` | Public (person lookup and creation — no auth required) |
| `/api/v1/platform/*` | `tipo=plataforma` only |
| `/api/v1/app-usuarios/*` | Platform or staff with appropriate permissions |
| `/api/v1/usuarios`, `/roles`, `/permisos`, `/bitacora` | Staff with permissions |
| `/actuator/health` | Public |

### Critical Business Rules

- Login failures return the same 401 regardless of whether the email exists.
- Passwords: bcrypt, minimum 12 rounds (`AppProperties.bcryptRounds`).
- All write operations (POST/PUT/DELETE) must generate a `BitacoraAcceso` entry.
- Refresh tokens are single-use; old token is invalidated on each refresh.
- Staff and client tokens must include `id_compania` matching the requested resource.
- Cannot delete the last `super_admin` platform operator (enforced in `PlatformUsuarioApplicationService`).
- Cannot delete the last owner-role staff user in a company (enforced in `UsuarioStaffApplicationService`).
- `Persona.ci` (national ID) is immutable after creation — update attempts on this field are rejected.
- One app account per person per company: `UsuarioApp` has `UNIQUE (id_compania, id_persona)`.
- One staff account per person per company: `UsuarioStaff` has `UNIQUE (id_persona, id_compania)`.
- One platform account per person: `UsuarioPlataforma` has `UNIQUE (id_persona)`.
- Roles cannot be deleted if any staff user is assigned to them.
- Permissions assigned to a role must belong to the same company as the role.

## Testing

Integration tests live in `src/test/` and use **Testcontainers** (spins up a real PostgreSQL container). All tests extend `IntegrationTestBase`, which provides:

- `@SpringBootTest` + `@Transactional` — each test rolls back automatically, no cleanup needed
- Seed helpers: `seedPlatform()`, `seedStaff()`, `seedPersona()`, `seedAppUser()`, `seedRol()`, `seedPermiso()`
- Bearer token builders: `bearerPlatform()`, `bearerStaff()`, `bearerCliente()`
- Rate limiter reset before each test
- All test data uses `id_compania = 99999` for isolation

Test files follow the `*IT.java` naming pattern (e.g., `AuthIT.java`, `UsuarioStaffIT.java`). When adding a new endpoint, seed the required entities in `@BeforeEach`, then test happy path and auth/permission failures.

## Configuration

All sensitive values come from environment variables (see `application.yml` and `docker-compose.yml`):

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
JWT_SECRET, JWT_EXPIRY_STAFF, JWT_EXPIRY_CLIENTE, JWT_EXPIRY_REFRESH
BCRYPT_ROUNDS, MAX_LOGIN_ATTEMPTS, LOCKOUT_DURATION_MINUTES, PASSWORD_RESET_EXPIRY_MINUTES
```

Local overrides go in `application-local.yml` (git-ignored).
