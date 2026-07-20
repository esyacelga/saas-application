# Plan de Pruebas — REQ-SAAS-001 Sub-fase 1.6 (Freemium/Trial + Emails transaccionales)

**Fecha:** 2026-07-12
**Ámbito:** funcionalidades introducidas en la sub-fase — activación de Trial, rechazo de pagos con notificación por email, procesamiento de la cola de notificaciones (`TRIAL_ACTIVADO`, `PAGO_RECHAZADO`) y flujos UI relacionados (Admin `MiSuscripcion` y Root `PagosPendientes`).
**Deuda técnica NO corregida:** ver [deuda-tecnica-subfase-1.6.md](deuda-tecnica-subfase-1.6.md). Los hallazgos HIGH/MEDIUM quedan pendientes y NO bloquean estas pruebas — se testea el "happy path" del negocio.

---

## 0. Pre-requisitos

### 0.1 Migración de base de datos
Aplicar el changeset nuevo antes de arrancar `platform-service`:
```powershell
cd C:\Respos\own-aplications\gym-administrator
./gradlew update
```
Debe correr el changeset **GYM-001-144** (`05_alter_table_tenant_notificaciones_suscripcion_nullable_id_compania_plan.sql`) sin errores. Idempotente — segunda corrida no falla.

**Verificar en DB:**
```sql
SELECT column_name, is_nullable
  FROM information_schema.columns
 WHERE table_schema = 'tenant'
   AND table_name = 'notificaciones_suscripcion'
   AND column_name = 'id_compania_plan';
-- Esperado: is_nullable = 'YES'
```

### 0.2 Servicios arriba
| Servicio | Puerto | Comando |
|---|---|---|
| `platform-service` | 8081 | `mvn -pl platform-service spring-boot:run` (JAVA_HOME=Zulu 25) |
| `auth-service` | 8080 | según su propia guía |
| `core-service` | 8081 | según su propia guía |
| `auth-service-frond-end` | 5173 | `cd auth-service-frond-end && npm run dev` |

### 0.3 Datos de prueba requeridos
- Al menos **1 compañía sin trial usado** (`trial_usado = false`, sin `CompaniaPlan` activa).
- Al menos **1 compañía con trial ya usado** (`trial_usado = true`).
- Al menos **1 compañía con suscripción activa** (para probar bloqueo de trial cuando ya hay plan).
- **1 usuario root** con permisos para operar `POST /platform/pagos-pendientes/{id}/rechazar`.
- **1 usuario owner (id_rol = 1)** de una compañía con trial disponible.
- **1 pago pendiente en estado `PENDIENTE`** listo para rechazar (crear vía `POST /pagos-pendientes` como owner o insertar manualmente).
- Correo real (o Mailtrap/Mailhog) configurado en `application.yml`. Confirmar dónde llegan los mails antes de empezar.

---

## 1. Backend — Escenarios de servicio (integración manual con REST client)

### T1. Activar Trial — happy path (RN-01)
- **Precondición:** compañía A con `trial_usado = false`, sin `CompaniaPlan` activa.
- **Acción:** `POST /companias/{idA}/suscripcion/trial` autenticado como owner de A.
- **Resultado esperado:**
  - HTTP 200 con `CompaniaPlan` creado: `estado = ACTIVO`, `tipoCambio = NUEVO`, `fechaInicio = hoy`, `fechaFin = hoy + 60d`.
  - Fila en `saas.companias`: `trial_usado = true`, `fecha_trial_usado ≈ now()`.
  - Fila en `saas.actividad_plataforma` con `tipo = 'TRIAL_ACTIVADO'`, `tipo_actor = 'OWNER'`, `detalle` con `fecha_inicio` y `fecha_fin`.
  - Fila en `tenant.notificaciones_suscripcion` con `tipo = 'TRIAL_ACTIVADO'`, `dias_antes = 0`, `canal = 'email'`, `estado = 'pendiente'`, `id_compania_plan = <id del trial>`.

### T2. Activar Trial — irrevocabilidad (RN-01)
- **Precondición:** compañía B con `trial_usado = true`.
- **Acción:** `POST /companias/{idB}/suscripcion/trial`.
- **Resultado esperado:** HTTP 400/409 con `TrialYaUsadoException` — mensaje "ya usó su Trial".

