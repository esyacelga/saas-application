# Flujo de anulación fiscal SRI + notas de crédito — billing-service

> **ESTADO:** ✅ **G3 · Refleja el código actual** (verificado contra `AnulacionService`, `AnulacionController`, `NotaCreditoService`, `EnvioSriService`).
>
> **Fase 2 · G3 — Anulación fiscal SRI (activo desde 2026-07-13).**

Ver también:
- [api/anulaciones.md](../api/anulaciones.md) — contrato REST de la máquina de estados.
- [api/notas-credito.md](../api/notas-credito.md) — contrato REST de la NC (G4) que el Flujo B dispara.
- [flows/sri-submission-retry.md](sri-submission-retry.md) — pipeline síncrono G2 heredado por la NC.

---

## 1. Modelo conceptual

El SRI Ecuador distingue **dos mecanismos** para cancelar los efectos de un comprobante:

| Mecanismo | Cuándo aplica | Requiere aceptación del receptor |
|-----------|---------------|:--------------------------------:|
| **Anulación directa (Flujo A)** | Facturas con receptor identificado, dentro de la ventana temporal, sin efectos económicos ya realizados | No |
| **Nota de crédito (Flujo B)** | Devoluciones, descuentos comerciales, errores parciales, o cuando la ventana de anulación directa venció | Sí (5 días hábiles) |

El sistema soporta ambos flujos, con la misma máquina de estados en `facturacion.anulaciones` y un solo endpoint de entrada (`POST /comprobantes/{id}/anular`). El flag `generar_nota_credito` en el request selecciona el flujo.

---

## 2. Máquina de estados

```
                         ┌───────────────┐
                         │  SOLICITADA   │  (POST /comprobantes/{id}/anular)
                         └───────┬───────┘
                                 │
                    ┌────────────┼────────────┐
                    │                         │
              [aprobar]                  [rechazar]
                    │                         │
                    ▼                         ▼
             ┌────────────┐            ┌────────────┐
             │  APROBADA  │            │ RECHAZADA  │
             └─────┬──────┘            └────────────┘
                   │
      ┌────────────┼─────────────┐
      │                          │
[Flujo A · confirmar-sri]   [Flujo B · NC autoriza]
      │                          │
      └──────────┬───────────────┘
                 ▼
          ┌────────────┐
          │  EJECUTADA │  (comprobante original marcado ANULADO)
          └────────────┘
```

**Estados terminales:** `RECHAZADA`, `EJECUTADA`.

**Estados con progreso pendiente:** `APROBADA` en Flujo B mientras la NC asociada no llegue a `AUTORIZADO` (el scheduler de G2 la retomará y la anulación transicionará a `EJECUTADA` automáticamente cuando la NC autorice).

---

## 3. Validaciones fiscales al solicitar (`POST /comprobantes/{id}/anular`)

Se ejecutan en orden. La primera que falla retorna el error correspondiente y no se persiste ninguna fila.

| # | Regla | Error |
|---|-------|-------|
| 1 | Motivo no vacío | `422 · El motivo de anulación es obligatorio` |
| 2 | El comprobante existe y pertenece a la compañía del JWT | `404 · Comprobante not found with id: {id}` |
| 3 | `estado ∈ {AUTORIZADO, GENERADO}` | `422 · No es posible solicitar anulación...` |
| 4 | `id_receptor != '9999999999999'` (consumidor final) | `422 · Las facturas emitidas a consumidor final...` |
| 5 | Ventana temporal: `hoy ≤ día 7 del mes siguiente al de emision` | `422 · Fuera de la ventana SRI para anulación...` |
| 6 | Si `codigo_motivo_anulacion` viene: debe existir en `sri.motivos_anulacion_nc` | `404 · Motivo de anulación no reconocido: {codigo}` |

**Ventana temporal — ejemplo:**
- Factura emitida `2026-07-15` → anulable hasta `2026-08-07` inclusive.
- Factura emitida `2026-07-31` → anulable hasta `2026-08-07` inclusive.
- Consulta `hoy` con `Clock` inyectable en zona `America/Guayaquil` (ver `ClockConfig`).

---

## 4. Flujo A · Anulación directa (sin nota de crédito)

