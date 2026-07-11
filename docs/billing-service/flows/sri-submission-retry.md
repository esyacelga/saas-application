# Flujo de envío al SRI y reintentos — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `RetrySchedulerService`, `EnvioSriService` y DDL `cola_envio`).

---

## Overview

Después de emitir una factura (endpoint `POST /api/v1/comprobantes/facturas`), el comprobante pasa por un proceso asíncrono de 3 pasos: **firmar XML**, **enviar al SRI** para recepción y **consultar autorización**. Si cualquier paso falla, la fila se encola en `facturacion.cola_envio` con reintentos automáticos con backoff exponencial (1, 5, 15, 60, 240 minutos). Un scheduler ejecuta cada 60 segundos.

**Disparadores del flujo:**
- `RetrySchedulerService` (`@Scheduled(fixedDelay = 60000)`) — procesa lotes de 10 filas pendientes cada minuto
- Endpoint `POST /api/v1/comprobantes/{id}/enviar` — envío manual/síncrono

---

## Máquina de estados del comprobante

```
GENERADO (inicial)
    │ (enviar → firmar XML)
    ↓
FIRMADO
    │ (enviar al SRI RECEPCION)
    ├─→ RECIBIDO (éxito en recepción) → (consultar autorización)
    │       ├─→ AUTORIZADO (éxito) ✓ [terminal]
    │       ├─→ NO_AUTORIZADO (rechazado por SRI) ✓ [cola → reintento]
    │       └─→ ERROR (fallo en AUTORIZACION) → [cola → reintento]
    │
    ├─→ DEVUELTO (rechazado en recepción) → [cola → reintento]
    │
    └─→ ERROR (fallo en RECEPCION) → [cola → reintento]
```

**Estados terminales sin reintento:**
- `AUTORIZADO` — factura válida y autorizada por SRI

**Estados con reintento automático:**
- `DEVUELTO` — SRI rechazó comprobante en RECEPCION
- `NO_AUTORIZADO` — SRI rechazó en consulta de AUTORIZACION
- `ERROR` — excepción en RECEPCION o AUTORIZACION

Cada reintento respeta el backoff: si agota intentos (`intentos >= max_intentos`), el comprobante queda en `ERROR` y la fila pasa a `FALLIDO_DEFINITIVO`.

---

## Estados de la fila en `cola_envio`

| Estado | Significado |
|--------|-------------|
| `PENDIENTE` | Fila lista para procesar en el siguiente tick del scheduler |
| `PROCESANDO` | Fila bloqueada durante la ejecución (aunque no se usa explícitamente en código) |
| `COMPLETADO` | Comprobante autorizado exitosamente; fila cerrada |
| `FALLIDO_DEFINITIVO` | Agotados reintentos (intentos >= max_intentos); requiere intervención manual |

**Constraint único:** Un comprobante solo puede tener 1 fila activa en `cola_envio` (UNIQUE `id_comprobante`).

---

## Ciclo del scheduler

1. **Cada 60 segundos**, `RetrySchedulerService.procesarPendientes()` ejecuta:
   - Consulta hasta **10 filas** en estado `PENDIENTE` con `proxima_ejecucion <= NOW()`
   - Para cada fila:
     - Busca el comprobante asociado
     - Llama a `EnvioSriService.procesarComprobante()` (firmar y enviar)
     - Si error → log de ERROR, **no aborta el batch** (`onErrorResume → Mono.empty()`)
   - Itera en reactivo (Flux) sin bloqueo

2. **No hay gestión manual de estado `PROCESANDO`** en la lógica actual.

3. **Errores no fatales:** Si una fila falla durante procesamiento, el scheduler continúa con la siguiente (patrón resiliente).

---

## Backoff exponencial

**Array de delays (en minutos):**
```
RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 240}
```