### T3. Activar Trial — bloqueado por suscripción activa
- **Precondición:** compañía C con `CompaniaPlan` activa (aunque `trial_usado = false`).
- **Acción:** `POST /companias/{idC}/suscripcion/trial`.
- **Resultado esperado:** HTTP 409 con `SuscripcionActivaException`.

### T4. Activar Trial — compañía inexistente
- **Acción:** `POST /companias/999999999/suscripcion/trial`.
- **Resultado esperado:** HTTP 404 (`NotFoundException`).

### T5. Rechazar pago — happy path (RN-08)
- **Precondición:** `PagoPendienteValidacion` id `P1` con `estado = PENDIENTE`, `idCompania = A`.
- **Acción:** `POST /platform/pagos-pendientes/{P1}/rechazar` como root, body `{ "motivo": "Comprobante ilegible, no se lee la fecha" }` (≥10 chars).
- **Resultado esperado:**
  - HTTP 200.
  - Fila `PagoPendienteValidacion` con `estado = RECHAZADO`, `motivo_rechazo`, `idUsuarioRootQueRechazo`, `fechaRechazo`.
  - Fila `actividad_plataforma` `PAGO_RECHAZADO` con `tipo_actor = ROOT`, `detalle.motivo` presente.
  - Fila en `tenant.notificaciones_suscripcion` con `tipo = 'PAGO_RECHAZADO'`, `id_compania_plan = NULL`, `dias_antes = 0`, `estado = pendiente`.

### T6. Rechazar pago — motivo insuficiente
- **Acción:** `POST .../rechazar` con motivo `"corto"` (< 10 chars) o `""` o sin `motivo`.
- **Resultado esperado:** HTTP 400 con `BusinessException` ("mínimo 10 caracteres").

### T7. Rechazar pago — doble rechazo (idempotencia)
- **Precondición:** pago `P1` ya rechazado en T5.
- **Acción:** repetir `POST .../rechazar` sobre `P1`.
- **Resultado esperado:** HTTP 409 con `PagoYaProcesadoException`, sin nueva fila de notificación ni de actividad.

### T8. Rechazar pago — inexistente
- **Acción:** `POST /platform/pagos-pendientes/999999999/rechazar` con motivo válido.
- **Resultado esperado:** HTTP 404 (`NotFoundException`) — el UPDATE no afectó filas.

---

## 2. Backend — Cola de emails (`ProcesarColaEmailsUseCase`)

### E1. Envío de `TRIAL_ACTIVADO`
- **Precondición:** T1 completado — hay una fila `pendiente` con `tipo = TRIAL_ACTIVADO` y `id_compania_plan` seteado; SMTP funcionando.
- **Acción:** disparar `procesarLote(10)` (por scheduler o endpoint manual si existe).
- **Resultado esperado:**
  - Notificación pasa a `estado = enviado`.
  - Email en el inbox del owner con:
    - Subject y contenido del template `trial_activado`.
    - `owner_nombre` = nombre de la compañía.
    - `fecha_vencimiento` = `hoy + 60d` en formato `dd/MM/yyyy`.
    - `plan_actual = "Trial"`, `dias_trial = 60`.
    - `url_gym_admin`, `url_comprar_premium` sustituidos.
  - Fila en `actividad_plataforma` `NOTIF_VENCIMIENTO_ENVIADA` con `detalle.tipo = 'TRIAL_ACTIVADO'`.

### E2. Envío de `PAGO_RECHAZADO`
- **Precondición:** T5 completado — hay una fila `pendiente` con `tipo = PAGO_RECHAZADO`, `id_compania_plan = NULL`; SMTP OK.
- **Acción:** disparar `procesarLote`.
- **Resultado esperado:**
  - Notificación pasa a `enviado`.
  - Email al owner con template `pago_rechazado`:
    - `owner_nombre` presente.
    - `motivo_rechazo` = motivo textual del rechazo (T5).
    - `fecha_reporte` en formato `dd/MM/yyyy`.
    - `url_reportar_pago` sustituido.
  - Registro de actividad `NOTIF_VENCIMIENTO_ENVIADA` con `detalle.tipo = 'PAGO_RECHAZADO'`.

