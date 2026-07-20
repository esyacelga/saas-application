# Integración core-service → billing-service — contrato propuesto

> **ESTADO:** 📋 **Propuesto — no implementado en core-service.** Este documento describe el contrato HTTP que `core-service` debe usar para emitir facturas a través de `billing-service` cuando se venda una membresía o registre otra transacción facturable. Los endpoints existen y funcionan en `billing-service`; falta el código en `core-service` que los consuma.
>
> ⚠️ **Nota post-Fase 1 G2 (2026-07-13):** las secciones históricas de este doc describen la emisión como asíncrona (POST retorna `GENERADO` y luego el estado avanza vía scheduler). Con G2 el pipeline es **síncrono** dentro del POST: la respuesta llega con el estado final (`AUTORIZADO` en el caso feliz, `DEVUELTO`/`ERROR` si no) tras firma + envío + autorización a `celcer.sri.gob.ec`, con timeout configurable (`sri.timeout.envio-seconds`, default 15). La cola de reintentos solo actúa como fallback ante error de red o timeout, no como camino principal. El campo `secuencial` del request quedó `@Deprecated` en Fase 0 G5 (el servidor lo asigna). Actualizar este doc cuando se implemente la integración real en core-service.

---

## Cuándo debe integrar core-service

`core-service` debe llamar a `billing-service` en los siguientes escenarios:

- **Al vender una membresía:** Después de `POST /api/v1/membresias` registrada exitosamente en `core.membresias`, si la empresa tiene `config_sri.facturacion_activa = true`, disparar la emisión de factura.
- **Al registrar una venta puntual:** Si existe un endpoint de ventas en `core-service`, idem (usando `id_venta`).
- **Patrón recomendado:** Fire-and-forget asíncrono. La venta/membresía NO debe fallar si `billing-service` está caído. Use una cola (outbox pattern o RabbitMQ) para garantizar reintentos sin bloquear la transacción original.

---

## Flujo de la llamada

```
core-service                              billing-service                   SRI
    |                                           |                            |
    | POST /api/v1/comprobantes/facturas        |                            |
    | Authorization: Bearer <jwt-staff>         |                            |
    | Content-Type: application/json            |                            |
    |------------------------------------------>|                            |
    |                                           |                            |
    |                    crea Comprobante (GENERADO)                         |
    |                                           |                            |
    |                    [G2 · transmisión inmediata, timeout 15 s]          |
    |                    firmar → RECEPCION → AUTORIZACION                   |
    |                                           |--------------------------->|
    |                                           |<---------------------------|
    |                                           |                            |
    |      201 Created + ComprobanteResponse (estado=AUTORIZADO / DEVUELTO / |
    |                                          NO_AUTORIZADO / ERROR)         |
    |<------------------------------------------|                            |
    |
    | guardar id_comprobante en core.membresias
    | o core.ventas (columna id_comprobante)
```

Con G2 (Fase 1) el comprobante llega a un estado terminal (`AUTORIZADO`) o transitorio (`DEVUELTO`, `NO_AUTORIZADO`, `ERROR`) **dentro del mismo POST**. Si el pipeline síncrono falla o cae por timeout, la firma XML, envío al SRI y autorización se reintentan asíncronamente vía `facturacion.cola_envio` (ver [flows/sri-submission-retry.md](../../billing-service/flows/sri-submission-retry.md)).

---

## Endpoint invocado

