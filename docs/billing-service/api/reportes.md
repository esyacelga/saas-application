# Reportes API — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `ReporteController`).

Reportes y estadísticas de facturación electrónica. Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff). La empresa (`id_compania`) se toma del token JWT, no del path ni de los query params.

---

## Endpoints

### GET /api/v1/reportes/ats
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Genera el Anexo Transaccional Simplificado (ATS) mensual en XML según el formato SRI Ecuador. La respuesta es un archivo XML adjunto (`Content-Disposition: attachment`).

**Query params:**
- `anio` — año del período, obligatorio (ej: `2026`)
- `mes` — mes del período (1–12), obligatorio (ej: `7`)

**Response 200:**
- `Content-Type: application/xml`
- `Content-Disposition: attachment; filename="ATS_{anio}_{mes:02}.xml"` (ej: `ATS_2026_07.xml`)
- Body: XML ATS conforme al esquema del SRI, agregando comprobantes `AUTORIZADO` del período.

**Errores:**
- `401` — no autenticado
- `404` — configuración SRI no encontrada para la empresa

---

### GET /api/v1/reportes/resumen
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Resumen agregado de facturación en un período: totales por estado, subtotales sin IVA, IVA y total facturado.

**Query params:**
- `desde` — fecha inicio en formato ISO-8601 (ej: `2026-07-01`), obligatorio
- `hasta` — fecha fin en formato ISO-8601 (ej: `2026-07-31`), obligatorio

**Response 200:**
```json
{
  "desde": "2026-07-01",
  "hasta": "2026-07-31",
  "total_emitidos": 152,
  "total_autorizados": 148,
  "total_error": 4,
  "subtotal_sin_iva": 12450.00,
  "total_iva": 1494.00,
  "total_facturado": 13944.00,
  "por_estado": {
    "AUTORIZADO": 148,
    "GENERADO": 0,
    "ANULADO": 0,
    "ERROR": 4
  }
}
```

Campos:
- `total_emitidos` — comprobantes creados en el período (cualquier estado)
- `total_autorizados` — subconjunto en estado `AUTORIZADO`
- `total_error` — subconjunto en estado `ERROR`
- `subtotal_sin_iva`, `total_iva`, `total_facturado` — sumas monetarias sobre comprobantes autorizados
- `por_estado` — desglose por cada estado presente en el período (claves dinámicas)

**Errores:**
- `401` — no autenticado
- `400` — `desde` o `hasta` con formato inválido (no ISO-8601)