**Lógica:**
- Intentó 1 falla → próxima ejecución en +1 min
- Intentó 2 falla → próxima ejecución en +5 min
- Intentó 3 falla → próxima ejecución en +15 min
- Intentó 4 falla → próxima ejecución en +60 min
- Intentó 5+ falla → próxima ejecución en +240 min (4 horas)
- **Cuando `intentos >= max_intentos` (default 5):** estado pasa a `FALLIDO_DEFINITIVO`

**Implementación:**
```java
int delayIndex = Math.min(nuevoIntento - 1, RETRY_DELAYS_MINUTES.length - 1);
long delayMinutes = RETRY_DELAYS_MINUTES[delayIndex];
OffsetDateTime proximaEjecucion = OffsetDateTime.now().plusMinutes(delayMinutes);
```

No hay jitter — los delays son determinísticos.

---

## Ciclo de vida visual

```
┌─────────────────────────────────────────────────────────────────────┐
│  POST /api/v1/comprobantes/facturas  (o POST /{id}/enviar)         │
└──────────────────────┬──────────────────────────────────────────────┘
                       │
                       ↓
              ┌────────────────┐
              │ GENERADO       │ (comprobante creado)
              └────────┬───────┘
                       │
    ┌──────────────────┴──────────────────┐
    │                                     │
    ↓ (scheduler o endpoint manual)       │
FIRMAR XML + BUILD CERTIFICADO            │ (cola_envio: PENDIENTE)
    │                                     │
    ├─ OK ──→ FIRMADO                    │
    │                                     │
    ├─ ERROR ──→ (scheduleRetry)         │
    │          próximo delay              │
    │                                     │
    ↓                                      │
ENVIAR AL SRI (RECEPCION)                 │
    │                                     │
    ├─ RECIBIDA ──→ RECIBIDO             │
    │               ↓                     │
    │               CONSULTAR AUTORIZACION│
    │               │                     │
    │               ├─ AUTORIZADO ──→ ✓ COMPLETADO
    │               │
    │               ├─ ERROR/RECHAZADO ──→ (scheduleRetry)
    │                                     próximo delay
    │
    ├─ RECHAZADA/ERROR ──→ DEVUELTO / ERROR
                          ↓
                      (scheduleRetry)
                      proxima_ejecucion += delay
                      intentos++
                      
    Si intentos >= max_intentos (5):
       cola_envio.estado = FALLIDO_DEFINITIVO
       comprobante.estado = ERROR
       Requiere intervención manual
```

---

## Operación y monitoreo

**Ver estado de la cola de reintentos:**
- Endpoint `GET /api/v1/admin/auditoria` con parámetro `estado=ERROR` filtra comprobantes con fallas
- Consultar directamente tabla `facturacion.cola_envio` para ver `intentos`, `proxima_ejecucion`, `ultimo_error`

**Reintento manual de filas en `FALLIDO_DEFINITIVO`:**
- Actualmente no existe endpoint para forzar reintento
- Operador debe intervenir en BD: actualizar `cola_envio.estado = 'PENDIENTE'`, `proximaEjecucion = NOW()`, e incrementar `max_intentos` si es necesario
- Scheduler lo procesará en el próximo tick (60 seg)

---

## Notas para desarrolladores

- **El scheduler no es transaccional entre filas:** un error en una fila NO aborta el batch completo (`onErrorResume` + log). Diseño resiliente.
- **Logs de ruido benigno:** Tests de integración (`EmitirFacturaIT`) generan filas huérfanas en `cola_envio` con `id_compania=99999`. El scheduler intenta procesarlas y registra ERROR "Certificado activo no encontrado para 99999" — es esperado, no indica fallo del sistema.
- **No hay lock explícito en `cola_envio`:** Si dos instancias del scheduler corren, ambas pueden procesar la misma fila. Mitigar con máximo 1 replica de billing-service o implementar lock distribuido.
- **Test coverage:** Existe `EmitirFacturaIT`, `ComprobanteIntegrationTest` — verificar antes de cambiar lógica de reintentos o backoff.
