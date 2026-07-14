# Reportes Financieros API

> **ESTADO:** ✅ Refleja el código actual (verificado contra `ReporteController`).

Reportes de análisis financiero: resumen de período, desglose mensual y proyección de ingresos. Todos los cálculos usan agregaciones directas en BD (sumas, agrupaciones) vía `DatabaseClient`.

Base path: `/api/v1/finanzas/reporte`  
Service: finance-service (port 8085)

---

## Endpoints

### GET /api/v1/finanzas/reporte/resumen

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:exportar` OR `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** Summary report for a date range: total incomes, total expenses, profit, margin, and breakdown by category.

**Query parameters:**
- `desde` (required, date `YYYY-MM-DD`) — Start date (inclusive)
- `hasta` (required, date `YYYY-MM-DD`) — End date (inclusive)

**Response 200:**
```json
{
  "periodo": {
    "desde": "2026-07-01",
    "hasta": "2026-07-31"
  },
  "total_ingresos": "15850.50",
  "total_egresos": "8420.75",
  "utilidad": "7429.75",
  "margen": 46.85,
  "ingresos_por_categoria": [
    {
      "categoria": "Membresías",
      "monto": "12500.00",
      "porcentaje": 78.92
    },
    {
      "categoria": "Clases particulares",
      "monto": "3350.50",
      "porcentaje": 21.08
    }
  ],
  "egresos_por_categoria": [
    {
      "categoria": "Suministros",
      "monto": "4200.00",
      "porcentaje": 49.88
    },
    {
      "categoria": "Servicios externos",
      "monto": "4220.75",
      "porcentaje": 50.12
    }
  ]
}
```

**Errors:**
- `400` — invalid date format or date range (desde > hasta)
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### GET /api/v1/finanzas/reporte/mensual

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:exportar` OR `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** Monthly breakdown for a given year: incomes, expenses, and profit for each month.

**Query parameters:**
- `anio` (optional, integer) — Year to report on. Defaults to current year if not provided

**Response 200:**
```json
{
  "anio": 2026,
  "meses": [
    {
      "mes": "enero",
      "ingresos": "12500.00",
      "egresos": "5200.00",
      "utilidad": "7300.00"
    },
    {
      "mes": "febrero",
      "ingresos": "15850.50",
      "egresos": "8420.75",
      "utilidad": "7429.75"
    }
  ]
}
```

**Errors:**
- `400` — invalid year parameter
- `401` — missing or invalid JWT
- `403` — insufficient permissions

---

### GET /api/v1/finanzas/reporte/proyeccion

**Auth:** Bearer JWT (`tipo: staff | plataforma`)  
**Permission:** `finanzas:exportar` OR `finanzas:leer` OR `isDueno()` OR `isPlataforma()`  
**Description:** Income projection for the next month based on the average of the last N months (default N=3, configurable via `finance.proyeccion-meses-base` application property).

**Query parameters:** (none)

**Response 200:**
```json
{
  "mes_proyectado": "agosto-2026",
  "ingresos_estimados": "14800.75",
  "base_calculo": "promedio de últimos 3 meses"
}
```

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions
