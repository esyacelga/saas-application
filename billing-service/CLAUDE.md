# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

API endpoint documentation lives in [../docs/billing-service/](../docs/billing-service/INDEX.md).

**When you add or change an endpoint, state transition, or business rule (e.g., retry backoff delays, SRI WSDL URLs, invoice states), update the matching file in `../docs/billing-service/api/` and/or `../docs/billing-service/flows/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-11):

| Área | Documento | Estado |
|------|-----------|--------|
| API — comprobantes (CRUD + emisión + solicitud de anulación) | [../docs/billing-service/api/comprobantes.md](../docs/billing-service/api/comprobantes.md) | ✅ Refleja el código (verificado contra `ComprobanteController`) |
| API — notas de crédito (G4, tipo 04) | [../docs/billing-service/api/notas-credito.md](../docs/billing-service/api/notas-credito.md) | ✅ Refleja el código (verificado contra `NotaCreditoController`) |
| API — anulaciones fiscales (G3, máquina de estados) | [../docs/billing-service/api/anulaciones.md](../docs/billing-service/api/anulaciones.md) | ✅ Refleja el código (verificado contra `AnulacionController`, `MotivosAnulacionController`) |
| API — administración (ping SRI, certificados, auditoría) | [../docs/billing-service/api/admin.md](../docs/billing-service/api/admin.md) | ✅ Refleja el código (verificado contra `AdminController`) |
| API — reportes (ATS mensual, resumen de ventas) | [../docs/billing-service/api/reportes.md](../docs/billing-service/api/reportes.md) | ✅ Refleja el código (verificado contra `ReporteController`) |
| Flujo de SRI — firma, envío y reintentos | [../docs/billing-service/flows/sri-submission-retry.md](../docs/billing-service/flows/sri-submission-retry.md) | ✅ Refleja el código (verificado contra `RetrySchedulerService`, `EnvioSriService`) |
| Flujo de anulación fiscal + NC (G3) | [../docs/billing-service/flows/anulacion-nc.md](../docs/billing-service/flows/anulacion-nc.md) | ✅ Refleja el código (verificado contra `AnulacionService`) |
| Especificación de dominio | [../docs/gym-administrator/specs/billing-service.md](../docs/gym-administrator/specs/billing-service.md) | 🟡 Índice — ligeramente desalineado con cambios recientes de estado |

Fuente de verdad del enrutamiento: los `@RestController` bajo `infrastructure/adapter/in/web/` (`ComprobanteController`, `AdminController`, `ReporteController`).

## Commands

```bash
# Build
mvn clean package

# Run (requires PostgreSQL)
mvn spring-boot:run

# Run all tests (unit only; integration tests are excluded by default)
mvn test

# Run integration tests
mvn test -P integration-tests

# Run all tests (unit + integration)
mvn test -P fulltest

# Run a single test class
mvn test -Dtest=EmitirFacturaIT

# Run a single test method
mvn test -Dtest=EmitirFacturaIT#methodName
```

## Architecture

This is a **Spring Boot WebFlux microservice** (reactive/non-blocking) for electronic invoice issuance and SRI submission in Ecuador. Runs on port **8086**.

The project follows **Hexagonal Architecture** with three layers:

- **`domain/`** — Pure business logic: domain models (`model/`), port interfaces (`port/in/` for use cases, `port/out/` for repository contracts).
- **`application/service/`** — Use case implementations that depend only on domain ports. Contains `ComprobanteService`, `EnvioSriService`, `RetrySchedulerService`, etc.
- **`infrastructure/`** — All framework/external concerns:
  - `adapter/in/web/` — WebFlux REST controllers + DTOs (`ComprobanteController`, `AdminController`, `ReporteController`)
  - `adapter/out/persistence/` — R2DBC adapters implementing repository ports
  - `config/` — Security, JWT filter, CORS, R2DBC, SRI WSDL/environment config, global exception handler
  - `exception/` — Custom exceptions mapped to HTTP status codes

**Reactive paradigm**: All service and repository methods return `Mono<T>` or `Flux<T>`. Never use blocking calls.

**Asynchronous invoice flow**: After `POST /api/v1/comprobantes/facturas`, the invoice enters an async 3-step process: sign XML → submit to SRI RECEPCION → query AUTORIZACION. Failures are queued in `facturacion.cola_envio` with exponential backoff (1, 5, 15, 60, 240 min) and automatically retried by `RetrySchedulerService` every 60 seconds. See [../docs/billing-service/flows/sri-submission-retry.md](../docs/billing-service/flows/sri-submission-retry.md).

**Mapping**: MapStruct mappers live alongside the infrastructure adapters. They use `componentModel = "spring"`.

**Authentication**: JWT Bearer tokens. The `JwtAuthenticationFilter` parses claims (userId, userType `staff`/`platform`, roleType, companyId) into a `JwtPrincipal` stored in the `SecurityContext`. Controllers access it via `ReactiveSecurityContextHolder`.

## Key Tech

- Java 25, Spring Boot 3.5.5, Spring WebFlux, Spring Security WebFlux
- Spring Data R2DBC + PostgreSQL (r2dbc-postgresql)
- JJWT 0.12.6 for JWT
- Spring WS for SOAP (SRI submission)
- XAdES-BES (xades4j 2.2.0) + xmlsec 4.0.3 for XML digital signature
- OpenPDF 2.0.3 for PDF generation (RIDE receipts)
- Spring Mail for notifications
- Lombok + MapStruct
- Jackson with snake_case naming strategy

## Configuration

Environment variables (loaded from `.env` in dev):

**Database:**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` — PostgreSQL R2DBC

