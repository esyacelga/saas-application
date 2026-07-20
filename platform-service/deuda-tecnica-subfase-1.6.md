# Deuda técnica — REQ-SAAS-001 Sub-fase 1.6 (Freemium)

**Fecha del registro:** 2026-07-12
**Estado global:** funcionalidad completa (backend + DB + frontend + templates + docs).
**Reviews aplicadas:** `code-reviewer` + `security-reviewer` (paralelos).
**Decisión:** los hallazgos se dejan documentados y quedan **pendientes** — NO bloquean pruebas manuales de la sub-fase.

---

## 🔴 HIGH — Fix antes del próximo release

### H1. Stored XSS vía `motivo_rechazo` en emails
- **Archivo:** `platform-service/src/main/java/com/gymadmin/platform/infrastructure/email/EmailTemplateEngine.java:71-79`
- **Síntoma:** `renderString` hace `String.replace("{motivo_rechazo}", value)` sin escapar HTML. Un operador `soporte` con motivo `</div><img src=x onerror=...>` inyecta markup literal en el inbox del owner.
- **Actor:** soporte (o token soporte comprometido).
- **Fix:** aplicar `StringEscapeUtils.escapeHtml4(value)` sólo para templates `.html`; los `.txt` se dejan sin escapar. Escapar también `owner_nombre` y cualquier campo user-controlled (razón social de compañía, etc.).

### H2. Hexagonal leak — `EmailQueueService` importa `infrastructure.email`
- **Archivo:** `platform-service/src/main/java/com/gymadmin/platform/application/service/EmailQueueService.java:16`
- **Síntoma:** application service depende directamente de `infrastructure/*`.
- **Fix:** crear puerto `domain/port/out/EmailTemplateEnginePort` con `RenderedEmail` como valor de dominio; `EmailTemplateEngine` pasa a ser el adapter.

### H3. Hexagonal leak — services importan `infrastructure.exception.*`
- **Archivos:** `ActivarTrialService.java:16`, `RechazarPagoService.java:10-11` — usan `NotFoundException` y `BusinessException`.
- **Síntoma:** application layer depende de excepciones de infrastructure.
- **Fix:** mover ambas a `domain/exception/` (donde ya viven `TrialYaUsadoException`, `PagoYaProcesadoException`, `SuscripcionActivaException`). Actualizar mapping en `GlobalExceptionHandler`.

### H4. `PagoPendienteValidacionPersistenceAdapter.findUltimoRechazadoByCompania` traga todos los errores
- **Archivo:** `platform-service/src/main/java/com/gymadmin/platform/infrastructure/adapter/out/persistence/adapter/PagoPendienteValidacionPersistenceAdapter.java:151-159`
- **Síntoma:** `onErrorResume(e -> Mono.empty())`. Combinado con el fire-and-forget del encolado, un fallo real de DB → email con `motivo_rechazo=""` y sin señal alguna.
- **Fix:** eliminar el `onErrorResume` blanket; que propague el error para que el caller decida (retry / `marcarFallido`).

### H5. Fire-and-forget de emails sin audit trail
- **Archivos:** `ActivarTrialService.java:140`, `RechazarPagoService.java:99`
- **Síntoma:** si falla el encolado (schema drift, FK, saturación) sólo queda un `log.warn` — sin fila en `actividad_plataforma` y sin manera de detectar en prod. Un atacante que sature `notificaciones_suscripcion` bloquea avisos silenciosamente.
- **Fix:** en el `doOnError`, registrar `actividad_plataforma.tipo = "EMAIL_ENCOLADO_FALLIDO"` con `{tipo_email, id_compania, error_class}` antes del `onErrorResume`.

---

## 🟡 MEDIUM — Follow-up en próximas iteraciones

### M1. UI: `diasRestantes ≤ 0` no muestra "trial vencido"
- `auth-service-frond-end/src/ui/features/admin/pages/MiSuscripcionPage.tsx:392-393`
- Falta branch explícito para trial vencido; usuario ve "0 días" o negativo sin alerta clara.

### M2. Fechas de email hardcodeadas a `ZoneOffset.UTC`
- `EmailQueueService.java:196`
- Tenants EC ven la fecha 1 día antes para reportes hechos después de 19:00 local. Usar `America/Guayaquil` (o timezone del tenant en el futuro).

### M3. `templateKey` fallthrough silencioso
- `EmailQueueService.java:214-219`
- Un `tipo` desconocido en el futuro cae a `"vencimiento_0d"` sin log. Retornar clave distinta + WARN.

