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
mvn test                                       # Unit + endpoint integration tests — EXCLUDES *IT.java
mvn test -P fulltest                           # Everything incl. repository IT (*RepositoryIT.java)
mvn test -P fulltest -Dtest='*RepositoryIT'    # Only the repository IT tests
mvn test -Dtest=CompaniaIntegrationTest        # Run a single integration test class
```

Java 25 (the JVM that runs the tests must be Java 25 — set `JAVA_HOME` to a JDK 25 install, e.g. Zulu 25). All tests hit a real PostgreSQL database, not mocks. Ensure your `.env` points to a reachable DB before running.

**Test flavors** (all extend `BaseIntegrationTest` + `DotEnvInitializer`, which loads `.env` and cleans the DB in FK-safe order in `@BeforeEach`):
- `unit/*Test.java` — Mockito unit tests (no Spring context).
- `integration/*IntegrationTest.java` — endpoint-level via `WebTestClient`, run by default with `mvn test`.
- `integration/repository/*RepositoryIT.java` — R2DBC repository integration against the real local Postgres (happy-path only). **Excluded by default; run with the `fulltest` profile.**

The `fulltest` profile (surefire include of `*IT.java`) mirrors attendance-service — same convention: no Testcontainers, the IT tests run against the local Postgres configured in `.env`.

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
  config/               → Security, Redis, Cloudinary, R2DBC auditing
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

**Error handling** — `GlobalExceptionHandler` maps domain exceptions (`NotFoundException`, `ConflictException`, `ForbiddenException`, etc.) to HTTP responses. Throw these from services; do not return error `Mono`s with generic `RuntimeException`.

**Redis caching** — Module check results are cached in Redis (TTL: `MODULE_CHECK_CACHE_TTL_SECONDS`, default 300s). Redis is required at startup.

**Scheduled job** — `SubscriptionJobService` runs daily at 00:05 (`SUBSCRIPTION_JOB_CRON`) to check and update subscription states. Controlled by `@EnableScheduling`.

**QR tokens** — 32-char random tokens (configurable via `QR_TOKEN_LENGTH`) issued per sucursal for mobile app entry validation. Endpoint `/api/v1/modulos/check` is public (no auth).

## Domain Entities

`Compania` (tenant company), `Sucursal` (branch), `Plan` (subscription template), `CompaniaPlan` (active subscription linking Compania+Plan), `PagoSuscripcion` (payment record), `Caracteristica` (plan feature), `PlanCaracteristica` (join), `ConfigNotifSuscripcion` (renewal notification config), `ActividadPlataforma` (audit log), `NotificacionSuscripcion` (sent notification record), `ModuloCheckResult` (value object, not persisted).

Key enums: `CompaniaPlan.Estado` (ACTIVO, EN_GRACIA, VENCIDO, SUSPENDIDO, CANCELADO, PROGRAMADO), `CompaniaPlan.TipoCambio` (NUEVO, RENOVACION, UPGRADE, DOWNGRADE), `PagoSuscripcion.MetodoPago` (EFECTIVO, TRANSFERENCIA, TARJETA), `PagoSuscripcion.EstadoPago` (PAGADO, FALLIDO, PENDIENTE).

## Endpoints

Port **8081**. All endpoints under `/api/v1/`. Public: `/api/v1/modulos/check`, `/actuator/health`. All others require `Authorization: Bearer <jwt>`.

Key route groups:
- `CompaniaController` → `/api/v1/companias` — CRUD + wizard registration (`POST /wizard`) + auto-registro + logo upload (`POST /{id}/logo`) + suspend (`PUT /{id}/suspender`)
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
