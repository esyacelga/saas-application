# Comprobantes API — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `ComprobanteController`).

Facturación electrónica SRI Ecuador. Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff).

---

## Endpoints

### POST /api/v1/comprobantes/facturas
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** Requiere autenticación  
**Description:** Emitir factura electrónica en estado `GENERADO`.

**Request body:**
```json
{
  "id_sucursal": 1,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000001",
  "codigo_numerico": "123456789",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "direccion_receptor": "Av. Los Shyris N32-14",
  "telefono_receptor": "0991234567",
  "id_membresia": 123,
  "id_venta": null,
  "detalles": [
    {
      "codigo_principal": "MEM-MENSUAL",
      "codigo_auxiliar": null,
      "descripcion": "Membresía Mensual",
      "cantidad": 1.000000,
      "precio_unitario": 33.040000,
      "descuento": 0.00
    }
  ],
  "pagos": [
    {
      "forma_pago": "19",
      "total": 38.00,
      "plazo": null,
      "unidad_tiempo": null
    }
  ]
}
```

**Campo obligatorio/opcional:**
- `id_sucursal`, `cod_establecimiento`, `cod_punto_emision`, `secuencial`, `codigo_numerico` — obligatorios
- `tipo_id_receptor`, `id_receptor`, `razon_social_receptor` — obligatorios
- `email_receptor`, `direccion_receptor`, `telefono_receptor` — opcionales
- `id_membresia`, `id_venta` — opcionales (origen del comprobante)
- `detalles` — array no vacío, cada detalle: `codigo_principal`, `descripcion`, `cantidad`, `precio_unitario` obligatorios; `codigo_auxiliar`, `descuento` opcionales
- `pagos` — array no vacío, cada pago: `forma_pago`, `total` obligatorios; `plazo`, `unidad_tiempo` opcionales

**Response 201:**
```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "tipo_comprobante": "01",
  "clave_acceso": "0207202601021001001000000001123456789X",
  "numero_autorizacion": null,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000001",
  "fecha_emision": "2026-07-02",
  "ambiente": "1",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "subtotal_sin_impuesto": 33.04,
  "total_descuento": 0.00,
  "total_iva": 4.96,
  "total": 38.00,
  "moneda": "DOLAR",
  "estado": "GENERADO",
  "fecha_autorizacion": null,
  "xml_firmado_path": null,
  "xml_autorizado_path": null,
  "ride_pdf_path": null,
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Errores:**
- `400` — campos requeridos faltantes o formato inválido (validación @NotNull, @NotBlank, @Pattern)
- `401` — no autenticado
- `404` — configuración SRI no encontrada para la empresa

---

### GET /api/v1/comprobantes/{id}
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Obtener detalles de un comprobante por ID.

**Path param:** `id` — ID del comprobante en `facturacion.comprobantes`

**Response 200:**
```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "tipo_comprobante": "01",
  "clave_acceso": "0207202601021001001000000001123456789X",
  "numero_autorizacion": null,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000001",
  "fecha_emision": "2026-07-02",
  "ambiente": "1",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "subtotal_sin_impuesto": 33.04,
  "total_descuento": 0.00,
  "total_iva": 4.96,
  "total": 38.00,
  "moneda": "DOLAR",
  "estado": "GENERADO",
  "fecha_autorizacion": null,
  "xml_firmado_path": null,
  "xml_autorizado_path": null,
  "ride_pdf_path": null,
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado o no pertenece a la empresa autenticada

---

### GET /api/v1/comprobantes
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Listar comprobantes de la empresa con paginación y filtros.

**Query params:**
- `estado` — filtrar por estado (opcional): `GENERADO`, `FIRMADO`, `ENVIADO`, `RECIBIDO`, `AUTORIZADO`, `NO_AUTORIZADO`, `DEVUELTO`, `ANULADO`, `ERROR`
- `id_sucursal` — filtrar por sucursal (opcional)
- `page` — número de página (default: 1)
- `limit` — resultados por página (default: 20)

