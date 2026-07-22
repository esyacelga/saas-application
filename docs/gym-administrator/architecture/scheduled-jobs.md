# Scheduled Jobs — Documentación Centralizada

**Última actualización:** 2026-07-16  
**Autores:** Team SaaS Platform

---

## Resumen ejecutivo

Este documento centraliza la especificación de todos los **8 scheduled jobs** del monorepo (4 microservicios Java). Los jobs son **dispersos, cross-servicio y críticos para la operación diaria**: algunos actualizan estados de negocio (clientes, suscripciones), otros procesan colas de notificaciones.

Históricamente estaban documentados fragmentariamente en comentarios de código. Hoy se consolidan en un único punto de referencia que incluye:

1. **Tabla unificada** con cron/delay, env vars, idempotencia
2. **Convención de startup hook** para recuperar ventanas perdidas (nuevo en 2026-07-16)
3. **Bug histórico corregido** en `ClienteStatusJobService` — síntoma y solución
4. **Excepciones documentadas** — `CertificadoAlertaService` sin idempotencia (no startup hook)

---

## Tipos de scheduled jobs

### 1. Cron-based (diarios, idempotentes)

Disparan una sola vez al día en una hora fija. **Spring in-memory**: si el proceso está caído a la hora programada, esa ejecución se pierde. Para recuperar la ventana perdida, aplicamos una **convención nueva**: `@EventListener(ApplicationReadyEvent.class)` que re-ejecuta al arrancar si `${jobs.run-on-startup:true}` (default `true`; tests lo ponen en `false`).

**Requisito previo:** el job debe ser **idempotente** (ejecutar N veces = ejecutar 1 vez). Definición por tipo de trabajo:

- **Recalcular estado a partir de datos** ✅ Idempotente — ejecutar 2 veces recalcula igual resultado. Ej.: `ClienteStatusJobService` recomputa la fecha de vencimiento de membresías sin perseguir "últimas N".
- **Encolar notificación con deduplicación** ✅ Idempotente — `existsIdempotente()` valida antes de encolar; re-ejecutar devuelve "ya encolada". Ej.: `NotificacionVencimientoJob` chequea `existsByIdCompaniaPlanAndDiasAntes`.
- **Enviar email sin chequear duplicado** ❌ **No idempotente** — re-ejecutar envía 2 emails (spam). Ej.: `CertificadoAlertaService` (billing) no tiene `existsEnviadoHoy`; correo se dispara cada vez.

### 2. Fixed-delay (loops continuos, procesamiento de colas)

Disparan cada N segundos indefinidamente. Spring las inicia inmediatamente al arrancar (no necesitan startup hook). Se parámetrizan con `fixedDelayString = "${env.var:defaultMs}"`.

---

## Tabla: 8 Jobs del monorepo

| # | Job | Servicio | Tipo | Cron/Delay | Env var | Idempotente | Startup Hook |
|---|---|---|---|---|---|---|---|
| 1 | `ClienteStatusJobService.ejecutar()` | core-service | Cron | `0 10 0 * * *` (00:10 UTC) | `client.status.job.cron` | ✅ Recalcula estado desde membresías | ✅ Sí |
| 2 | `SubscriptionJobService.runSubscriptionJob()` | platform-service | Cron | `0 5 0 * * *` (00:05 UTC) | `subscription.job.cron` | ✅ Activar PROGRAMADOS + degradar VENCIDOS por fecha | ✅ Sí |
| 3 | `NotificacionVencimientoJob.ejecutar()` | platform-service | Cron | `0 15 3 * * *` (03:15 UTC) | `notificacion.vencimiento.cron` | ✅ Encola con `existsIdempotente` | ✅ Sí |
| 4 | `MensajeriaJob.ejecutar()` | attendance-service | Cron | `0 15 0 * * *` (00:15 Guayaquil, JVM) | `scheduling.messaging-job-cron` | ✅ Encola con `existsEnviadoHoy` | ✅ Sí |
| 5 | `CertificadoAlertaService.verificarVencimientos()` | billing-service | Cron | `0 0 8 * * *` (08:00 UTC) | Hardcoded (sin env var) | ❌ **No** — sin dedup | ❌ **No** — spam emails |
| 6 | `WhatsAppQueueProcessorJob.procesarLote()` | platform-service | Fixed-delay | 30s | `notificacion.whatsapp.queue.fixed-delay-ms` | ✅ Procesa cola de PENDIENTE→ENVIADO | — |
| 7 | `EmailQueueProcessorJob.procesarLote()` | platform-service | Fixed-delay | 30s | `notificacion.email.queue.fixed-delay-ms` | ✅ Procesa cola con retry (30s→2m→10m→1h) | — |
| 8 | `RetrySchedulerService.procesarPendientes()` | billing-service | Fixed-delay | 60s | Hardcoded | ✅ Reintentos SRI con backoff {1,5,15,60,240} min | — |

---

## Job #1: ClienteStatusJobService (core-service)

**Ruta:** [`core-service/src/main/java/com/gymadmin/core/application/service/ClienteStatusJobService.java`](../../../core-service/src/main/java/com/gymadmin/core/application/service/ClienteStatusJobService.java)

**Descripción:** Recalcula el estado de todos los clientes activos basándose en sus membresías. Evalúa fechas de vencimiento y accesos restantes. Aplica la regla: `≤3 días al vencimiento` → estado `proximo_vencer`.

**Cron:** `0 10 0 * * *` (00:10 UTC diariamente)  
**Env var:** `client.status.job.cron`  
**Idempotente:** ✅ Sí — recalcula desde estado de membresías (idempotente).  
**Startup hook:** ✅ Sí — `@EventListener(ApplicationReadyEvent.class)` si `jobs.run-on-startup=true`

**Lógica:**
1. Itera `clientes` activos (`findActivosParaJob`)
2. Por cada cliente:
   - Si está congelado → omite
   - Si tiene membresía activa → evalúa su fecha fin vs hoy
   - Si no hay membresía → marca `vencido`
3. Persiste estado actualizado

**Control de tests:** `application-test.yml` pone `client.status.job.cron: "-"` (desactivado) + `jobs.run-on-startup: false`.

---

## Job #2: SubscriptionJobService (platform-service)

**Ruta:** [`platform-service/src/main/java/com/gymadmin/platform/application/service/SubscriptionJobService.java`](../../../platform-service/src/main/java/com/gymadmin/platform/application/service/SubscriptionJobService.java)

**Descripción:** Gestiona el ciclo de vida diario de suscripciones (REQ-SAAS-001, RN-03/RN-10). Pasos en orden estricto:
1. Activa planes `PROGRAMADOS` cuya fecha inicio ≤ hoy (reemplaza anterior a REEMPLAZADA)
2. Degrada planes vencidos u EN_GRACIA agotados al plan de degradación (con detección de sobre-límite RN-06)
3. Procesa notificaciones de vencimiento y invalida cache Redis

**Cron:** `0 5 0 * * *` (00:05 UTC diariamente)  
**Env var:** `subscription.job.cron`  
**Idempotente:** ✅ Sí — consulta estado actual de suscripciones; re-ejecutar idempotente.  
**Startup hook:** ✅ Sí — `@EventListener(ApplicationReadyEvent.class)`

**Clock inyectable:** Usa `Clock` para time-travel en tests.

**Control de tests:** `application-test.yml` desactiva el cron: `subscription.job.cron: "-"` + `jobs.run-on-startup: false`.

---

## Job #3: NotificacionVencimientoJob (platform-service)

**Ruta:** [`platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/NotificacionVencimientoJob.java`](../../../platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/NotificacionVencimientoJob.java)

**Descripción:** Genera notificaciones de vencimiento del dueño (REQ-SAAS-001, Sub-fase 1.5 + Fase 3/6). Para cada suscripción ACTIVA/EN_GRACIA próxima a vencer, evalúa el bucket `{bucketPrevio, 0}` (aviso previo + día del vencimiento). Buckets por plan: TRIAL/PREMIUM notificables; FREE ignorado.

