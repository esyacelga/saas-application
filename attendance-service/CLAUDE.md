# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See also [../docs/attendance-service/INDEX.md](../docs/attendance-service/INDEX.md) for centralized docs.

**When you add or change an endpoint, business rule, or messaging template type, update README.md's endpoint table (and this file if it affects conventions) in the same task.**

## Commands

```bash
# Build
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=AsistenciaManualIntegrationTest

# Run a single test method
mvn test -Dtest="AsistenciaManualIntegrationTest#duenoRegistraOverride"

# Run dev server
mvn spring-boot:run
```

Tests require a `.env` file at the project root — the `DotEnvInitializer` loads it automatically before tests run.

## Architecture

For the folder layout, stack, main domains, and endpoint list, see [README.md](README.md) — this file only covers conventions and implementation details not already documented there.

### External dependencies

Two WebClient beans are configured in `WebClientConfig`:

- **`CoreServiceClient`** — calls Core Service at `CORE_SERVICE_URL` (default `http://localhost:8082`). Three methods:
  - `validarAcceso(idPersona, idCompania, token)` → `ValidarAccesoResponse` (permitido, razon, idCliente, idMembresia, modoControl, diasAccesoRestantes, fechaFin, tipoMembresia, accesosUsados)
  - `buscarSucursalPorQr(qrToken)` → `SucursalQrResponse` (idSucursal, idCompania, nombreSucursal, qrTokenExpira)
  - `buscarIdClientePropio(idPersona, token)` → client ID for `/asistencias/me` endpoints
- **`AuthServiceClient`** — calls Auth Service at `AUTH_SERVICE_URL` (default `http://localhost:8080`). Used to resolve QR tokens to sucursal via `buscarSucursalPorQr`.

The override endpoint (`POST /asistencias/manual/override`) bypasses Core Service validation entirely.

### Database

PostgreSQL via R2DBC (reactive). The schema uses three PostgreSQL schemas:
- `asistencia.*` — owned by this service
- `core.*` and `identidad.*` — read-only references to data owned by Core Service

All domain tables have soft-delete (`eliminado` boolean) and audit columns (`creacion_fecha`, `creacion_usuario`, `modifica_fecha`, `modifica_usuario`) populated by `AuditAwareImpl`. **Audit fields in `BaseAuditEntity` use `java.time.Instant`** — Spring Data `@CreatedDate` does not support `OffsetDateTime`. Business timestamp fields in `MensajeLogEntity` (`fechaProgramada`, `fechaEnvio`) remain `OffsetDateTime`.

The JVM timezone is set to `America/Guayaquil` (UTC-5) in `AttendanceServiceApplication.main()` before Spring starts, so `LocalDate.now()` and `LocalTime.now()` reflect Ecuador local time.

### Security

JWT authentication via `JwtAuthenticationFilter` (WebFilter). **Public routes** (no token required):
- `POST /api/v1/asistencias/check` — QR self-check-in
- `GET /actuator/health`

All other routes require a valid JWT.

The `JwtPrincipal` carries: `userId`, `tipo` (`cliente` | `staff` | `plataforma`), `rol_gym` (`dueno` | `admin_compania` | `recepcion` | `entrenador`), `rol_plataforma` (`super_admin`), and `id_compania`. Convenience predicates: `isCliente()`, `isStaff()`, `isDueno()`, `isRecepcion()`, `isEntrenador()`. **`isDueno()` returns true for both `"dueno"` and `"admin_compania"` roles.**

Authorization logic lives in `AccessControlService`. Key methods:
- `requireStaff(principal)` — rejects clientes
- `requireDueno(principal)` — requires dueno or admin_compania
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

### Custom exceptions → HTTP status mapping

`GlobalExceptionHandler` maps these to specific HTTP statuses:
- `NotFoundException` → 404
- `ForbiddenException` → 403
- `ConflictException` → 409
- `GoneException` → 410
- `com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException` → 422
- Bean validation failures (`@Valid`) → 400 with field-level error details

Use the **custom** `IllegalArgumentException` from the `infrastructure.exception` package, not `java.lang.IllegalArgumentException`, to get 422 responses.

### Configuration

`AppProperties` only binds `app.cors.*`. JWT and scheduling properties are consumed directly via `@Value` elsewhere, not through `AppProperties`.

Required environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORE_SERVICE_URL`, `AUTH_SERVICE_URL`, `MESSAGING_JOB_CRON`.

### MensajeriaJob

Fires on cron `${scheduling.messaging-job-cron}` (default `0 15 0 * * *` — 00:15 UTC daily). **In tests the cron is set to `"-"` to disable it.**

Before sending, the job checks `MensajeLogService.contarEnviadosDesde()` to skip re-sending a notification type that was already sent since the client's last attendance (anti-spam deduplication).

Supported template variables: `{nombre}`, `{dias}`, `{fecha_vencimiento}`, `{accesos_restantes}`, `{gym_nombre}`. Falls back to hardcoded default templates if no custom `PlantillaMensaje` exists for a given `tipo` (`ausencia_2d`, `recuperacion_5d`, `recuperacion_10d`, `recuperacion_15d`, `vencimiento_3d`, `vencimiento_hoy`).

### Tests

All tests are integration tests (`@SpringBootTest` + `WebTestClient`) under `src/test/java/.../integration/`. There are no unit tests.

`BaseIntegrationTest` provides:
- A pre-wired `WebTestClient`
- A `DatabaseClient` for raw SQL setup/teardown
- JWT builder helpers for minting tokens with any role combination
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
