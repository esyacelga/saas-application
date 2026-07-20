# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


**When you add or change an endpoint, business rule, or messaging template type, update README.md's endpoint table (and this file if it affects conventions) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-08):

| Área | Documento | Estado |
|------|-----------|--------|
| Endpoints (16 rutas reales) | Tabla en [README.md](README.md) | ✅ Corregido 2026-07-08 (antes decía `/asistencias/check` público — no existe) |
| Convenciones + seguridad + jobs | Este archivo (`CLAUDE.md`) | ✅ Corregido 2026-07-08 con la nota de la regla muerta |
| Índice de docs centralizados | [../docs/attendance-service/INDEX.md](../docs/attendance-service/INDEX.md) | 🟡 Índice — verificar detalle contra el código |

> ⚠️ Recordatorio: el único endpoint público real es `GET /actuator/health`. El registro por QR (`POST /api/v1/asistencias/qr`) **exige JWT de cliente**. La regla `.permitAll("/api/v1/asistencias/check")` en `SecurityConfig.java` es dead code — no confiar en ella.

Fuente de verdad del enrutamiento: `AsistenciaController`, `MensajeLogController`, `PlantillaMensajeController` bajo `infrastructure/adapter/in/web/`.

## Commands

```bash
# Build
mvn clean package

# Run ONLY unit tests (excludes *IntegrationTest.java and *IT.java)
mvn test

# Run everything: unit + endpoint IT (*IntegrationTest) + repository IT (*IT)
mvn test -P fulltest

# Run only the repository IT tests
mvn test -P fulltest -Dtest='*RepositoryIT'

# Run a single integration test class
mvn test -P fulltest -Dtest=AsistenciaManualIntegrationTest

# Run a single test method
mvn test -P fulltest -Dtest="AsistenciaManualIntegrationTest#duenoRegistraOverride"

# Run dev server (listens on port 8084)
mvn spring-boot:run
```

Tests require a `.env` file at the project root — the `DotEnvInitializer` loads it automatically before tests run. `application-test.yml` reads the same `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` variables as the dev runtime (no separate test DB).

**Maven profiles:**

| Comando | Qué corre |
|---|---|
| `mvn test` | Solo unit tests — excluye `*IntegrationTest.java` y `*IT.java` (convención monorepo 2026-07-16) |
| `mvn test -P fulltest` | Todo: unit + endpoint `*IntegrationTest.java` + repository `*IT.java` — requiere `.env` con `DB_*` válido |

## Architecture

For the folder layout, stack, main domains, and endpoint list, see [README.md](README.md) — this file only covers conventions and implementation details not already documented there.

### External dependencies

Three WebClient beans are configured in `WebClientConfig`:

- **`CoreServiceClient`** — calls Core Service at `CORE_SERVICE_URL` (default `http://localhost:8082`). Four methods:
  - `validarAcceso(idPersona, idCompania, token)` → `ValidarAccesoResponse` (permitido, razon, idCliente, idMembresia, modoControl, diasAccesoRestantes, fechaFin, tipoMembresia, accesosUsados)
  - `buscarSucursalPorQr(qrToken, bearerToken)` → `SucursalQrResponse` (idSucursal, idCompania, nombreSucursal, qrTokenExpira)
  - `buscarIdClientePropio(idPersona, token)` → client ID for `/asistencias/me` endpoints
  - `listarClientesPorVencer(idCompania, dias, modo)` → list of clients expiring in the next `dias` days (feature WhatsApp avisos vencimiento, Fase 6). Sends the `X-Internal-Call` header from the injected `internal-secret`.
- **`PlatformServiceClient`** — calls Platform Service at `PLATFORM_SERVICE_URL` (env var, default `http://localhost:8081`). One method:
  - `obtenerBucketPrevioSocio(fallback)` → reads `GET /internal/v1/notif-buckets/socio` (`{destinatario, dias_previo, activo}`) and returns the effective previo bucket for the socio; `activo=false` → 0, and any error / missing row / unreachable platform → the `fallback` value so the job never breaks (Fase 6). Sends the `X-Internal-Call` header from the injected `internal-secret`.
