# REQ-SAAS-001 — Bitácora de implementación (Sub-fases 1.3, 1.4, 1.5)

| Campo | Valor |
|---|---|
| **Requerimiento** | REQ-SAAS-001: Nuevo esquema de planes SaaS (Free / Trial / Premium) |
| **Fases implementadas** | 1.1, 1.2, 1.3, 1.4, 1.5 (Fase 1 ~ 85%) |
| **Commits** | 6bd7f0b (1.1), 3c91d7e (1.2), e4bf796 (1.3+1.4), c1a5b75 (1.5) |
| **Última revisión** | 2026-07-10 |
| **Estado actual** | Implementado en `platform-service` + `core-service` + DTOs y validaciones |

> **Nota sobre numeración de reglas de negocio.** El requerimiento fuente [`planes-saas-freemium.md`](./planes-saas-freemium.md) define **RN-01…RN-10** canónicas. Este documento y `specs/platform-service.md` usan el namespace paralelo **`RN-SAAS-001…012`** para no colisionar con las RN-01..RN-10 preexistentes en el spec del platform-service (que cubren reglas generales de suscripción, no del esquema freemium). La correspondencia RN-SAAS ↔ RN del requerimiento está en la tabla de la sección 11 de [`specs/platform-service.md`](../specs/platform-service.md#nuevas-reglas-req-saas-001-sub-fases-1315).

---

## 1. Decisiones arquitectónicas (D1–D6)

### D1: Extender `tenant.notificaciones_suscripcion` en lugar de crear tabla nueva
- Se reutiliza la tabla existente con nuevas columnas: `proximo_intento`, `id_compania`, `tipo`, `intentos`, `ultimo_error`, `descartado_at`; el `canal` acepta ahora `email|whatsapp|banner` y `estado` acepta `pendiente|enviado|fallido|reintentar`.
- Definido en el CREATE consolidado: `db/scripts/202605_GYM-001/ddl/21_create_table_tenant_notificaciones_suscripcion.sql` (changeset `GYM-001-21`).
- Beneficio: evita fragmentación; una única tabla para auditoría de notificaciones (emails y banners in-app).

### D2: Coexistencia con `tenant.pagos_suscripcion`
- **Nueva tabla `tenant.pagos_pendientes_validacion`**: buzón de pagos reportados por owner antes de aprobación.
- **`pagos_suscripcion`** (legacy): mantiene historial de pagos confirmados (para reportes financieros).
- Razón: separar "pendiente validación" (workflow manual) de "históricamente pagado" (auditoría).

### D3: Migración de `saas.actividad_plataforma`
- Nuevos campos: `id_compania`, `id_usuario_actor`, `tipo_actor` (enum: OWNER, ROOT, STAFF, SISTEMA), `detalle` (JSONB), `ip` (INET).
- Permite auditar qué actor (humano o job) hizo qué acción y desde dónde.
- Compatible con eventos auditables de RN-SAAS-001 (eventos de cambio de plan, pago, notificaciones, etc.).

### D4: Enums en BD (minúsculas), Java (UPPERCASE), mapping manual
- DB guarda: `'ACTIVO'`, `'VENCIDO'`, etc. en minúsculas.
- Java `@Enum`: `ACTIVO`, `VENCIDO` (UPPERCASE).
- Conversor personalizado en adapter R2DBC para el bidireccional.
- Propósito: coherencia SQL + idiomatismo Java.

### D5: EmailAdapter copiado desde auth-service, NO compartido
- Cada servicio mantiene su propia instancia de `EmailAdapter`.
- Razón: independencia de deployments; evita dependencia circular.
- Futuro: centralizar si un `mail-service` compartido existe.

### D6: Sin Redis para cola de emails — Postgres FOR UPDATE SKIP LOCKED
- **No existe** Redis remoto en algunos entornos (dev, staging local).
- **Solución**: cola en `tenant.notificaciones_suscripcion`, procesada con `SELECT ... FOR UPDATE SKIP LOCKED`.
- **Job `EmailQueueProcessorJob`**: cada 30s, batch 50, reintentos con backoff exponencial (30s → 2m → 10m → 1h).
- Cache en proceso vía Caffeine (aún no wireado en tests; está presente en código).

---

## 2. Cambios de base de datos (Sub-fase 1.1)

> **Nota de consolidación (2026-07-10).** Los cambios de Sub-fase 1.1 vivían originalmente como scripts `ALTER` en la story `202608_GYM-003` (changesets `GYM-003-01..11`). Esa story fue **retirada** al consolidar todas las migraciones en la story única `202605_GYM-001/`. Hoy los campos nuevos están directamente en el `CREATE TABLE` de cada tabla afectada (no hay `ALTER`), y las tablas nuevas viven en `ddl-freemium/`. Los IDs de changeset ahora son `GYM-001-XX`.

### Tablas modificadas (definidas ya consolidadas en `ddl/`)

| Tabla | Archivo (baseline) | Changeset | Columnas relevantes |
|-------|--------------------|-----------|---------------------|
| `saas.planes` | `ddl/11_create_table_saas_planes.sql` | `GYM-001-11` | `codigo`, `duracion_dias`, `es_gratuito`, `plan_degradacion_id`, `max_sucursales`, `max_clientes_activos`, `max_staff`, `moneda`, `es_legacy` |
| `tenant.companias` | `ddl/16_create_table_tenant_companias.sql` | `GYM-001-16` | `trial_usado`, `fecha_trial_usado` (+ campos SRI: `nombre_comercial`, `dir_matriz`, `obligado_contabilidad`, `contribuyente_especial`) |
| `tenant.compania_planes` | `ddl/18_create_table_tenant_compania_planes.sql` | `GYM-001-18` | `sobre_limite`, `sobre_limite_hasta`, `causa_degradacion` (+ estados `reemplazada` y transiciones `degradacion_auto`, `cancelacion`, `suspension`) |
| `tenant.notificaciones_suscripcion` | `ddl/21_create_table_tenant_notificaciones_suscripcion.sql` | `GYM-001-21` | `id_compania`, `tipo`, `proximo_intento`, `intentos`, `ultimo_error`, `descartado_at`; `canal` ahora acepta `email|whatsapp|banner`; `estado` incluye `pendiente|reintentar` |
| `saas.actividad_plataforma` | `ddl/67_create_table_saas_actividad_plataforma.sql` | `GYM-001-67` | `id_compania`, `id_usuario_actor`, `tipo_actor`, `ip` (INET); `detalle` es `JSONB` |

### Tablas nuevas (en `ddl-freemium/`)

| Tabla | Archivo | Changeset | Propósito | Índices |
|-------|---------|-----------|-----------|---------|
| `tenant.pagos_pendientes_validacion` | `ddl-freemium/01_create_table_tenant_pagos_pendientes_validacion.sql` | `GYM-001-140` | Buzón de pagos reportados por owner (RN-08) | `ux_pagos_pendientes_hash` (idempotencia parcial), `idx_pagos_pendientes_estado_fecha`, `idx_pagos_pendientes_compania` |
| `saas.config_plataforma` | `ddl-freemium/02_create_table_saas_config_plataforma.sql` | `GYM-001-141` | Configuración runtime (datos bancarios, precios, etc.) | PRIMARY KEY `clave` |

Seed asociado: `ddl-freemium/03_seed_saas_config_plataforma.sql` inserta 7 claves `pago.banco.*` con valores de ejemplo (reemplazar en prod).

### Constraints y garantías

- Idempotencia de reportes: `UNIQUE(hash_idempotencia) WHERE estado IN ('pendiente','aprobado')` en `tenant.pagos_pendientes_validacion` (índice parcial, no viola cuando estados terminales).
- Self-FK de degradación: `saas.planes.plan_degradacion_id → saas.planes(id)`.
- FK: `tenant.pagos_pendientes_validacion.id_plan_destino → saas.planes(id)`.
- FK: `tenant.pagos_pendientes_validacion.aprobado_por → saas.usuarios_plataforma(id)`.
- Trial irrevocable: la app bloquea `INSERT` de suscripción Trial si `tenant.companias.trial_usado = TRUE` (validación en `ActivarTrialService`, no en constraint DB).

---

## 3. Modelo de dominio (Sub-fase 1.2)

### Nuevas entidades Java

| Entidad | Archivo | Propósito |
|---------|---------|-----------|
| `PagoPendienteValidacion` | `domain/model/` | DTO del pago reportado (monto, banco, referencia, comprobante URL) |
| `NotificacionSuscripcion` | `domain/model/` | Record de notificación enviada (tipo, canal, días_antes, estado_envío) |
| `ConfigPlataforma` (opcional) | `domain/model/` | Clave-valor de config SaaS (precios, datos bancarios) |
| `RecursoLimitable` | `domain/model/` | Enum: `SUCURSALES`, `CLIENTES_ACTIVOS`, `STAFF` |
| `ActividadPlataforma` (extendida) | `domain/model/` | Auditoría con campos de actor, IP, JSONB detalle |

### Enums nuevos

```java
// CompaniaPlan.TipoCambio → agregar:
DEGRADACION_AUTO
CANCELACION
SUSPENSION

// CompaniaPlan.Estado → agregar:
REEMPLAZADA  // estado terminal tras activación de PROGRAMADO

// NotificacionSuscripcion:
CANAL_EMAIL = "EMAIL"
CANAL_BANNER = "BANNER"
CANAL_WHATSAPP = "WHATSAPP"

// PagoPendienteValidacion.Estado:
PENDIENTE, APROBADO, RECHAZADO
```

### Máquina de estados `CompaniaPlan.Estado`

Ver sección 5bis del requerimiento. Implementada en `domain/model/CompaniaPlan.java` con validaciones de transiciones:

```
[*] → PROGRAMADO | ACTIVO
PROGRAMADO → ACTIVO (fecha_inicio ≤ hoy)
ACTIVO → EN_GRACIA (fecha_fin < hoy)
EN_GRACIA → VENCIDO (gracia agotada)
ACTIVO → VENCIDO (si destino=Free, sin gracia)
ACTIVO → REEMPLAZADA (nuevo PROGRAMADO se activa)
ACTIVO → CANCELADO (owner cancela)
EN_GRACIA → CANCELADO
ACTIVO → SUSPENDIDO (root suspende)
CANCELADO → [*] (terminal)
REEMPLAZADA → [*] (terminal)
VENCIDO → [*] (expiración archivada)
```

---

## 4. Servicios de negocio (Sub-fase 1.3)

### Use cases nuevos (application/service/)

| Use case | Clase | Responsabilidad |
|----------|-------|---|
| `ActivarTrialUseCase` | `ActivarTrialService` | Valida `trial_usado=false`, crea `CompaniaPlan` ACTIVO con 60 días. Registra evento `TRIAL_ACTIVADO`. |
| `CancelarSuscripcionUseCase` | `CancelarSuscripcionService` | Cancela suscripción vigente. Si es Trial, degrada inmediato a Free (sin gracia). Owner obtiene comprobante de no-refund. |
| `ReportarPagoUseCase` | `ReportarPagoService` | Owner sube comprobante de transferencia. Valida MIME, calcula `hash_idempotencia`, sube a Cloudinary, persiste en `pagos_pendientes_validacion`. Rate limit: 3/hora. |
| `AprobarPagoUseCase` | `AprobarPagoService` | Root aprueba pago. Crea `CompaniaPlan` ACTIVO o actualiza existente si es upgrade agendado (RN-05). Registra evento `PAGO_APROBADO`. |
| `RechazarPagoUseCase` | `RechazarPagoService` | Root rechaza pago con motivo. Marca estado RECHAZADO. TODO: enviar email al owner con razón. |
| `LimiteRecursoUseCase` | `LimiteRecursoService` | Valida hard limits (sucursales, clientes, staff) antes de crear recurso. Usa `pg_advisory_xact_lock()` para serializar race conditions. |
| `ConsultarUsoLimitesUseCase` | `ConsultarUsoLimitesService` | Retorna uso actual vs límites del plan. Llamado por frontend para mostrar widgets. |
| `ListarPagosPendientesUseCase` | `ListarPagosPendientesService` | Bandeja paginada para root/soporte. Filtros: estado, antigüedad. |
| `EnviarNotificacionUseCase` | (split: `EmailQueueService` + jobs) | Encola notificación, procesa con retry exponencial. |
| `BannerUseCase` | `BannerService` | Lista banners in-app activos (RN-07), marca como descartados. |

### Servicios de infraestructura

| Servicio | Archivo | Responsabilidad |
|----------|---------|---|
| `LimiteRecursoService` | `application/service/` | Ejecutor de `validarPuedeCrear()` — consulta contadores vía HTTP call a core-service (clientes activos) y auth-service (staff count). Usa advisory lock. |
| `EmailTemplateEngine` | `infrastructure/email/` | Render simple de templates HTML/TXT con sustitución de variables. Cachea recursos en memoria. |
| `EmailAdapter` | `infrastructure/email/` | Wrapper sobre `JavaMailSender`. Envía via SMTP. Silent si no configurado. |
| `EmailQueueService` | `application/service/` | Encola notificación en `tenant.notificaciones_suscripcion`. |
| `NotificacionVencimientoJob` | `infrastructure/scheduler/` | Job diario (@Scheduled), detecta planes a vencer, encola notificaciones por bucket (15, 7, 3, 1, 0 días). Predicado idempotente. |
| `EmailQueueProcessorJob` | `infrastructure/scheduler/` | Job cada 30s, batch 50, `SELECT ... FOR UPDATE SKIP LOCKED`. Retry exponencial. Estado: ENVIADO, FALLIDO. |
| `PostgresRateLimiter` | `infrastructure/ratelimit/` | Limita reportes de pago (3/hora/tenant). Usa Postgres como contador distribuido (sin Redis). |
| `CoreServiceClient` | `infrastructure/adapter/out/http/` | HTTP call a core-service para contar clientes activos. Fail-open (retorna 0 en error). |

---

## 5. Endpoints REST (Sub-fase 1.4)

### Públicos (sin auth)

| Método | Ruta | DTOs | Guard |
|--------|------|------|-------|
| **GET** | `/api/v1/planes/publicos` | → `PlanPublicoResponse[]` | None |

**Respuesta 200:**
```json
[
  {
    "id": 1,
    "codigo": "FREE",
    "nombre": "Plan Free",
    "descripcion": "Acceso básico permanente",
    "precio_mensual": 0.00,
    "duracion_dias": null,
    "es_gratuito": true,
    "max_sucursales": 1,
    "max_clientes_activos": 50,
    "max_staff": 2,
    "moneda": "USD"
  },
  {
    "id": 2,
    "codigo": "TRIAL",
    "nombre": "Plan Trial",
    "precio_mensual": 0.00,
    "duracion_dias": 60,
    "es_gratuito": true,
    "max_sucursales": null,
    "max_clientes_activos": null,
    "max_staff": null
  },
  {
    "id": 3,
    "codigo": "PREMIUM",
    "nombre": "Plan Premium",
    "precio_mensual": 29.99,
    "duracion_dias": 30,
    "es_gratuito": false,
    "max_sucursales": null,
    "max_clientes_activos": null,
    "max_staff": null
  }
]
```

Nota: **Omite** planes con `es_legacy=true` o `activo=false`.

---

### Owner/Admin del tenant

| Método | Ruta | Descripción | Guard | DTOs |
|--------|------|---|---|---|
| **POST** | `/api/v1/companias/{id}/suscripcion/trial` | Activa Trial (RN-01) | owner ✓ | Void → `SuscripcionResponse` |
| **POST** | `/api/v1/companias/{id}/suscripcion/cancelar` | Cancela suscripción | owner ✓ | `CancelarSuscripcionRequest` → Void (204) |
| **GET** | `/api/v1/companias/{id}/uso-limites` | Uso vs límites (HU-04) | owner ✓ | → `UsoLimitesResponse` |
| **POST** | `/api/v1/companias/{id}/pagos/reportar` | Reportar pago (RN-08) | owner ✓ | multipart: comprobante, monto, fecha, banco, referencia → `PagoPendienteResponse` |
| **GET** | `/api/v1/companias/{id}/banners-activos` | Listar banners (1.5) | owner ✓ | → `BannerActivoView[]` |
| **POST** | `/api/v1/companias/{id}/banners/{idBanner}/descartar` | Descartar banner (1.5) | owner ✓ | Void → 204 |

**Endpoint: Activar Trial**
```
POST /api/v1/companias/{id}/suscripcion/trial
Authorization: Bearer <jwt-staff>
Content-Type: application/json

Response 200:
{
  "id": 15,
  "id_plan": 2,
  "estado": "ACTIVO",
  "fecha_inicio": "2026-07-10",
  "fecha_fin": "2026-09-08",
  "dias_restantes": 60,
  "dias_gracia": 5,
  "tipo_cambio": "NUEVO"
}

Errors:
- 403: acceso denegado
- 409: trial_usado=true o ya hay suscripción activa
```

**Endpoint: Reportar pago (multipart)**
```
POST /api/v1/companias/{id}/pagos/reportar
Authorization: Bearer <jwt-staff>
Content-Type: multipart/form-data

Form parts:
- comprobante: file (JPEG/PNG/PDF, máx 5MB)
- id_plan_destino: long (plan a activar)
- monto: decimal (monto transferencia)
- fecha_transferencia: date (YYYY-MM-DD)
- banco_origen: string (nombre banco)
- referencia: string (número referencia)

Response 201:
{
  "id": 42,
  "id_compania": 5,
  "id_plan_destino": 3,
  "monto": "29.99",
  "moneda": "USD",
  "fecha_reporte": "2026-07-10T14:30:00Z",
  "comprobante_url": "https://res.cloudinary.com/.../pago-42-abc123.pdf",
  "banco_origen": "Banco Pichincha",
  "referencia": "TXN-20260710-001",
  "estado": "PENDIENTE",
  "hash_idempotencia": "sha256(...)"
}

Errors:
- 400: MIME inválido, tamaño > 5MB, datos malformados
- 403: acceso denegado
- 409: hash_idempotencia duplicado (ya existe pago con esos datos)
- 429: rate limit (máx 3 reportes/hora)
```

**Endpoint: Consultar uso vs límites**
```
GET /api/v1/companias/{id}/uso-limites
Authorization: Bearer <jwt-staff>

Response 200:
{
  "plan_codigo": "FREE",
  "sucursales": {
    "actual": 1,
    "maximo": 1
  },
  "clientes_activos": {
    "actual": 48,
    "maximo": 50
  },
  "staff": {
    "actual": 2,
    "maximo": 2
  },
  "sobre_limite": false,
  "sobre_limite_hasta": null
}

Errors:
- 403: acceso denegado
- 404: sin suscripción activa
```

**Endpoint: Listar banners in-app**
```
GET /api/v1/companias/{id}/banners-activos
Authorization: Bearer <jwt-staff>

Response 200:
[
  {
    "id": 101,
    "id_compania_plan": 15,
    "tipo": "VENCIMIENTO_TRIAL",
    "dias_antes": 3,
    "mensaje": "Tu plan Trial vence en 3 días",
    "fecha_envio": "2026-07-07T10:00:00Z",
    "descartado_at": null
  }
]

Errors:
- 403: tenant mismatch
```

---

### Root/Soporte (plataforma)

| Método | Ruta | Descripción | Guard | DTOs |
|--------|------|---|---|---|
| **GET** | `/api/v1/plataforma/pagos-pendientes` | Bandeja (HU-05) | root ✓ | ?estado=PENDIENTE&page=1&limit=20 → `{ total, pagina, limit, datos: PagoPendienteResponse[] }` |
| **POST** | `/api/v1/plataforma/pagos-pendientes/{id}/aprobar` | Aprueba pago | root ✓ | Void → `{ id_pago, id_compania_plan, estado }` (200) |
| **POST** | `/api/v1/plataforma/pagos-pendientes/{id}/rechazar` | Rechaza pago | root ✓ | `RechazarPagoRequest` → Void (204) |

**Endpoint: Bandeja de pagos pendientes**
```
GET /api/v1/plataforma/pagos-pendientes?estado=PENDIENTE&pagina=1&limit=20
Authorization: Bearer <jwt-root>

Response 200:
{
  "total": 5,
  "pagina": 1,
  "limit": 20,
  "datos": [
    {
      "id": 42,
      "id_compania": 5,
      "id_compania_nombre": "Gym Power",
      "id_plan_destino": 3,
      "monto": "29.99",
      "fecha_reporte": "2026-07-10T14:30:00Z",
      "fecha_transferencia": "2026-07-10",
      "comprobante_url": "https://res.cloudinary.com/.../pago-42.pdf",
      "estado": "PENDIENTE",
      "banco_origen": "Banco Pichincha",
      "referencia": "TXN-001"
    }
  ]
}

Errors:
- 403: acceso denegado (solo root/soporte)
```

**Endpoint: Aprobar pago**
```
POST /api/v1/plataforma/pagos-pendientes/{id}/aprobar
Authorization: Bearer <jwt-root>
Content-Type: application/json

Response 200:
{
  "id_pago": 42,
  "id_compania_plan": 16,
  "estado": "ACTIVO"
}

Errors:
- 403: acceso denegado
- 404: pago no encontrado
- 409: pago ya fue procesado (idempotencia)
```

**Endpoint: Rechazar pago**
```
POST /api/v1/plataforma/pagos-pendientes/{id}/rechazar
Authorization: Bearer <jwt-root>
Content-Type: application/json

Request:
{
  "motivo_rechazo": "Comprobante no corresponde al monto"
}

Response 204 No Content

Errors:
- 403: acceso denegado
- 404: pago no encontrado
- 409: pago ya procesado
```

---

### Internos (entre servicios, secret compartido)

| Método | Ruta | Consumidor | Descripción |
|--------|------|-----------|---|
| **POST** | `/internal/v1/limites/verificar` | core-service (crear cliente) | Valida límite de recurso. |
| **GET** | `/internal/v1/companias/{id}/staff/count` | platform-service (LimiteRecursoService) | **TODO Sub-fase 1.4b** — contar staff de compañía. Aún no expuesto en auth-service. |

---

## 6. Validaciones de límites en otros servicios (Sub-fase 1.4b — TODO)

### Core Service

El controlador de clientes debe llamar a `LimiteRecursoService.validarPuedeCrear()` en platform-service antes de insertar:

```java
// ClienteController.java
@PostMapping
public Mono<ResponseEntity<ClienteResponse>> crear(...) {
    return limiteRecursoService.validarPuedeCrear(idCompania, RecursoLimitable.CLIENTES_ACTIVOS)
        .then(clienteService.crear(...))
        .map(cliente -> ResponseEntity.created(...).body(toResponse(cliente)));
}
```

**Respuesta ante límite alcanzado:**
```
POST /api/v1/clientes
Response 403 Forbidden:
{
  "codigo": "limite_plan_alcanzado",
  "recurso": "CLIENTES_ACTIVOS",
  "actual": 50,
  "maximo": 50,
  "plan_actual": "FREE",
  "mensaje": "Has alcanzado el límite de 50 clientes activos en tu plan. Actualiza a Premium."
}
```

---

## 7. Notificaciones por email (Sub-fase 1.5)

### Architecture

**Sin Redis remoto** — cola en `tenant.notificaciones_suscripcion` (columnas nuevas: `proximo_intento`, `id_compania`, `tipo`).

```
NotificacionVencimientoJob (00:15 UTC diario)
  ↓
  Detecta planes a vencer (15, 7, 3, 1, 0 días)
  Encola por predicado idempotente
  ↓
  INSERT notificaciones_suscripcion {estado='pendiente_envio', canal='EMAIL'}
  ↓
EmailQueueProcessorJob (cada 30s)
  ↓
  SELECT ... FROM notificaciones_suscripcion
  WHERE estado='pendiente_envio' AND proximo_intento <= NOW()
  FOR UPDATE SKIP LOCKED
  Batch 50
  ↓
  Para cada notificación:
    - Render template (EmailTemplateEngine)
    - Enviar via SMTP (JavaMailSender)
    - Si OK: UPDATE estado='enviado'
    - Si falla: retry con backoff (30s → 2m → 10m → 1h)
    - Después de 4 intentos fallidos: UPDATE estado='fallido' (DLQ)
  ↓
Alertar a root (via auditoría o email de error)
```

### Buckets de notificación

| Días antes | Tipo | Cadencia | Observación |
|---|---|---|---|
| 15 | VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM | Una vez | Predicado idempotente evita duplicados |
| 7 | VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM | Una vez | — |
| 3 | VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM | Una vez | — |
| 1 | VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM | Una vez | — |
| 0 | VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM | Una vez | El día del vencimiento |

### Templates (Sub-fase 1.5 — assets no verificados en código)

Se espera que existan en `platform-service/src/main/resources/email-templates/`:

```
vencimiento_15d.html / .txt
vencimiento_7d.html / .txt
vencimiento_3d.html / .txt
vencimiento_1d.html / .txt
vencimiento_0d.html / .txt
```

**Variables de template:**
- `{plan_nombre}`: ej. "Plan Trial"
- `{fecha_fin}`: fecha de vencimiento (formato TODO: verificar en código)
- `{dias_restantes}`: días al vencimiento
- `{empresa_nombre}`: nombre del gym (tenant)
- `{url_renovacion}`: link a dashboard de suscripción

**Subjects:**
```
vencimiento_15d: "Tu plan vence en 15 días"
vencimiento_7d:  "Tu plan vence en 7 días"
vencimiento_3d:  "Tu plan vence en 3 días"
vencimiento_1d:  "Tu plan vence mañana"
vencimiento_0d:  "Tu plan vence hoy"
```

### Limitaciones conocidas (Sub-fase 1.5)

1. **Destinatario hardcodeado**: emails van a `tenant.companias.correo` (email del gym), no resuelve email del owner staff específico.
   - **Workaround**: root/soporte verifica comprobante con email visible en pago reportado.
   - **Follow-up Sub-fase 1.6**: usar `email_contacto_suscripcion` de una tabla nueva.

2. **`fechaFin` pasado como string vacío**: `{fecha_fin}` se renderiza incorrectamente en templates.
   - **Causa**: formato de fecha no definido en `NotificacionVencimientoJob`.
   - **Follow-up**: formatear con `DateTimeFormatter.ofPattern("dd/MM/yyyy")`.

3. **`tenant.config_notif_suscripcion` no consultada**: se envían siempre las 5 notificaciones sin respetar `activo=false` del tenant.
   - **Causa**: job itera buckets estáticos, no per-tenant config.
   - **Follow-up Sub-fase 1.6**: filtrar por `config_notif_suscripcion` antes de encolar.

4. **Templates `pago_rechazado` y `trial_activado` fuera de scope**: no existen aún.
   - **Listado de templates existentes verificado en código**: solo vencimiento_{15,7,3,1,0}d.
   - **Follow-up Sub-fase 1.6**: agregar templates de confirmation + rechazo.

---

## 8. Banners in-app (Sub-fase 1.5)

### Modelo

- Persistidos en `tenant.notificaciones_suscripcion` con `canal='BANNER'`.
- Predicado idempotente: una por (compania_plan, tipo, dias_antes).
- Owner puede descartar (marca `descartado_at`).
- Reaparece al día siguiente si la notificación sigue siendo válida.

### Endpoints

**Listar activos:**
```
GET /api/v1/companias/{id}/banners-activos
Response 200:
[
  {
    "id": 101,
    "id_compania_plan": 15,
    "tipo": "VENCIMIENTO_TRIAL",
    "dias_antes": 7,
    "mensaje": "Tu plan Trial vence en 7 días",
    "fecha_envio": "2026-07-03T03:15:00Z",
    "descartado_at": null
  }
]
```

**Descartar:**
```
POST /api/v1/companias/{id}/banners/{idBanner}/descartar
Response 204 No Content
```

---

## 9. Modelos de datos nuevos (DTOs)

### PlanPublicoResponse
```json
{
  "id": 1,
  "codigo": "FREE",
  "nombre": "Plan Free",
  "descripcion": "Acceso básico permanente",
  "precio_mensual": 0.00,
  "duracion_dias": null,
  "es_gratuito": true,
  "max_sucursales": 1,
  "max_clientes_activos": 50,
  "max_staff": 2,
  "moneda": "USD"
}
```

### PagoPendienteResponse
```json
{
  "id": 42,
  "id_compania": 5,
  "id_compania_nombre": "Gym Power",
  "id_plan_destino": 3,
  "monto": "29.99",
  "moneda": "USD",
  "fecha_reporte": "2026-07-10T14:30:00Z",
  "fecha_transferencia": "2026-07-10",
  "comprobante_url": "https://res.cloudinary.com/.../pago-42.pdf",
  "banco_origen": "Banco Pichincha",
  "referencia": "TXN-001",
  "hash_idempotencia": "sha256(...)",
  "estado": "PENDIENTE",
  "motivo_rechazo": null,
  "aprobado_por": null,
  "fecha_aprobacion": null,
  "activacion_programada": false
}
```

### UsoLimitesResponse
```json
{
  "plan_codigo": "FREE",
  "sucursales": {
    "actual": 1,
    "maximo": 1
  },
  "clientes_activos": {
    "actual": 48,
    "maximo": 50
  },
  "staff": {
    "actual": 2,
    "maximo": 2
  },
  "sobre_limite": false,
  "sobre_limite_hasta": null
}
```

### BannerActivoView (Sub-fase 1.5)
```json
{
  "id": 101,
  "id_compania_plan": 15,
  "tipo": "VENCIMIENTO_TRIAL",
  "dias_antes": 7,
  "mensaje": "Tu plan Trial vence en 7 días",
  "fecha_envio": "2026-07-03T03:15:00Z",
  "descartado_at": null
}
```

---

## 10. Eventos auditables (Sub-fase 1.3+1.4)

Registrados en `saas.actividad_plataforma`:

| Evento | Actor | Detalle JSONB | Caso de uso |
|--------|-------|---|---|
| `TRIAL_ACTIVADO` | OWNER | `{ fecha_inicio, fecha_fin }` | Activar Trial |
| `PLAN_SELECCIONADO` | OWNER | `{ plan_codigo, precio }` | Reporte de pago |
| `PAGO_REPORTADO` | OWNER | `{ id_pago, monto, banco_origen, referencia }` | Reportar pago |
| `PAGO_APROBADO` | ROOT | `{ id_pago, id_root, id_compania_plan_creado }` | Aprobar pago |
| `PAGO_RECHAZADO` | ROOT | `{ id_pago, id_root, motivo_rechazo }` | Rechazar pago |
| `PLAN_DEGRADADO_AUTO` | SISTEMA | `{ plan_anterior, plan_nuevo, causa, id_compania_plan_anterior }` | Job de degradación |
| `SUSCRIPCION_CANCELADA` | OWNER | `{ id_compania_plan, motivo_owner }` | Cancelación |
| `LIMITE_FREE_ALCANZADO` | (SISTEMA en log) | `{ recurso, actual, maximo }` | Creación bloqueada |
| `SOBRE_LIMITE_DETECTADO` | SISTEMA | `{ recursos: [...], gracia_hasta }` | Degradación con sobre-límite |
| `NOTIF_VENCIMIENTO_ENVIADA` | SISTEMA | `{ dias_antes, canal, id_notif }` | Email/Banner enviado |

---

## 11. Servicios HTTP de cross-service integration

### PlatformServiceClient (core-service → platform-service)

```java
// En core-service, inyectar PlatformServiceClient
// Usarlo en ClienteService.crear():

PlatformServiceClient.verificarLimite(idCompania, "CLIENTES_ACTIVOS")
    .flatMap(...crear cliente...)
    .onErrorResume(err -> {
        // Fail-open: logging WARN, permitir creación
        log.warn("Límite check falló; permitiendo creación de cliente", err);
        return crearCliente(...);
    })
```

**Endpoint:** `POST /internal/v1/limites/verificar`
```json
Request:
{
  "id_compania": 5,
  "recurso": "CLIENTES_ACTIVOS"
}

Response 200 (OK):
{ "permitido": true }

Response 403 (Límite alcanzado):
{
  "permitido": false,
  "codigo": "limite_plan_alcanzado",
  "recurso": "CLIENTES_ACTIVOS",
  "actual": 50,
  "maximo": 50
}
```

### CoreServiceClient (platform-service → core-service)

```java
// En LimiteRecursoService:
coreServiceClient.contarClientesActivos(idCompania)
    .onErrorResume(err -> {
        log.warn("CoreServiceClient falló; retornando 0 (fail-open)");
        return Mono.just(0L);
    })
```

**Endpoint:** `GET /internal/v1/companias/{id}/clientes-activos/count`
```json
Response 200:
{
  "count": 48
}
```

---

## 12. Validaciones y guards

### AccessControlService (platform-service)

```java
accessControl.requireOwnerOrAdminOfCompania(principal, idCompania)
    // Verifica que principal.getUserId() es owner o admin del tenant
    
accessControl.requireRootOrSoporte(principal)
    // Verifica que principal.rolPlataforma == ROOT | SOPORTE
```

### TenantMismatchGuard

Middleware que valida que `idCompania` del path coerces con `principal.getIdCompania()`.

---

## 13. State transitions (SubscriptionJobService)

**Orden de operaciones en job diario (00:05 UTC) — CRÍTICO:**

```
1. Activar suscripciones PROGRAMADO donde fecha_inicio <= hoy
   UPDATE compania_planes SET estado='ACTIVO'
   WHERE estado='PROGRAMADO' AND fecha_inicio <= CURRENT_DATE

2. Degradar suscripciones vencidas a su plan_degradacion_id
   Si destino=FREE: transición ACTIVO/EN_GRACIA → VENCIDO sin gracia
   Si destino!=FREE: transición ACTIVO → EN_GRACIA, luego EN_GRACIA → VENCIDO

3. Registrar cambios en saas.actividad_plataforma

4. Invalidar caché Redis
   DEL modulo_check:{tenant_id}:*
```

**Razón del orden:** un upgrade agendado (Trial → Premium en PROGRAMADO) debe activarse ANTES de degradar Trial vencido. Sin esto, el Trial se degrada a Free mientras Premium espera.

---

## 14. Race conditions y mitigaciones

### 1. Creación simultánea de recurso en Free (dos clientes #50 y #51)

**Mitigación:** `pg_advisory_xact_lock(idCompania)` serializa requests del mismo tenant.
- Solo uno recibe 201, el otro recibe 403 límite_plan_alcanzado.

### 2. Aprobación simultánea del mismo pago por dos roots

**Mitigación:** `UPDATE WHERE estado='PENDIENTE'` y verificar `affectedRows==1`.
- Si `affectedRows==0`: retornar 409 Conflict.

### 3. Reporte duplicado del mismo pago (por error del owner)

**Mitigación:** Hash idempotencia `SHA-256(id_compania + monto + fecha_transferencia + referencia)`.
- Si existe otro pago con ese hash en estado PENDIENTE/APROBADO: 409 Conflict con referencia al primero.

### 4. Notificación enviada dos veces (job falló un día)

**Mitigación:** Predicado idempotente en `NotificacionRepository.existsIdempotente()`.
- No insertará si ya existe notificación con (id_compania_plan, tipo, canal, dias_antes).

---

## 15. TODOs pendientes (Sub-fase 1.4b, 1.6)

### Sub-fase 1.4b (Integración con core-service)

- [ ] Exponer en `auth-service`: `GET /internal/v1/companias/{id}/staff/count`
- [ ] Llamar a ese endpoint desde `LimiteRecursoService.contarStaff()` (hoy retorna 0)
- [ ] Tests de integración: crear cliente → verifica límite → 403 si alcanzado

### Sub-fase 1.6 (Frontend + tests + code review)

- [ ] Componentes React:
  - [ ] `Step3Plan.tsx` (wizard): Trial pre-seleccionado, link "Prefiero Free"
  - [ ] `MiSuscripcionPage.tsx`: badge de plan, días restantes, historial
  - [ ] `ReportarPagoModal.tsx`: upload de comprobante, formulario
  - [ ] `PagosPendientesPage.tsx` (root): bandeja, preview, aprobar/rechazar
  - [ ] Widget: "Uso: 48/50 clientes"
  - [ ] Bloqueo de límite: modal "Actualiza a Premium"
- [ ] Templates de email (assets HTML/TXT):
  - [ ] vencimiento_{15,7,3,1,0}d.{html,txt}
  - [ ] pago_rechazado.{html,txt} (nuevo)
  - [ ] trial_activado.{html,txt} (nuevo)
- [ ] Tests de integración:
  - [ ] Time-travel con `Clock.fixed()` → degradación exacta en día 61
  - [ ] Concurrencia: dos creaciones simultáneas de cliente #50/#51
  - [ ] Idempotencia: dos POST del mismo pago
  - [ ] Cache invalidation: aprobar pago → `GET /modulos/check` responde en <1s
- [ ] Code review + verificar limitaciones documentadas

---

## 16. Resumen de cambios por archivo

### Database (Liquibase changesets)

Todos los cambios de Sub-fase 1.1 viven en la story consolidada `db/scripts/202605_GYM-001/`:

- **`ddl/`** — CREATE consolidados (sin ALTER) para `saas.planes` (`GYM-001-11`), `tenant.companias` (`GYM-001-16`), `tenant.compania_planes` (`GYM-001-18`), `tenant.notificaciones_suscripcion` (`GYM-001-21`), `saas.actividad_plataforma` (`GYM-001-67`).
- **`ddl-freemium/`** — Tablas 100 % nuevas: `tenant.pagos_pendientes_validacion` (`GYM-001-140`), `saas.config_plataforma` (`GYM-001-141`), y seed `03_seed_saas_config_plataforma.sql`.

> La story antigua `202608_GYM-003` (que contenía los `ALTER` originales) fue retirada del changelog al hacer la consolidación (commit `e5ff46f`).

### platform-service (Java)

**Nuevos controladores:**
- `PlanPublicoController` (público)
- `SuscripcionController` (Trial, cancelar)
- `PagoOwnerController` (reportar pago)
- `PagoPlataformaController` (bandeja root, aprobar/rechazar)
- `UsoLimitesController` (consultar límites)
- `BannerController` (Sub-fase 1.5)

**Nuevos servicios:**
- `ActivarTrialService`, `CancelarSuscripcionService`
- `ReportarPagoService`, `AprobarPagoService`, `RechazarPagoService`
- `LimiteRecursoService` (advisor locks)
- `ConsultarUsoLimitesService`
- `ListarPagosPendientesService`
- `EmailQueueService`, `EmailTemplateEngine`
- `BannerService`

**Nuevos jobs (@Scheduled):**
- `NotificacionVencimientoJob` (00:15 UTC)
- `EmailQueueProcessorJob` (cada 30s)

**Nuevos adaptadores:**
- `CoreServiceClient` (HTTP a core-service)
- `EmailAdapter` (SMTP)
- `PagoPendienteValidacionPersistenceAdapter` (R2DBC)
- `NotificacionPersistenceAdapter` (R2DBC)

**Nuevos DTOs:**
- `PlanPublicoResponse`, `PagoPendienteResponse`, `UsoLimitesResponse`, `BannerActivoView`
- `ActivarTrialRequest`, `CancelarSuscripcionRequest`, `RechazarPagoRequest`

**Nuevos modelos:**
- `PagoPendienteValidacion`, `NotificacionSuscripcion` (extendida)
- `RecursoLimitable` (enum)

### core-service (Java)

**Pendiente Sub-fase 1.4b:**
- Integrar `PlatformServiceClient` en `ClienteService.crear()`
- Llamar a `/internal/v1/limites/verificar` antes de insertar cliente

---

## 17. Logs y alertas

### Nivel INFO

```
NotificacionVencimientoJob: "Iniciando job de notificaciones de vencimiento — hoy=2026-07-10"
NotificacionVencimientoJob: "Job de notificaciones completado" (o error)
EmailQueueProcessorJob: "Procesando lote de emails (batch 50)"
EmailQueueProcessorJob: "Email enviado a {email} (intento 1)"
```

### Nivel WARN

```
LimiteRecursoService: "CoreServiceClient.contarClientesActivos({id}) falló: timeout; retornando 0 (fail-open)"
EmailQueueProcessorJob: "Reintentando email {id} (intento 2 de 4)"
```

### Nivel ERROR

```
NotificacionVencimientoJob: "Job de notificaciones falló", <stacktrace>
EmailQueueProcessorJob: "Email {id} DLQ tras 4 reintentos", <stacktrace>
```

---

## 18. Verificación contra el código (2026-07-10)

| Aspecto | Verificado | Archivo |
|--------|-----------|---------|
| Endpoint público `/planes/publicos` | ✅ | `PlanPublicoController` |
| Endpoint Trial `POST /suscripcion/trial` | ✅ | `SuscripcionController` |
| Endpoint cancelar `POST /suscripcion/cancelar` | ✅ | `SuscripcionController` |
| Endpoint reportar pago (multipart) | ✅ | `PagoOwnerController` |
| Endpoint bandeja pagos (root) | ✅ | `PagoPlataformaController` |
| Endpoint aprobar/rechazar | ✅ | `PagoPlataformaController` |
| Endpoint uso-limites | ✅ | `UsoLimitesController` |
| Endpoint banners (Sub-fase 1.5) | ✅ | `BannerController` |
| Job NotificacionVencimientoJob | ✅ | `infrastructure/scheduler/` |
| Job EmailQueueProcessorJob | ✅ | `infrastructure/scheduler/` |
| Advisory lock en LimiteRecursoService | ✅ | `LimiteRecursoService.adquirirAdvisoryLock()` |
| Rate limiter Postgres | ✅ | `PostgresRateLimiter` |
| EmailTemplateEngine render | ✅ | `infrastructure/email/` |
| CoreServiceClient HTTP | ✅ | `infrastructure/adapter/out/http/` |

---

## Fin de documento

**Estado:** Implementación de Sub-fases 1.3, 1.4, 1.5 completada y verificada.  
**Próximo paso:** Sub-fase 1.6 (frontend, tests, code review).

