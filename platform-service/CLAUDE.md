# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See also [../docs/platform-service/INDEX.md](../docs/platform-service/INDEX.md) for centralized docs.

**When you add or change an endpoint, route group, or business rule, update the "Endpoints" section here and README.md's endpoint documentation in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-08):

| Área | Documento | Estado |
|------|-----------|--------|
| Endpoints (route groups) | Sección "Endpoints" de este archivo | ✅ Refleja los `@RestController` actuales |
| Setup, variables de entorno, schemas | [README.md](README.md) | ✅ Corregido 2026-07-08 (schemas y `/actuator/health`) |
| Índice de docs centralizados | [../docs/platform-service/INDEX.md](../docs/platform-service/INDEX.md) | 🟡 Índice — verifica cada doc contra el código si el detalle importa |

Fuente de verdad del enrutamiento: los `@RestController` bajo `infrastructure/adapter/in/web/`. Salvedad conocida: las rutas públicas también incluyen `/planes/publicos` y `/companias/auto-registro` además de `/modulos/check` y `/actuator/health`.

## Commands

```bash
mvn clean package                              # Build JAR → target/platform-service-new.jar
mvn spring-boot:run                            # Run locally (reads .env via spring-dotenv)
mvn test                                       # ONLY unit tests — EXCLUDES *IntegrationTest.java and *IT.java
mvn test -P fulltest                           # Everything: unit + endpoint IT (*IntegrationTest) + repository IT (*IT)
mvn test -P fulltest -Dtest='*RepositoryIT'    # Only the repository IT tests
mvn test -P fulltest -Dtest=CompaniaIntegrationTest  # Run a single integration test class
```

Java 25 (the JVM that runs the tests must be Java 25 — set `JAVA_HOME` to a JDK 25 install, e.g. Zulu 25). All tests hit a real PostgreSQL database, not mocks. Ensure your `.env` points to a reachable DB before running.

