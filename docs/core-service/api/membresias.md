# Membresías API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `MembresiaController`).

Gestión del ciclo de vida de membresías: venta, validación de acceso, congelamiento, anulación y actualización de asistencias previas.

Base path: `/api/v1`  
Service: core-service (port 8083)

---

## Endpoints

### GET /api/v1/clientes/{id}/membresias
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Historial de membresías de un cliente (paginado).

**Path param:**
- `id` — ID del cliente en `core.clientes`

**Response 200:**
```json
[
  {
    "id": 1,
    "id_cliente": 10,
    "id_tipo_membresia": 3,
    "fecha_inicio": "2026-01-15",
    "fecha_fin": "2026-04-15",
    "dias_acceso_total": 90,
    "precio_pagado": "150.00",
    "descuento_aplicado": "10.00",
    "estado": "ACTIVA"
  }
]
```

**Response fields:**
- `id` — Membership ID
- `id_cliente` — Client ID
- `id_tipo_membresia` — Membership type ID
- `fecha_inicio` — Start date (ISO format)
- `fecha_fin` — End date (ISO format)
- `dias_acceso_total` — Total days for calendar-based memberships
- `precio_pagado` — Amount paid
- `descuento_aplicado` — Applied discount (if any)
- `estado` — State: `ACTIVA`, `VENCIDA`, `ANULADA`, `CONGELADA`

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions or wrong company
- `404` — client not found

---

### POST /api/v1/clientes/{id}/membresias
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()`  
**Description:** Vender (crear) una membresía a un cliente. Calcula `fecha_fin` según el tipo. Soporta ventas cobradas al momento (`estado_pago = PAGADO`) y ventas pendientes de pago (`estado_pago = PENDIENTE`, reservado para el flujo de compra desde la PWA — HU-B).

**Path param:**
- `id` — ID del cliente

**Request body:**
```json
{
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "id_metodo_pago": 1,
  "descuento_aplicado": "10.00",
  "estado_pago": "PAGADO"
}
```

**Body fields:**
- `id_tipo_membresia` (required, integer) — Membership type ID from `tipos-membresia`
- `fecha_inicio` (required, date `YYYY-MM-DD`) — Start date (ignorado cuando `estado_pago = PENDIENTE`)
- `id_metodo_pago` (optional, integer) — Payment method ID
- `descuento_aplicado` (optional, decimal >= 0) — Discount amount
- `estado_pago` (optional, enum: `PAGADO` \| `PENDIENTE`, default `PAGADO`) — Estado de cobro de la venta. Con `PENDIENTE`, `fecha_inicio`/`fecha_fin` se persisten como `null` (respetando el CHECK `ck_membresias_fechas_por_estado_pago`) y no se dispara efecto sobre el estado del cliente ni evento hacia billing.

**Validaciones de negocio:**
- Si `estado_pago = PAGADO` y el cliente ya tiene otra `PAGADA` activa → `409 Conflict`.
- Si `estado_pago = PENDIENTE` y el cliente ya tiene otra `PENDIENTE` viva (`estado_pago = PENDIENTE` y `eliminado = false`) → `409 Conflict`. Se permite coexistencia `PAGADA activa + PENDIENTE nueva` (renovación anticipada).

**Response 201:**
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "fecha_fin": "2026-04-15",
  "dias_acceso_total": 90,
  "precio_pagado": "150.00",
  "descuento_aplicado": "10.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "eliminado": false,
  "motivo_eliminacion": null
}
```

**Errors:**
- `400` — invalid request (missing required fields, invalid dates, valor de `estado_pago` fuera del catálogo)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — client or membership type not found
- `409` — business rule conflict (`El cliente ya tiene una membresía activa` o `El cliente ya tiene una membresía pendiente de pago`)

