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
**Description:** Vender (crear) una membresía a un cliente. Calcula `fecha_fin` según el tipo.

**Path param:**
- `id` — ID del cliente

**Request body:**
```json
{
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "id_metodo_pago": 1,
  "descuento_aplicado": "10.00"
}
```

**Body fields:**
- `id_tipo_membresia` (required, integer) — Membership type ID from `tipos-membresia`
- `fecha_inicio` (required, date `YYYY-MM-DD`) — Start date
- `id_metodo_pago` (optional, integer) — Payment method ID
- `descuento_aplicado` (optional, decimal >= 0) — Discount amount

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
  "estado": "ACTIVA"
}
```

**Errors:**
- `400` — invalid request (missing required fields, invalid dates)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — client or membership type not found
- `409` — business rule conflict (e.g., active membership already exists)

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
**Description:** Validar si una persona puede acceder al gym. Usada por sistemas de acceso (turnstiles, QR readers). Devuelve estado de la membresía y accesos restantes.

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
  "razon": "Sin membresía activa",
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
- `razon` — Reason why access is denied
- `tipo_membresia` — (Optional) Last membership type
- `ultima_membresia_fin` — (Optional) Last membership end date
- `accesos_usados` — (Optional) If the last membership was access-based
- `accesos_total` — (Optional) Total accesses of last membership

**Possible reasons (razon):**
- `Sin membresía activa` — No active membership
- `Membresía vencida` — Membership expired
- `Sin accesos disponibles` — Access-based membership has no remaining visits

**Errors:**
- `400` — missing or invalid query parameters
- `403` — access denied (see response body for razon)

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/clientes/{id}/membresias` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/clientes/{id}/membresias` | POST | `requireRecepcionOrAbove()` |
| `/membresias/{id}` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/membresias/{id}/asistencias-previas` | PATCH | `requireRecepcionOrAbove()` |
| `/membresias/{id}/anular` | PUT | `requireAdminOrDueno()` |
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