**Security:**
- `JWT_SECRET` — Base64-encoded HMAC-SHA256 secret (shared with auth-service in dev)

**SRI Electronic Invoicing:**
- `SRI_AMBIENTE` — Environment: `1` (testing celcer.sri.gob.ec) or `2` (production cel.sri.gob.ec); default `1`
- `SRI_WSDL_RECEPCION` — Override WSDL URL for RECEPCION service (optional; uses standard URL based on ambiente)
- `SRI_WSDL_AUTORIZACION` — Override WSDL URL for AUTORIZACION service (optional; uses standard URL based on ambiente)
- `SRI_STORAGE_PATH` — Directory to store signed XML and RIDE PDFs; default `./storage/sri`

**Certificate Encryption:**
- `CERT_ENCRYPTION_KEY` — Base64-encoded 32-byte AES-256-GCM key for encrypting/decrypting P12 certificate passphrases in database

**Email (optional):**
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS` — Gmail or custom SMTP server for sending RIDE PDFs to customers
- `EMAIL_FROM` — Sender address; default `noreply@gymadmin.com`
- `EMAIL_ENABLED` — Enable/disable email notifications; default `false`

**CORS:**
- `CORS_ALLOWED_ORIGINS` — Comma-separated origin URLs; default `http://localhost:5173`
- `CORS_ALLOW_ALL` — Allow all origins; default `false`

Jackson globally uses `snake_case` for all JSON field names.

## Database Schemas

Persistence adapters write to the `facturacion` schema:

- `facturacion.comprobantes` — Invoice records with state (`GENERADO`, `FIRMADO`, `RECIBIDO`, `AUTORIZADO`, `DEVUELTO`, `NO_AUTORIZADO`, `ERROR`, `ANULADO`)
- `facturacion.comprobante_detalles` — Line items (qty, unit price, discounts, taxes)
- `facturacion.cola_envio` — Async submission queue with retry state, backoff tracking, and error logs
- `facturacion.config_sri` — SRI environment config per company (active certificate, establishment code, issuance point)
- `facturacion.certificados_info` — P12 certificate metadata (subject, expiry, serial, encrypted passphrase)

All entities extend `BaseAuditEntity` (creacion_fecha/usuario, modifica_fecha/usuario). Deletions are **soft-deletes** via `eliminado` boolean; all queries must filter `WHERE eliminado = false`.

See DDL under `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/` for exact schema definitions.

## Authorization

No centralized `AccessControlService` — controllers validate permissions inline via JWT claims:

- `tipo` (`staff` or `platform`) — User type from token
- `rol_plataforma` — Role name for staff users (e.g., `admin_compania`, `super_admin`)
- `id_compania` — Company ID
- `id_persona` — User ID
- `permisos` — List of permission strings (not currently used in billing-service but passed through)

Controllers extract claims via `ReactiveSecurityContextHolder` and check `tipo == "staff"` and `id_compania` matches the invoice's company. Platform tokens are rejected for most endpoints.

## Scheduled Jobs

Billing-service tiene 2 scheduled jobs:

1. **`RetrySchedulerService.procesarPendientes()`** (fixed-delay 60s) — Procesa reintentos de envío de comprobantes al SRI. Idempotente: ✅ Sí. Backoff automático: {1, 5, 15, 60, 240} minutos, máx 5 intentos. Detalles: [`../docs/billing-service/flows/sri-submission-retry.md`](../docs/billing-service/flows/sri-submission-retry.md)