> Revisar contra código: `MembresiaController#vender` + `MembresiaService#vender` (§4.4 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### GET /api/v1/membresias/{id}
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Detalle de una membresía con información computada (días usados, restantes, etc.).

**Path param:**
- `id` — Membership ID

**Response 200:**
```json
{
  "id": 1,
  "tipo": "Plan Mensual",
  "modo_control": "calendario",
  "fecha_inicio": "2026-01-15",
  "fecha_fin": "2026-04-15",
  "dias_acceso_total": 90,
  "dias_acceso_usados": 15,
  "dias_acceso_restantes": 75,
  "asistencias_previas": 0,
  "precio_pagado": "150.00",
  "estado": "ACTIVA"
}
```

**Response fields:**
- `id` — Membership ID
- `tipo` — Membership type name
- `modo_control` — `calendario` (calendar-based) or `accesos` (access-based)
- `fecha_inicio` — Start date
- `fecha_fin` — End date
- `dias_acceso_total` — Total days (calendar mode) or total accesses (access mode)
- `dias_acceso_usados` — Used days (from `asistencia.asistencias`)
- `dias_acceso_restantes` — Remaining days
- `asistencias_previas` — Prepaid visits before gym access tracking started
- `precio_pagado` — Amount paid
- `estado` — `ACTIVA`, `VENCIDA`, `ANULADA`, `CONGELADA`

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions or wrong company
- `404` — membership not found

---

### PATCH /api/v1/membresias/{id}/asistencias-previas
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()`  
**Description:** Actualizar el contador de asistencias previas (before migrating to attendance tracking system). Ajusta el recount de visitas pendientes.

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "cantidad": 5
}
```

**Body fields:**
- `cantidad` (required, integer >= 0) — New value for `asistencias_previas`

**Response 200:**
```json
{
  "id": 1,
  "asistencias_previas": 5
}
```

**Errors:**
- `400` — invalid quantity (negative, non-integer)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — membership not found

---

### PUT /api/v1/membresias/{id}/anular
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireAdminOrDueno()`  
**Description:** Anular una membresía. Requiere motivo. Cambiar estado a `ANULADA`.

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "motivo": "Solicitud del cliente"
}
```

**Body fields:**
- `motivo` (required, string) — Reason for cancellation

**Response 200:** (no body)

**Errors:**
- `400` — missing motivo
- `401` — missing or invalid JWT
- `403` — insufficient permissions (admin/owner only)
- `404` — membership not found

---

### GET /api/v1/membresias/validar-acceso
**Auth:** None (PUBLIC endpoint)  
**Description:** Validar si una persona puede acceder al gym. Usada por sistemas de acceso (turnstiles, QR readers) y consumida vía HTTP por `attendance-service` (`CoreServiceClient.validarAcceso`), que propaga el `razon` como `ForbiddenException` sin traducirlo. El texto amigable vive en el i18n de la PWA/kiosko.

**Query parameters:**
- `id_persona` (required, integer) — Person ID from `identidad.personas`
- `id_compania` (required, integer) — Company ID

**Response 200 (Acceso permitido):**
```json
{
  "permitido": true,
  "id_cliente": 10,
  "id_membresia": 1,
  "modo_control": "calendario",
  "tipo_membresia": "Plan Mensual",
  "dias_acceso_restantes": 75,
  "fecha_fin": "2026-04-15",
  "accesos_usados": 5
}
```

**Response 403 (Acceso denegado):**
```json
{
  "permitido": false,
  "razon": "sin_membresia",
  "tipo_membresia": "Plan Mensual",
  "ultima_membresia_fin": "2026-04-15",
  "accesos_usados": 5,
  "accesos_total": 90
}
```

**Response fields (acceso permitido):**
- `permitido` — Always `true` for 200
- `id_cliente` — Client ID
- `id_membresia` — Active membership ID
- `modo_control` — `calendario` or `accesos`
- `tipo_membresia` — Membership type name
- `dias_acceso_restantes` — Remaining visits/days
- `fecha_fin` — Membership expiry date
- `accesos_usados` — (Optional) Used accesses if access-based

**Response fields (acceso denegado):**
- `permitido` — Always `false` for 403
- `razon` — Código corto (snake_case) explicando el bloqueo. Ver catálogo abajo.
- `tipo_membresia` — (Optional) Last membership type
- `ultima_membresia_fin` — (Optional) Last membership end date
- `accesos_usados` — (Optional) If the last membership was access-based
- `accesos_total` — (Optional) Total accesses of last membership

**Catálogo de `razon` (códigos cortos, snake_case):**

| Código | Situación | Origen |
|--------|-----------|--------|
| `pago_pendiente` | Existe una membresía con `estado_pago = PENDIENTE` viva | §4.5 GYM-003 |
| `membresia_rechazada` | La membresía fue soft-deleted (`eliminado = true`) | §4.5 GYM-003 |
| `sin_membresia` | No existe membresía activa ni cliente registrado | histórico |
| `membresia_congelada` | Estado `congelada` en curso | histórico |
| `membresia_vencida` | Estado `vencida` o `fecha_fin < hoy` | histórico |
| `accesos_agotados` | Membresía por accesos con `usados >= total` | histórico |

**Orden de evaluación (importante):** las razones `pago_pendiente` y `membresia_rechazada` se evalúan **antes** que `sin_membresia`, `membresia_congelada`, `membresia_vencida` y `accesos_agotados`. Consecuencia: un cliente cuya única "activa" es una PENDIENTE + tiene una PAGADA vencida reporta `pago_pendiente` (no `membresia_vencida`).

**Errors:**
- `400` — missing or invalid query parameters
- `403` — access denied (see response body for `razon`)

> Revisar contra código: `MembresiaController#validarAcceso` + `MembresiaService#resolverAcceso/evaluarMembresia`.

---

### POST /api/v1/membresias/{id}/confirmar-pago
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago` (se exige solo si el token trae la lista `permisos` explícita; los tokens legacy sin `permisos` y `super_admin` la bypass-ean — el gate real vive en `MembresiaController#requireConfirmarPagoPermiso`).  
**Description:** Marca una membresía `PENDIENTE` como `PAGADO`. Calcula `fecha_inicio = hoy` y `fecha_fin = hoy + duración`. Publica `MembresiaPagadaEvent` en el bus interno (Spring `ApplicationEventPublisher`) solo en la transición real. Idempotente sobre membresías ya `PAGADO`.

**Path param:**
- `id` — Membership ID

**Request body:** ninguno.

**Response 200:**
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-07-17",
  "fecha_fin": "2026-08-17",
  "dias_acceso_total": null,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "eliminado": false,
  "motivo_eliminacion": null
}
```

**Reglas:**
- Idempotente: si la membresía ya está `PAGADO`, devuelve `200 OK` con el recurso actual (no recalcula fechas, no re-emite evento).
- Si `eliminado = true` → `409 Conflict` con mensaje `"La membresía fue rechazada y no puede confirmarse"`.
- Efecto lateral: cambia el estado del cliente a `activo`.

**Errors:**
- `401` — missing or invalid JWT
- `403` — sin permiso `membresias:confirmar_pago`, o compañía distinta
- `404` — membership o tipo de membresía no encontrados
- `409` — membresía rechazada (`eliminado = true`)

> Revisar contra código: `MembresiaController#confirmarPago` + `MembresiaService#confirmarPago` (§4.6 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### POST /api/v1/membresias/{id}/rechazar
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago` (cubre confirmar y rechazar).  
**Description:** Soft-delete de una membresía `PENDIENTE`. Marca `eliminado = true`, setea auditoría (`fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`). La membresía sigue viva en BD para reportería (métrica de conversión, §HU-E). NO aplica a membresías ya `PAGADAS` (esas se anulan con `PUT /membresias/{id}/anular`).

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "motivo_eliminacion": "SOCIO_CAMBIO_OPINION"
}
```

