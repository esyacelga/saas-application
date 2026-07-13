# Comprobantes API — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `ComprobanteController`).

Facturación electrónica SRI Ecuador. Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff).

---

## Endpoints

### POST /api/v1/comprobantes/facturas
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** Requiere autenticación  
**Description:** Emitir factura electrónica **y transmitirla inmediatamente al SRI** dentro del mismo request.

> **Nota (G5, Fase 0):** El servidor asigna automáticamente el siguiente `secuencial` disponible para la combinación (`id_compania`, `id_sucursal`, `cod_establecimiento`, `cod_punto_emision`, `tipo_comprobante = "01"`), reservándolo atómicamente contra `facturacion.secuenciales`. El campo `secuencial` en el request está **deprecated** y será eliminado en la próxima versión mayor. Si el cliente lo envía, el servidor lo ignora y registra un `WARN` en logs.

> **G2, Fase 1 — Transmisión inmediata (activo desde 2026-01-01):** El request **puede tardar hasta ~15 segundos** (default `sri.timeout.envio-seconds = 15`). Después de persistir el comprobante en estado `GENERADO`, el servidor dispara síncronamente el pipeline **firmar XML → enviar al SRI RECEPCION → consultar AUTORIZACION → generar RIDE**. El body de la respuesta 201 incluye el `estado` final:
>
> - `AUTORIZADO` — El SRI aceptó y autorizó el comprobante (happy path). `numero_autorizacion` viene poblado.
> - `DEVUELTO` — El SRI rechazó en RECEPCION. Se encoló para reintento con backoff `{1, 5, 15, 60, 240}` min.
> - `NO_AUTORIZADO` — El SRI rechazó en AUTORIZACION. Se encoló para reintento con backoff.
> - `ERROR` — Timeout de `sri.timeout.envio-seconds` o error de red. Se encoló para reintento **inmediato** (el scheduler lo procesa en su próxima pasada, dentro de 60 s).
> - `GENERADO` — Estado inicial. **Nunca debe verse** en la respuesta si la transmisión inmediata está activa; si aparece indica un bug.
>
> El core-service (u otro cliente) **debe** guardar el `id` del comprobante y considerar los estados `DEVUELTO`, `NO_AUTORIZADO` y `ERROR` como transitorios — el reintento asíncrono los llevará a `AUTORIZADO` sin intervención (salvo agotar `max_intentos = 5`). Ver [flows/sri-submission-retry.md](../flows/sri-submission-retry.md) para detalles.
>
> El servicio **siempre devuelve HTTP 201** cuando la factura queda persistida (aun cuando el SRI la rechace). Solo devuelve 5xx si falla el `INSERT` inicial.

**Request body:**
```json
{
  "id_sucursal": 1,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
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
- `id_sucursal`, `cod_establecimiento`, `cod_punto_emision`, `codigo_numerico` — obligatorios
- `secuencial` — **DEPRECATED (G5)**. Ignorado por el servidor. Será eliminado en la próxima versión mayor.
- `tipo_id_receptor`, `id_receptor`, `razon_social_receptor` — obligatorios
- `email_receptor`, `direccion_receptor`, `telefono_receptor` — opcionales
- `id_membresia`, `id_venta` — opcionales (origen del comprobante)
- `detalles` — array no vacío, cada detalle: `codigo_principal`, `descripcion`, `cantidad`, `precio_unitario` obligatorios; `codigo_auxiliar`, `descuento` opcionales
- `pagos` — array no vacío, cada pago: `forma_pago`, `total` obligatorios; `plazo`, `unidad_tiempo` opcionales

**Validaciones semánticas (G6, Fase 0):**
- `tipo_id_receptor` debe existir en el catálogo `sri.tipos_identificacion_comprador` (códigos vigentes: `04` RUC, `05` CEDULA, `06` PASAPORTE, `07` CONSUMIDOR_FINAL, `08` ID_EXTERIOR).
- Cada `pagos[].forma_pago` debe existir en el catálogo `sri.formas_pago` (códigos vigentes: `01`, `15`, `16`, `17`, `18`, `19`, `20`, `21`).

Los catálogos SRI se cargan en memoria al arranque del servicio; nuevas tarifas o formas de pago publicadas por el SRI requieren solo actualizar el seed (`09_insert_seed_sri.sql`) y reiniciar el servicio.

**Response 201 (happy path — G2 autorizado):**
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
  "estado": "AUTORIZADO",
  "fecha_autorizacion": "2026-07-02T14:30:12Z",
  "xml_firmado_path": null,
  "xml_autorizado_path": null,
  "ride_pdf_path": "blob://ride_...",
  "created_at": "2026-07-02T14:30:00Z"
}
```

**Response 201 (fallback — pipeline síncrono cayó por timeout):**
```json
{
  "id": 1,
  "estado": "ERROR",
  "numero_autorizacion": null,
  "fecha_autorizacion": null,
  "ride_pdf_path": null,
  "...": "resto de campos igual"
}
```
En este caso el reintento se dispara automáticamente en la próxima pasada del scheduler (dentro de 60 s). Consultar `GET /api/v1/comprobantes/{id}` para ver el estado final.

El campo `secuencial` en la respuesta viene siempre formateado a **9 dígitos con padding a la izquierda** (ej. `"000000042"`). Corresponde al valor reservado atómicamente desde `facturacion.secuenciales`.

**Errores:**
- `400` — campos requeridos faltantes o formato inválido (validación @NotNull, @NotBlank, @Pattern)
- `401` — no autenticado
- `404` — configuración SRI no encontrada para la empresa
- `422` — validación semántica contra catálogo SRI falla (G6):
  - `Tipo de identificación no reconocido: {codigo}` — el `tipo_id_receptor` no está en `sri.tipos_identificacion_comprador`.
  - `Forma de pago no reconocida: {codigo}` — alguna `forma_pago` no está en `sri.formas_pago`.

> **Los rechazos del SRI (DEVUELTO / NO_AUTORIZADO / timeout ERROR) NO se traducen a 4xx/5xx.** La respuesta sigue siendo 201 con el `estado` en el body, porque el comprobante quedó persistido y entrará al ciclo de reintentos.

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