- **`AuthServiceClient`** — calls Auth Service at `AUTH_SERVICE_URL` (default `http://localhost:8080`). Used to resolve QR tokens to sucursal via `buscarSucursalPorQr`.

Configuration (from `application.yml`):
- `services.platform-service.url` — Platform Service base URL (env var `PLATFORM_SERVICE_URL`, default `http://localhost:8081`)
- `services.platform-service.internal-secret` — shared secret for `/internal/v1/` endpoints (env var `INTERNAL_SECRET`, default `platform-secret-dev`; same value across core/platform/attendance)

The override endpoint (`POST /asistencias/manual/override`) bypasses Core Service validation entirely.

### Database

PostgreSQL via R2DBC (reactive). The schema uses three PostgreSQL schemas:
- `asistencia.*` — owned by this service
- `core.*` and `identidad.*` — read-only references to data owned by Core Service

All domain tables have soft-delete (`eliminado` boolean) and audit columns (`creacion_fecha`, `creacion_usuario`, `modifica_fecha`, `modifica_usuario`) populated by `AuditAwareImpl`. **Audit fields in `BaseAuditEntity` use `java.time.Instant`** — Spring Data `@CreatedDate` does not support `OffsetDateTime`. Business timestamp fields in `MensajeLogEntity` (`fechaProgramada`, `fechaEnvio`) remain `OffsetDateTime`.

The JVM timezone is set to `America/Guayaquil` (UTC-5) in `AttendanceServiceApplication.main()` before Spring starts, so `LocalDate.now()` and `LocalTime.now()` reflect Ecuador local time.

### Security

JWT authentication via `JwtAuthenticationFilter` (WebFilter). **Public routes** (no token required):
- `GET /actuator/health`

All other routes require a valid JWT — **including QR self-check-in** (`POST /api/v1/asistencias/qr`), which requires a `cliente` token (enforced via `accessControl.requireCliente`). The real member flow logs in first, then checks in.

> ⚠️ **Known discrepancy:** `SecurityConfig` has a `.permitAll()` rule for `/api/v1/asistencias/check`, but no such endpoint exists (the QR endpoint is `/asistencias/qr`). It is a dead rule — it permits a path nothing is mapped to. See STATUS.md. Do not rely on it; do not assume `/qr` is public.

The `JwtPrincipal` carries: `userId`, `tipo` (`cliente` | `staff` | `plataforma`), `rol_gym` (`dueno` | `admin_compania` | `recepcion` | `entrenador`), `rol_plataforma` (`super_admin`), and `id_compania`. Convenience predicates: `isCliente()`, `isStaff()`, `isDueno()`, `isRecepcion()`, `isEntrenador()`. **`isDueno()` returns true for both `"dueno"` and `"admin_compania"` roles.**

Authorization logic lives in `AccessControlService`. Key methods:
- `requireCliente(principal)` — rejects non-clientes
- `requireStaff(principal)` — rejects clientes
- `requireStaffOrPlataforma(principal)` — allows staff | plataforma
- `requireDueno(principal)` — requires dueno or admin_compania
- `requireDuenoOrPlataforma(principal)` — allows dueno | admin_compania | plataforma
- `requireNotEntrenador(principal)` — blocks entrenadores from manual registration
- `requireAccessToCompania(principal, idCompania)` — cross-tenant guard; ensures staff only touch their own company

When building a use-case command inside a controller `flatMap` that starts with an access check, **always wrap the command construction in `Mono.defer()`** to prevent eager evaluation before the access check can short-circuit:

```java
return getJwtPrincipal()
    .flatMap(principal -> accessControl.requireCliente(principal)
        .then(Mono.defer(() -> useCase.execute(new Command(
            Integer.parseInt(principal.getUserId()), // only runs if access check passed
            ...
        ))))
        .map(result -> ResponseEntity.status(201).body(result)));
```

### Error Handling — contrato estandarizado (RFC 7807 + `codigo`)

