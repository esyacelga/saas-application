# Categorías de Egreso API

> **ESTADO:** ✅ Refleja el código actual (verificado contra `CategoriaEgresoController`).

Catálogo de categorías para clasificar egresos financieros. Permite crear, listar y desactivar categorías por compañía y sucursal.

Base path: `/api/v1/finanzas/categorias-egreso`  
Service: finance-service (port 8085)

---

## Endpoints

### GET /api/v1/finanzas/categorias-egreso

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** List expense categories for the authenticated company and optional branch.

**Query parameters:**
- `idSucursal` (optional, integer) — Filter by branch ID. If not provided, lists all categories for all branches.

**Response 200:**
```json
[
  {
    "id": 10,
    "id_compania": 2,
    "id_sucursal": 1,
    "nombre": "Suministros",
    "activo": true,
    "eliminado": false,
    "creacion_fecha": "2026-01-15T10:30:00Z",
    "creacion_usuario": "admin",
    "modifica_fecha": "2026-01-15T10:30:00Z",
    "modifica_usuario": "admin"
  },
  {
    "id": 11,
    "id_compania": 2,
    "id_sucursal": 1,
    "nombre": "Servicios externos",
    "activo": true,
    "eliminado": false,
    "creacion_fecha": "2026-02-01T08:00:00Z",
    "creacion_usuario": "staff1",
    "modifica_fecha": "2026-02-01T08:00:00Z",
    "modifica_usuario": "staff1"
  }
]
```

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### POST /api/v1/finanzas/categorias-egreso

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:crear` OR `isDueno()` OR `isPlataforma()`  
**Description:** Create a new expense category for the authenticated company.

**Request body:**
```json
{
  "nombre": "Mantenimiento equipo"
}
```

**Body fields:**
- `nombre` (required, string, max 100 chars) — Category name

**Response 201:**
```json
{
  "id": 12,
  "id_compania": 2,
  "id_sucursal": 1,
  "nombre": "Mantenimiento equipo",
  "activo": true,
  "eliminado": false,
  "creacion_fecha": "2026-03-10T14:45:00Z",
  "creacion_usuario": "admin",
  "modifica_fecha": "2026-03-10T14:45:00Z",
  "modifica_usuario": "admin"
}
```

> **Note (RN-05):** The `id_sucursal` is automatically set to the principal's `id_sucursal` from JWT, or defaults to `1` if not present in JWT.

**Errors:**
- `400` — validation error (empty name, exceeds max length)
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### PUT /api/v1/finanzas/categorias-egreso/{id}/desactivar

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:crear` OR `isDueno()` OR `isPlataforma()`  
**Description:** Deactivate an expense category. **Only possible if no active expense records reference it** (RN-04).

**Path parameters:**
- `id` (required, integer) — Category ID

**Request body:** (empty)

**Response 200:**
```json
{
  "id": 10,
  "id_compania": 2,
  "id_sucursal": 1,
  "nombre": "Suministros",
  "activo": false,
  "eliminado": false,
  "creacion_fecha": "2026-01-15T10:30:00Z",
  "creacion_usuario": "admin",
  "modifica_fecha": "2026-07-10T16:20:00Z",
  "modifica_usuario": "staff2"
}
```

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — category not found or belongs to a different company
- `409` — cannot deactivate: active expense records still reference this category (RN-04)
