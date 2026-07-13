# Flujo de envío al SRI y reintentos — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `ComprobanteService.emitirFactura`, `EnvioSriService.procesarEmisionInmediata`, `RetrySchedulerService`, y DDL `cola_envio`).
>
> **Fase 1 · G2 — Transmisión inmediata (activo desde 2026-01-01).** El primer intento firma → envío → autorización se ejecuta **síncronamente dentro del POST**. La cola pasa a ser fallback (solo se puebla cuando el pipeline síncrono falla o el SRI devuelve estado transitorio).

---

## Overview

1. `POST /api/v1/comprobantes/facturas` reserva el secuencial, calcula la clave de acceso, persiste el `Comprobante` en estado `GENERADO` y **acto seguido** dispara el pipeline síncrono (`EnvioSriService.procesarEmisionInmediata`).
2. El pipeline ejecuta **firmar XML → enviar al SRI RECEPCION → consultar AUTORIZACION → generar RIDE**, todo bajo un `timeout` configurable (default 15 s, ver `sri.timeout.envio-seconds`).
3. El controller **siempre devuelve HTTP 201** con el `Comprobante` tal como quedó (`AUTORIZADO`, `DEVUELTO`, `NO_AUTORIZADO`, `ERROR`). Solo se devuelve 5xx si falla la persistencia inicial (antes del pipeline).
4. Si el pipeline falla (timeout, error de red, `DEVUELTO`, `NO_AUTORIZADO`), se crea una fila en `facturacion.cola_envio` para reintento asíncrono con backoff `{1, 5, 15, 60, 240}` min.
5. `RetrySchedulerService` (`@Scheduled(fixedDelay = 60000)`) procesa lotes de 10 filas pendientes cada minuto.

**Disparadores del flujo:**
- `POST /api/v1/comprobantes/facturas` — **flujo principal, síncrono** (G2).
- `POST /api/v1/comprobantes/{id}/enviar` — envío manual (fallback si el usuario quiere reintentar sin esperar al scheduler).
- `RetrySchedulerService` — procesa la cola cada 60 s (reintentos automáticos).

---

## Máquina de estados del comprobante

```
GENERADO (persistido antes del pipeline síncrono)
    │
    │ [G2 · síncrono dentro del POST, con timeout de 15 s]
    │
    ↓  firmar XML
FIRMADO
    │  enviar al SRI RECEPCION
    │
    ├─→ RECIBIDO (éxito en recepción) → consultar AUTORIZACION
    │       │
    │       ├─→ AUTORIZADO ✓ [terminal] — no se crea fila en cola_envio
    │       │
    │       ├─→ NO_AUTORIZADO → fila en cola_envio (backoff {1,5,15,60,240} min)
    │       │
    │       └─→ ERROR → fila en cola_envio (backoff {1,5,15,60,240} min)
    │
    ├─→ DEVUELTO (rechazado en recepción) → fila en cola_envio (backoff)
    │
    ├─→ ERROR (excepción en RECEPCION) → fila en cola_envio (backoff)
    │
    └─→ [TIMEOUT del pipeline síncrono >15 s]
        ERROR → fila en cola_envio con proxima_ejecucion = now()
                (reintento inmediato en la próxima pasada del scheduler)
```

**Estados terminales sin reintento:**
- `AUTORIZADO` — factura válida y autorizada por SRI.
- `ANULADO` — anulación manual.

**Estados con reintento automático (fila en `cola_envio`):**
- `DEVUELTO` — SRI rechazó comprobante en RECEPCION.
- `NO_AUTORIZADO` — SRI rechazó en consulta de AUTORIZACION.
- `ERROR` — excepción en RECEPCION o AUTORIZACION, **incluyendo timeout de 15 s**.

Cada reintento respeta el backoff: si agota intentos (`intentos >= max_intentos`), el comprobante queda en `ERROR` y la fila pasa a `FALLIDO_DEFINITIVO`.

