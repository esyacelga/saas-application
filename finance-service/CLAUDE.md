# CLAUDE.md — finance-service

This file provides guidance to Claude Code when working with code in this repository.

**When you add or change an endpoint, business rule, or permission, update the endpoint table below and this file in the same task.**

## Fuentes autoritativas

| Área | Documento | Estado |
|------|-----------|--------|
| Endpoints (12 rutas) | Tabla al final de este archivo | ✅ Corregido al crear el servicio 2026-07-08 |
| Convenciones + seguridad | Este archivo (`CLAUDE.md`) | ✅ Actualizado 2026-07-08 |
| Índice de docs centralizados | [../docs/STATUS.md](../docs/STATUS.md) | 🟡 Verificar estado |

Fuente de verdad del enrutamiento: `CategoriaIngresoController`, `IngresoController`, `CategoriaEgresoController`, `EgresoController`, `ReporteController` bajo `infrastructure/adapter/in/web/`.

## Commands

```bash
# Build
mvn clean package

# Run all tests
mvn test

# Run dev server (listens on port 8085)
mvn spring-boot:run
```

## Architecture

Hexagonal architecture (ports & adapters):
- **Domain** (`domain/model/`, `domain/port/`): pure Java, zero framework imports. Models: `CategoriaIngreso`, `Ingreso`, `CategoriaEgreso`, `Egreso`. Ports define interfaces only.
- **Application** (`application/service/`): orchestrates use cases, calls domain ports. `AccessControlService` centralizes permission checks.
- **Infrastructure** (`infrastructure/`): Spring Boot, R2DBC, WebFlux adapters. Controllers → Services → Persistence Adapters → R2DBC Repositories.

### Database

PostgreSQL via R2DBC (reactive). Schema: `finanzas.*`
- `finanzas.categorias_ingreso` — income categories per company/branch
- `finanzas.ingresos` — income records (immutable — no PUT/DELETE)
- `finanzas.categorias_egreso` — expense categories per company/branch
- `finanzas.egresos` — expense records (soft-delete via `eliminado`)

All domain tables have soft-delete (`eliminado BOOLEAN`) and audit columns populated by `AuditAwareImpl`. **Audit fields in `BaseAuditEntity` use `java.time.Instant`**. Business date fields (`fecha`) use `LocalDate`.

The JVM timezone is set to `America/Guayaquil` (UTC-5) in `FinanceServiceApplication.main()` before Spring starts.

### Multi-tenancy

Every query MUST filter by `id_compania` extracted from the JWT. Never expose another company's data. `id_compania` comes from `JwtPrincipal.getIdCompania()`.

### Security

JWT authentication via `JwtAuthenticationFilter` (WebFilter). **Public routes** (no token required):
- `GET /actuator/health`

All other routes require a valid JWT. Authorization logic lives in `AccessControlService`:
- `requireFinanzasLeer(principal)` — `finanzas:leer` OR `isDueno()` OR `isPlataforma()`
- `requireFinanzasCrear(principal)` — `finanzas:crear` OR `isDueno()` OR `isPlataforma()`
- `requireFinanzasReportes(principal)` — `finanzas:exportar` OR `finanzas:leer` OR `isDueno()` OR `isPlataforma()`

Special case for `POST /finanzas/ingresos`: also allowed for `isRecepcion()`.

`JwtPrincipal` carries: `userId`, `tipo` (`staff` | `plataforma`), `rol_gym` (`dueno` | `admin_compania` | `recepcion` | `entrenador`), `rol_plataforma` (`super_admin`), `id_compania`, `id_sucursal`, `permisos` (List<String>).

### Business Rules

- **RN-01**: Ingresos are immutable — no PUT or DELETE on `ingresos`. Once registered, a record cannot be modified or deleted (only soft-delete is technically possible but blocked at API level).
- **RN-02**: All queries filter `eliminado = false`.
- **RN-03**: Multi-tenancy — all queries filter by `id_compania` from JWT.
- **RN-04**: Categorías can be deactivated (`activo=false`) but only if no active ingresos/egresos reference them.
- **RN-05**: `id_sucursal` defaults to JWT principal's `idSucursal` if not provided in request body.
- **RN-06**: Report aggregations use `DatabaseClient` for SUM + GROUP BY queries.

### Jackson serialization

All JSON uses `SNAKE_CASE` (configured globally in `application.yml`). DTO fields are `camelCase` in Java — Jackson maps them automatically. `write-dates-as-timestamps: false` means `Instant`/`LocalDate` fields serialize as ISO-8601 strings.

### Error Handling — contrato estandarizado RFC 7807 + `codigo`

Contrato de errores estandarizado **RFC 7807 (`ProblemDetail`) + campo `codigo`** — ver [../docs/gym-administrator/architecture/error-contract.md](../docs/gym-administrator/architecture/error-contract.md). Replicado del piloto de `core-service` (2026-07-19). Reemplaza al antiguo `@RestControllerAdvice`.