> **Windows PowerShell — importante**: el `mvn` del sistema en Windows suele apuntar a JDK 21 por default. Antes de correr cualquier comando Maven, exporta `JAVA_HOME` a Zulu 25:
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Zulu\zulu-25"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> mvn -v   # debe mostrar "Java version: 25"
> ```
> Sin este ajuste el build falla con `error: release version 25 not supported`.

**Test flavors** (all extend `BaseIntegrationTest` + `DotEnvInitializer`, which loads `.env` and cleans the DB in FK-safe order in `@BeforeEach`):
- `unit/*Test.java` — Mockito unit tests (no Spring context). **The only ones run by default with `mvn test`.**
- `integration/*IntegrationTest.java` — endpoint-level via `WebTestClient` against the real local Postgres. **Excluded by default; run with the `fulltest` profile.**
- `integration/repository/*RepositoryIT.java` — R2DBC repository integration against the real local Postgres (happy-path only). **Excluded by default; run with the `fulltest` profile.**

The `fulltest` profile (surefire include of `*IntegrationTest.java` + `*IT.java`) mirrors the monorepo-wide convention (2026-07-16): `mvn test` never touches the DB; anything that needs the real Postgres from `.env` requires `-P fulltest`. No Testcontainers.

Copy `.env.example` to `.env` and configure before running. Required vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.

## Architecture

Clean (hexagonal) architecture with Spring WebFlux (fully reactive — no blocking I/O):

```
domain/model/           → Domain entities (no framework dependencies)
domain/port/in/         → Use case interfaces (commands as inner Records)
domain/port/out/        → Repository interfaces
application/service/    → Use case implementations + infrastructure services
infrastructure/
  adapter/in/web/       → @RestController classes + DTOs
  adapter/out/persistence/ → R2DBC repositories, entity mappers
  config/               → Security, Cloudinary, R2DBC auditing
```

All controllers return `Mono<T>` / `Flux<T>`. Never mix blocking calls into reactive chains.

## Key Patterns

**Security context** — JWT is parsed by `JwtAuthenticationFilter` (WebFilter) into a `JwtPrincipal` (implements `Principal`). Controllers extract it with:
```java
ReactiveSecurityContextHolder.getContext()
    .map(SecurityContext::getAuthentication)
    .map(Authentication::getPrincipal)
    .cast(JwtPrincipal.class)
```
`JwtPrincipal` convenience methods: `isStaff()`, `isSuperAdmin()`, `isSoporte()`, `isAdminCompania()`, `isPlataforma()`.

**Access control** — Always gate handlers through `AccessControlService`:
- `requirePlataforma(principal)` — any platform operator (super_admin, soporte, viewer)
- `requireSuperAdmin(principal)` — super_admin only
- `requireSuperAdminOrSoporte(principal)` — super_admin or soporte
- `requireStaff(principal)` — gym staff token
- `requireAccessToCompania(principal, idCompania)` — staff must belong to that company

**Entity mapping** — MapStruct mappers (in `adapter/out/persistence/`) convert between domain models and R2DBC entities. Add new fields to both the domain model and the corresponding entity + mapper; never bypass the mapper.

**Use case commands** — Use cases define inner `record` types for commands (e.g., `CompaniaUseCase.ActualizarCompaniaCommand`). Always pass commands, not raw primitives, to use case methods.

**Persistence** — R2DBC (reactive, no JPA). DB schema is `tenant` for company data, `saas` for platform data. All entities extend `BaseAuditEntity` (creacionFecha, creacionUsuario, modificaFecha, modificaUsuario populated automatically via R2DBC auditing from JWT context). **Soft-delete**: all entities have an `eliminado` boolean — filter on `eliminado = false` in all queries.

**Cloudinary** — `CloudinaryService` (in `application/service/`) runs uploads on `Schedulers.boundedElastic()` to avoid blocking the event loop. Logos are stored in `gym-admin/logos/` with `public_id = "compania-{id}"`. Inject `CloudinaryService`, not the raw `Cloudinary` bean.

**File uploads** — Multipart handling in reactive context uses `DataBufferUtils.join(filePart.content())` to collect bytes before calling Cloudinary.

**Error handling** — Contrato de errores estandarizado **RFC 7807 (`ProblemDetail`) + campo `codigo`** — ver [../docs/gym-administrator/architecture/error-contract.md](../docs/gym-administrator/architecture/error-contract.md). Replicado del piloto de `core-service` (2026-07-19).

`GlobalExceptionHandler` (implements `ErrorWebExceptionHandler`, `@Order(HIGHEST_PRECEDENCE)`) es el punto único de salida de errores. Traduce todas las excepciones al mismo sobre; lanza el tipo correcto desde service/adapter (no devuelvas `Mono`s con `RuntimeException` genérico):

| Exception | HTTP | `codigo` |
|---|---|---|
| `NotFoundException` | 404 | `recurso_no_encontrado` |
| `ConflictException` | 409 | `conflicto` (+ `conflicto` extra si `getConflicto() != null`) |
| `ForbiddenException` / `AccessDeniedException` | 403 | `acceso_denegado` |
| `BusinessException` | 422 | `regla_negocio` |
| `LimiteAlcanzadoException` | 403 | `limite_plan_alcanzado` (+ `recurso`, `actual`, `maximo`, `plan_actual`) |
| `TrialYaUsadoException` | 409 | `trial_ya_usado` |
| `SuscripcionActivaException` | 409 | `suscripcion_activa` |
| `SinSuscripcionCancelableException` | 400 | `sin_suscripcion_cancelable` |
| `PagoDuplicadoException` | 409 | `pago_duplicado` |
| `PagoYaProcesadoException` | 409 | `pago_ya_procesado` |
| `EstadoInvalidoException` | 400 | `transicion_invalida` |
| `RateLimitExcedidoException` | 429 | `rate_limit_excedido` (+ `ventana`, `max`) |
| `DataIntegrityViolationException` | 409 | `datos_duplicados` / `referencia_invalida` / `campo_requerido` (vía `DataIntegrityMapper`) |
| `WebExchangeBindException` | 400 | `validacion` (+ `errores: [{campo, mensaje}]`) |
| no controlada | 500 | `error_interno` |

**Sobre de salida** (`application/problem+json`): 5 campos RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) + extensiones en **snake_case** (`codigo`, `mensaje` [alias de `detail`], `timestamp`, y `errores`/metadata según el caso). La metadata SaaS que consume el frontend (`UpgradeModal`) va al nivel raíz — nota: `plan_actual` es **snake_case** (antes salía como `planActual`).

**Piezas del paquete** (bajo `infrastructure/exception/` y `infrastructure/config/`):
- `ErrorCode` (enum), `ProblemDetailFactory` (construye y **aplana** el `ProblemDetail` vía `toMap()`), `DataIntegrityMapper`.
- `ApiAuthenticationEntryPoint` (401 → `no_autenticado`) + `ApiAccessDeniedHandler` (403 → `acceso_denegado`), registrados en `SecurityConfig.exceptionHandling(...)`. El `JwtAuthenticationFilter` delega su 401 al entrypoint. Las reglas `permitAll` públicas se mantienen intactas.

Tests: `GlobalExceptionHandlerTest` (mapeo + serialización snake_case, incl. `plan_actual`), `SecurityErrorContractTest` (401/403 de Security).

**Module check cache** — Redis was removed for Cloud Run deployment without external dependencies (see [docs/REDIS_REMOVAL.md](../docs/REDIS_REMOVAL.md) if it exists, and the stub at `infrastructure/adapter/out/cache/RedisModuloCheckCache.java`). The `ModuloCheckCache` port is currently implemented as a no-op stub: `get` returns `Mono.empty()`, `put`/`evict` do nothing, and `invalidateByCompania` returns `0`. Consequence: every call to `/api/v1/modulos/check` hits Postgres directly (small join over `saas.plan_caracteristicas × saas.caracteristicas`). Consumers that need caching (e.g. billing-service's `ModuloGatingFilter`) implement their own in-JVM cache (Caffeine). Redis is **not required** to start the service.

**Scheduled jobs** — Platform-service tiene 4 jobs cron-based e idempotentes (todos con startup hook para recuperar ventana perdida) + 2 fixed-delay para procesar colas:

1. `SubscriptionJobService.runSubscriptionJob()` (cron `0 5 0 * * *`, 00:05 UTC) — activa suscripciones PROGRAMADAS, degrada VENCIDAS, procesa notificaciones
2. `NotificacionVencimientoJob.ejecutar()` (cron `0 15 3 * * *`, 03:15 UTC) — encola notificaciones de vencimiento del dueño (buckets {previo, 0})
3. `WhatsAppQueueProcessorJob.procesarLote()` (fixed-delay 30s) — procesa cola de mensajes WhatsApp pendientes
4. `EmailQueueProcessorJob.procesarLote()` (fixed-delay 30s) — procesa cola de emails pendientes con retry exponencial

Ver doc centralizado: [`../docs/gym-administrator/architecture/scheduled-jobs.md`](../docs/gym-administrator/architecture/scheduled-jobs.md).

**QR tokens** — 32-char random tokens (configurable via `QR_TOKEN_LENGTH`) issued per sucursal for mobile app entry validation. Endpoint `/api/v1/modulos/check` is public (no auth).

## Domain Entities

`Compania` (tenant company), `Sucursal` (branch), `Plan` (subscription template), `CompaniaPlan` (active subscription linking Compania+Plan), `PagoSuscripcion` (payment record), `Caracteristica` (plan feature), `PlanCaracteristica` (join), `ConfigNotifSuscripcion` (renewal notification config), `ActividadPlataforma` (audit log), `NotificacionSuscripcion` (sent notification record), `ModuloCheckResult` (value object, not persisted).

Key enums: `CompaniaPlan.Estado` (ACTIVO, EN_GRACIA, VENCIDO, SUSPENDIDO, CANCELADO, PROGRAMADO), `CompaniaPlan.TipoCambio` (NUEVO, RENOVACION, UPGRADE, DOWNGRADE), `PagoSuscripcion.MetodoPago` (EFECTIVO, TRANSFERENCIA, TARJETA), `PagoSuscripcion.EstadoPago` (PAGADO, FALLIDO, PENDIENTE).

## Endpoints

Port **8081**. All endpoints under `/api/v1/`. Public: `/api/v1/modulos/check`, `/actuator/health`. All others require `Authorization: Bearer <jwt>`.

Key route groups:
- `CompaniaController` → `/api/v1/companias` — CRUD + wizard registration (`POST /wizard`) + auto-registro + logo upload (`POST /{id}/logo`) + suspend (`PUT /{id}/suspender`). El listado (`GET /api/v1/companias`) enriquece cada compañía con su suscripción vigente: `planActivo: { nombre, estado (MAYÚSCULA, igual que `SuscripcionResponse` — vía `Estado.name()`), fechaFin, diasRestantes }` o `null` si no hay suscripción activa. El use case `listarCompanias` devuelve `Flux<CompaniaConPlan>` (VO de dominio que agrupa `Compania` + plan activo); el enriquecimiento vive en `CompaniaService` (por compañía: `findActivoByIdCompania` + `PlanRepository.findById` para el nombre). Los endpoints de una sola compañía (`GET/PUT /{id}`, logo) devuelven `planActivo: null` (sin contexto de plan).
- `MiEmpresaController` → `/api/v1/mi-empresa` — staff self-service (logo upload, sucursal, QR renewal)
- `SuscripcionController` → `/api/v1/companias/{id}/suscripcion` — get active, historial, renovar, upgrade, downgrade
- `PagoController` → `/api/v1/companias/{id}/pagos` + `POST /api/v1/pagos` + `PUT /api/v1/pagos/{id}/confirmar`
- `SucursalController` → `/api/v1/companias/{idCompania}/sucursales` (list/create) + `/api/v1/sucursales/{id}` (update/qr)
- `PlanController` → `/api/v1/planes` — CRUD + assign characteristics (`PUT /{id}/caracteristicas`) + deactivate
- `CaracteristicaController` → `/api/v1/caracteristicas` — list + create
- `NotifConfigController` → `/api/v1/companias/{id}/notif-config`
- `ActividadPlataformaController` → `/api/v1/actividad` — list audit log
- `ModuloCheckController` → `/api/v1/modulos/check` — public QR-based module access check
- `BannerController` → `/api/v1/companias/{id}/banners-activos` (GET) + `/api/v1/companias/{id}/banners/{idBanner}/descartar` (POST) — REQ-SAAS-001 Sub-fase 1.5, banners in-app de vencimiento (owner/admin del tenant)
- `PagosPendientesOwnerController` → `/api/v1/companias/{idCompania}/pagos-pendientes` (GET) — REQ-SAAS-001 Sub-fase 1.6, listado de pagos reportados por el propio tenant (todos los estados, ordenados por `fecha_reporte DESC`); alimenta el banner "pago en revisión" / "pago rechazado: {motivo}" de la página *Mi suscripción*. Desde item #4 la respuesta incluye `nombreCompania`.
- `PagoOwnerController` → `POST /api/v1/companias/{id}/pagos/reportar` — reporte multipart; desde Sub-fase 1.6 item #4 la parte `comprobante` pasa a ser **opcional** (sin archivo, `comprobanteUrl` queda `null`; no se sube a Cloudinary). Rate limit: 3/hora/tenant. Respuesta incluye `nombreCompania`.
- `PagoPlataformaController` → `/api/v1/plataforma/pagos-pendientes` (GET bandeja + POST `/{id}/aprobar` + POST `/{id}/rechazar`) — bandeja root/soporte; desde Sub-fase 1.6 item #4 la bandeja enriquece cada pago con `nombreCompania` via batch fetch (`CompaniaRepository.findAllByIds`).
- `NotifBucketsController` → `/api/v1/plataforma/notif-buckets` (GET lista + PUT /{destinatario} actualiza) — super_admin only, configuración global de días de aviso previo (feature WhatsApp de vencimiento, Fase 6).
- `ConsentimientoWaController` → `/api/v1/companias/{id}/consentimiento-wa` (PATCH) — opt-in del dueño para WhatsApp (gate `requireAccessToCompania`, Fase 6).
- `InternalNotifBucketsController` → `/internal/v1/notif-buckets/{destinatario}` (GET, header `X-Internal-Call`) — endpoint interno (sin JWT) que consume attendance-service para leer el bucket del socio (Fase 6).

## Monorepo Context

This service is one folder in the `gym-administrator` monorepo (`C:\Respos\own-aplications\`). See [../INDEX.md](../INDEX.md) for the full service map.

| Service | Port | Role |
|---|---|---|
| `auth-service` | 8080 | JWT auth, users, roles, personas |
| **platform-service** | **8081** | **This service — SaaS platform management** |
| `core-service` | 8083 | Gym operations (clients, memberships) |
| `attendance-service` | 8084 | Attendance tracking |
| `auth-service-frond-end` | 5173 | React frontend |

All services share the same `JWT_SECRET` and the same PostgreSQL instance (`gym-app-saas` DB). The `identidad` schema (personas, usuarios) is owned by auth-service. Platform-service owns schemas `tenant` (companias, sucursales) and `saas` (planes, pagos, etc.). DB migrations live in `gym-administrator/db/` (Liquibase) within this same monorepo.