---

## Estados de la fila en `cola_envio`

| Estado | Significado |
|--------|-------------|
| `PENDIENTE` | Fila lista para procesar en el siguiente tick del scheduler |
| `PROCESANDO` | Fila bloqueada durante la ejecución (no se usa explícitamente hoy) |
| `COMPLETADO` | Comprobante autorizado exitosamente (path async); fila cerrada |
| `FALLIDO_DEFINITIVO` | Agotados reintentos; requiere intervención manual |

**Constraint único:** Un comprobante solo puede tener 1 fila activa en `cola_envio` (UNIQUE `id_comprobante`).

**Nota (G2):** en el path síncrono la fila se crea **solo cuando el pipeline falla**. Si el SRI autoriza durante el POST, **no** se crea fila en `cola_envio`.

---

## Timeline del path síncrono (G2)

```
Cliente                     billing-service                          SRI
   |                             |                                    |
   | POST /facturas              |                                    |
   |---------------------------->|                                    |
   |                             |                                    |
   |                             | validar catálogos                  |
   |                             | reservar secuencial (RETURNING)    |
   |                             | generar claveAcceso                |
   |                             | INSERT comprobante (GENERADO)       |
   |                             |                                    |
   |                       ┌─────┴──── inicia timeout 15 s ────┐      |
   |                       |     |                              |     |
   |                       |     | build XML real (v2.24)       |     |
   |                       |     | firmar XAdES-BES             |     |
   |                       |     | UPDATE estado=FIRMADO        |     |
   |                       |     | POST RECEPCION SOAP          |     |
   |                       |     |-----------------------------→|     |
   |                       |     |                              |RECIBIDA
   |                       |     |←-----------------------------|     |
   |                       |     | UPDATE estado=RECIBIDO       |     |
   |                       |     | POST AUTORIZACION SOAP       |     |
   |                       |     |-----------------------------→|     |
   |                       |     |                              |AUTORIZADO
   |                       |     |←-----------------------------|     |
   |                       |     | UPDATE estado=AUTORIZADO     |     |
   |                       |     | generar RIDE PDF             |     |
   |                       |     | enviar email (fire&forget)   |     |
   |                       └─────┴─── OK (<15 s) ──────────────┘      |
   |                             |                                    |
   |    201 Created              |                                    |
   |    estado=AUTORIZADO        |                                    |
   |<----------------------------|                                    |
```

### Path timeout (SRI no responde en 15 s)

```
Cliente                     billing-service                          SRI
   |                             |                                    |
   | POST /facturas              |                                    |
   |---------------------------->|                                    |
   |                             | ... INSERT comprobante             |
   |                             |                                    |
   |                       ┌─────┴──── timeout 15 s ──────────┐       |
   |                       |     | POST RECEPCION SOAP        |       |
   |                       |     |----------------------------→|      |
   |                       |     |    (sin respuesta en 15 s)  |      |
   |                       └─────┴──── TimeoutException ────┘         |
   |                             |                                    |
   |                             | UPDATE estado=ERROR                |
   |                             | INSERT cola_envio                  |
   |                             |   estado=PENDIENTE                 |
   |                             |   proxima_ejecucion=now()          |
   |                             |   ultimo_error="Timeout de 15s..." |
   |                             |                                    |
   |    201 Created              |                                    |
   |    estado=ERROR             |                                    |
   |<----------------------------|                                    |
                                 |
                                 | [<60 s más tarde]
                                 | RetrySchedulerService
                                 | procesa la cola
```

---

## Ciclo del scheduler (fallback asíncrono)

1. **Cada 60 segundos**, `RetrySchedulerService.procesarPendientes()` ejecuta:
   - Consulta hasta **10 filas** en estado `PENDIENTE` con `proxima_ejecucion <= NOW()` (`FOR UPDATE SKIP LOCKED`).
   - Para cada fila:
     - Busca el comprobante asociado.
     - Llama a `EnvioSriService.procesarComprobante()` (path async: carga detalles y ConfigSri desde BD, reconstruye XML real, firma, envía).
     - Si error → log de ERROR, **no aborta el batch** (`onErrorResume → Mono.empty()`).
   - Itera en reactivo (Flux) sin bloqueo.