### M4. Strings mágicos duplicados
- `"TRIAL_ACTIVADO"` y `"trial_activado"` en `ActivarTrialService.java:132`. Ya existe `EmailQueueService.TIPO_TRIAL_ACTIVADO`.
- Fix: promover a enum o clase de constantes de dominio.

### M5. Sin rate-limit en `POST /companias/{id}/suscripcion/trial`
- `SuscripcionController.java:154-162`
- Aunque es idempotente después de `trial_usado=true`, cada intento hace 2 hits de DB antes de fallar. Aplicar `RateLimiterPort` con clave `trial:{idCompania}` (3/hora) — mismo patrón que `PagoOwnerController.reportar-pago`.

### M6. `findUltimoRechazadoByCompania` no identifica el pago específico
- `EmailQueueService.java:149-153`
- Dos rechazos rápidos → el email del primero puede renderizar motivo del segundo. Guardar `id_pago_pendiente` en `notificaciones_suscripcion` y usarlo como filtro.

### M7. `err.getMessage()` en WARN puede leakear internals
- `EmailQueueService.java:244-246`, `RechazarPagoService.java:97`, `ActivarTrialService.java:138`
- Constraint names, hostnames, connection strings.
- Fix: `log.warn("...: {}", err.getClass().getSimpleName())`. Reservar `getMessage()` para DEBUG.

### M8. UI: `Step3Plan.tsx` traga error de `getPlanes()`
- `auth-service-frond-end/src/ui/features/platform/pages/RegistrarGymWizard/steps/Step3Plan.tsx:37`
- `.catch(() => {})` sin toast — wizard muestra lista vacía sin feedback.

### M9. Repositorio de pagos no filtra `eliminado = false`
- `PagoPendienteValidacionPersistenceAdapter.java:51-67, 138-148`
- `listar` y `listarPorCompania` no aplican convención soft-delete del `CLAUDE.md`. Si algún día se soft-deletea, reaparecen en la bandeja.

### M10. `listar()` de root sin enforcement estructural
- `PagoPendienteValidacionPersistenceAdapter.java:51-66`
- Consulta cross-tenant intencional (bandeja root), pero nada evita que un dev futuro la conecte a un endpoint de owner → leak inmediato.
- Fix: renombrar a `listarRoot`, o anotar `@PlatformOnly` con lint, o assertion en el método.

### M11. Column comment sin CHECK que lo enforce
- `gym-administrator/db/scripts/202605_GYM-001/ddl-freemium/05_alter_table_tenant_notificaciones_suscripcion_nullable_id_compania_plan.sql:47`
- El comment dice "NULL sólo para transaccionales sin plan" pero DB no lo enforce. Un INSERT futuro con `tipo='VENCIMIENTO_TRIAL'` y `id_compania_plan=NULL` pasaría.
- Fix opcional: `CHECK (id_compania_plan IS NOT NULL OR tipo = 'PAGO_RECHAZADO')`.

---

## ⚠️ Out-of-scope pero registrado

### O1. Ubicación del changeset `GYM-001-144` vs baseline invariant
- Vive en `gym-administrator/db/scripts/202605_GYM-001/ddl-freemium/`, técnicamente rompe la regla "each table defined once".
- Alternativas: (a) editar directamente `ddl/21_...sql` (safe porque el story es single-pass baseline), o (b) abrir nuevo story `202607_GYM-002/`.

### O2. `marcarAprobado/marcarRechazado` aceptan `null` para `idUsuarioRoot`
- `PagoPendienteValidacionPersistenceAdapter.java:132-134, 174-176`
- Atribución perdida silenciosamente si JWT parsing falla. Coordinar con equipo auth-service.

---

## ✅ Aprobado (patrones correctos, para referencia futura)

- Tests unitarios sin `Strictness.LENIENT`, `ArgumentCaptor` sobre `EncolarNotificacionCommand` con asserts explícitos de `templateKey`, `diasAntes`, `tipo`.
- `Mono.defer(...)` alrededor de writes post-send en `EmailQueueService.java:169-170` evita re-subscripción del publisher completado.
- Changeset idempotente vía `DO $$ ... IF is_nullable='NO' THEN ALTER ... END IF; $$`.
- Route guards (`AuthGuard`, `PlatformGuard`) chequean `initialized` antes de auth → evita flash-of-unauthenticated-content.
- `idUsuarioRoot` sale del JWT (`principal.getUserId()`), no del body → no spoofable.
- `idCompania` para activar trial validado por `AccessControlService.requireOwnerOrAdminOfCompania`.
- `marcarRechazado` bindea el motivo con `.bind("motivo", motivo)` → no SQL injection.