**Cron:** `0 15 3 * * *` (03:15 UTC diariamente — ~10 min después de SubscriptionJobService)  
**Env var:** `notificacion.vencimiento.cron`  
**Idempotente:** ✅ Sí — usa `existsIdempotente(idCompaniaPlan, tipo, canal, bucket)` antes de encolar.  
**Startup hook:** ✅ Sí

**Detalles Fase 6:**
- Lee bucket previo del dueño desde `saas.notif_buckets_globales` (destinatario DUENO)
- Fallback a `BUCKET_PREVIO_DEFAULT = 3` si tabla vacía o error
- Si bucket `activo=false` → solo evalúa día 0
- Canales por tenant: banner (siempre) + email/whatsapp según `config_notif_suscripcion.canal`
- **Decisión 2026-07-15:** WhatsApp solo en aviso previo, no día 0. Banner y email día 0 sin cambios.
- R4: opt-in WhatsApp + teléfono normalizable a E.164; si no → omite canal (sin error)

**Caveat: PhoneNumberE164Normalizer (EC-only, 2026-07-21):**
El normalizer del backend `platform-service` solo procesa números ecuatorianos (`+593` + 9 dígitos de celular). Si el teléfono del dueño se ingresó con un país diferente (ej. `+14155552671` USA):
- ✅ Se valida y guarda en BD correctamente
- ❌ **No se envía WhatsApp** — el normalizer retorna `Optional.empty()` (rechaza silenciosamente)
- ❌ No hay error registrado — la omisión es silenciosa; el dueño simplemente no recibe el aviso

Localización del validador: `platform-service/src/main/java/.../PhoneNumberE164Normalizer.java`

Todas las compañías hoy son ecuatorianas, así que este es un hallazgo teórico. Pero si en futuro hay multi-país, este es el punto de extensión. Para QA/soporte: si un dueño reporta "no recibí WhatsApp" y su número está guardado, validar que sea `+593...` (EC).

**Control de tests:** `application-test.yml`: `notificacion.vencimiento.cron: "-"` + `jobs.run-on-startup: false`.

---

## Job #4: MensajeriaJob (attendance-service)

**Ruta:** [`attendance-service/src/main/java/com/gymadmin/attendance/infrastructure/scheduler/MensajeriaJob.java`](../../../attendance-service/src/main/java/com/gymadmin/attendance/infrastructure/scheduler/MensajeriaJob.java)

**Descripción:** Avisa por WhatsApp a los socios cuya membresía está por vencer (Fase 5/6). **Solo aviso previo** (decisión 2026-07-15); no envía notificación el día del vencimiento. Consume endpoint interno de core `/internal/v1/companias/{id}/clientes-por-vencer` (no duplica detección).

**Cron:** `0 15 0 * * *` (00:15 Guayaquil time, JVM en `America/Guayaquil`)  
**Env var:** `scheduling.messaging-job-cron`  
**Idempotente:** ✅ Sí — usa `existsEnviadoHoy(idCliente, tipo, canal)` antes de enviar.  
**Startup hook:** ✅ Sí

**Detalles Fase 6:**
- Lee bucket previo del socio desde `platform-service` (`GET /internal/v1/notif-buckets/socio`)
- Fallback a `BUCKET_PREVIO_DEFAULT = 3` si platform no responde
- Si bucket `activo=false` → no envía nada
- R4: opt-in WhatsApp + teléfono normalizable; si no → skip (sin email fallback — attendance solo WhatsApp)
- RN-05: excluye clientes congelados
- Mapeo `modoControl` → plantilla HSM:
  - Calendario: `recordatorio_vencimiento_membresia`
  - Accesos: `recordatorio_vencimiento_accesos`

**Caveat: PhoneNumberE164Normalizer (EC-only, 2026-07-21):**
El normalizer del backend `platform-service` solo procesa números ecuatorianos. Si el teléfono del socio se guardó con un país diferente, el job **no enviará WhatsApp** (silenciosamente). Ver detalles en Job #3 arriba.

**Control de tests:** `application-test.yml`: `scheduling.messaging-job-cron: "-"` + `jobs.run-on-startup: false`.

---

