# Integración core-service → billing-service — contrato propuesto

> **ESTADO:** 📋 **Propuesto — no implementado.** Este documento describe el contrato HTTP que `core-service` debe usar para emitir facturas a través de `billing-service` cuando se venda una membresía o registre otra transacción facturable. Los endpoints existen y funcionan en `billing-service`; falta el código en `core-service` que los consuma.

---

## Cuándo debe integrar core-service

`core-service` debe llamar a `billing-service` en los siguientes escenarios:

- **Al vender una membresía:** Después de `POST /api/v1/membresias` registrada exitosamente en `core.membresias`, si la empresa tiene `config_sri.facturacion_activa = true`, disparar la emisión de factura.
- **Al registrar una venta puntual:** Si existe un endpoint de ventas en `core-service`, idem (usando `id_venta`).
- **Patrón recomendado:** Fire-and-forget asíncrono. La venta/membresía NO debe fallar si `billing-service` está caído. Use una cola (outbox pattern o RabbitMQ) para garantizar reintentos sin bloquear la transacción original.

---

## Flujo de la llamada

```
core-service                              billing-service
    |                                           |
    | POST /api/v1/comprobantes/facturas        |
    | Authorization: Bearer <jwt-staff>         |
    | Content-Type: application/json            |
    |------------------------------------------>|
    |                                           |
    |                    crea Comprobante
    |                    estado=GENERADO
    |                    encola envío al SRI
    |                                           |
    |      201 Created + ComprobanteResponse     |
    |<------------------------------------------|
    |
    | guardar id_comprobante en core.membresias
    | o core.ventas (columna id_comprobante)
```

El comprobante entra en estado `GENERADO` inmediatamente. La firma XML, envío al SRI y autorización ocurren de forma asíncrona en `billing-service` (ver [flows/sri-submission-retry.md](../flows/sri-submission-retry.md)).

---

## Endpoint invocado

