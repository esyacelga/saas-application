# Egresos API

> **ESTADO:** ✅ Refleja el código actual (verificado contra `EgresoController`).

Registro y consulta de egresos financieros. A diferencia de ingresos, egresos pueden ser eliminados lógicamente (soft-delete) pero esta operación no se expone en la API — se maneja internamente.

Base path: `/api/v1/finanzas/egresos`  
Service: finance-service (port 8085)

---

## Endpoints

### GET /api/v1/finanzas/egresos

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** List expense records for the authenticated company with pagination and optional date/category filtering.

**Query parameters:**
- `desde` (optional, date `YYYY-MM-DD`) — Filter records from this date (inclusive)
- `hasta` (optional, date `YYYY-MM-DD`) — Filter records up to this date (inclusive)
- `idCategoria` (optional, integer) — Filter by expense category ID
- `page` (optional, integer, default `1`) — Page number (1-based)
- `limit` (optional, integer, default `50`) — Records per page

**Response 200:**
```json
{
  "total_periodo": "8420.75",
  "total_registros": 23,
  "datos": [
    {
      "id": 201,
      "categoria": "Suministros",
      "monto": "350.00",
      "descripcion": "Compra de mancuernas y discos",
      "fecha": "2026-07-10"
    },
    {
      "id": 202,
      "categoria": "Servicios externos",
      "monto": "150.00",
      "descripcion": "Mantenimiento de aire acondicionado",
      "fecha": "2026-07-09"
    }
  ]
}
```

**Response fields:**
- `total_periodo` (decimal) — Sum of all `monto` in the filtered period
- `total_registros` (integer) — Total number of records matching the filter (across all pages)
- `datos` (array) — Current page of records
  - `id` — Expense record ID
  - `categoria` — Category name (denormalized for convenience)
  - `monto` — Amount (decimal as string)
  - `descripcion` — Free text description
  - `fecha` — Record date (ISO format `YYYY-MM-DD`)

**Errors:**
- `400` — invalid date format or page/limit parameters
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### POST /api/v1/finanzas/egresos

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:crear` OR `isDueno()` OR `isPlataforma()`  
**Description:** Register a new expense record.

**Request body:**
```json
{
  "id_categoria": 10,
  "monto": "275.50",
  "descripcion": "Compra de toallas de gimnasio",
  "fecha": "2026-07-10",
  "id_sucursal": 1
}
```

**Body fields:**
- `id_categoria` (required, integer) — ID of the expense category
- `monto` (required, decimal >= 0.01) — Expense amount
- `descripcion` (optional, string) — Free text description
- `fecha` (optional, date `YYYY-MM-DD`) — Record date. Defaults to today if not provided
- `id_sucursal` (optional, integer) — Branch ID. Defaults to principal's `id_sucursal` from JWT, or `1` if not present (RN-05)

**Response 201:**
```json
{
  "id": 201,
  "id_compania": 2,
  "id_sucursal": 1,
  "id_categoria": 10,
  "monto": "275.50",
  "descripcion": "Compra de toallas de gimnasio",
  "fecha": "2026-07-10",
  "id_usuario_registro": 42,
  "eliminado": false,
  "creacion_fecha": "2026-07-10T16:45:20Z",
  "creacion_usuario": "staff2",
  "modifica_fecha": "2026-07-10T16:45:20Z",
  "modifica_usuario": "staff2"
}
```

**Errors:**
- `400` — validation error (missing category ID, negative amount, invalid date format, etc.)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `422` — business rule violation (category not found, category inactive, etc.)

> **Note (RN-02):** Egresos can be soft-deleted (marked with `eliminado = true`) but this deletion is not exposed via API — it happens internally. Only records with `eliminado = false` are returned by GET endpoints and counted in reports.