## Job #5: CertificadoAlertaService (billing-service) — EXCEPCIÓN SIN STARTUP HOOK

**Ruta:** [`billing-service/src/main/java/com/gymadmin/billing/application/service/CertificadoAlertaService.java`](../../../billing-service/src/main/java/com/gymadmin/billing/application/service/CertificadoAlertaService.java)

**Descripción:** Alerta al administrador si sus certificados digitales P12 vencen en ≤30 días. Envía email al contacto configurado en `config_sri.email_notificacion`.

**Cron:** `0 0 8 * * *` (08:00 UTC diariamente) — **HARDCODED**, sin env var  
**Idempotente:** ❌ **No** — no valida `existsEnviadoHoy`. Envía email cada vez que corre.  
**Startup hook:** ❌ **No** — sin startup hook por no ser idempotente (spam de emails al reiniciar).

**Por qué sin idempotencia:**
- No hay tabla que persista "avisos de vencimiento ya enviados hoy"
- Reintento del job = reenvío del email
- Solución futura: agregar fila a `saas.notificaciones_suscripcion` o tabla dedicada, + chequeo `existsEnviadoHoy('CERT_VENCIMIENTO', compania, fecha)` antes de enviar

**Alternativa propuesta (Fase X):**
```sql
-- Opción A: reusar saas.notificaciones_suscripcion
-- (id_compania, tipo='CERT_VENCIMIENTO', dias_antes=30, estado='PENDIENTE')

-- Opción B: tabla dedicada saas.notificaciones_certificados
CREATE TABLE saas.notificaciones_certificados (
  id BIGSERIAL PRIMARY KEY,
  id_compania INTEGER NOT NULL,
  tipo VARCHAR(50), -- 'CERT_VENCIMIENTO'
  fecha DATE NOT NULL,
  estado VARCHAR(20), -- 'PENDIENTE', 'ENVIADO'
  fecha_envio TIMESTAMP
);
```

---

## Job #6: WhatsAppQueueProcessorJob (platform-service)

**Ruta:** [`platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/WhatsAppQueueProcessorJob.java`](../../../platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/WhatsAppQueueProcessorJob.java)

**Descripción:** Procesa la cola de mensajes WhatsApp pendientes (REQ-SAAS-001, Fase 3). Toma lotes de N (default 50) registros en estado `PENDIENTE` y los envía; transiciona a `ENVIADO` o `FALLIDO` según respuesta de API.

**Tipo:** Fixed-delay  
**Delay:** 30s (default)  
**Env var:** `notificacion.whatsapp.queue.fixed-delay-ms`  
**Batch size:** `notificacion.whatsapp.queue.batch-size` (default 50)  
**Idempotente:** ✅ Sí — procesa `PENDIENTE`; si reintentas, ya están en `ENVIADO`/`FALLIDO`

**Retry:** Configurado en el `ProcesarColaWhatsAppUseCase` (no en este job).

---

## Job #7: EmailQueueProcessorJob (platform-service)

**Ruta:** [`platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/EmailQueueProcessorJob.java`](../../../platform-service/src/main/java/com/gymadmin/platform/infrastructure/scheduler/EmailQueueProcessorJob.java)

**Descripción:** Procesa la cola de emails pendientes (REQ-SAAS-001, Sub-fase 1.5). Toma lotes de N (default 50) registros en estado `PENDIENTE` y los envía; transiciona a `ENVIADO` o `FALLIDO`.

**Tipo:** Fixed-delay  
**Delay:** 30s (default)  
**Env var:** `notificacion.email.queue.fixed-delay-ms`  
**Batch size:** `notificacion.email.queue.batch-size` (default 50)  
**Idempotente:** ✅ Sí — procesa `PENDIENTE`; re-ejecutar sin cambios

**Retry exponencial:** 30s → 2m → 10m → 1h (configurable en `ProcesarColaEmailsUseCase`).

---

## Job #8: RetrySchedulerService (billing-service)

**Ruta:** [`billing-service/src/main/java/com/gymadmin/billing/application/service/RetrySchedulerService.java`](../../../billing-service/src/main/java/com/gymadmin/billing/application/service/RetrySchedulerService.java)