Referencia completa en [comprobantes.md — POST /api/v1/comprobantes/facturas](comprobantes.md#post-apiv1comprobantesfacturas).

**Contrato HTTP mínimo:**

```http
POST /api/v1/comprobantes/facturas HTTP/1.1
Host: billing-service:8086
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
```

**Campos obligatorios del body:**

- `id_sucursal` (Integer) — ID de la sucursal en `core.sucursales`. Billing valida que pertenezca a la empresa del JWT.
- `cod_establecimiento` (String, 3 dígitos) — Código SRI del establecimiento, ej. `"001"`.
- `cod_punto_emision` (String, 3 dígitos) — Código SRI del punto de emisión, ej. `"001"`.
- `secuencial` (String, 9 dígitos) — Número secuencial para este tipo de comprobante. **Core debe reservar atomicamente via lock en BD.**
- `codigo_numerico` (String, 9 dígitos) — Parte variable de la clave de acceso. **Recomendación:** generar determinísticamente (hash del `id_membresia` + fecha) para garantizar idempotencia en reintentos.
- `tipo_id_receptor`, `id_receptor`, `razon_social_receptor` — Del cliente (miembro).
- `detalles` (Array) — Líneas de la membresía/venta. Cada detalle: `codigo_principal`, `descripcion`, `cantidad`, `precio_unitario` (obligatorios); `descuento`, `codigo_auxiliar` (opcionales).
- `pagos` (Array) — Formas de pago. Cada pago: `forma_pago` (string SRI, ej. `"19"` tarjeta crédito), `total` (Decimal).

**Campos opcionales:**

- `id_membresia` (Integer) — Foreign key a `core.membresias(id)`. Permite a billing correlacionar el comprobante.
- `id_venta` (Integer) — Foreign key a `core.ventas(id)` o la tabla de ventas de otro servicio.
- `email_receptor`, `direccion_receptor`, `telefono_receptor` — Datos del cliente para el RIDE.

**Respuesta 201:**

Retorna `ComprobanteResponse` con:

```json
{
  "id": 1,
  "id_compania": 2,
  "id_sucursal": 1,
  "clave_acceso": "0207202601021001001000000001123456789X",
  "estado": "GENERADO",
  "created_at": "2026-07-02T14:30:00Z",
  "...": "..."
}
```

Guardar `id` (PK del comprobante) en la tabla origen (`core.membresias.id_comprobante` o `core.ventas.id_comprobante`).

---

## Autenticación y autorización

**JWT Bearer Token:**

El JWT debe cumplir:

- Claim `tipo = "staff"` — solo personal del gimnasio puede emitir facturas (rechaza tokens `tipo = "platform"`).
- Claim `id_compania` (Number) — debe coincidir con la empresa dueña de la sucursal. **Billing valida:** `config_sri.id_compania == JWT.id_compania`.
- Claim `id_persona` (Number) — identidad del usuario que emite (auditado en `created_by`).
- Claim `permisos` (Array de strings) — presente pero **no validado** por `billing-service` actualmente. No requiere permiso específico como `facturacion:emitir`.

**Compartir JWT con auth-service:**

El mismo `JWT_SECRET` Base64 se usa en `auth-service`, `core-service` y `billing-service` (via `.env`). `core-service` debe **reusar el token del staff** que originó la venta (propagarlo en el header `Authorization`). No re-emitir ni generar tokens nuevos — la auditoría de quién emitió la factura se hereda del flujo de core.

**Multi-tenancy:**

El token ya incluye `id_compania`. Billing extrae del contexto (`ReactiveSecurityContextHolder`) y valida que la sucursal pertenezca a esa compañía.

---

## Manejo de errores

| HTTP | Significado | Acción en core-service |
|------|-------------|----------------------|
| **201** | Comprobante creado en estado `GENERADO` | Éxito. Guardar `id_comprobante` en BD. |
| **400** | Payload inválido (campos faltantes, formato incorrecto) | Log de error. Devolver 500 al usuario final (bug de core-service). Revisar `secuencial`, `codigo_numerico`, `detalles`, `pagos`. |
| **401** | JWT inválido, expirado o ausente | Refrescar token con auth-service; no reintentar inmediatamente. |
| **404** | `config_sri` no encontrada para la empresa | La empresa no tiene facturación activa o la configuración SRI no está inicializada. Marcar la venta como "no facturada"; notificar admin. |
| **422** | Error de negocio SRI (p.ej. secuencial duplicado, cifras no coinciden) | Log detallado con el mensaje de `billing-service`. Marcar venta como "pendiente de facturar" e intentar manualmente después (o ver la sección de reintentos). |
| **5xx** | Billing service caído | No fallar la venta. Encolar para reintento posterior (patrón outbox). |

---

## Idempotencia

El `codigo_numerico` (9 dígitos) funciona como **idempotency key natural**:

- Si `core-service` reintenta `POST /api/v1/comprobantes/facturas` con el mismo `codigo_numerico` + `secuencial` + `cod_establecimiento` + `cod_punto_emision`, `billing-service` generará la misma clave de acceso (49 dígitos).
- **Recomendación:** generar `codigo_numerico` de forma determinística, p.ej. `codigo_numerico = hash(id_membresia || date).substring(0, 9)`.
- Evita duplicados si core reintenta tras un timeout HTTP.

---

## Consultas complementarias

Después de emitir la factura, `core-service` puede necesitar:

- **Consultar estado del comprobante:** `GET /api/v1/comprobantes/{id}` — retorna el comprobante con estado actual (`GENERADO`, `ENVIADO`, `AUTORIZADO`, `ERROR`, etc.).
- **Descargar RIDE PDF para el cliente:** `GET /api/v1/comprobantes/{id}/ride` — disponible cuando el SRI autoriza (estado `AUTORIZADO`). Enviar por email o guardar en blob.
- **Anular la factura:** `POST /api/v1/comprobantes/{id}/anular` — si el usuario cancela la membresía/venta. Solo funciona en estados `AUTORIZADO` o `GENERADO`.

Ver [comprobantes.md](comprobantes.md) para el contrato completo de estas operaciones.

---

## Flujo asíncrono — NO esperar autorización SRI

**Dato crítico:** La respuesta HTTP 201 significa que el comprobante se **generó e insertó** en BD (estado `GENERADO`). **El envío al SRI y la autorización ocurren fuera del request HTTP.**

1. `billing-service` devuelve 201 en <500ms.
2. Internamente, encola el comprobante en `facturacion.cola_envio` con `estado = 'PENDIENTE'`.
3. Un job scheduler (`RetrySchedulerService`) procesa la cola cada 60 segundos:
   - Firma XML con el certificado P12 (estado → `FIRMADO`).
   - Envía al SRI vía SOAP (estado → `ENVIADO`).
   - Si SRI acepta, consulta autorización (estado → `AUTORIZADO` o `NO_AUTORIZADO`).
   - Si falla, reintentos con backoff: 1, 5, 15, 60, 240 minutos.

**Implicación para core-service:**

- NO confiar en que el comprobante esté `AUTORIZADO` inmediatamente después del 201.
- Si necesita el RIDE PDF o número de autorización para enviar al cliente de inmediato, consultar posteriormente: `GET /api/v1/comprobantes/{id}` con reintentos.
- Alternativa: usar un webhook (no implementado hoy) que notifique a core cuando el estado cambie a `AUTORIZADO`.

---

## Checklist para implementación

- [ ] Cliente HTTP reactivo (`WebClient`) en `core-service`, apuntando a `${BILLING_SERVICE_URL}` (env var).
- [ ] Propagación del JWT del staff en header `Authorization: Bearer <token>`.
- [ ] Generación atómica del `secuencial` (ej. stored procedure + lock en `core.membresias` por sucursal).
- [ ] Generación determinística del `codigo_numerico` (hash o secuencia).
- [ ] Construcción del body según DTO `EmitirFacturaRequest` — todos los fields en **snake_case** en JSON (Jackson configura así).
- [ ] Guardar `id_comprobante` (respuesta 201) en la fila de origen (`core.membresias` o `core.ventas`).
- [ ] Manejo de códigos 400/404/422 sin fallar la operación original (venta/membresía).
- [ ] Reintentos con backoff exponencial para 5xx (usar patrón outbox o queue).
- [ ] (Opcional) Test de integración end-to-end: vender membresía en core → verificar que factura se creó en billing con estado `GENERADO`.

---

## Referencias cruzadas

- [comprobantes.md](comprobantes.md) — Contrato HTTP completo del endpoint.
- [flows/sri-submission-retry.md](../flows/sri-submission-retry.md) — Máquina de estados y lógica de reintentos.
- [../../gym-administrator/specs/billing-service.md](../../gym-administrator/specs/billing-service.md) — Especificación de dominio y arquitectura (nota: algunos paths están desalineados; usar este doc como referencia de negocio).
