# Congelamientos API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `CongelamientoController`).

Gestión de congelamientos (suspensión temporal) de membresías. Permite pausar la membresía sin perder vigencia, con compensación de días.

Base path: `/api/v1`  
Service: core-service (port 8083)

---

## Endpoints

### POST /api/v1/membresias/{id}/congelar
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` (normal) OR `requireAdminOrDueno()` (retroactive)  
**Description:** Congelar una membresía activa. Si es retroactivo, requiere rol Admin/Dueño. Cambia estado de membresía a `CONGELADA` y del cliente a `congelado`.

**Path param:**
- `id` — Membership ID to freeze

**Request body:**
```json
{
  "fecha_inicio": "2026-02-01",
  "motivo": "SOLICITUD_CLIENTE",
  "detalle": "Cliente en vacaciones",
  "retroactivo": false,
  "documento_respaldo": null,
  "aprobado_por": 5
}
```

**Body fields:**
- `fecha_inicio` (required, date `YYYY-MM-DD`) — Freeze start date
- `motivo` (required, enum) — Reason: `SOLICITUD_CLIENTE`, `MOTIVO_MEDICO`, `VIAJE`, `OTRO`
- `detalle` (optional, string) — Additional notes
- `retroactivo` (optional, boolean, default `false`) — If `true`, freeze retroactively from `fecha_inicio` (requires Admin/Dueño)
- `documento_respaldo` (optional, string) — URL or reference to supporting document
- `aprobado_por` (optional, integer) — ID of approving user (usually Staff member)

**Response 201:**
```json
{
  "id_congelamiento": 1,
  "fecha_inicio": "2026-02-01",
  "fecha_fin": "2026-03-01"
}
```

**Response fields:**
- `id_congelamiento` — Freeze record ID
- `fecha_inicio` — Actual freeze start date
- `fecha_fin` — Calculated unfreeze date (membership end date is extended by the frozen period)

**Errors:**
- `400` — invalid request (missing required fields, invalid date format, invalid motivo enum)
- `401` — missing or invalid JWT
- `403` — insufficient permissions (retroactive requires Admin/Dueño)
- `404` — membership not found or not active
- `409` — business rule conflict (e.g., membership already frozen)

---

### PUT /api/v1/congelamientos/{id}/reactivar
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()`  
**Description:** Reactivate a freeze (admin endpoint). Ends the freeze, calculates day compensation, and extends membership end date accordingly.

**Path param:**
- `id` — Freeze record ID in `core.congelamientos`

**Response 200:**
```json
{
  "fecha_fin_anterior": "2026-03-01",
  "dias_compensados": 28,
  "fecha_fin_nueva": "2026-03-29"
}
```

**Response fields:**
- `fecha_fin_anterior` — Previous membership end date (before unfreeze)
- `dias_compensados` — Days between freeze start and reactivation (actual freeze duration)
- `fecha_fin_nueva` — New membership end date (extended by `dias_compensados`)

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions (reception/staff only)
- `404` — freeze record not found or already reactivated
- `409` — business rule conflict (e.g., membership not frozen)

---

### PUT /api/v1/mis-congelamientos/{id}/reactivar
**Auth:** Bearer JWT (`tipo: cliente`)  
**Permission:** `requireCliente()`  
**Description:** Client-side unfreeze. Cliente puede reactivar su propio congelamiento sin aprobación. Mismo comportamiento que admin reactivate, pero requiere que el cliente sea dueño de la membresía.

**Path param:**
- `id` — Freeze record ID

**Response 200:**
```json
{
  "fecha_fin_anterior": "2026-03-01",
  "dias_compensados": 28,
  "fecha_fin_nueva": "2026-03-29"
}
```

**Response fields:** (identical to admin reactivate)
- `fecha_fin_anterior` — Previous membership end date
- `dias_compensados` — Actual freeze duration (days)
- `fecha_fin_nueva` — New membership end date (extended)

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions (client token only) or freeze not owned by this client
- `404` — freeze record not found
- `409` — business rule conflict (e.g., freeze already reactivated)

---

### GET /api/v1/membresias/{id}/congelamientos
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Historial de congelamientos para una membresía específica.

**Path param:**
- `id` — Membership ID

**Response 200:**
```json
[
  {
    "id": 1,
    "fecha_inicio": "2026-02-01",
    "fecha_fin": "2026-03-01",
    "motivo": "SOLICITUD_CLIENTE",
    "retroactivo": false
  },
  {
    "id": 2,
    "fecha_inicio": "2026-04-10",
    "fecha_fin": "2026-05-10",
    "motivo": "VIAJE",
    "retroactivo": false
  }
]
```

**Response fields:**
- `id` — Freeze record ID
- `fecha_inicio` — Freeze start date
- `fecha_fin` — Unfreeze date (when membership end was extended to)
- `motivo` — Freeze reason enum
- `retroactivo` — Whether the freeze was retroactive

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — membership not found

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/membresias/{id}/congelar` | POST | `requireRecepcionOrAbove()` (normal) \| `requireAdminOrDueno()` (retroactive) |
| `/congelamientos/{id}/reactivar` | PUT | `requireRecepcionOrAbove()` |
| `/mis-congelamientos/{id}/reactivar` | PUT | `requireCliente()` |
| `/membresias/{id}/congelamientos` | GET | `requireGymStaff()` \| `requireCliente()` |

---

## Enums

### Motivo (freeze reason)
- `SOLICITUD_CLIENTE` — Client requested
- `MOTIVO_MEDICO` — Medical reason
- `VIAJE` — Travel
- `OTRO` — Other

---

## Comportamiento de congelamiento

1. **Congelar:** Membresía pasa a estado `CONGELADA`. Cliente pasa a estado `congelado`. Se registra en `core.congelamientos`.
2. **Reactivar (Admin):** Freeze termina. Membresía vuelve a estado `ACTIVA`. Cliente regresa a su estado anterior (activo, proximo_vencer, etc.). Fecha fin se extiende por `dias_compensados`.
3. **Reactivar (Cliente):** El cliente puede auto-reactivar su congelamiento sin esperar aprobación. Mismo efecto que admin reactivate.
4. **Retroactivo:** Solo admin/dueño pueden congelar retroactivamente (freeze start date en el pasado). Calcula compensación correctamente.

---

## Códigos de error comunes

| Código | Significado |
|--------|-------------|
| 400 | Datos de request inválidos |
| 401 | Token ausente o inválido |
| 403 | Sin permisos (rol/permiso insuficiente) |
| 404 | Recurso no encontrado |
| 409 | Conflicto de negocio (membresía no congelada, etc.) |