### E3. `templateKey` — ruteo por tipo
- **Acción:** unit-check rápido (opcional). Insertar 4 filas `notificaciones_suscripcion` con `tipo` = `TRIAL_ACTIVADO`, `PAGO_RECHAZADO`, `VENCIMIENTO_TRIAL` (`dias_antes=3`), `VENCIMIENTO_PREMIUM` (`dias_antes=15`).
- **Resultado esperado:** templates usados = `trial_activado`, `pago_rechazado`, `vencimiento_3d`, `vencimiento_15d` respectivamente (verificar en el HTML entregado).

### E4. Backoff exponencial + fallido definitivo
- **Precondición:** apagar SMTP (o poner credenciales inválidas). Una notificación `TRIAL_ACTIVADO` pendiente.
- **Acción:** ejecutar `procesarLote` 5 veces con esperas (usar clock manual o tolerar los delays reales: 30s, 2m, 10m, 1h).
- **Resultado esperado:**
  - Después del intento 1 → `estado = pendiente`, `intentos = 1`, `proximo_intento` en +30s.
  - Cada retry siguiente incrementa `intentos` y ajusta `proximo_intento` con el próximo `BACKOFF`.
  - En el 5º intento fallido → `estado = fallido`, y una fila `actividad_plataforma` `NOTIF_EMAIL_FALLIDA` con `detalle.intentos = 4` y `ultimo_error`.

### E5. Compañía sin correo
- **Precondición:** compañía D con `correo = NULL` o `''`. Insertar una notificación `TRIAL_ACTIVADO` para D.
- **Acción:** `procesarLote`.
- **Resultado esperado:** notificación pasa a `fallido` con `ultimo_error = "compania sin correo"`, sin intento real de envío, y fila `NOTIF_EMAIL_FALLIDA` en `actividad_plataforma`.

### E6. Compañía inexistente
- **Precondición:** insertar manualmente una notificación con `id_compania = 999999999`.
- **Acción:** `procesarLote`.
- **Resultado esperado:** notificación `fallido` con `ultimo_error = "compania inexistente"`, sin intento de envío.

### E7. Concurrencia — `FOR UPDATE SKIP LOCKED`
- **Precondición:** ≥ 3 notificaciones `pendiente` cuya `proximo_intento <= now()`. Dos instancias de platform-service ejecutando `procesarLote(10)` simultáneamente (o dos hilos manualmente).
- **Resultado esperado:** cada notificación se procesa **una sola vez** — no hay duplicados en logs de envío ni en `actividad_plataforma`.

---

## 3. Frontend — Admin (owner) [`auth-service-frond-end` @5173]

### F1. Verificar fix del error `useNavigate`
- **Acción:** login como owner. Navegar a `/admin/dashboard`.
- **Resultado esperado:** carga sin el error `useNavigate() may be used only in the context of a <Router> component`. Consola limpia.

### F2. Página `MiSuscripcion` — trial activo
- **Precondición:** owner con trial activo (T1 aplicado).
- **Acción:** navegar a `/admin/mi-suscripcion`.
- **Resultado esperado:**
  - Muestra plan "Trial", días restantes ≈ 60, fecha vencimiento correcta.
  - Banner de "en trial" visible.
  - **NOTA (M1 de deuda):** si `diasRestantes ≤ 0`, hoy la UI no distingue "trial vencido" — está aceptado como pendiente, no marcar como bug en esta ronda.

### F3. `UpgradeModal` se abre desde el widget de plan
- **Precondición:** owner en `/admin/*`.
- **Acción:** disparar el modal (algún flow: intentar crear cliente cuando se está en el límite del plan, botón "Mejorar plan" del sidebar, etc. — el trigger real depende de `useLimitPlanModalStore`).
- **Resultado esperado:**
  - Modal aparece sin errores.
  - Click en "Ver planes" navega a `/admin/mi-suscripcion` correctamente (esto confirma que `useNavigate` ya tiene contexto).
  - Cerrar el modal funciona.

### F4. Modal NO aparece fuera del panel admin
- **Acción:** navegar a `/login` (logout previo). Recargar la página.
- **Resultado esperado:** consola limpia, no hay intentos de montar `UpgradeModal` fuera del router-tree.