Referencia completa en [comprobantes.md — POST /api/v1/comprobantes/facturas](../../billing-service/api/comprobantes.md#post-apiv1comprobantesfacturas).

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
- `codigo_numerico` (String, 9 dígitos) — Parte variable de la clave de acceso. **Recomendación:** generar determinísticamente (hash del `id_membresia` + fecha) para garantizar idempotencia en reintentos.
- `tipo_id_receptor`, `id_receptor`, `razon_social_receptor` — Del cliente (miembro).
- `detalles` (Array) — Líneas de la membresía/venta. Cada detalle: `codigo_principal`, `descripcion`, `cantidad`, `precio_unitario` (obligatorios); `descuento`, `codigo_auxiliar` (opcionales).
- `pagos` (Array) — Formas de pago. Cada pago: `forma_pago` (string SRI, ej. `"19"` tarjeta crédito), `total` (Decimal).

**Campos deprecated:**

- `secuencial` (String, 9 dígitos) — **DEPRECATED desde G5.** Ver la sección [Breaking change — G5 (reserva atómica del secuencial)](#breaking-change--g5-reserva-atómica-del-secuencial) más abajo.

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

Ver [comprobantes.md](../../billing-service/api/comprobantes.md) para el contrato completo de estas operaciones.

---

## G2 · Transmisión inmediata (Fase 1, activo desde 2026-01-01)

**Dato crítico:** El pipeline **firmar XML → enviar al SRI RECEPCION → consultar AUTORIZACION → generar RIDE** se ejecuta **síncronamente dentro del POST** con un `timeout` configurable (default `sri.timeout.envio-seconds = 15`). El comprobante llega a un estado terminal (`AUTORIZADO`) o transitorio (`DEVUELTO` / `NO_AUTORIZADO` / `ERROR`) antes de responder.

### Timeline esperado

| Fase | Tiempo típico | Notas |
|------|---------------|-------|
| Validación de catálogos + reserva secuencial + INSERT | <200 ms | En memoria + una escritura R2DBC. |
| Firma XAdES-BES local | <500 ms | Requiere P12 desencriptado en memoria. |
| RECEPCION SOAP al SRI | 1–5 s | Depende del SRI. |
| AUTORIZACION SOAP al SRI | 1–5 s | Depende del SRI. |
| Generación de RIDE PDF | <200 ms | OpenPDF, sincrónico. |
| **Total del POST** | **3–10 s (p95)** | **Timeout duro a 15 s.** |

### Qué esperar en el response

- **Happy path (`estado = AUTORIZADO`):** el `numero_autorizacion`, `fecha_autorizacion` y `ride_pdf_path` vienen poblados. La factura está lista para entregar al cliente.
- **`estado = DEVUELTO` o `NO_AUTORIZADO`:** el SRI rechazó por datos (RUC inválido, secuencial repetido, etc.). El billing-service ya encoló un reintento con backoff `{1, 5, 15, 60, 240}` min. **Core-service debe considerar la venta como emitida** — el reintento la llevará a `AUTORIZADO` en la mayoría de casos.
- **`estado = ERROR`:** timeout de 15 s o error de red. La cola se creó con `proxima_ejecucion = now()` para que el scheduler la agarre en <60 s. **Core-service debe considerar la venta como emitida** y consultar el estado más tarde.
- **HTTP siempre 201** cuando la factura queda persistida. Solo se devuelve 5xx si falla el `INSERT` inicial.

### Implicación para core-service

- NO fallar la venta si el estado es `DEVUELTO`, `NO_AUTORIZADO` o `ERROR` — todos son transitorios.
- Si necesita el RIDE PDF o `numero_autorizacion` para enviar al cliente y no vino en la respuesta 201, hacer polling a `GET /api/v1/comprobantes/{id}` (p.ej. cada 30 s durante 5 min).
- Alternativa futura: webhook (no implementado hoy) que notifique cuando el estado cambie a `AUTORIZADO`.
- **Configurar timeouts del `WebClient`** en core-service para **al menos 20 s** al llamar a `POST /api/v1/comprobantes/facturas` (deja margen para los 15 s del pipeline + red).

---

## Checklist para implementación

- [ ] Cliente HTTP reactivo (`WebClient`) en `core-service`, apuntando a `${BILLING_SERVICE_URL}` (env var).
- [ ] Propagación del JWT del staff en header `Authorization: Bearer <token>`.
- [ ] **No** reservar el `secuencial` en `core-service`: desde **G5** lo asigna `billing-service` (ver breaking change abajo).
- [ ] Generación determinística del `codigo_numerico` (hash o secuencia).
- [ ] Construcción del body según DTO `EmitirFacturaRequest` — todos los fields en **snake_case** en JSON (Jackson configura así).
- [ ] Guardar `id_comprobante` (respuesta 201) en la fila de origen (`core.membresias` o `core.ventas`).
- [ ] Manejo de códigos 400/404/422 sin fallar la operación original (venta/membresía).
- [ ] Reintentos con backoff exponencial para 5xx (usar patrón outbox o queue).
- [ ] (Opcional) Test de integración end-to-end: vender membresía en core → verificar que factura se creó en billing con estado `GENERADO`.

---

## Breaking change — G5 (reserva atómica del secuencial)

**Cuándo:** Fase 0 del [roadmap SRI 2026](../../billing-service/pendientes/roadmap-sri-2026.md).

**Qué cambia:**

- `billing-service` asigna el `secuencial` reservándolo atómicamente contra la tabla `facturacion.secuenciales` (`INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING`). Esto elimina el riesgo de dos requests concurrentes con el mismo secuencial y claves de acceso duplicadas ante el SRI.
- `core-service` **debe dejar de enviar** el campo `secuencial` en el body de `POST /api/v1/comprobantes/facturas`.
- La combinación de reserva es `(id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, tipo_comprobante)`; por tipo `"01"` (factura) el contador arranca en 1 y se incrementa por cada emisión.

**Compatibilidad temporal:**

- El campo `secuencial` se mantiene en `EmitirFacturaRequest` como `@Deprecated(since = "Fase 0 G5", forRemoval = true)` sin validaciones (`@NotNull`, `@Size`, `@Pattern` retiradas).
- Si `core-service` (u otro cliente) todavía envía el campo, `billing-service` **lo ignora** y emite un `WARN` en logs con la combinación `id_compania:id_sucursal:cod_establecimiento:cod_punto_emision` para facilitar la detección de clientes rezagados.
- La respuesta 201 sigue devolviendo `secuencial` (asignado por el servidor, con padding a 9 dígitos, ej. `"000000042"`).

**Timeline:**

1. **Hoy (Fase 0, G5 desplegado):** el servidor asigna y logea WARN si el cliente envía el campo. `core-service` sigue funcionando con o sin el campo.
2. **Próxima release del cliente:** `core-service` deja de enviar `secuencial`.
3. **Próxima versión mayor de `billing-service`:** el campo se **elimina** del DTO `EmitirFacturaRequest`. Enviarlo generará `400 Bad Request` por campo desconocido si Jackson está configurado en modo estricto (o simplemente se ignorará silenciosamente).

**Acción requerida en `core-service`:**

- Retirar del payload el campo `secuencial`.
- Retirar cualquier lógica local de reserva/lock del secuencial (por ejemplo un stored procedure o un `SELECT ... FOR UPDATE` en `core.membresias`).
- Continuar generando el `codigo_numerico` determinísticamente para preservar la idempotencia del `POST`.

---

## Referencias cruzadas

- [comprobantes.md](../../billing-service/api/comprobantes.md) — Contrato HTTP completo del endpoint.
- [flows/sri-submission-retry.md](../../billing-service/flows/sri-submission-retry.md) — Máquina de estados y lógica de reintentos.
- [../../gym-administrator/specs/billing-service.md](../../gym-administrator/specs/billing-service.md) — Especificación de dominio y arquitectura (nota: algunos paths están desalineados; usar este doc como referencia de negocio).