```
Solicitante (staff)      billing-service         Admin (admin_compania+)     Portal SRI
     │                        │                          │                        │
     │ POST /comprobantes/{id}/anular                    │                        │
     │  { motivo, generar_nota_credito: false }          │                        │
     │───────────────────────►│                          │                        │
     │                        │  INSERT anulaciones      │                        │
     │                        │  estado=SOLICITADA       │                        │
     │  201 Created           │                          │                        │
     │◄───────────────────────│                          │                        │
     │                        │                          │                        │
     │                        │  POST /anulaciones/{id}/aprobar                   │
     │                        │◄─────────────────────────│                        │
     │                        │  UPDATE anulaciones      │                        │
     │                        │  estado=APROBADA         │                        │
     │                        │  fecha_resolucion=NOW()  │                        │
     │                        │  id_usuario_aprueba=...  │                        │
     │                        │  200 OK                  │                        │
     │                        │─────────────────────────►│                        │
     │                        │                          │                        │
     │                        │                          │ Ingresa clave acceso   │
     │                        │                          │ + motivo en el portal  │
     │                        │                          │───────────────────────►│
     │                        │                          │                        │
     │                        │                          │◄── Anulación ejecutada │
     │                        │                          │                        │
     │                        │  POST /anulaciones/{id}/confirmar-sri             │
     │                        │◄─────────────────────────│                        │
     │                        │  UPDATE anulaciones      │                        │
     │                        │  estado=EJECUTADA        │                        │
     │                        │  UPDATE comprobantes     │                        │
     │                        │  SET estado=ANULADO      │                        │
     │                        │  200 OK                  │                        │
     │                        │─────────────────────────►│                        │
     │                        │                          │                        │
     │                        │  enviarSolicitudAprobada │                        │
     │                        │  (email best-effort)     │                        │
```

**Puntos clave:**
- El backend **no puede** ejecutar la anulación contra el SRI: no hay webservice SOAP público para esa operación en el esquema offline. La operación se hace manualmente en el portal (`srienlinea.sri.gob.ec`).
- La confirmación (`confirmar-sri`) es un **acto declarativo** del admin — el sistema confía en que el trámite se ejecutó.
- La notificación por email al solicitante es best-effort: si el SMTP falla, la transición se completa igual y el error queda en logs `WARN`.

---

## 5. Flujo B · Anulación con nota de crédito

```
Solicitante         billing-service                        SRI                     Receptor
     │                    │                                  │                         │
     │ POST /comprobantes/{id}/anular                        │                         │
     │  { motivo, codigo_motivo_anulacion: "DEVOLUCION",     │                         │
     │    generar_nota_credito: true }                       │                         │
     │───────────────────►│                                  │                         │
     │                    │  INSERT anulaciones              │                         │
     │                    │  estado=SOLICITADA               │                         │
     │                    │  observacion_resolucion=         │                         │
     │                    │    "[FLUJO_B][MOTIVO=DEVOLUCION]"│                         │
     │  201 Created       │                                  │                         │
     │◄───────────────────│                                  │                         │
     │                    │                                  │                         │
     │             POST /anulaciones/{id}/aprobar (admin_compania+)                    │
     │                    │                                  │                         │
     │                    │  UPDATE anulaciones              │                         │
     │                    │  estado=APROBADA                 │                         │
     │                    │                                  │                         │
     │                    │  NotaCreditoService              │                         │
     │                    │    .emitirNotaCredito()          │                         │
     │                    │  ─── G4 · pipeline síncrono G2 ──────────────────────────► │
     │                    │  1. reservarSecuencial NC 04     │                         │
     │                    │  2. firmar XAdES-BES             │                         │
     │                    │  3. POST RECEPCION SOAP     ────►│                         │
     │                    │  4. POST AUTORIZACION SOAP  ────►│                         │
     │                    │  5. UPDATE comprobantes NC       │                         │
     │                    │     estado=AUTORIZADO            │                         │
     │                    │                                  │                         │
     │                    │  UPDATE anulaciones              │                         │
     │                    │  estado=EJECUTADA                │                         │
     │                    │  id_comprobante_nc=<nc_id>       │                         │
     │                    │                                  │                         │
     │                    │  UPDATE comprobantes (original)  │                         │
     │                    │  estado=ANULADO                  │                         │
     │                    │                                  │                         │
     │                    │  enviarNotaCreditoAceptacion ──────────────────────────────►│
     │                    │  enviarSolicitudAprobada ─────────►Solicitante              │
     │                    │  (emails best-effort)                                       │
     │                    │  200 OK                          │                         │
     │             ◄──────│                                  │                         │
```