2. **No hay gestión manual de estado `PROCESANDO`** en la lógica actual.

3. **Errores no fatales:** Si una fila falla durante procesamiento, el scheduler continúa con la siguiente (patrón resiliente).

---

## Backoff exponencial (path async)

**Array de delays (en minutos):**
```
RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 240}
```

**Lógica:**
- Intento 1 falla → próxima ejecución en +1 min
- Intento 2 falla → próxima ejecución en +5 min
- Intento 3 falla → próxima ejecución en +15 min
- Intento 4 falla → próxima ejecución en +60 min
- Intento 5+ falla → próxima ejecución en +240 min (4 horas)
- **Cuando `intentos >= max_intentos` (default 5):** estado pasa a `FALLIDO_DEFINITIVO`

**Excepción — Timeout del path síncrono:**
Cuando el fallo síncrono es por **timeout / error de red** (no un rechazo explícito del SRI), la fila se inserta con `proxima_ejecucion = now()` para que el scheduler la agarre en su próxima pasada (dentro de 60 s), **no en 1 min de backoff**.

**Implementación:**
```java
int delayIndex = Math.min(nuevoIntento - 1, RETRY_DELAYS_MINUTES.length - 1);
long delayMinutes = RETRY_DELAYS_MINUTES[delayIndex];
OffsetDateTime proximaEjecucion = OffsetDateTime.now().plusMinutes(delayMinutes);
```

No hay jitter — los delays son determinísticos.

---

## Configuración

| Property | Default | Descripción |
|----------|---------|-------------|
| `sri.timeout.envio-seconds` | `15` | Timeout aplicado al pipeline síncrono (G2) firma → envío → autorización dentro del POST. Al vencer, la factura queda en `ERROR` y la fila en `cola_envio` se crea con `proxima_ejecucion = now()`. |
| `SRI_TIMEOUT_ENVIO_SECONDS` | — | Variable de entorno equivalente. |

**SLO objetivo (G2):**
- `sri.emision.duracion` — p95 < 30 s en primer envío, p99 < 5 min (incluyendo fallback vía cola).
- `sri.emision.timeouts` — < 1 % de los POST deberían caer por timeout en operación normal.

---

## Métricas Micrometer

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `billing.comprobantes.emitidos{tipo=FACTURA}` | Counter | Facturas emitidas (post-firma). |
| `billing.comprobantes.emitidos{tipo=NOTA_CREDITO}` | Counter | Notas de crédito (Fase 2). |
| `billing.comprobantes.autorizados` | Counter | Comprobantes autorizados por el SRI. |
| `billing.comprobantes.errores_sri` | Counter | Errores de comunicación o rechazo del SRI. |
| `billing.comprobantes.reintentos` | Counter | Reintentos de envío al SRI. |
| **`sri.emision.duracion`** | **Timer** | **G2 · duración del pipeline síncrono POST→AUTORIZADO (p50/p95/p99).** |
| **`sri.emision.timeouts`** | **Counter** | **G2 · incrementado cuando el pipeline síncrono cae por timeout.** |

---

## Ciclo de vida visual (G2 · resumen)

