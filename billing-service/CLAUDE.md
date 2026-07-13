# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

API endpoint documentation lives in [../docs/billing-service/](../docs/billing-service/INDEX.md).

**When you add or change an endpoint, state transition, or business rule (e.g., retry backoff delays, SRI WSDL URLs, invoice states), update the matching file in `../docs/billing-service/api/` and/or `../docs/billing-service/flows/` (and README.md/this file if the change affects architecture or conventions) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Para este servicio, los siguientes docs están verificados contra el código (2026-07-11):

| Área | Documento | Estado |
|------|-----------|--------|
| API — comprobantes (CRUD + emisión + anulación) | [../docs/billing-service/api/comprobantes.md](../docs/billing-service/api/comprobantes.md) | ✅ Refleja el código (verificado contra `ComprobanteController`) |
| API — notas de crédito (G4, tipo 04) | [../docs/billing-service/api/notas-credito.md](../docs/billing-service/api/notas-credito.md) | ✅ Refleja el código (verificado contra `NotaCreditoController`) |
| API — administración (ping SRI, certificados, auditoría) | [../docs/billing-service/api/admin.md](../docs/billing-service/api/admin.md) | ✅ Refleja el código (verificado contra `AdminController`) |
| API — reportes (ATS mensual, resumen de ventas) | [../docs/billing-service/api/reportes.md](../docs/billing-service/api/reportes.md) | ✅ Refleja el código (verificado contra `ReporteController`) |
| Flujo de SRI — firma, envío y reintentos | [../docs/billing-service/flows/sri-submission-retry.md](../docs/billing-service/flows/sri-submission-retry.md) | ✅ Refleja el código (verificado contra `RetrySchedulerService`, `EnvioSriService`) |
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

## Invoice State Machine

See [../docs/billing-service/flows/sri-submission-retry.md](../docs/billing-service/flows/sri-submission-retry.md) for complete state diagram and retry logic.

**Terminal states:**
- `AUTORIZADO` — Accepted by SRI; invoice is valid and can be delivered to customer
- `ANULADO` — Manually canceled

**Transient states with automatic retry:**
- `DEVUELTO` — SRI rejected at RECEPCION step; scheduled for resubmission
- `NO_AUTORIZADO` — SRI rejected at AUTORIZACION query step; scheduled for resubmission
- `ERROR` — Exception during submission; scheduled for resubmission

Backoff delays: **1, 5, 15, 60, 240 minutes**. Max retries: **5**. After max retries, invoice enters `ERROR` state and row in `cola_envio` is marked `FALLIDO_DEFINITIVO` (requires manual intervention).

## Error Handling

`GlobalExceptionHandler` (implements `ErrorWebExceptionHandler`) maps custom exceptions to HTTP status codes. Throw the right type from service/adapter layers:

| Exception | HTTP | Meaning |
|---|---|---|
| `NotFoundException` | 404 | Invoice, certificate, or config not found |
| `ConflictException` | 409 | Duplicate invoice number, certificate already active, etc. |
| `ForbiddenException` | 403 | User lacks permission or company mismatch |
| `BusinessException` | 422 | SRI error, validation error, state transition invalid |

Validation errors (`WebExchangeBindException`) → 400. All error responses include `timestamp`, `status`, `error`, `message`, `path`.

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