Contrato de errores estandarizado **RFC 7807 (`ProblemDetail`) + campo `codigo`** — ver [../docs/gym-administrator/architecture/error-contract.md](../docs/gym-administrator/architecture/error-contract.md). Replicado del piloto de core-service (2026-07-19). El antiguo `@RestControllerAdvice` fue **reemplazado** por un `GlobalExceptionHandler` que implementa `ErrorWebExceptionHandler` con `@Order(HIGHEST_PRECEDENCE)` (vive en `infrastructure/config/`, no en `infrastructure/exception/`). Es el punto único de salida de errores y captura también fallos de filtros/routing que el advice no veía.

| Exception | HTTP | `codigo` |
|---|---|---|
| `NotFoundException` | 404 | `recurso_no_encontrado` |
| `ForbiddenException` / `AccessDeniedException` | 403 | `acceso_denegado` |
| `ConflictException` | 409 | **su `getCodigo()` EXACTO** (fallback `conflicto` si null) |
| `GoneException` | 410 | `recurso_no_disponible` |
| `com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException` | 422 | `regla_negocio` |
| `DataIntegrityViolationException` | 409 | `datos_duplicados` / `referencia_invalida` / `campo_requerido` (vía `DataIntegrityMapper`) |
| `WebExchangeBindException` (`@Valid`) | 400 | `validacion` (+ `errores: [{campo, mensaje}]`) |
| no controlada | 500 | `error_interno` |

Use the **custom** `IllegalArgumentException` from the `infrastructure.exception` package, not `java.lang.IllegalArgumentException`, to get 422 responses.

> 🔒 **Contrato PWA CONGELADO (hallazgo #3):** los `codigo` que lanza `ConflictException` — `ya_registrado_hoy`, `sin_membresia`, `membresia_expirada`, `accesos_agotados`, `congelado`, `ultima_plantilla` — se emiten **tal cual** en el campo `codigo` del sobre. El handler NO los traduce (usa `getCodigo()` directo). El PWA (`gym-member-pwa` `CheckInPage`) hace branching de UI sobre esos strings exactos — renombrarlos rompe el check-in del socio en producción.

**Sobre de salida** (`application/problem+json`): 5 campos RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) + extensiones en **snake_case** (`codigo`, `mensaje` [alias de `detail`], `timestamp`, y `errores` en validación).

**Piezas del paquete:**
- `infrastructure/exception/`: `ErrorCode` (enum, incluye `RECURSO_NO_DISPONIBLE` para el 410), `ProblemDetailFactory` (construye y aplana el sobre), `DataIntegrityMapper` (constraints PostgreSQL → `codigo` + mensaje legible), + las excepciones de dominio existentes.
- `infrastructure/config/`: `GlobalExceptionHandler` (el `ErrorWebExceptionHandler`), `ApiAuthenticationEntryPoint` (401 → `no_autenticado`) + `ApiAccessDeniedHandler` (403 → `acceso_denegado`), registrados en `SecurityConfig.exceptionHandling(...)`. El `JwtAuthenticationFilter` delega su 401 al entrypoint (no un 401 vacío).

Tests: `unit/GlobalExceptionHandlerTest` (mapeo, serialización snake_case, 410, y verificación de los `codigo` congelados del PWA), `unit/SecurityErrorContractTest` (401/403 de Security).

### Jackson serialization

All JSON uses `SNAKE_CASE` (configured globally in `application.yml`). DTO fields must be `camelCase` in Java — Jackson maps them automatically. `write-dates-as-timestamps: false` means `Instant`/`OffsetDateTime` fields serialize as ISO-8601 strings.

### MensajeLog estado lifecycle

`estado` transitions: `pendiente` → `enviado` (on success) or `fallido` (on error). `POST /mensajes/reenviar/{id}` is only allowed from `fallido` state — attempting reenvio on a non-`fallido` log returns 409.

### Configuration

`AppProperties` only binds `app.cors.*`. JWT and scheduling properties are consumed directly via `@Value` elsewhere, not through `AppProperties`.