**Body fields:**
- `motivo_eliminacion` (required, enum) — Catálogo cerrado: `SOCIO_CAMBIO_OPINION` \| `ERROR_DE_VENTA` \| `DUPLICADA` \| `DATOS_INCORRECTOS` \| `OTRO`.

**Response 200:**
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": null,
  "fecha_fin": null,
  "dias_acceso_total": null,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "estado": "activa",
  "estado_pago": "PENDIENTE",
  "eliminado": true,
  "motivo_eliminacion": "SOCIO_CAMBIO_OPINION"
}
```

**Reglas:**
- Si `estado_pago = PAGADO` → `409 Conflict` con mensaje `"No se puede rechazar una membresía pagada; usar anulación"`.
- Si `eliminado = true` (ya rechazada) → `409 Conflict` con mensaje `"La membresía ya fue rechazada"`.

**Errors:**
- `400` — body ausente, `motivo_eliminacion` null o fuera del catálogo
- `401` — missing or invalid JWT
- `403` — sin permiso `membresias:confirmar_pago`, o compañía distinta
- `404` — membership no encontrada
- `409` — membresía ya pagada o ya rechazada

> Revisar contra código: `MembresiaController#rechazar` + `MembresiaService#rechazar` (§4.7 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### GET /api/v1/companias/{idCompania}/membresias/pendientes
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago`.  
**Description:** Dashboard "Ventas pendientes" (§4.8). Lista membresías con `estado_pago = PENDIENTE` y `eliminado = false` de la compañía indicada, ordenadas por `creacion_fecha` DESC (última primero). El frontend calcula el "hace X días" a partir de `creacion_fecha`.

**Path param:**
- `idCompania` — Company ID (debe coincidir con la compañía del JWT).

**Response 200:**
```json
[
  {
    "id": 42,
    "id_cliente": 10,
    "nombre_cliente": "Ana Pérez",
    "id_tipo_membresia": 3,
    "tipo_nombre": "Plan Mensual",
    "modo_control": "calendario",
    "precio_pagado": "35.00",
    "descuento_aplicado": "0.00",
    "creacion_fecha": "2026-07-15T09:12:33Z"
  }
]
```

**Response fields por fila:**
- `id` — Membership ID
- `id_cliente` — Client ID
- `nombre_cliente` — Nombre completo del cliente (join a `identidad.personas.nombre`). `null` si la persona fue borrada.
- `id_tipo_membresia` — Membership type ID
- `tipo_nombre` — Nombre del tipo (join a `core.tipos_membresia`)
- `modo_control` — `calendario` o `accesos`
- `precio_pagado` — Amount to be paid
- `descuento_aplicado` — Discount applied
- `creacion_fecha` — Timestamp de creación (ISO-8601 con TZ)

**Errors:**
- `401` — missing or invalid JWT
- `403` — cross-tenant (`idCompania` no es la del JWT) o sin permiso granular

> Revisar contra código: `MembresiaController#listarPendientes` + `MembresiaService#listarPendientesPorCompania` (§4.8 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/clientes/{id}/membresias` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/clientes/{id}/membresias` | POST | `requireRecepcionOrAbove()` |
| `/membresias/{id}` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/membresias/{id}/asistencias-previas` | PATCH | `requireRecepcionOrAbove()` |
| `/membresias/{id}/anular` | PUT | `requireAdminOrDueno()` |
| `/membresias/{id}/confirmar-pago` | POST | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/membresias/{id}/rechazar` | POST | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/companias/{idCompania}/membresias/pendientes` | GET | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/membresias/validar-acceso` | GET | PUBLIC (sin auth) |

---

## Códigos de error comunes

| Código | Significado |
|--------|-------------|
| 400 | Datos de request inválidos |
| 401 | Token ausente o inválido |
| 403 | Sin permisos o acceso denegado |
| 404 | Recurso no encontrado |
| 409 | Conflicto de negocio |