```
┌──────────────────────────────────────────────────────────────────────┐
│  POST /api/v1/comprobantes/facturas                                  │
└──────────────────────┬───────────────────────────────────────────────┘
                       │
                       ↓
              ┌────────────────┐
              │ GENERADO       │ (comprobante persistido)
              └────────┬───────┘
                       │
                       │ [G2 · pipeline síncrono con timeout de 15 s]
                       ↓
      ┌────────────────┴────────────────┐
      │                                 │
      ↓                                 ↓
FIRMAR XML + ENVIAR + AUTORIZAR      TIMEOUT / EXCEPCIÓN
      │                                 │
   ┌──┴──────┐                          ↓
   ↓         ↓                    UPDATE estado=ERROR
AUTORIZADO  DEVUELTO/              INSERT cola_envio(
   ✓        NO_AUTORIZADO          proxima_ejecucion=now())
            │                          │
            ↓                          │
     INSERT cola_envio(                │
     backoff {1,5,15,60,240} min)      │
            │                          │
            └────────────┬─────────────┘
                         │
                         ↓
           [<60 s más tarde]
           RetrySchedulerService
              procesa la cola
                         │
                         ↓
              procesarComprobante(id, idC, idS)
              (recarga detalles + config desde BD)
```

---

## Operación y monitoreo

**Ver estado de la cola de reintentos:**
- Endpoint `GET /api/v1/admin/auditoria` con parámetro `estado=ERROR` filtra comprobantes con fallas.
- Consultar directamente tabla `facturacion.cola_envio` para ver `intentos`, `proxima_ejecucion`, `ultimo_error`.
- Alerta métrica: `sri.emision.timeouts` incrementándose de forma sostenida indica que el SRI está lento o caído.

**Reintento manual de filas en `FALLIDO_DEFINITIVO`:**
- Actualmente no existe endpoint para forzar reintento.
- Operador debe intervenir en BD: actualizar `cola_envio.estado = 'PENDIENTE'`, `proxima_ejecucion = NOW()`, e incrementar `max_intentos` si es necesario.
- Scheduler lo procesará en el próximo tick (60 s).

**Envío manual:**
- `POST /api/v1/comprobantes/{id}/enviar` sigue existiendo como fallback manual (no aplica timeout de 15 s; corre en el mismo request pero sin `.timeout(...)`).

---

## Notas para desarrolladores

- **G2 · `procesarEmisionInmediata` vs `procesarComprobante`:** el primero se llama desde `ComprobanteService.emitirFactura` con los detalles y pagos **en memoria** (evita round-trip a BD). El segundo se usa desde el scheduler y `POST /{id}/enviar`, y **relee** detalles + `ConfigSri` desde BD.
- **XML real (elimina placeholder):** `FacturaXmlBuilder.buildXml(comprobante, detalles, configSri, impuestosTotales, pagos)` genera el XML v2.24 completo. Se acabó el `<placeholder claveAcceso="..."/>` que rechazaría el SRI.
- **El scheduler no es transaccional entre filas:** un error en una fila NO aborta el batch completo (`onErrorResume` + log). Diseño resiliente.
- **Logs de ruido benigno:** Tests de integración generan filas huérfanas en `cola_envio` con `id_compania=99999`. El scheduler intenta procesarlas y registra ERROR "Certificado activo no encontrado para 99999" — esperado.
- **Bug latente pre-G2 (no tocado aquí):** el path async del scheduler recrea la fila en `cola_envio` cada vez (`save`) en vez de actualizar la existente; esto viola el `UNIQUE (id_comprobante)`. Se manifiesta como logs "duplicate key value violates unique constraint uq_facturacion_cola_envio_comprobante". El scheduler ignora el error (`onErrorResume`), pero la fila no se actualiza. Fixear en un follow-up (fuera de alcance de G2).
- **No hay lock explícito en `cola_envio`:** Si dos instancias del scheduler corren, ambas pueden procesar la misma fila. Mitigar con máximo 1 replica de billing-service (o `FOR UPDATE SKIP LOCKED`, que ya está en la query).
- **Test coverage:** `EmitirFacturaIT` (happy path AUTORIZADO), `EmisionInmediataTimeoutIT` (timeout G2), `EnvioSriServiceTest` (unit — autorizado / devuelto / timeout / error / builder). Verificar antes de cambiar lógica de emisión inmediata o backoff.
