# Anulaciones API — billing-service

> **ESTADO:** ✅ **G3 · IMPLEMENTADO 2026-07-13**. Refleja el código actual (`AnulacionController`, `ComprobanteController.anularComprobante`, `MotivosAnulacionController`).

Anulación fiscal SRI Ecuador. Cubre los dos flujos oficiales (anulación directa y con nota de crédito) y la máquina de estados de la solicitud.

Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff con `id_compania` de la empresa dueña del comprobante). Los verbos de resolución (aprobar / rechazar / confirmar-sri) requieren rol `admin_compania`, `super_admin` o `Dueño` — cualquier otro rol responde `403 Forbidden`.

Ver también:
- [flows/anulacion-nc.md](../flows/anulacion-nc.md) — diagrama de estados + flujos A y B en detalle.
- [api/notas-credito.md](notas-credito.md) — API de NC (G4) que el Flujo B dispara internamente.

---

## Máquina de estados

```
SOLICITADA ─aprobar─→ APROBADA ─confirmar-sri (Flujo A)─→ EJECUTADA
    │                    │
    │                    └─NC AUTORIZADO (Flujo B)──────→ EJECUTADA
    │
    └─rechazar─→ RECHAZADA
```

En **Flujo B** (con NC), la aprobación dispara inmediatamente la emisión de la NC vía el pipeline síncrono G2:
- Si la NC llega a `AUTORIZADO` en el mismo request → la anulación queda `EJECUTADA` y el comprobante original pasa a `ANULADO`.
- Si la NC queda `DEVUELTO`, `NO_AUTORIZADO` o `ERROR` → la anulación queda `APROBADA` con `id_comprobante_nc` poblado, y el scheduler de G2 la reintentará. Cuando la NC autorice, la anulación transiciona a `EJECUTADA`.

---

## Endpoints

### POST /api/v1/comprobantes/{id}/anular
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Solicita la anulación fiscal del comprobante. Crea una fila en `facturacion.anulaciones` en estado `SOLICITADA`. **No** cambia el estado del comprobante — la anulación efectiva se aplica al ejecutar (confirmar-sri o NC autorizada).

**Reemplaza al viejo `POST /comprobantes/{id}/anular` sin body**, que hacía anulación lógica local sin validaciones fiscales. El endpoint anterior está **eliminado**.

**Validaciones (todas retornan `422 BusinessException`):**
1. `estado del comprobante ∈ {AUTORIZADO, GENERADO}`.
2. `id_receptor != '9999999999999'` (consumidor final no admite anulación en línea).
3. `fecha_emision` dentro de la ventana SRI — `hoy ≤ día 7 del mes siguiente`.
   - Ejemplo: emitida `2026-07-15` → anulable hasta `2026-08-07` inclusive.
4. `motivo` no vacío (obligatorio).
5. Si viene `codigo_motivo_anulacion`: debe existir en `sri.motivos_anulacion_nc` (si no → `404`).

**Path param:** `id` — ID del comprobante a anular.

**Request body:**
```json
{
  "motivo": "Cliente devolvió el pago con reversa bancaria",
  "codigo_motivo_anulacion": "DEVOLUCION",
  "generar_nota_credito": false
}
```

- `motivo` — **obligatorio**, 5 a 500 caracteres.
- `codigo_motivo_anulacion` — opcional para Flujo A; **obligatorio para Flujo B** (si `generar_nota_credito=true` sin motivo válido, la aprobación fallará con 422). Valores oficiales: `DEVOLUCION`, `DESCUENTO`, `ANULACION`, `ERROR_PRECIO`, `ERROR_CALIDAD` (consultar `GET /sri/motivos-anulacion`).
- `generar_nota_credito` — opcional, default `false`. Marca la solicitud como Flujo B.

**Response 201:**
```json
{
  "id": 42,
  "id_compania": 2,
  "id_sucursal": 1,
  "id_comprobante": 152,
  "motivo": "Cliente devolvió el pago con reversa bancaria",
  "estado": "SOLICITADA",
  "id_comprobante_nc": null,
  "id_usuario_solicita": 999,
  "id_usuario_aprueba": null,
  "fecha_solicitud": "2026-07-13T18:30:00Z",
  "fecha_resolucion": null,
  "observacion_resolucion": null,
  "link_resource": "/api/v1/anulaciones/42"
}
```

**Errores:**
- `400` — body inválido (motivo vacío, largo fuera de rango).
- `401` — no autenticado.
- `403` — usuario no es staff.
- `404` — comprobante no encontrado (o pertenece a otra compañía) o motivo no reconocido.
- `422` — reglas fiscales violadas (ver validaciones arriba).

---

### GET /api/v1/comprobantes/{id}/anulaciones
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Historial completo de solicitudes de anulación del comprobante (ordenado por `fecha_solicitud` desc).

**Path param:** `id` — ID del comprobante.

**Response 200:**
```json
{
  "id_comprobante": 152,
  "total": 2,
  "datos": [
    {
      "id": 43,
      "estado": "RECHAZADA",
      "motivo": "Prueba interna",
      "fecha_solicitud": "2026-07-14T09:15:00Z",
      "fecha_resolucion": "2026-07-14T10:00:00Z",
      "observacion_resolucion": "No procede — cliente ya utilizó el servicio",
      "...": "resto de campos igual que AnulacionResponse"
    },
    {
      "id": 42,
      "estado": "EJECUTADA",
      "motivo": "Cliente devolvió el pago",
      "fecha_solicitud": "2026-07-13T18:30:00Z"
    }
  ]
}
```