**Response 200:**
```json
{
  "total": 50,
  "pagina": 1,
  "datos": [
    {
      "id": 1,
      "id_compania": 2,
      "id_sucursal": 1,
      "tipo_comprobante": "01",
      "clave_acceso": "0207202601021001001000000001123456789X",
      "numero_autorizacion": null,
      "cod_establecimiento": "001",
      "cod_punto_emision": "001",
      "secuencial": "000000001",
      "fecha_emision": "2026-07-02",
      "ambiente": "1",
      "tipo_id_receptor": "05",
      "id_receptor": "1712345678",
      "razon_social_receptor": "Juan Carlos Pérez",
      "email_receptor": "juan@email.com",
      "subtotal_sin_impuesto": 33.04,
      "total_descuento": 0.00,
      "total_iva": 4.96,
      "total": 38.00,
      "moneda": "DOLAR",
      "estado": "GENERADO",
      "fecha_autorizacion": null,
      "xml_firmado_path": null,
      "xml_autorizado_path": null,
      "ride_pdf_path": null,
      "created_at": "2026-07-02T14:30:00Z"
    }
  ]
}
```

**Errores:**
- `401` — no autenticado

---

### POST /api/v1/comprobantes/{id}/enviar
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Enviar comprobante al SRI para autorización. Dispara proceso de firma y envío SOAP.

**Path param:** `id` — ID del comprobante  
**Query param:** `id_sucursal` — requerido para validación de propiedad

**Response 200:**
```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "tipo_comprobante": "01",
  "clave_acceso": "0207202601021001001000000001123456789X",
  "numero_autorizacion": null,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000001",
  "fecha_emision": "2026-07-02",
  "ambiente": "1",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "subtotal_sin_impuesto": 33.04,
  "total_descuento": 0.00,
  "total_iva": 4.96,
  "total": 38.00,
  "moneda": "DOLAR",
  "estado": "ENVIADO",
  "fecha_autorizacion": null,
  "xml_firmado_path": "blob://...",
  "xml_autorizado_path": null,
  "ride_pdf_path": null,
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado

---

### GET /api/v1/comprobantes/{id}/xml-firmado
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Descargar XML firmado del comprobante.

**Path param:** `id` — ID del comprobante

**Response 200:** archivo XML, `Content-Type: application/xml`, `Content-Disposition: attachment`

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado o XML firmado no disponible

---

### GET /api/v1/comprobantes/{id}/ride
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Descargar RIDE PDF del comprobante.

**Path param:** `id` — ID del comprobante

**Response 200:** archivo PDF, `Content-Type: application/pdf`, `Content-Disposition: attachment`

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado o RIDE PDF no disponible

---

### POST /api/v1/comprobantes/{id}/anular
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Anular comprobante. Solo permitido en estados `AUTORIZADO` o `GENERADO`.

> ⚠️ **Riesgo fiscal — anulación lógica local, NO ante SRI.** Este endpoint solo actualiza `estado = ANULADO` en la BD del gimnasio. **No** llama al SRI, **no** valida ventana temporal (día 7 del mes siguiente), **no** rechaza facturas a consumidor final, **no** requiere motivo y **no** genera nota de crédito. Su uso en producción puede generar divergencia con el SRI y sanciones tributarias. Ver [pendientes/anulacion-sri.md](../pendientes/anulacion-sri.md) para el rediseño necesario.

**Path param:** `id` — ID del comprobante

**Response 200:**
```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "tipo_comprobante": "01",
  "clave_acceso": "0207202601021001001000000001123456789X",
  "numero_autorizacion": "0207202601021001001000000001123456789",
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000001",
  "fecha_emision": "2026-07-02",
  "ambiente": "1",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "subtotal_sin_impuesto": 33.04,
  "total_descuento": 0.00,
  "total_iva": 4.96,
  "total": 38.00,
  "moneda": "DOLAR",
  "estado": "ANULADO",
  "fecha_autorizacion": "2026-07-02T14:35:00Z",
  "xml_firmado_path": "blob://...",
  "xml_autorizado_path": "blob://...",
  "ride_pdf_path": "blob://...",
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado
- `422` — estado del comprobante no permite anulación (solo `AUTORIZADO` y `GENERADO` son anulables)

---

### POST /api/v1/comprobantes/{id}/reenviar-email
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Reenviar RIDE PDF al email del receptor.

**Path param:** `id` — ID del comprobante

**Response 200:**
```json
{
  "mensaje": "Email enviado"
}
```

**Errores:**
- `401` — no autenticado
- `404` — comprobante no encontrado o RIDE PDF no disponible