**Puntos clave:**
- La NC se emite **atómicamente en la aprobación** — no hay job asíncrono adicional. Reutiliza el pipeline síncrono G2 (timeout 15 s configurable, cola de fallback).
- Si la NC autoriza en el mismo request → anulación `EJECUTADA` + comprobante `ANULADO` en un solo POST.
- Si la NC queda `DEVUELTO`, `NO_AUTORIZADO` o `ERROR` → anulación queda `APROBADA` con `id_comprobante_nc` poblado. El scheduler de G2 retomará la NC en su próxima pasada (60 s); cuando autorice, un job posterior deberá transicionar la anulación a `EJECUTADA`. **Este segundo paso automático aún no está implementado en G3 — el cierre requiere intervención manual vía `confirmar-sri` (o volver a llamar `aprobar` una vez la NC autorice).** Ver TODO en `NotaCreditoService`.
- El `codigo_motivo_anulacion` de la solicitud se persiste embebido en `observacion_resolucion` con el prefijo interno `[MOTIVO=<codigo>]` porque el DDL de `facturacion.anulaciones` no tiene columna dedicada. La UI nunca lo ve — se strippea antes de mapear a `AnulacionResponse`.

### Persistencia del flag Flujo B

Al no existir columna `generar_nota_credito` en el DDL, se codifica:

| Campo | Valor persistido | Ejemplo |
|-------|------------------|---------|
| `observacion_resolucion` | `[FLUJO_B][MOTIVO=<codigo>] <observacion libre>` | `[FLUJO_B][MOTIVO=DEVOLUCION] Aprobado por gerencia` |

- Al leer (todos los endpoints GET/POST responses) el servicio **strippea** el prefijo antes de mapear a DTO.
- Si se pide anulación sin NC (`generar_nota_credito=false`) sin codigo motivo, `observacion_resolucion` inicial es `null`.
- La aprobación de Flujo B **preserva** los marcadores al agregarle la observación del aprobador; el rechazo hace lo mismo.

---

## 6. Autorización por endpoint

| Endpoint | Roles con permiso |
|----------|-------------------|
| `POST /comprobantes/{id}/anular` | Cualquier staff (`tipo=staff`) con `id_compania` coincidente |
| `GET /comprobantes/{id}/anulaciones` | Cualquier staff |
| `GET /anulaciones/{id}` | Cualquier staff |
| `GET /anulaciones` | Cualquier staff |
| `GET /sri/motivos-anulacion` | Cualquier staff |
| `POST /anulaciones/{id}/aprobar` | `admin_compania`, `super_admin`, `Dueño` |
| `POST /anulaciones/{id}/rechazar` | `admin_compania`, `super_admin`, `Dueño` |
| `POST /anulaciones/{id}/confirmar-sri` | `admin_compania`, `super_admin`, `Dueño` |

Un staff con rol distinto (ej. `recepcion`) recibe `403 Forbidden` al intentar resolver.

---

## 7. Notificaciones

Todas las notificaciones son **best-effort**: usan `EmailNotificationPort` sobre `Schedulers.boundedElastic()` y las excepciones se capturan como `WARN` sin romper el flujo funcional.

| Evento | Método | Destinatario |
|--------|--------|--------------|
| Aprobación (A o B) | `enviarSolicitudAprobada` | Solicitante (email del comprobante como best-effort) |
| Rechazo | `enviarSolicitudRechazada` | Solicitante |
| NC emitida (Flujo B) | `enviarNotaCreditoAceptacion` | Receptor de la factura (pide aceptación 5 días hábiles) |

Los emails de `enviarSolicitudAprobada` / `enviarSolicitudRechazada` se envían al email del receptor porque hoy el JWT no expone email del solicitante; es aceptable como fallback informativo.

---

## 8. Métricas / observabilidad

G3 no agrega métricas propias — los eventos se cubren mediante:
- Los estados de la NC bajo `billing.comprobantes.emitidos{tipo=NOTA_CREDITO}` (métrica ya existente por G4).
- El pipeline SRI bajo `sri.emision.duracion` y `sri.emision.timeouts` (métricas de G2).

Log level `INFO` incluye:
- `Anulación {id} queda en APROBADA — NC {id} en estado {estado} (el scheduler la retomará)` — cuando Flujo B no cierra en un request.

Log level `WARN`:
- `Fallo al notificar aprobación/rechazo/NC ...` — cuando el email falla.

---

## 9. Pendientes conocidos

- **G9 (Fase 3):** el ATS mensual (`AtsXmlBuilder`) todavía solo reporta `tipo_comprobante = '01'`. Debe incluir NC (`04`) y anulaciones cuando G9 se implemente. Hay un `TODO(G9)` en `ReporteService.generarAts`.
- **Reintento automático de aprobación en Flujo B:** cuando la NC queda `DEVUELTO`/`ERROR` en la aprobación, el sistema no dispara automáticamente la transición a `EJECUTADA` cuando la NC autoriza en un retry del scheduler. El operador debe re-aprobar o el sistema debe agregar un job. Documentado en `NotaCreditoService.emitirNotaCredito` — abordar en un follow-up.
