# Ingresos API

> **ESTADO:** ✅ Refleja el código actual (verificado contra `IngresoController`).

Registro y consulta de ingresos financieros. **IMPORTANTE: Ingresos son inmutables (RN-01)** — una vez registrados, no pueden ser modificados ni eliminados. No hay endpoints PUT ni DELETE para ingresos.

Base path: `/api/v1/finanzas/ingresos`  
Service: finance-service (port 8085)

---

## Endpoints

### GET /api/v1/finanzas/ingresos

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** List income records for the authenticated company with pagination and optional date/category filtering.

**Query parameters:**
- `desde` (optional, date `YYYY-MM-DD`) — Filter records from this date (inclusive)
- `hasta` (optional, date `YYYY-MM-DD`) — Filter records up to this date (inclusive)
- `idCategoria` (optional, integer) — Filter by income category ID
- `page` (optional, integer, default `1`) — Page number (1-based)
- `limit` (optional, integer, default `50`) — Records per page

**Response 200:**
```json
{
  "total_periodo": "15850.50",
  "total_registros": 47,
  "datos": [
    {
      "id": 101,
      "categoria": "Membresías",
      "monto": "100.00",
      "descripcion": "Membresía mensual - Juan Pérez",
      "fecha": "2026-07-10",
      "origen": "membresia",
      "id_referencia": 5
    },
    {
      "id": 102,
      "categoria": "Clases particulares",
      "monto": "50.00",
      "descripcion": "Clase particular - Personal training",
      "fecha": "2026-07-10",
      "origen": "venta",
      "id_referencia": 12
    }
  ]
}
```

**Response fields:**
- `total_periodo` (decimal) — Sum of all `monto` in the filtered period
- `total_registros` (integer) — Total number of records matching the filter (across all pages)
- `datos` (array) — Current page of records
  - `id` — Income record ID
  - `categoria` — Category name (denormalized for convenience)
  - `monto` — Amount (decimal as string)
  - `descripcion` — Free text description
  - `fecha` — Record date (ISO format `YYYY-MM-DD`)
  - `origen` — Origin type: `membresia` or `venta`
  - `id_referencia` — ID of the membership or sale this income came from (null if not from either)

**Errors:**
- `400` — invalid date format or page/limit parameters
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### POST /api/v1/finanzas/ingresos

**Auth:** Bearer JWT (`tipo: staff | plataforma | recepcion`)  
**Permission:** `finanzas:crear` OR `isDueno()` OR `isRecepcion()` OR `isPlataforma()`  
**Description:** Register a new income record. **Immutable (RN-01)** — cannot be modified or deleted after creation. The `recepcion` role can also register incomes (special permission).

**Request body:**
```json
{
  "id_categoria": 1,
  "monto": "125.50",
  "descripcion": "Membresía mensual - Juan Pérez",
  "fecha": "2026-07-10",
  "id_membresia": 5,
  "id_venta": null,
  "id_sucursal": 1
}
```

**Body fields:**
- `id_categoria` (required, integer) — ID of the income category
- `monto` (required, decimal >= 0.01) — Income amount
- `descripcion` (optional, string) — Free text description
- `fecha` (optional, date `YYYY-MM-DD`) — Record date. Defaults to today if not provided
- `id_membresia` (optional, integer) — ID of the membership this income came from (for traceability)
- `id_venta` (optional, integer) — ID of the sale this income came from (for traceability)
- `id_sucursal` (optional, integer) — Branch ID. Defaults to principal's `id_sucursal` from JWT, or `1` if not present (RN-05)

**Response 201:**
```json
{
  "id": 101,
  "id_compania": 2,
  "id_sucursal": 1,
  "id_categoria": 1,
  "id_membresia": 5,
  "id_venta": null,
  "monto": "125.50",
  "descripcion": "Membresía mensual - Juan Pérez",
  "fecha": "2026-07-10",
  "id_usuario_registro": 42,
  "eliminado": false,
  "creacion_fecha": "2026-07-10T15:30:45Z",
  "creacion_usuario": "staff1",
  "modifica_fecha": "2026-07-10T15:30:45Z",
  "modifica_usuario": "staff1"
}
```

**Errors:**
- `400` — validation error (missing category ID, negative amount, invalid date format, etc.)
- `401` — missing or invalid JWT
- `403` — insufficient permissions (not `finanzas:crear`, `dueno`, `recepcion`, or `plataforma`)
- `422` — business rule violation (category not found, category inactive, etc.)

> **Note (RN-01):** Ingresos cannot be modified or deleted via API. Once registered, they are permanent records.
>
> **Note (RN-02):** Only records with `eliminado = false` are returned by GET endpoints and counted in reports.