2. **`CertificadoAlertaService.verificarVencimientos()`** (cron `0 0 8 * * *`, 08:00 UTC) — Alerta si certificados digitales P12 vencen en ≤30 días. **EXCEPCIÓN:** NO es idempotente (sin `existsEnviadoHoy`), por lo tanto **sin startup hook** (evita spam de emails al reiniciar). Env var: **HARDCODED** (sin override).

Ver doc centralizado de todos los 8 jobs del monorepo: [`../docs/gym-administrator/architecture/scheduled-jobs.md`](../docs/gym-administrator/architecture/scheduled-jobs.md).

## Module gating (billing feature-flag por compañía)

Además de la autenticación JWT y del check de rol/compañía en cada controller, un `WebFilter` global (`ModuloGatingFilter`, registrado en `SecurityConfig` con `addFilterAfter(..., SecurityWebFiltersOrder.AUTHENTICATION)`) consulta a **platform-service** si la compañía tiene el módulo `FACTURACION` incluido en su plan actual.

- **Endpoint consumido:** `GET {platform.base-url}/api/v1/modulos/check?id_compania={id}&codigo=FACTURACION`. Es público en platform-service (no envía JWT). El código consultado es una constante (`FACTURACION`, mayúsculas exactas) definida en `billing.gating.modulo-codigo`.
- **Paths protegidos** (prefijos): `/api/v1/comprobantes`, `/api/v1/notas-credito`, `/api/v1/anulaciones`, `/api/v1/reportes`. Todo lo demás (actuator, swagger, `/api/v1/sri/motivos-anulacion`) pasa transparente.
- **Bypass:** principales con `tipo=plataforma` o `rol_plataforma=super_admin` pasan sin consultar (soporte/administración interna). Si el path está fuera de scope o `billing.gating.enabled=false`, tampoco se llama a platform-service.
- **Cache local:** Caffeine, `id_compania → Boolean`, TTL **600 s (10 min) por defecto** (configurable vía `BILLING_GATING_CACHE_TTL_SECONDS`), max 10 000 entradas. Se cachea el resultado 200 (true) y 403 (false). El 402 y los errores de red **no** se cachean. Cambios de plan en Platform tardan hasta el TTL en propagarse por pod (no hay invalidación explícita).
- **Comportamiento por status HTTP de platform-service:**
  | Status platform | Respuesta del filter | Body |
  |---|---|---|
  | 200 | pasa la cadena | — |
  | 403 | 403 al cliente | `{"permitido":false,"razon":"modulo_no_incluido"}` |
  | 402 | 402 al cliente | `{"permitido":false,"razon":"plan_vencido_o_suspendido"}` |
  | otros / timeout / error de red | **503 (fail-closed)** | `{"permitido":false,"razon":"gate_unavailable"}` |
  | staff sin `id_compania` en el JWT | 403 | `{"permitido":false,"razon":"id_compania_missing"}` |
- **Feature flag:** `BILLING_GATING_ENABLED=false` desactiva el filter (útil en tests de integración locales y rollout gradual).
- **Config relacionada:** `PLATFORM_SERVICE_URL` (default `http://localhost:8081`), `billing.gating.webclient.connect-timeout-ms` (2 s), `read-timeout-ms` (3 s).

## Invoice State Machine

See [../docs/billing-service/flows/sri-submission-retry.md](../docs/billing-service/flows/sri-submission-retry.md) for complete state diagram and retry logic.

**Terminal states:**
- `AUTORIZADO` — Accepted by SRI; invoice is valid and can be delivered to customer
- `ANULADO` — Canceled via G3 anulación fiscal (either after `POST /anulaciones/{id}/confirmar-sri` for Flujo A or after the NC in Flujo B reaches AUTORIZADO)

**Transient states with automatic retry:**
- `DEVUELTO` — SRI rejected at RECEPCION step; scheduled for resubmission
- `NO_AUTORIZADO` — SRI rejected at AUTORIZACION query step; scheduled for resubmission
- `ERROR` — Exception during submission; scheduled for resubmission

Backoff delays: **1, 5, 15, 60, 240 minutes**. Max retries: **5**. After max retries, invoice enters `ERROR` state and row in `cola_envio` is marked `FALLIDO_DEFINITIVO` (requires manual intervention).

## Anulación fiscal SRI (G3) — máquina de estados

Ver [../docs/billing-service/flows/anulacion-nc.md](../docs/billing-service/flows/anulacion-nc.md) para diagramas y detalles.

Tabla: `facturacion.anulaciones` — enum `EstadoAnulacion`:

```
SOLICITADA ─aprobar─→ APROBADA ─confirmar-sri (Flujo A)──→ EJECUTADA
    │                    │
    │                    └─NC AUTORIZADO (Flujo B)────────→ EJECUTADA
    │
    └─rechazar─→ RECHAZADA
```

**Endpoints (`AnulacionController` + `MotivosAnulacionController`):**
- `POST /api/v1/comprobantes/{id}/anular` — con body `{motivo, codigo_motivo_anulacion?, generar_nota_credito?}`, crea `SOLICITADA`. Reemplaza el viejo endpoint sin body.
- `POST /api/v1/anulaciones/{id}/aprobar` — rol `admin_compania`/`super_admin`/`Dueño`.
- `POST /api/v1/anulaciones/{id}/rechazar` — mismo rol; observación obligatoria.
- `POST /api/v1/anulaciones/{id}/confirmar-sri` — mismo rol; solo Flujo A.
- `GET /api/v1/comprobantes/{id}/anulaciones` — historial.
- `GET /api/v1/anulaciones` — listado paginado con filtros.
- `GET /api/v1/sri/motivos-anulacion` — catálogo `sri.motivos_anulacion_nc`.

**Validaciones fiscales al solicitar:** motivo obligatorio, estado ∈ {AUTORIZADO, GENERADO}, `id_receptor != '9999999999999'` (consumidor final), ventana `hoy ≤ día 7 mes+1` (`Clock` inyectable en `America/Guayaquil`).

**Persistencia del flag Flujo B:** el DDL no expone columna `generar_nota_credito`; se codifica dentro de `observacion_resolucion` con el prefijo interno `[FLUJO_B][MOTIVO=<codigo>]` y se strippea al leer. Ver `AnulacionService.combineMetadata` / `stripFlagObservacion`.

## Notas de crédito electrónicas (G4)

Ver [../docs/billing-service/api/notas-credito.md](../docs/billing-service/api/notas-credito.md).

Endpoint principal: `POST /api/v1/notas-credito` con `EmitirNotaCreditoRequest`. `NotaCreditoService` valida la factura original (tipo `01`, estado `AUTORIZADO`, misma compañía, `valorModificacion ≤ total`), reserva secuencial atómico tipo `04`, persiste referencia en `facturacion.notas_credito_referencias`, y dispara el pipeline síncrono G2 (`EnvioSriService.procesarEmisionInmediataConXml`). El flujo B de G3 llama internamente a `NotaCreditoUseCase.emitirNotaCredito` copiando detalles y total de la factura original.

## Bancarización sobre USD 500 (G10)

Si el total de una factura supera **USD 500**, el SRI exige que el **excedente** sobre ese umbral se pague con una forma de pago que use el sistema financiero. `ComprobanteService.validarBancarizacion()` lo valida y lanza `BusinessException` (422) si no se cumple.

Se valida el **excedente**, no el total: una factura de USD 600 puede pagar 100 en efectivo mientras al menos 500 vayan por medio bancarizado. El flag vive en `sri.formas_pago.bancarizada` (migración `202607_GYM-002`) y se consulta vía `CatalogoSriService.esBancarizada()` (cacheado).

**Códigos bancarizados: 16, 17, 18, 19, 20.** Ojo — el catálogo real es `16=tarjeta débito, 17=dinero electrónico, 18=tarjeta prepago, 19=tarjeta crédito, 20=otros con utilización del sistema financiero`; el código `01` es literalmente `SIN_UTILIZACION_SISTEMA_FINANCIERO`. (El gap-analysis §G10 los lista mal.)

## ATS mensual (G9)

