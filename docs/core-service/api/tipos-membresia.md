# Tipos de Membresía API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `TipoMembresiaController`).

Catálogo de tipos de membresía por compañía. Define duración, modo de control (calendario vs accesos), precio y disponibilidad.

Base path: `/api/v1/tipos-membresia`  
Service: core-service (port 8083)

---

## Endpoints

### GET /api/v1/tipos-membresia
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Listar tipos de membresía activos de la compañía del usuario.

**Response 200:**
```json
[
  {
    "id": 1,
    "nombre": "Plan Mensual",
    "modo_control": "CALENDARIO",
    "duracion_tipo": "DIAS",
    "duracion_valor": 30,
    "dias_acceso": null,
    "precio": "50.00",
    "activo": true
  },
  {
    "id": 2,
    "nombre": "Plan por Accesos",
    "modo_control": "ACCESOS",
    "duracion_tipo": "MESES",
    "duracion_valor": 1,
    "dias_acceso": 20,
    "precio": "45.00",
    "activo": true
  }
]
```

**Response fields:**
- `id` — Membership type ID
- `nombre` — Type name (e.g., "Plan Mensual")
- `modo_control` — `CALENDARIO` (fixed duration) or `ACCESOS` (visit-limited)
- `duracion_tipo` — `DIAS`, `SEMANAS`, `MESES`, `AÑOS`
- `duracion_valor` — Duration amount (e.g., 1 for 1 month)
- `dias_acceso` — (Optional) Number of accesses/visits for `ACCESOS` mode; null for `CALENDARIO`
- `precio` — Price per unit (decimal as string)
- `activo` — Boolean; only active types are listed

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### POST /api/v1/tipos-membresia
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireAdminOrDueno()`  
**Description:** Crear un nuevo tipo de membresía para la compañía.

**Request body:**
```json
{
  "nombre": "Plan Trimestral",
  "modo_control": "CALENDARIO",
  "duracion_tipo": "MESES",
  "duracion_valor": 3,
  "dias_acceso": null,
  "precio": "140.00"
}
```

**Body fields:**
- `nombre` (required, string) — Membership type name
- `modo_control` (required, enum: `CALENDARIO` | `ACCESOS`) — Control mode
- `duracion_tipo` (required, enum: `DIAS` | `SEMANAS` | `MESES` | `AÑOS`) — Duration unit
- `duracion_valor` (required, integer > 0) — Duration amount
- `dias_acceso` (optional, integer > 0) — Required only if `modo_control` is `ACCESOS`
- `precio` (required, decimal > 0) — Price

**Response 201:**
```json
{
  "id": 3,
  "nombre": "Plan Trimestral",
  "modo_control": "CALENDARIO",
  "duracion_tipo": "MESES",
  "duracion_valor": 3,
  "dias_acceso": null,
  "precio": "140.00",
  "activo": true
}
```

**Errors:**
- `400` — invalid request (missing required fields, invalid enum values, precio <= 0)
- `401` — missing or invalid JWT
- `403` — insufficient permissions (admin/owner only)

---

### PUT /api/v1/tipos-membresia/{id}
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireAdminOrDueno()`  
**Description:** Actualizar nombre y precio de un tipo de membresía. No permite cambiar modo_control ni duracion.

**Path param:**
- `id` — Membership type ID

**Request body:**
```json
{
  "nombre": "Plan Trimestral Plus",
  "precio": "155.00"
}
```

**Body fields:**
- `nombre` (required, string) — Updated name
- `precio` (required, decimal > 0) — Updated price

**Response 200:**
```json
{
  "id": 3,
  "nombre": "Plan Trimestral Plus",
  "modo_control": "CALENDARIO",
  "duracion_tipo": "MESES",
  "duracion_valor": 3,
  "dias_acceso": null,
  "precio": "155.00",
  "activo": true
}
```

**Errors:**
- `400` — invalid request (empty nombre, invalid precio)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — membership type not found

---

### PUT /api/v1/tipos-membresia/{id}/desactivar
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireAdminOrDueno()`  
**Description:** Desactivar un tipo de membresía. Clientes con membresías existentes de este tipo siguen activos; solo previene que se vendan nuevas membresías de este tipo.

**Path param:**
- `id` — Membership type ID

**Response 200:** (no body)

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — membership type not found

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/tipos-membresia` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/tipos-membresia` | POST | `requireAdminOrDueno()` |
| `/tipos-membresia/{id}` | PUT | `requireAdminOrDueno()` |
| `/tipos-membresia/{id}/desactivar` | PUT | `requireAdminOrDueno()` |

---

## Modos de control

### CALENDARIO (calendar-based)
- Membresía válida por un período fijo (e.g., 30 días, 3 meses, 1 año)
- La fecha de fin se calcula: `fecha_inicio + duracion_tipo(duracion_valor)`
- No hay límite de visitas
- `dias_acceso` es null

### ACCESOS (access-based)
- Membresía válida por un número limitado de visitas
- Cada visita registrada en `asistencia.asistencias` decrementa el contador
- Expira en la fecha calculada O cuando se agotan los accesos (lo que ocurra primero)
- `dias_acceso` > 0

---

## Códigos de error comunes

| Código | Significado |
|--------|-------------|
| 400 | Datos de request inválidos |
| 401 | Token ausente o inválido |
| 403 | Sin permisos (admin/owner only) |
| 404 | Tipo de membresía no encontrado |
