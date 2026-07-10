# REQ-SAAS-001 — Limitaciones conocidas y TODOs

**Estado:** Documentación de problemas hallados durante la verificación de la implementación (2026-07-10).

---

## Limitaciones en Sub-fase 1.5 (Notificaciones por email)

### L1: Destinatario hardcodeado (email del gym, no del owner staff)

**Problema:**
- Los emails de vencimiento se envían a `tenant.companias.correo` (email del gimnasio).
- No se resuelve el email del owner staff específico que contrató el servicio.

**Impacto:**
- Owner staff que no aparece en `companias.correo` no recibe notificaciones personales.

**Workaround actual:**
- Root/soporte verifica manualmente el comprobante de pago con email visible en `pagos_pendientes_validacion`.

**Solución propuesta (Sub-fase 1.6):**
- Agregar columna `email_contacto_suscripcion` en `tenant.companias`.
- Resolver en `NotificacionVencimientoJob.generarNotificaciones()` antes de encolar.

---

### L2: `fechaFin` renderizada incorrectamente en templates

**Problema:**
- Variable `{fecha_fin}` en templates se pasa como string vacío o en formato incorrecto.
- Variable `{dias_restantes}` se calcula correctamente en el job, pero no se usa en templates.

**Impacto:**
- Emails muestran "Tu plan vence en ?" en lugar de una fecha legible.

**Causa:**
- `NotificacionVencimientoJob.generarNotificaciones()` encola notificación sin pasar `fecha_fin` formateada al template.
- `EmailTemplateEngine.render()` recibe map de variables pero `fecha_fin` no está presente.

**Solución propuesta (Sub-fase 1.6):**
```java
// Antes de encolar:
Map<String, Object> variables = Map.of(
    "empresa_nombre", compania.getNombre(),
    "plan_nombre", plan.getNombre(),
    "fecha_fin", cp.getFechaFin().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
    "dias_restantes", dias_restantes
);
// Pasar a EmailQueueService.encolar(notificacion, variables);
```

---

### L3: `tenant.config_notif_suscripcion` no se consulta

**Problema:**
- Se envían siempre los 5 buckets (15, 7, 3, 1, 0 días) sin respetar `activo=false` del tenant.
- Owner no puede reducir avisos aunque configure `dias_antes=null` en `config_notif_suscripcion`.

**Impacto:**
- Spam de emails al owner; no hay forma de opt-out en fase 1.

**Causa:**
- `NotificacionVencimientoJob` itera buckets estáticos `List.of(15, 7, 3, 1, 0)`.
- No filtra por `SELECT ... FROM config_notif_suscripcion WHERE id_compania = ? AND dias_antes = ? AND activo = TRUE`.

**Solución propuesta (Sub-fase 1.6):**
```java
// En generarNotificaciones():
for (Integer bucket : BUCKETS) {
    if (existeConfigActiva(compania.getId(), bucket)) {
        encolarNotificacion(cp, tipo, bucket);
    }
}
```

---

### L4: Templates `pago_rechazado` y `trial_activado` no existen

**Problema:**
- Esos eventos no disparan emails en fase 1.
- Si se agregan endpoints para rechazar pago (Sub-fase 1.4) o activar Trial (Sub-fase 1.3), no hay template.

**Impacto:**
- Owner no recibe confirmación de Trial activado ni notificación de pago rechazado.

**Solución propuesta (Sub-fase 1.6):**
- Crear `email-templates/pago_rechazado.{html,txt}` con variable `{motivo_rechazo}`.
- Crear `email-templates/trial_activado.{html,txt}` con variable `{fecha_fin_trial}`.
- Encolar automáticamente tras `RechazarPagoService.rechazar()` y `ActivarTrialService.activar()`.

---

## Limitaciones en Sub-fase 1.4 (Endpoints REST)

### L5: Integración con core-service aún no implementada (Sub-fase 1.4b)

**Problema:**
- `LimiteRecursoService.contarUsoActual(CLIENTES_ACTIVOS)` hace HTTP call a core-service.
- Endpoint `GET /internal/v1/companias/{id}/clientes-activos/count` **no existe** en core-service.
- Hoy retorna 0 (fail-open), lo que permite crear clientes sin verificar límite real.

**Impacto:**
- Free plan con 50 clientes puede crear cliente #51 (bloqueo inefectivo).