`AtsXmlBuilder` genera el XML del Anexo Transaccional Simplificado siguiendo el [XSD oficial del SRI](https://descargas.sri.gob.ec/download/anexos/ats/ats.xsd), versionado en `src/test/resources/sri/ats.xsd`. **`AtsXmlBuilderTest` valida el XML generado contra ese XSD** — mantener esa validación al tocar el builder; la implementación anterior emitía campos que no existían en el esquema y nadie lo notó por no tener este test.

Puntos del esquema que no son obvios:
- La raíz es **`<iva>`**, no `<ats>`; `codigoOperativo` es la constante `IVA`.
- `detalleVentas` **agrupa** por (tipo id, identificación, tipo de comprobante). `numeroComprobantes` es un **conteo**, no el número de una factura.
- Las **notas de crédito no tienen nodo propio**: van en `detalleVentas` con `tipoComprobante = 04` y sus importes **en positivo** (`monedaType` no admite signo negativo). Solo el `totalVentas` global netea (ahí sí resta).
- Las formas de pago van en `formasDePago` → N `formaPago` (leídas de `facturacion.comprobante_pagos`). **No existe el campo `tipoPago`.**
- Los **anulados** van en el nodo `anulados`, fuera de las ventas.

## Error Handling

Contrato de errores estandarizado **RFC 7807 (`ProblemDetail`) + campo `codigo`** — ver [../docs/gym-administrator/architecture/error-contract.md](../docs/gym-administrator/architecture/error-contract.md). Replicado del piloto de core-service.

`GlobalExceptionHandler` (implements `ErrorWebExceptionHandler`, `@Order(HIGHEST_PRECEDENCE)`) es el punto único de salida de errores. Traduce las excepciones de dominio al sobre estándar; lanza el tipo correcto desde service/adapter:

| Exception | HTTP | `codigo` |
|---|---|---|
| `NotFoundException` | 404 | `recurso_no_encontrado` |
| `ConflictException` | 409 | `conflicto` |
| `ForbiddenException` / `AccessDeniedException` | 403 | `acceso_denegado` |
| `BusinessException` | 422 | `regla_negocio` |
| `DataIntegrityViolationException` | 409 | `datos_duplicados` / `referencia_invalida` / `campo_requerido` (vía `DataIntegrityMapper`) |
| `WebExchangeBindException` | 400 | `validacion` (+ `errores: [{campo, mensaje}]`) |
| `ResponseStatusException` | por status | switch sobre el status HTTP |
| no controlada | 500 | `error_interno` |

**Sobre de salida** (`application/problem+json`): 5 campos RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) + extensiones en **snake_case** (`codigo`, `mensaje` [alias de `detail`], `timestamp`, y `errores` en validación).

**Piezas del paquete** (bajo `infrastructure/exception/` y `infrastructure/config/`):
- `ErrorCode` (enum) — catálogo `codigo` → HTTP status.
- `ProblemDetailFactory` — construye y **aplana** el `ProblemDetail` (un `ObjectMapper` plano anidaría las extensiones bajo `properties`; `toMap()` las lleva al nivel raíz en snake_case).
- `DataIntegrityMapper` — traduce constraints de PostgreSQL a `codigo` + mensaje legible.
- `ApiAuthenticationEntryPoint` (401 → `no_autenticado`) + `ApiAccessDeniedHandler` (403 → `acceso_denegado`) — registrados en `SecurityConfig.exceptionHandling(...)` para que los errores de Spring Security emitan el mismo sobre. El `JwtAuthenticationFilter` delega su 401 al entrypoint (no un 401 vacío).

Tests: `GlobalExceptionHandlerTest` (mapeo + serialización snake_case), `SecurityErrorContractTest` (401/403 de Security).

## Ecuador-Specific Invoice Details

- **ClaveAcceso** — 49-digit unique access key generated per invoice. Format: `ddmmyyyyxxxxxxxxxxxxxxxxxxxx{codigo_numerico}{estado}` where codigo_numerico is 8 digits and estado is 1 digit.
- **Certificado P12** — Digital signature certificate stored per company. Passphrase encrypted with `CERT_ENCRYPTION_KEY` in database.
- **Firma XML** — Invoice XML is signed locally using XAdES-BES before submission to SRI (no external signing service).
- **WSDL Endpoints:**
  - Testing: `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline` (RECEPCION), `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline` (AUTORIZACION)
  - Production: `https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline` (RECEPCION), `https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline` (AUTORIZACION)
- **RIDE** — Customer-facing PDF receipt (Comprobante de Aceptación de la Administración). Generated from invoice data; can be emailed or downloaded.

## Testing

Integration tests use `WebTestClient` + a real PostgreSQL (configured in `application-integration.yml`). `BaseIntegrationTest` (if exists) or individual test classes set up test data via helpers.

Tests load `.env` via `DotEnvInitializer` for database connection. **Profiles:** `integration` activates integration test config.

**Test maven profiles:**
- `mvn test -P integration-tests` — Run only integration tests (`*IT.java`)
- `mvn test -P fulltest` — Run all tests (unit + integration)

**Test constants** (verify in actual test files):
- Typically use a high, reserved company ID (e.g., `99999`) to avoid collisions with production data seeding

**Key test classes:**
- `EmitirFacturaIT` — Tests invoice emission, SRI submission, and retry flow
- `ComprobanteIntegrationTest` — Tests comprobante CRUD and state transitions
- Logs may show benign ERRORs like "Certificado activo no encontrado para 99999" from test cleanup — expected.

