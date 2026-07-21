# Changeset 2026-07-21 — Registro manual de asistencias, estado inicial de cliente y ajustes de UI

> Documenta los cambios pendientes de commit al 2026-07-21. Agrupados por feature.
> Ámbitos tocados: `core-service`, `auth-service-frond-end`, `docs`.

---

## 1. Cliente sin membresía nace `vencido` (core-service)

**Qué cambió:** al registrar un cliente nuevo, su estado inicial pasa de `activo` a `vencido`.
Un cliente recién creado aún no tiene membresía, así que `activo` era inconsistente. La venta de
una membresía lo pasa a `activo` (lo maneja `MembresiaService`).

**Archivos:**
- `core-service/.../application/service/ClienteService.java` — en `registrar(...)` y
  `registrarDesdeApp(...)`, `cliente.setEstado(Cliente.Estado.vencido)` (antes `activo`).
- `core-service/.../unit/ClienteServiceTest.java` — los tests `creaClienteConNuevaPersona` y el de
  `registrarDesdeApp` ahora afirman `estado == vencido`.

**Impacto operativo:** para que aplique a clientes nuevos hay que reiniciar core-service (8083).
Clientes ya existentes no cambian de estado retroactivamente.

---

## 2. Registro manual de asistencia desde el calendario (heatmap) — panel admin

**Qué cambió:** en el detalle del cliente → pestaña **Asistencias**, ahora se puede hacer clic en una
celda del calendario para agregar una asistencia manual en esa fecha.

- **Celdas registrables:** día del mes actual, **sin** asistencia y **no** futuro. Muestran cursor de
  mano y son accesibles por teclado (`role=button`, `tabIndex`, Enter/Espacio).
- **Celdas no clickeables:** días con asistencia (verdes) y días futuros.
- **Flujo:** clic → `confirmDialog` ("¿Agregar una asistencia para {nombre} el {fecha}?") → al aceptar,
  se registra en esa fecha **a las 06:00** (hora fija, no editable, y no se muestra en el mensaje) y se
  recargan calendario + estadísticas.
- **Errores:** si el backend rechaza (p. ej. sin membresía activa en esa fecha, o ya registrado ese
  día), se muestra el mensaje del sobre de error estandarizado vía `getApiErrorMessage`.

**Archivos:**
- `auth-service-frond-end/.../http/attendance/AttendanceHttpRepository.ts` — `registrarManual(idCliente,
  opts?)` ahora acepta `{ fecha, horaEntrada }` opcionales y los envía como `fecha` / `hora_entrada`.
  El endpoint backend `POST /asistencias/manual` ya soportaba ambos campos (opcionales en
  `RegistrarManualRequest`).
- `auth-service-frond-end/.../features/core/pages/ClientesPage.tsx`:
  - `AsistenciasHeatmap` recibe `onSelectDia?` y marca celdas registrables como clickeables.
  - `AsistenciasClienteTab` propaga `onSelectDia`.
  - Nuevo handler `handleSelectDiaHeatmap` (confirmDialog + `registrarManual` con `horaEntrada: '06:00:00'`).
  - Import de `getApiErrorMessage` / `getApiErrorStatus`; el componente principal ahora también
    desestructura `i18n` de `useTranslation`.
- i18n (`es.json` / `en.json`), sección `asistencias`: `agregarManualCeldaTitle`,
  `agregarManualCeldaHeader`, `agregarManualCeldaMsg`, y las leyendas `leyendaAsistio` / `leyendaAusente`.

**Nota backend:** `/asistencias/manual` valida acceso de membresía (`CoreServiceClient.validarAcceso`).
Si el cliente no tenía acceso en esa fecha, el registro se rechaza. Para saltar esa validación existe
`/asistencias/manual/override` (solo dueño/admin), que este flujo **no** usa.

---

## 3. Calendario (heatmap) solo del mes actual con número de día

**Qué cambió (contexto de la misma sesión, ya en `ClientesPage.tsx`):** el heatmap ahora dibuja solo
los días del **mes actual** (día 1 → último día), con el **número de día** centrado en cada celda y un
encabezado localizado de mes/año. Fechas parseadas/formateadas en hora **local** para evitar el desfase
UTC (`new Date('YYYY-MM-DD')` / `toISOString()`) en zonas con offset (Ecuador, UTC-5).

**Limitación conocida:** el backend (`ultimos30Dias`) solo devuelve los últimos 30 días. En meses de
31 días los primeros días pueden quedar "sin dato". Ampliar el mes completo requeriría tocar el endpoint.

---

## 4. Tope de asistencias previas al total del pack (CargarAsistenciasModal)

**Qué cambió:** el modal de carga de asistencias históricas ahora valida que la cantidad no supere el
total de días de acceso del pack.

**Archivos:**
- `auth-service-frond-end/.../features/core/components/CargarAsistenciasModal.tsx` — nueva prop
  `diasAccesoTotal`; el input añade `max`, valida antes de enviar y muestra un hint "(máx N)".
- i18n `membresias`: `asistPreviasMaxError`, `asistPreviasMaxHint`.

---

## 5. Método de pago por defecto en CompletarVentaClienteModal

**Qué cambió:** al abrir el modal de completar venta, el método de pago se preselecciona en "Efectivo"
(o el primero disponible) en lugar de quedar sin selección.

**Archivos:**
- `auth-service-frond-end/.../features/core/components/CompletarVentaClienteModal.tsx` — `reset(...)`
  ahora calcula `metodoPorDefecto` (busca "efectivo" case-insensitive, con fallback a `metodos[0]`).

---

## 6. Opción "Cuentas App" oculta del menú admin

**Qué cambió:** se retiró temporalmente el ítem "Cuentas App" (`nav.appAccounts` → `/admin/clientes/app`)
del sidebar. Solo se comentó el NavItem; ruta, página y traducciones siguen intactas.

**Archivos:**
- `auth-service-frond-end/.../layouts/AdminLayout.tsx` — NavItem comentado en `ALL_NAV_ITEMS`; import
  del icono `Smartphone` retirado para no dejar import sin uso.
- Documentado en `docs/auth-service-frond-end/features-ocultas.md` (nuevo) + entradas en
  `docs/STATUS.md` e `docs/auth-service-frond-end/INDEX.md`.

**Cómo restaurar:** ver `features-ocultas.md`.

---

## 7. Badge "Pendiente" de solicitud (i18n)

Nueva clave `clientes.badgeSolicitudPendiente` ("Pendiente" / "Pending") — usada para marcar clientes
con solicitud de membresía pendiente en la lista.

---

## Verificación

- `auth-service-frond-end`: `npx tsc -b --noEmit` → limpio.
- `core-service`: los unit tests de `ClienteServiceTest` fueron actualizados a `vencido`; correr
  `mvn test` (con JAVA_HOME en Zulu 25) para confirmar.
