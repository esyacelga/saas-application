# Administración API — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `AdminController`).

Diagnóstico y auditoría del servicio de facturación. Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff).

---

## Endpoints

### GET /api/v1/admin/sri/ping
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Verificar conectividad y latencia del endpoint SRI (pruebas o producción según config).

**Query params:** ninguno

**Response 200:**
```json
{
  "ambiente": "1",
  "url": "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl",
  "estado": "DISPONIBLE",
  "latencia_ms": 345,
  "timestamp": "2026-07-02T14:35:21.123456Z"
}
```

Si el SRI no responde en 10 segundos o devuelve error:

```json
{
  "ambiente": "1",
  "url": "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl",
  "estado": "NO_DISPONIBLE",
  "latencia_ms": 10001,
  "timestamp": "2026-07-02T14:35:21.123456Z"
}
```

**Errores:**
- `401` — no autenticado

---

### GET /api/v1/admin/certificado/estado
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Estado del certificado digital activo de la empresa. Incluye días restantes antes de vencimiento.

**Query params:** ninguno

**Response 200:**
```json
{
  "id_certificado": 5,
  "ruc": null,
  "fecha_vencimiento": "2027-12-15",
  "dias_restantes": 528,
  "estado": "VIGENTE"
}
```

Estados posibles de `estado`:
- `VIGENTE` — más de 30 días para vencer
- `POR_VENCER` — entre 0 y 30 días
- `VENCIDO` — fecha de vencimiento pasada

**Errores:**
- `401` — no autenticado
- `404` — certificado activo no encontrado para la empresa

---

### GET /api/v1/admin/auditoria
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Auditoría de emisión de comprobantes en un período. Retorna stream de registros (Flux).

**Query params:**
- `desde` — fecha inicio en formato ISO-8601 (ej: `2026-07-01`), obligatorio
- `hasta` — fecha fin en formato ISO-8601 (ej: `2026-07-31`), obligatorio
- `estado` — filtrar por estado del comprobante (opcional): `GENERADO`, `AUTORIZADO`, `ANULADO`, etc.

**Response 200:** stream de objetos `AuditoriaEmision`

Cada elemento:
```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "id_comprobante": 5,
  "tipo_comprobante": "01",
  "clave_acceso": "0207202601021001001000000001123456789X",
  "secuencial": "000000001",
  "fecha_emision": "2026-07-02",
  "estado": "AUTORIZADO",
  "total": 38.00,
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "fecha_autorizacion": "2026-07-02T14:35:00Z",
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Errores:**
- `401` — no autenticado
- `400` — parámetros `desde`/`hasta` con formato inválido