### F5. `TopLoader` sigue funcionando
- **Acción:** navegar entre `/admin/dashboard` → `/admin/clientes` → `/admin/mi-suscripcion`.
- **Resultado esperado:** la barra de progreso superior aparece brevemente en cada navegación. Sin errores en consola.

---

## 4. Frontend — Root (platform) [`auth-service-frond-end` @5173]

### F6. Listado de pagos pendientes
- **Precondición:** login como usuario root; existen pagos `PENDIENTE`.
- **Acción:** navegar a `/platform/pagos-pendientes`.
- **Resultado esperado:** tabla con los pagos pendientes, columnas de compañía, plan destino, fecha reporte, monto.

### F7. Rechazo desde UI — happy path
- **Acción:** desde F6, click "Rechazar" sobre un pago, ingresar motivo válido (≥10 chars), confirmar.
- **Resultado esperado:**
  - Toast de éxito.
  - El pago desaparece del listado (o cambia a estado "RECHAZADO" si el filtro incluye rechazados).
  - Backend: se dispara T5 con todos sus efectos (fila de actividad, notificación encolada, email en E2).

### F8. Validación de motivo corto en UI
- **Acción:** intentar rechazar con motivo de 5 caracteres.
- **Resultado esperado:** UI bloquea el submit con mensaje inline o el backend responde 400 y la UI muestra toast de error.

---

## 5. Verificación end-to-end (bucle completo)

### G1. Flujo Trial completo
1. Login como owner de compañía sin trial usado.
2. Owner activa Trial desde UI (o admin ejecuta T1 vía API).
3. Verificar en DB los efectos de T1.
4. Correr `procesarLote` (o esperar al scheduler).
5. Verificar E1: email llega al inbox del owner con datos correctos.
6. Recargar `/admin/mi-suscripcion`: muestra el nuevo plan Trial.

### G2. Flujo Rechazo de pago completo
1. Owner reporta un pago desde `/admin/mi-suscripcion` (funcionalidad previa a esta sub-fase).
2. Login como root en otra sesión.
3. Root rechaza el pago (T5/F7) con motivo válido.
4. Correr `procesarLote`.
5. Verificar E2: email llega al owner con el motivo textual del rechazo.
6. Owner ve el estado actualizado en `/admin/mi-suscripcion`.

---

## 6. Regresión rápida

### R1. Sub-fase 1.5 sigue funcionando
- **Acción:** insertar manualmente una notificación con `tipo = 'VENCIMIENTO_TRIAL'`, `dias_antes = 3`, `id_compania_plan` válido. `procesarLote`.
- **Resultado esperado:** template `vencimiento_3d` se usa; email llega. La sub-fase 1.6 no rompió el ruteo por días.

### R2. Otros flujos owner
- **Acción:** listar clientes, crear cliente, listar tipos de membresía. Sanity check.
- **Resultado esperado:** sin regresiones ni errores de router (confirmando que mover `UpgradeModal` a `AdminLayout` no afectó otras vistas).

---

## 7. Suite automatizada

Cuando el ambiente esté configurado, correr:
```powershell
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-25"
mvn -f platform-service/pom.xml -P fulltest test
```
Esperado: todos los tests unitarios verdes, en particular:
- `ActivarTrialServiceTest` — verifica encolado de `TRIAL_ACTIVADO`.
- `RechazarPagoServiceTest` — verifica encolado de `PAGO_RECHAZADO`.
- `EmailQueueServiceTest` — verifica `templateKey`, backoff, marcar-fallido.

Si algún test falla, es un bloqueante — no está en la lista de deuda técnica.

---

## 8. Checklist de salida

- [ ] Migración `GYM-001-144` aplicada sin errores.
- [ ] T1 – T8 pasan.
- [ ] E1 – E7 pasan.
- [ ] F1 – F8 pasan.
- [ ] G1 y G2 completan end-to-end con email real recibido.
- [ ] R1 y R2 sin regresiones.
- [ ] `mvn ... test` verde.
- [ ] Deuda técnica documentada en [deuda-tecnica-subfase-1.6.md](deuda-tecnica-subfase-1.6.md) revisada y comprendida por el equipo.