Required environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORE_SERVICE_URL`, `AUTH_SERVICE_URL`, `MESSAGING_JOB_CRON`.

### MensajeriaJob

Fires on cron `${scheduling.messaging-job-cron}` (default `0 15 0 * * *` — 00:15 Guayaquil time, JVM en `America/Guayaquil`). Avisa por WhatsApp a los socios cuya membresía está próxima a vencer (Fase 5/6). **Solo aviso previo** — no envía notificación el día del vencimiento. Idempotente: ✅ Sí — usa `existsEnviadoHoy()` antes de enviar. Startup hook: ✅ Sí — si `jobs.run-on-startup=true`.

**In tests the cron is set to `"-"` to disable it.**

Ver doc centralizado: [`../docs/gym-administrator/architecture/scheduled-jobs.md`](../docs/gym-administrator/architecture/scheduled-jobs.md).

Supported template variables: `{nombre}`, `{dias}`, `{fecha_vencimiento}`, `{accesos_restantes}`, `{gym_nombre}`. Falls back to hardcoded default templates if no custom `PlantillaMensaje` exists for a given `tipo` (`ausencia_2d`, `recuperacion_5d`, `recuperacion_10d`, `recuperacion_15d`, `vencimiento_3d`, `vencimiento_hoy`).

### Tests

Three flavors coexist under `src/test/java/`:

- `unit/*Test.java` — Mockito unit tests (no Spring context). **The only ones run by default with `mvn test`.**
- `integration/*IntegrationTest.java` — endpoint-level integration via `WebTestClient` against the real local Postgres. Excluded by default; run with `mvn test -P fulltest`.
- `integration/repository/*RepositoryIT.java` — R2DBC repository integration against the real local Postgres. Excluded by default; run with `mvn test -P fulltest`.

`BaseIntegrationTest` provides:
- A pre-wired `WebTestClient`
- A `DatabaseClient` for raw SQL setup/teardown
- JWT helpers (all return a raw token string; wrap with `bearerHeader(jwt)` for the `Authorization` header):
  - `jwtCliente(idCliente, idCompania)`
  - `jwtRecepcion(idCompania)`
  - `jwtDueno(idCompania)`
  - `jwtSuperAdmin()`
  - `jwtEntrenador(idCompania)`
- SQL helper methods (`insertarAsistencia`, `insertarPlantilla`, `insertarClienteCore`, `insertarMembresia`)

Each test class calls `cleanDatabase()` in `@BeforeEach`, which deletes all rows in reverse FK order. Tests rely on a live PostgreSQL instance configured via `.env`.

Test methods use `@DisplayName` with human-readable descriptions. Test case IDs follow the pattern `TC-ASI-DB-001` (domain + category + sequence) as inline comments.

#### Cross-schema FK constraints in test helpers

`core.clientes` → `identidad.personas` → `core.membresias` → `asistencia.asistencias` is the FK chain. Any test inserting an `asistencia` or `mensajes_log` row must first call:
1. `insertarClienteCore(idCompania, idSucursal)` — inserts into `identidad.personas` + `core.clientes`
2. `insertarMembresia(idCliente, idCompania)` — inserts into `core.tipos_membresia` + `core.membresias`

`insertarClienteCore` generates a CI string capped at 15 chars (`"IT" + 14-digit suffix`) to fit `identidad.personas.ci varchar(20)`. The CI prefix `"IT%"` is used by `cleanDatabase()` to identify test rows.

#### Persistence adapter pattern

When adding or updating a `toEntity()` mapper for an **UPDATE** operation, always forward the audit fields so they are not nulled out:

```java
return EntityClass.builder()
    ...
    .creacionFecha(domain.getCreacionFecha())   // preserve on UPDATE
    .creacionUsuario(domain.getCreacionUsuario())
    .build();
```

R2DBC determines insert vs update based on whether `@Id` is null, so new records (null id) do not need audit fields pre-set — `@CreatedDate` populates them automatically.