**Descripción:** Procesa reintentos de envío de comprobantes al SRI. Toma lotes de N (default 10) registros en `cola_envio` con estado `PENDIENTE` e intenta (re)enviar al servicio RECEPCION/AUTORIZACION del SRI.

**Tipo:** Fixed-delay  
**Delay:** 60s — **HARDCODED**, sin env var  
**Idempotente:** ✅ Sí — si ya AUTORIZADO, no reintenta. Backoff: {1, 5, 15, 60, 240} minutos (max 5 intentos).

**Flujo de reintentos completo:** Ver [`docs/billing-service/flows/sri-submission-retry.md`](../../../docs/billing-service/flows/sri-submission-retry.md).

**Batch size:** default 10 (no configurable).

---

## Convención: Startup Hook para Recuperar Ventana Perdida

### Problema: Jobs perdidos cuando el proceso está caído

Spring `@Scheduled(cron = "...")` es **in-memory**. Si el pod está caído a las 00:10 UTC (hora de `ClienteStatusJobService`), esa ejecución se pierde indefinidamente — **no hay catch-up automático**.

**Escenario:** El monorepo corre en Kubernetes. Pod de `core-service` crashea a las 00:05 UTC. Se reinicia a las 00:20 UTC (después de que ya pasó la ventana 00:10). El cliente nunca se marca vencido hasta mañana a las 00:10.

**Impacto:**
- Clientes activos que debieron ser `vencido` siguen activos por horas/días
- Notificaciones no se envían en la ventana prevista
- Membresías no degradan a tiempo

### Solución: @EventListener(ApplicationReadyEvent.class)

**Patrón:**

```java
@Service
public class ClienteStatusJobService {

    @Value("${jobs.run-on-startup:true}")
    private boolean runOnStartup;

    @Scheduled(cron = "${client.status.job.cron:0 10 0 * * *}")
    public void ejecutar() {
        // ... lógica idempotente ...
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlIniciar() {
        if (!runOnStartup) {
            log.info("[ClienteStatusJob] Skip startup run (jobs.run-on-startup=false)");
            return;
        }
        log.info("[ClienteStatusJob] Ejecutando al arrancar (recuperación de ventana perdida)");
        ejecutar();
    }
}
```

**Comportamiento:**
- **Prod/staging:** `jobs.run-on-startup: true` (default) → se ejecuta al arrancar + a la hora cron
- **Tests:** `application-test.yml` pone `jobs.run-on-startup: false` → no dispara al iniciar Spring context (evita side effects en unit tests)

**Requisito:** El job debe ser **idempotente**. Ver sección "Tipos de scheduled jobs" arriba.

---

## Bug Histórico Corregido: ClienteStatusJobService (2026-07-16)

### Síntoma
Todos los clientes con membresía activa eran marcados como `vencido` cada noche, independientemente de su fecha real de vencimiento.

### Causa
En `procesarCliente()`, el `flatMap` externo devolvía `Mono<Void>` (no emitía valor):

```java
private Mono<Void> procesarCliente(Cliente cliente) {
    if (Cliente.Estado.congelado.equals(cliente.getEstado())) {
        return Mono.empty();
    }
    return membresiaRepository.findActivaByIdClienteAndIdCompania(...)
            .flatMap(mem -> tipoMembresiaRepository.findById(...)
                    .flatMap(tipo -> evaluarEstado(cliente, mem, tipo))
                    // ❌ AQUÍ: evaluarEstado() devuelve Mono<Void>
                    // Entonces flatMap emite vacío
            )
            .switchIfEmpty(Mono.defer(() -> {
                cliente.setEstado(Cliente.Estado.vencido);  // ← SIEMPRE se ejecuta
                return clienteRepository.save(cliente);
            }))
            .then();
}
```

`evaluarEstado()` termina con `.then()` (devuelve `Mono<Void>`). El `flatMap` de afuera no emitía nada → `switchIfEmpty` siempre disparaba → estado siempre `vencido`.

### Fix
Agregar `.thenReturn(cliente)` en el `flatMap` externo; mover `.then()` al final:

```java
private Mono<Void> procesarCliente(Cliente cliente) {
    if (Cliente.Estado.congelado.equals(cliente.getEstado())) {
        return Mono.empty();
    }
    return membresiaRepository.findActivaByIdClienteAndIdCompania(...)
            .flatMap(mem -> tipoMembresiaRepository.findById(...)
                    .flatMap(tipo -> evaluarEstado(cliente, mem, tipo))
                    .thenReturn(cliente)  // ← FIX: emitir el cliente
            )
            .switchIfEmpty(Mono.defer(() -> {
                cliente.setEstado(Cliente.Estado.vencido);
                return clienteRepository.save(cliente);
            }))
            .then();  // ← FIX: mover al final
}
```

### Lección
Cuando encadenes un `flatMap → switchIfEmpty`, el `flatMap` debe emitir un valor (no ser `Mono<Void>`) para que `switchIfEmpty` NO dispare erróneamente:

- **Bien:** `flatMap(x → x.flatMap(...).thenReturn(x))` — emite `x`
- **Mal:** `flatMap(x → x.flatMap(...).then())` — emite nada, `switchIfEmpty` siempre corre

---

## Configuración por Entorno

### Desarrollo (`.env` + `application.yml`)

```yaml
# core-service
client.status.job.cron: 0 10 0 * * *
jobs.run-on-startup: true

# platform-service
subscription.job.cron: 0 5 0 * * *
notificacion.vencimiento.cron: 0 15 3 * * *
notificacion.whatsapp.queue.fixed-delay-ms: 30000
notificacion.email.queue.fixed-delay-ms: 30000
jobs.run-on-startup: true

# attendance-service
scheduling.messaging-job-cron: 0 15 0 * * *
jobs.run-on-startup: true

# billing-service (hardcoded, sin env var)
# CertificadoAlertaService.cron: 0 0 8 * * *
# RetrySchedulerService.fixedDelay: 60000
```

### Testing (`application-test.yml`)

```yaml
# Deshabilita todos los crons (evita side effects)
client.status.job.cron: "-"
subscription.job.cron: "-"
notificacion.vencimiento.cron: "-"
scheduling.messaging-job-cron: "-"

# Deshabilita startup hooks
jobs.run-on-startup: false
```

### Production (Azure Container Instances / Kubernetes)

```yaml
# default = development, sin overrides específicos
# Sin secrets en .yml; usar variables de entorno
```

---

## Docs Relacionados

- **SRI reintentos:** [`docs/billing-service/flows/sri-submission-retry.md`](../../../docs/billing-service/flows/sri-submission-retry.md) — Detalles del backoff {1,5,15,60,240} min y cadena completa firma→envío→consulta
- **WhatsApp avisos vencimiento:** [`docs/_archive/gym-administrator/whatsapp-avisos-vencimiento.md`](../../_archive/gym-administrator/whatsapp-avisos-vencimiento.md) — Contexto de negocio, buckets {3,0}, decisión 2026-07-15 (📜 archivado tras implementarse; el estado vigente de los jobs es este mismo documento)
- **Platform service specs:** [`docs/gym-administrator/specs/platform-service.md`](../../../docs/gym-administrator/specs/platform-service.md) — RN-SAAS-002/003/010, ciclo de vida suscripción

---

## Checksum & Auditoría

| Archivo | Última verificación |
|---|---|
| `ClienteStatusJobService.java` | 2026-07-16 ✅ |
| `SubscriptionJobService.java` | 2026-07-16 ✅ |
| `NotificacionVencimientoJob.java` | 2026-07-16 ✅ |
| `MensajeriaJob.java` | 2026-07-16 ✅ |
| `CertificadoAlertaService.java` | 2026-07-16 ✅ |
| `WhatsAppQueueProcessorJob.java` | 2026-07-16 ✅ |
| `EmailQueueProcessorJob.java` | 2026-07-16 ✅ |
| `RetrySchedulerService.java` | 2026-07-16 ✅ |
| `application-test.yml` (core, platform, attendance) | 2026-07-16 ✅ |