**Solución pendiente (Sub-fase 1.4b):**
- Exponer en `core-service`: `GET /internal/v1/companias/{id}/clientes-activos/count` → `{ count: 48 }`.
- Completar `LimiteRecursoService.contarUsoActual(CLIENTES_ACTIVOS)` para HTTP call real.
- Completar integración de auth-service para contar `staff`.

---

### L6: Rate limiter en Postgres sin métricas Micrometer

**Problema:**
- `PostgresRateLimiter` usa tabla `rate_limit_buckets` como contador distribuido.
- No expone métricas a Prometheus (falta wiring de `MeterRegistry`).

**Impacto:**
- Operaciones no pueden observar cuántas request están siendo limitadas.

**Solución propuesta (Sub-fase 1.6):**
```java
// En PostgresRateLimiter:
private final MeterRegistry meterRegistry;
// Agregar: meterRegistry.counter("saas.rate_limit_exceeded", ...).increment();
```

---

### L7: Cloudinary URL pública vs autenticada (comprobantes)

**Problema:**
- Endpoint `/api/v1/plataforma/pagos-pendientes/{id}` retorna `comprobante_url` que es Cloudinary URL.
- Si Cloudinary está configurado con `access_mode=authenticated`, root sin sesión activa en Cloudinary no ve el archivo.

**Impacto:**
- Root backend ve URL pero no puede abrir comprobante en navegador (401).

**Solución propuesta (Sub-fase 1.6):**
- Pasar `signed_url=true` al subir a Cloudinary → generar URL con signature temporal.
- O: renderizar preview en visor embebido en React que autentica con sesión backend.

---

## TODOs técnicos sin cerrar

### T1: Caché Caffeine en-process no wireado

**Ubicación:** `EmailTemplateEngine` carga templates del classpath una sola vez.

**Estado:** Cache en memoria implementado, pero podría mejorarse con `Caffeine` para LRU con TTL.

**Prioridad:** Baja — templates son archivos estáticos.

---

### T2: Auditoría detallada del cambio de estado

**Ubicación:** `SubscriptionJobService` no registra en `saas.actividad_plataforma` los estados transitorios.

**Problema:** Si un plan entra en EN_GRACIA y luego pasa a VENCIDO dentro de un mismo día de job, solo se registra VENCIDO.

**Solución:** Registrar cada transición de estado como evento auditado separado.

**Prioridad:** Media — útil para debugging, no crítico.

---

### T3: Invalidación de caché Redis en SubscriptionJobService

**Ubicación:** `SubscriptionJobService` debe invalidar `modulo_check:{tenant_id}:*` tras cada degradación.

**Estado:** Código presente pero verificar que se ejecuta en orden correcto (después del UPDATE, antes de retorno).

**Prioridad:** Alta — sin esto, tenants pueden ver módulos por hasta 5 min (TTL) sin derecho.

---

### T4: Tests con Clock.fixed() para time-travel

**Ubicación:** `NotificacionVencimientoJobTest` debe usar `Clock.fixed()` para verificar transiciones exactas.

**Estado:** Constructor existe, tests aún no verifica degradación en día 61.

**Prioridad:** Alta — es test de aceptación crítica.

---

### T5: Tests de concurrencia (pg_advisory_lock)

**Ubicación:** `LimiteRecursoServiceTest` debe simular dos requests simultáneos.

**Problema:** Validar que advisory lock serializa correctamente.

**Prioridad:** Alta — crítico para evitar race conditions en Free plan.

---

## Checklist para Sub-fase 1.6

- [ ] Implementar integración core-service (L5)
- [ ] Actualizar templates de email con variables formateadas (L2)
- [ ] Agregar filtrado por `config_notif_suscripcion.activo` (L3)
- [ ] Crear templates `pago_rechazado.{html,txt}` y `trial_activado.{html,txt}` (L4)
- [ ] Crear email_contacto_suscripcion column (L1)
- [ ] Wiring de MeterRegistry en PostgresRateLimiter (L6)
- [ ] Tests de concurrencia con advisory lock (T5)
- [ ] Tests de time-travel con Clock.fixed() (T4)
- [ ] Auditoría detallada de transiciones (T2)
- [ ] Verificar invalidación de caché Redis (T3)
- [ ] Code review + verificación de seguridad (uploads, MIME validation)

---

*Documento de limitaciones · REQ-SAAS-001 · 2026-07-10*