---

### POST /api/v1/anulaciones/{id}/aprobar
**Auth:** Bearer JWT (`tipo: staff`, roles: `admin_compania` / `super_admin` / `Dueño`)
**Description:** Transiciona `SOLICITADA → APROBADA`. Si la solicitud pidió NC dispara la emisión y puede llegar directo a `EJECUTADA` si el SRI autoriza en el mismo request.

**Path param:** `id` — ID de la anulación (`facturacion.anulaciones.id`).

**Request body (opcional):**
```json
{
  "observacion": "Aprobado por gerencia"
}
```

**Response 200:** ver `AnulacionResponse`. Para Flujo B con NC autorizada, `estado = "EJECUTADA"` y `id_comprobante_nc` poblado.

**Errores:**
- `401` — no autenticado.
- `403` — rol no autorizado.
- `404` — anulación no encontrada (o de otra compañía).
- `422` — transición inválida (estado ≠ SOLICITADA) o Flujo B sin `codigo_motivo_anulacion`.

---

### POST /api/v1/anulaciones/{id}/rechazar
**Auth:** Bearer JWT (`tipo: staff`, roles: `admin_compania` / `super_admin` / `Dueño`)
**Description:** Transiciona `SOLICITADA → RECHAZADA`. Observación obligatoria (auditoría interna).

**Request body:**
```json
{
  "observacion": "No procede — cliente ya utilizó el servicio"
}
```

**Response 200:** ver `AnulacionResponse` con `estado = "RECHAZADA"`.

**Errores:**
- `400` — observación en blanco.
- `401`, `403`, `404` — igual que aprobar.
- `422` — estado ≠ SOLICITADA.

---

### POST /api/v1/anulaciones/{id}/confirmar-sri
**Auth:** Bearer JWT (`tipo: staff`, roles: `admin_compania` / `super_admin` / `Dueño`)
**Description:** **Solo Flujo A.** El admin ejecutó manualmente la anulación en el portal SRI y confirma para reflejar el estado en el sistema. Transiciona `APROBADA → EJECUTADA` y marca el comprobante original como `ANULADO`.

**Path param:** `id` — ID de la anulación.

**Body:** sin body.

**Response 200:** ver `AnulacionResponse` con `estado = "EJECUTADA"`.

**Errores:**
- `401`, `403`, `404` — igual que aprobar.
- `422` — estado ≠ APROBADA.

---

### GET /api/v1/anulaciones/{id}
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Detalle de una solicitud por ID (validando multi-tenancy).

**Response 200:** ver `AnulacionResponse`.

**Errores:**
- `401` — no autenticado.
- `404` — no encontrada o de otra compañía.

---

### GET /api/v1/anulaciones
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Listado paginado de solicitudes de la empresa.

**Query params:**
- `estado` — filtrar por estado (`SOLICITADA`, `APROBADA`, `RECHAZADA`, `EJECUTADA`).
- `idSucursal` — filtrar por sucursal.
- `idComprobante` — filtrar por comprobante.
- `page` — default `1`.
- `limit` — default `20`.

**Response 200:**
```json
{
  "total": 12,
  "pagina": 1,
  "datos": [ { "...": "AnulacionResponse" } ]
}
```

---

### GET /api/v1/sri/motivos-anulacion
**Auth:** Bearer JWT (`tipo: staff`)
**Description:** Lista los motivos oficiales SRI para NC (catálogo `sri.motivos_anulacion_nc`). Se consume desde el frontend para poblar el dropdown de motivos.

**Response 200:**
```json
[
  { "id": 1, "codigo": "DEVOLUCION", "descripcion": "Devolución de mercadería" },
  { "id": 2, "codigo": "DESCUENTO", "descripcion": "Descuento comercial" },
  { "id": 3, "codigo": "ANULACION", "descripcion": "Anulación de factura" },
  { "id": 4, "codigo": "ERROR_PRECIO", "descripcion": "Error en precio" },
  { "id": 5, "codigo": "ERROR_CALIDAD", "descripcion": "Diferencia de calidad" }
]
```

---

## AnulacionResponse — schema completo

```json
{
  "id": 42,
  "id_compania": 2,
  "id_sucursal": 1,
  "id_comprobante": 152,
  "motivo": "Cliente devolvió el pago con reversa bancaria",
  "estado": "APROBADA",
  "id_comprobante_nc": 999,
  "id_usuario_solicita": 501,
  "id_usuario_aprueba": 502,
  "fecha_solicitud": "2026-07-13T18:30:00Z",
  "fecha_resolucion": "2026-07-13T19:00:00Z",
  "observacion_resolucion": "Aprobado por gerencia",
  "link_resource": "/api/v1/anulaciones/42"
}
```

**Notas:**
- `estado` es uno de: `SOLICITADA`, `APROBADA`, `RECHAZADA`, `EJECUTADA`.
- `id_comprobante_nc` es no-nulo solo para Flujo B (una vez emitida la NC).
- `observacion_resolucion` **no** incluye la metadata interna (`[FLUJO_B]`, `[MOTIVO=...]`) que el servicio usa para persistir el flag y el código motivo. Ese detalle es transparente al cliente.
