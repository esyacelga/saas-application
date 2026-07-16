# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

API endpoint documentation lives in [../docs/core-service/](../docs/core-service/INDEX.md).

**When you add or change an endpoint or business rule (e.g. membership modes), update the matching file in `../docs/core-service/api/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-08):

| Área | Documento | Estado |
|------|-----------|--------|
| API — clientes (CRUD + registro app + plataforma) | [../docs/core-service/api/clientes.md](../docs/core-service/api/clientes.md) | ✅ Refleja el código (verificado contra `ClienteController`) |
| API — membresías (venta, validación, anulación) | [../docs/core-service/api/membresias.md](../docs/core-service/api/membresias.md) | ✅ Refleja el código (verificado contra `MembresiaController`) |
| API — tipos de membresía (catálogo) | [../docs/core-service/api/tipos-membresia.md](../docs/core-service/api/tipos-membresia.md) | ✅ Refleja el código (verificado contra `TipoMembresiaController`) |
| API — congelamientos (admin y cliente) | [../docs/core-service/api/congelamientos.md](../docs/core-service/api/congelamientos.md) | ✅ Refleja el código (verificado contra `CongelamientoController`) |
| API — endpoints internos (platform-service) | [../docs/core-service/api/internal.md](../docs/core-service/api/internal.md) | ✅ Refleja el código (verificado contra `InternalCoreController`) |
| Índice de docs centralizados | [../docs/core-service/INDEX.md](../docs/core-service/INDEX.md) | ✅ Actualizado con 4 nuevos documentos de API |

Fuente de verdad del enrutamiento: los `@RestController` bajo `infrastructure/adapter/in/web/` (`ClienteController`, `MembresiaController`, `TipoMembresiaController`, `CongelamientoController`). El único endpoint público es `GET /api/v1/membresias/validar-acceso`.

## Commands

```bash
# Build
mvn clean package

# Run (requires PostgreSQL and Redis)
mvn spring-boot:run

# Run ONLY unit tests (excludes *IntegrationTest.java — convención monorepo 2026-07-16)
mvn test

# Run everything: unit + integration tests (*IntegrationTest — requieren PostgreSQL+Redis reales)
mvn test -P fulltest

# Run a single integration test class
mvn test -P fulltest -Dtest=ClienteIntegrationTest

# Run a single test method
mvn test -P fulltest -Dtest=ClienteIntegrationTest#methodName
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

## Database Schemas

Persistence adapters write to the `core` schema (`core.clientes`, `core.membresias`, `core.congelamientos`, `core.tipos_membresia`). Several queries **cross schema boundaries** with JOINs:
- `identidad.personas` — person identity (name, CI, phone, email, avatar); not owned by this service
- `asistencia.asistencias` — attendance logs used to count used accesses for access-based memberships

All entities extend `BaseAuditEntity` (creacion_fecha/usuario, modifica_fecha/usuario). Deletions are **soft-deletes** via an `eliminado` boolean; all queries must filter `WHERE eliminado = false`.

## Authorization

`AccessControlService` exposes helper methods consumed by every controller:

| Method | Allowed roles |
|---|---|
| `requireAdminOrDueno()` | `super_admin`, `admin_compania`, `Dueño` |
| `requireRecepcionOrAbove()` | above + `Recepción` |
| `requireGymStaff()` | any staff token whose `id_compania` matches |
| `requireCliente()` | `tipo=cliente` token whose `id_compania` matches |

JWT claims: `tipo` (`staff`/`cliente`/`plataforma`), `rol_plataforma`, `id_compania`, `id_persona`.

The only **public** endpoint (no auth) is `GET /api/v1/membresias/validar-acceso`.

## State Machines

**Membresia** (membership):
```
activa ──congelar──→ congelada ──reactivar──→ activa
activa ──anular──→  anulada
activa ──(cron)──→  vencida  (past end date)
```

**Cliente** (updated by `CongelamientoService` on freeze/unfreeze, and by `ClienteStatusJobService` daily at 00:10):
```
activo ──congelar──→ congelado ──reactivar──→ activo | proximo_vencer | vencido
activo ──(cron)──→  proximo_vencer  (≤3 days to membership expiry)
proximo_vencer ──(cron)──→ vencido
vencido ──(new membership)──→ activo
```

## Error Handling

`GlobalExceptionHandler` (implements `ErrorWebExceptionHandler`) maps custom exceptions to HTTP status codes. Throw the right type from service/adapter layers:

| Exception | HTTP |
|---|---|
| `BusinessException` | 422 |
| `NotFoundException` | 404 |
| `ConflictException` | 409 |
| `ForbiddenException` | 403 |

Validation errors (`WebExchangeBindException`) → 400. All error responses include `timestamp`, `status`, `error`, `message`, `path`.

## Membership Control Modes

Memberships have two distinct billing/tracking modes:
- **Calendar-based** (`calendario`): Fixed-duration (days/weeks/months/años from start date). Expiry is computed once at creation.
- **Access-based** (`accesos`): Limited daily visits counted against `asistencia.asistencias`, with an overall expiration date. Remaining accesses are computed on every `validar-acceso` call.

This distinction drives branching logic in `MembresiaService` and the `ValidarAcceso` flow.

## Testing

Two test flavors coexist:
- `*Test.java` — Mockito unit tests (no Spring context). **The only ones run by default with `mvn test`.**
- `*IntegrationTest.java` — `WebTestClient` + a real PostgreSQL + Redis (configured in `application-test.yml`). **Excluded by default; run with `mvn test -P fulltest`** (convención monorepo 2026-07-16). `BaseIntegrationTest` provides shared setup. `DotEnvInitializer` loads `.env` for test context.

`BaseIntegrationTest` helpers:
- Seed methods: `seedPersona()`, `seedCliente()`, `seedTipoCalendario()`, `seedTipoAccesos()`, `seedMembresia()`, `seedMembresiaAccesos()`
- Token factories: `jwtAdminCompania()`, `jwtRecepcion()`, `jwtSuperAdmin()`
- `@BeforeEach cleanDatabase()` deletes all rows in reverse FK order

Test constants: `TEST_COMPANIA=1`, `TEST_SUCURSAL=1`, `TEST_USUARIO=1`.