`GlobalExceptionHandler` (implements `ErrorWebExceptionHandler`, `@Order(HIGHEST_PRECEDENCE)`, en `infrastructure/config/`) es el punto único de salida de errores; captura también los errores fuera del controller (filtros/routing):

| Exception | HTTP | `codigo` |
|---|---|---|
| `NotFoundException` | 404 | `recurso_no_encontrado` |
| `ConflictException` | 409 | su `getCodigo()` propio (o `conflicto` si es null) |
| `ForbiddenException` / `AccessDeniedException` | 403 | `acceso_denegado` |
| `com.gymadmin.finance.infrastructure.exception.IllegalArgumentException` | 422 | `regla_negocio` |
| `DataIntegrityViolationException` | 409 | `datos_duplicados` / `referencia_invalida` / `campo_requerido` (vía `DataIntegrityMapper`) |
| `WebExchangeBindException` | 400 | `validacion` (+ `errores: [{campo, mensaje}]`) |
| no controlada | 500 | `error_interno` |

Always use the **custom** `IllegalArgumentException` from `infrastructure.exception`, not `java.lang.IllegalArgumentException`.

**Sobre de salida** (`application/problem+json`): 5 campos RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) + extensiones en **snake_case** (`codigo`, `mensaje` [alias de `detail`], `timestamp`, y `errores` en validación).

**Piezas del paquete:**
- `infrastructure/exception/`: `ErrorCode` (enum), `ProblemDetailFactory` (construye y aplana el `ProblemDetail`), `DataIntegrityMapper`.
- `infrastructure/config/`: `GlobalExceptionHandler`, `ApiAuthenticationEntryPoint` (401 → `no_autenticado`) + `ApiAccessDeniedHandler` (403 → `acceso_denegado`) registrados en `SecurityConfig.exceptionHandling(...)`. El `JwtAuthenticationFilter` delega su 401 al entrypoint (no un 401 vacío).

Tests: `GlobalExceptionHandlerTest` (mapeo + serialización snake_case + preservación del `codigo` propio de `ConflictException`), `SecurityErrorContractTest` (401/403 de Security).

### Persistence adapter pattern

When adding or updating a `toEntity()` mapper for an **UPDATE** operation, always forward the audit fields:

```java
return EntityClass.builder()
    ...
    .creacionFecha(domain.getCreacionFecha())   // preserve on UPDATE
    .creacionUsuario(domain.getCreacionUsuario())
    .build();
```

### Defer pattern

Always wrap command construction in `Mono.defer()` inside flatMap after access check:

```java
return getJwtPrincipal()
    .flatMap(p -> accessControl.requireFinanzasCrear(p)
        .then(Mono.defer(() -> ingresoUseCase.registrar(new IngresoUseCase.RegistrarCommand(...))))
        .map(i -> ResponseEntity.status(201).body(i)));
```

## Endpoint List

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/finanzas/categorias-ingreso` | `finanzas:leer` / dueno / plataforma | List income categories |
| POST | `/api/v1/finanzas/categorias-ingreso` | `finanzas:crear` / dueno / plataforma | Create income category |
| PUT | `/api/v1/finanzas/categorias-ingreso/{id}/desactivar` | `finanzas:crear` / dueno / plataforma | Deactivate income category |
| GET | `/api/v1/finanzas/ingresos` | `finanzas:leer` / dueno / plataforma | List income records with pagination |
| POST | `/api/v1/finanzas/ingresos` | `finanzas:crear` / dueno / recepcion / plataforma | Register income (immutable) |
| GET | `/api/v1/finanzas/categorias-egreso` | `finanzas:leer` / dueno / plataforma | List expense categories |
| POST | `/api/v1/finanzas/categorias-egreso` | `finanzas:crear` / dueno / plataforma | Create expense category |
| PUT | `/api/v1/finanzas/categorias-egreso/{id}/desactivar` | `finanzas:crear` / dueno / plataforma | Deactivate expense category |
| GET | `/api/v1/finanzas/egresos` | `finanzas:leer` / dueno / plataforma | List expense records with pagination |
| POST | `/api/v1/finanzas/egresos` | `finanzas:crear` / dueno / plataforma | Register expense |
| GET | `/api/v1/finanzas/reporte/resumen` | `finanzas:leer` / dueno / plataforma | Summary report (totals + by category) |
| GET | `/api/v1/finanzas/reporte/mensual` | `finanzas:leer` / dueno / plataforma | Monthly breakdown by year |
| GET | `/api/v1/finanzas/reporte/proyeccion` | `finanzas:leer` / dueno / plataforma | Projection based on last N months |
| GET | `/actuator/health` | public | Health check |
