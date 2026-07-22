# Estado del proyecto — Mapa de implementación

> **Propósito:** Fuente única de verdad sobre **qué está construido hoy** vs. **qué es solo diseño**. Antes de implementar o de confiar en un documento como referencia, consulta aquí su estado.
>
> Última verificación contra el código: **2026-07-10** (general) · **2026-07-14** (billing-service, tras cierre de Fase 3 SRI 2026) · **2026-07-19** (reestructuración de docs + contrato de errores) · **2026-07-21** (bootstrap de métodos de pago en wizard de compañía · asistencias manuales desde heatmap · estado inicial de cliente `vencido` · zona horaria Ecuador uniforme en los 6 microservicios · **GYM-003: estado de pago en membresías end-to-end** backend + PWA).

---

## ✅ Actualización 2026-07-21 (GYM-003: estado de pago en membresías, parte A implementada)

**Implementación completada del backend y PWA de miembros** para la HU GYM-003 — "Estado de pago en ventas de membresía":

- **core-service**: DTO `MembresiaResponse` extendido con campos `tipoNombre`, `modoControl`, `montoPagado`, `saldoPendiente`, `diasAccesoUsados`/`diasAccesoRestantes`. Nuevo factory method `from(MembresiaHistorialItem)` para endpoints de historial enriquecido.
- **Nuevo endpoint**: `GET /api/v1/clientes/me/membresias` — PWA de miembros consulta sus membresías sin parámetro de ruta (resuelve `id_cliente` del JWT).
- **Cambio de comportamiento**: `GET /api/v1/clientes/{id}/membresias` ahora **incluye** membresías con `eliminado = true` (rechazadas), mostrando su `motivo_eliminacion` (`SOCIO_CAMBIO_OPINION`, `ERROR_DE_VENTA`, `DUPLICADA`, `DATOS_INCORRECTOS`, `OTRO`). Bonus: agrega filtro `id_compania` faltante (hardening).
- **gym-member-pwa**: Nueva página `HistorialPagosMembresiaPage.tsx` en `/membresia/historial` — lista todas las membresías del socio con tarjetas expandibles mostrando monto pagado, saldo pendiente, y si aplica: accesos usados/totales y motivo de eliminación. Llama `coreRepository.misMembresias()` que consume el nuevo endpoint.
- **Interfaces TypeScript**: `MembresiaHistorialItem` con todos los campos nuevos; `CoreHttpRepository.misMembresias()` retorna `Promise<MembresiaHistorialItem[]>`.
- **i18n agregado**: namespace `pagoHistorial.*` + claves `estadoPago`, `motivoEliminacion`, `card.labels`, `card.expand`, `card.collapse`, etc. (en `es.json` y `en.json`).
- **Documento de requerimiento actualizado**: [estado-pago-membresias.md](gym-administrator/requirements/estado-pago-membresias.md) — marcado como ✅ **Implementado** con split de alcance: HU-A (backend + PWA, completada), HU-B (compra autoservicio desde PWA, pendiente), HU-C (integración billing, pendiente).

**Qué NO está implementado en esta iteración** (OUT, para HU-B y HU-C):
- Compra desde PWA (`POST /clientes/me/membresias/solicitar` existe en backend pero PWA no la llama).
- Dashboard admin "Ventas pendientes" — backend listo (`GET /companias/{id}/membresias/pendientes`), UI no.
- Integración facturación (table `core.pagos`, evento `MembresiaPagadaEvent` emitido pero sin consumidor).

**Detalle técnico**: el DTO tiene dos factory methods: `from(Membresia)` para endpoints "crudos" (devuelve campos null para tipo/modo/accesos), `from(MembresiaHistorialItem)` para historial (campos rellenos). Decisión de arquitectura: minimiza la lógica de enriquecimiento en la capa presentation, centraliza en use case.

---

## ✅ Actualización 2026-07-21 (`ci_validada` en registro de cliente admin + recálculo al editar CI)

- **Registro de cliente desde el panel admin** (`core-service`): ahora calcula el dígito verificador ecuatoriano (módulo 10) y puebla `identidad.personas.ci_validada` al crear la persona. Antes esta ruta dejaba el flag en `FALSE` (default BD) aun para cédulas EC válidas. Nueva copia idéntica del algoritmo `core-service/.../domain/validation/CedulaEcuatoriana.java` (4ª copia: front + platform + auth + core, deben mantenerse iguales). Wiring en `PersonaPersistenceAdapter.create`.
- **Recálculo al editar CI** (`auth-service`): `PersonaMapper.toEntity` ahora calcula `ci_validada` **siempre** desde el `ci` actual (INSERT y UPDATE), no solo al crear. El panel admin permite editar el CI (`PUT /personas`); corregirlo a una cédula válida actualiza el flag. Idempotente (`esValida` es función pura del `ci`).
- **Pendiente restante**: backfill de personas previas + exposición REST del flag. ⚠️ No retroactivo; requiere reiniciar core (8083) y auth (8080).
- **Detalle**: [_changesets/2026-07-21-ci-validada-registro-admin.md](_changesets/2026-07-21-ci-validada-registro-admin.md) · [pendientes/validacion-cedula-persona.md](gym-administrator/pendientes/validacion-cedula-persona.md).

---

## ✅ Actualización 2026-07-21 (zona horaria Ecuador uniforme en los 6 microservicios)

- **Zona horaria uniformada a `America/Guayaquil` (UTC-5)** en los 6 servicios. Antes había 3 formas distintas: attendance/finance fijaban zona JVM + Jackson; core/billing solo el `Clock` de negocio; platform usaba `Clock.systemUTC()` (**bug**); auth nada.
- **Bug corregido en `platform-service`**: `ClockConfig` pasó de `Clock.systemUTC()` a `Clock.system(America/Guayaquil)`. De noche (19:00–24:00 Ecuador) el "hoy" de negocio (`LocalDate.now(clock)`) se adelantaba un día en jobs de suscripción/vencimiento/buckets. Los `Instant.now(clock)` no se afectan (instante absoluto). **224 unit tests verdes** tras el cambio.
- **JVM default zone**: se añadió `TimeZone.setDefault("America/Guayaquil")` en `main()` de core, platform, billing y auth (attendance/finance ya lo tenían).
- **Jackson**: se añadió `spring.jackson.time-zone: America/Guayaquil` (+ `write-dates-as-timestamps: false`) en core, billing, auth y platform. ⚠️ **platform NO** usa `SNAKE_CASE` (sus DTOs son camelCase y el frontend depende de ello) — no se tocó su naming strategy.
- **Sin migración de datos**: las columnas de auditoría son `TIMESTAMPTZ` (instante absoluto), nunca se corrompieron. ⚠️ Requiere **reiniciar** los servicios para tomar la zona (se fija al arranque).
- **Detalle**: [_changesets/2026-07-21-zona-horaria-ecuador-uniforme.md](_changesets/2026-07-21-zona-horaria-ecuador-uniforme.md).

---

## ✅ Actualización 2026-07-21 (asistencias manuales desde heatmap + estado inicial de cliente)

- **Cliente sin membresía nace `vencido`** (`core-service`): `ClienteService.registrar` y `registrarDesdeApp` fijan `estado = vencido` (antes `activo`). Un cliente recién creado no tiene membresía; la venta lo pasa a `activo` (`MembresiaService`). Unit tests actualizados. ⚠️ Requiere reiniciar core-service (8083) para aplicar a clientes nuevos; no es retroactivo.
- **Registro manual de asistencia desde el calendario** (panel admin, detalle de cliente → pestaña Asistencias): clic en una celda del heatmap (día del mes, sin asistencia y no futuro) abre un `confirmDialog` que registra la asistencia en esa fecha **a las 06:00** (hora fija, no mostrada). Usa `POST /asistencias/manual` (que ya aceptaba `fecha`/`hora_entrada` opcionales); `attendanceRepository.registrarManual` se extendió para enviarlos.
- **Heatmap solo del mes actual con número de día**: el calendario dibuja solo el mes en curso, con el número de día centrado y encabezado localizado; fechas en hora local para evitar desfase UTC. Limitación: el backend `ultimos30Dias` solo trae 30 días.
- **Tope de asistencias previas** (`CargarAsistenciasModal`): valida que la cantidad no supere el total de días de acceso del pack (nueva prop `diasAccesoTotal`, hint "(máx N)").
- **Método de pago por defecto** en `CompletarVentaClienteModal`: preselecciona "Efectivo" (o el primero disponible).
- **Detalle**: [_changesets/2026-07-21-asistencias-manual-y-estado-inicial.md](_changesets/2026-07-21-asistencias-manual-y-estado-inicial.md).

---

## ✅ Actualización 2026-07-21 (panel admin: opción "Cuentas App" oculta)

- **"Cuentas App" oculta del menú**: en el panel admin se retiró temporalmente el ítem de navegación "Cuentas App" (`nav.appAccounts` → `/admin/clientes/app`). Solo se comentó el NavItem en `AdminLayout.tsx`; la ruta, la página `ClientesAppPage.tsx` y las traducciones `appAccounts.*` siguen intactas — el módulo es accesible por URL directa y la reversión es de una línea.
- **Documento creado**: [auth-service-frond-end/features-ocultas.md](auth-service-frond-end/features-ocultas.md) — registro de opciones ocultas a propósito y cómo restaurarlas.

---

## ✅ Actualización 2026-07-21 (documentación: bootstrap de métodos de pago en wizard)

- **Bootstrap automático de métodos de pago**: ✅ implementado en `platform-service` (`CompaniaService.registrarGymWizard`, `MetodoPagoPersistenceAdapter`). Al registrar una compañía nueva, se crean automáticamente 3 métodos de pago por defecto (Efectivo, Tarjeta, Transferencia) en `config.metodos_pago`, idempotente por nombre.
- **Documento creado**: [platform-service/wizard-bootstrap.md](platform-service/wizard-bootstrap.md) — especificación completa del flujo de bootstrap de compañía (7 pasos), incluyendo la creación de métodos de pago, consumidores (core-service `GET /metodos-pago`), arquitectura cross-schema, y consideraciones de operación.
- **Índice actualizado**: [platform-service/INDEX.md](platform-service/INDEX.md) — enlace al nuevo documento de wizard/bootstrap.

---

## ✅ Actualización 2026-07-19 (reestructuración de documentación + contrato de errores)

- **Contrato de errores estandarizado (RFC 7807 + `codigo`)**: ✅ implementado en los **6 microservicios** (core, billing, finance, attendance, platform, auth) + frontend transversal. Ver [gym-administrator/architecture/error-contract.md](gym-administrator/architecture/error-contract.md).
- **Avisos WhatsApp de vencimiento**: ✅ implementados (jobs `NotificacionVencimientoJob`, `WhatsAppQueueProcessorJob`, `MensajeriaJob`, adapter Meta). El doc de diseño se archivó (ver abajo); el estado vigente vive en [scheduled-jobs.md](gym-administrator/architecture/scheduled-jobs.md).
- **Documentación histórica archivada** en [`_archive/`](_archive/README.md): `impl/`, `backend-prompts/`, `preguntas/` y decisiones del panel admin; `anulacion-sri.md` e `integracion.md` de billing; `restructuracion-onboarding-facturacion.md` y `whatsapp-avisos-vencimiento.md` de gym-administrator. **No** describen el sistema actual.
- **Renombres a kebab-case**: `platform-service/{deuda-tecnica,plan-pruebas}-subfase-1.6.md`; `docs/auth-service-frond-end/INDEX-api.md` (antes `api-index.md`).
- **Encabezados corregidos**: las specs de `finance-service` y `billing-service` decían "sin implementar" — ambos servicios ya están implementados (🟡 spec histórica).

---

## ✅ Actualización 2026-07-16 (consolidación final de migraciones WhatsApp)

**Fase de consolidación completada:** La BD se recreó desde cero eliminando el volumen Docker. Las stories incrementales `202607_GYM-002` (opt-in WhatsApp para consentimiento) y `202607_GYM-003` (tabla `saas.notif_buckets_globales` para buckets globales de aviso) fueron **consolidadas directamente en la baseline `202605_GYM-001`** al aplicar las migraciones:

- `202607_GYM-002` → Sus columnas `acepta_whatsapp BOOLEAN NOT NULL DEFAULT FALSE` + `fecha_consentimiento_wa TIMESTAMPTZ` ahora están integradas en los `CREATE TABLE` de:
  - `gym-administrator/db/scripts/202605_GYM-001/ddl/14_create_table_identidad_personas.sql` (schema `identidad.personas`)
  - `gym-administrator/db/scripts/202605_GYM-001/ddl/16_create_table_tenant_companias.sql` (schema `tenant.companias`)

- `202607_GYM-003` → Su tabla `saas.notif_buckets_globales` ahora es el script de baseline:
  - `gym-administrator/db/scripts/202605_GYM-001/ddl/70_create_table_saas_notif_buckets_globales.sql` (changeSet `GYM-001-70`, seed `socio=3, dueno=3`)

- `main-changelog.yml` tiene un único include: `202605_GYM-001/partial-changelog.yml` con 98 changesets (`GYM-001-01..98`).
- BD recreada desde cero: 69 tablas verificadas contra `information_schema.tables`, 98/98 changesets "up to date".

**Consolidación anterior (2026-07-10):** Las tres stories de migración (`202605_GYM-001` core, `202607_GYM-002` facturación, `202608_GYM-003` freemium) se **fusionaron en una única story `202605_GYM-001`** con tres subcarpetas por dominio:

- `ddl/` — 10 schemas base + 45 tablas CREATE (saas, identidad, tenant, core, asistencia, finanzas, marketing, inventario, config, seguridad).
- `ddl-facturacion/` — schemas `sri` + `facturacion` y sus 22 tablas CREATE.
- `ddl-freemium/` — extras REQ-SAAS-001 (`tenant.pagos_pendientes_validacion`, `saas.config_plataforma`, seed).

Ya no existen scripts `ALTER`: cada tabla se define una sola vez en su `CREATE TABLE`. Totales: **69 tablas / 12 schemas**. IDs de changeset unificados como `GYM-001-XX`.

## ✅ Actualización 2026-07-10 (mañana): REQ-SAAS-001 Sub-fases 1.1–1.5 documentadas

Se completó la **auditoría técnica** de los commits 6bd7f0b–c1a5b75 (nuevo esquema de planes Freemium). Documentación creada:

- **`planes-saas-freemium-implementacion.md`** — Bitácora completa: DDL, modelos, 10 endpoints REST, jobs de notificación, decisiones D1–D6.
- **`planes-saas-limitaciones.md`** — 7 limitaciones conocidas + checklist Sub-fase 1.6.
- **`platform-service.md`** (sección 6.9 nueva) — 11 nuevos endpoints documentados con DTOs y ejemplos.
- **`STATUS.md`** (aquí) — Tabla de estado de Sub-fases.

**Próximo paso:** Sub-fase 1.6 (frontend React + tests de integración + code review).

---

---

## Vocabulario de estado

Cada documento en `docs/` lleva un encabezado con uno de estos marcadores. Su significado:

| Marcador | Significa | Cómo tratarlo al implementar |
|----------|-----------|------------------------------|
| ✅ **Refleja el código actual** | El documento fue verificado contra el código y coincide. Autoritativo. | Confiar en él como referencia del comportamiento actual. |
| 🟡 **Parcial / puede haber divergencia** | El documento describe algo mayormente real, pero parte ya divergió del código o está incompleto. | Verificar contra el código antes de confiar en el detalle. |
| 📋 **Planeado — sin implementar** | Diseño/especificación de algo que **no existe** en el código todavía. | No asumir que existe. Es una guía de construcción futura, no una descripción del sistema actual. |
| 📜 **Histórico — registro de cómo se construyó** | Diario de implementación o backlog. No describe cómo funciona el sistema hoy. | No usar como referencia de estado actual. Útil solo como contexto de decisiones pasadas. |

---

## Estado por servicio

### Backends (Java / Spring WebFlux)

| Servicio | Puerto | Código | Estado del servicio |
|----------|--------|:------:|---------------------|
| auth-service | 8080 | ✅ presente (167 archivos Java) | **Implementado** |
| platform-service | 8081 | ✅ presente (148 archivos Java) | **Implementado** |
| core-service | 8083 | ✅ presente (66 archivos Java) | **Implementado** |
| attendance-service | 8084 | ✅ presente (51 archivos Java) | **Implementado** |
| billing-service | 8086 | ✅ presente | **Implementado — Fases SRI 0-3 completas** (6 controllers, 23 endpoints; ciclo de vida completo: emisión síncrona v2.24, NC tipo 04, anulación fiscal con workflow, bancarización > USD 500, ATS validado contra XSD oficial). Ver [roadmap](billing-service/pendientes/roadmap-sri-2026.md) |
| finance-service | 8085 | ✅ presente | **Implementado** (5 controllers, 13 endpoints + `/actuator/health`; ingresos/egresos inmutables, categorías con soft-delete, reportes resumen/mensual/proyección; `AccessControlService` centralizado). Ver [docs/finance-service/](finance-service/INDEX.md) (en construcción 2026-07-14). |
| marketing-service | — | ❌ no existe | 📋 Solo especificación |
| inventory-service | — | ❌ no existe | 📋 Solo especificación |

### Frontends (React)

| Frontend | Puerto | Código | Estado |
|----------|--------|:------:|--------|
| auth-service-frond-end | 5173 | ✅ presente | **Implementado** (panel admin/staff) |
| gym-member-pwa | 5174 | ✅ presente | **Implementado** (PWA miembros) |

### Base de datos

| Componente | Estado |
|------------|--------|
| Migraciones Liquibase (`gym-administrator/db/`) | ✅ Implementadas — **69 tablas, 12 schemas** (consolidadas en `202605_GYM-001/` desde 2026-07-10, commit `e5ff46f`) |
| Schemas `finanzas` | ✅ Tablas creadas en BD y **consumidas por `finance-service`** (verificado 2026-07-14): `categorias_ingreso`, `ingresos` (inmutables), `categorias_egreso`, `egresos` (soft-delete). |
| Schemas `marketing`, `inventario` | ✅ Tablas creadas en BD, pero 📋 sin servicio que las use aún |
| Schemas `sri`, `facturacion` (billing) | ✅ Tablas creadas en BD (6 + 16 tablas) y consumidas por `billing-service`. **Extendido 2026-07-13** (Fase 3 · G10): columna `bancarizada` en `sri.formas_pago` marcando los códigos 16-20 del catálogo SRI (consolidada en baseline `ddl-facturacion/05_create_table_sri_formas_pago.sql`, changeSet `GYM-001-54`). |

---

## REQ-SAAS-001 — Nuevo esquema de planes SaaS (Free / Trial / Premium)

**Estado:** Fase 1 implementada al ~85% (Sub-fases 1.1, 1.2, 1.3, 1.4, 1.5 completadas).

| Sub-fase | Descripción | Estado | Archivos |
|----------|---|---|---|
| 1.1 | DDL Liquibase (tablas, índices, constraints) | ✅ Implementada (commit 6bd7f0b, consolidada en `e5ff46f`) | `gym-administrator/db/scripts/202605_GYM-001/` (subcarpetas `ddl/` + `ddl-freemium/`) |
| 1.2 | Modelo de dominio + adapters R2DBC + máquina de estados | ✅ Implementada (commit 3c91d7e) | `platform-service/domain/model/`, adapters |
| 1.3 | Servicios de negocio, use cases, guards, auditoría | ✅ Implementada (commit e4bf796) | `ActivarTrialService`, `CancelarSuscripcionService`, etc. |
| 1.4 | 10 endpoints REST (owner + root), rate limiting, cross-service | ✅ Implementada (commit e4bf796) | `PlanPublicoController`, `SuscripcionController`, `PagoOwnerController`, `PagoPlataformaController`, `UsoLimitesController` |
| 1.5 | Notificaciones email (cola Postgres + retry), banners in-app | ✅ Implementada (commit c1a5b75) | `NotificacionVencimientoJob`, `EmailQueueProcessorJob`, `BannerController`, `EmailTemplateEngine` |
| 1.6 | Frontend (React), tests de integración, code review | 📋 Pendiente | Será próxima tarea |

**Bitácora completa:** `docs/gym-administrator/requirements/planes-saas-freemium-implementacion.md` (recién creado).

---

## Estado por documento

> Los veredictos de precisión (✅/🟡) provienen de verificar cada doc contra el código el 2026-07-10.

### docs/auth-service/api/
| Documento | Estado |
|-----------|--------|
| auth.md, personas.md, usuarios-staff.md, app-usuarios.md, platform.md, bitacora.md | ✅ Refleja el código actual — verificado contra `ApiRouter.java`: cada endpoint documentado existe y cada ruta del código está documentada |

### docs/core-service/api/
| Documento | Estado |
|-----------|--------|
| clientes.md | ✅ Refleja el código actual — verificado contra `ClienteController` (se añadió el endpoint `/clientes/plataforma` que faltaba). El README del servicio omite varios endpoints que sí existen; usar este doc como referencia de API. |

### docs/finance-service/api/
| Documento | Estado |
|-----------|--------|
| INDEX.md | ✅ Creado 2026-07-14 — índice del servicio con convenciones (JWT roles, multi-tenancy, defaulteo de sucursal, snake_case, timezone America/Guayaquil, mapping de errores HTTP) y las 6 reglas de negocio RN-01 a RN-06. |
| categorias-ingreso.md, categorias-egreso.md | ✅ Refleja el código 2026-07-14 — 3 endpoints cada uno (GET listar, POST crear, PUT desactivar). Incluye RN-04: la desactivación devuelve 409 si hay registros activos referenciando la categoría. |
| ingresos.md | ✅ Refleja el código 2026-07-14 — 2 endpoints (GET paginado con filtros `desde`/`hasta`/`idCategoria`, POST registrar). Destaca **RN-01 inmutabilidad** (no hay PUT ni DELETE) y el permiso especial `isRecepcion()` para POST. |
| egresos.md | ✅ Refleja el código 2026-07-14 — 2 endpoints análogos a ingresos, sin permiso especial de recepción. |
| reportes.md | ✅ Refleja el código 2026-07-14 — 3 endpoints (`/resumen` por rango, `/mensual` por año, `/proyeccion` basada en últimos N meses). Permiso `requireFinanzasReportes`. |

**Total endpoints:** 13 · **Total controllers:** 5.

### docs/billing-service/api/
| Documento | Estado |
|-----------|--------|
| comprobantes.md | ✅ Refleja el código actual (reverificado 2026-07-14 contra `ComprobanteController`). 9 endpoints — incluye `POST /{id}/anular` rediseñado (Fase 2 · G3) con body de motivo y `GET /{id}/anulaciones`. |
| notas-credito.md | ✅ Refleja el código actual 2026-07-14 (Fase 2 · G4). 3 endpoints sobre `NotaCreditoController`: `POST /notas-credito`, `GET /notas-credito/{id}`, `GET /notas-credito`. Reutiliza el pipeline síncrono de G2. |
| anulaciones.md | ✅ Refleja el código actual 2026-07-14 (Fase 2 · G3). 6 endpoints (5 `AnulacionController` + 1 `MotivosAnulacionController`): aprobar, rechazar, confirmar-sri, listar, detalle y catálogo de motivos. |
| admin.md | ✅ Refleja el código actual (verificado 2026-07-11 contra `AdminController`). 3 endpoints. |
| reportes.md | ✅ Refleja el código actual (verificado 2026-07-11 contra `ReporteController`). 2 endpoints — el generador ATS fue reescrito 2026-07-13 (Fase 3 · G9) contra el XSD oficial del SRI. |
| integracion.md | 📜 **Archivado** en [`_archive/billing-service/integracion.md`](_archive/billing-service/integracion.md) (2026-07-19) — describía emisión asíncrona pre-G2; hoy `POST /facturas` es síncrono y `core-service` aún no consume la integración. Estado vigente: `api/comprobantes.md`. |

**Total endpoints:** 23 · **Total controllers:** 6.

### docs/billing-service/flows/
| Documento | Estado |
|-----------|--------|
| sri-submission-retry.md | ✅ Refleja el código actual (reescrito 2026-07-13 tras Fase 1 · G2). Documenta el pipeline síncrono `POST → firma → envío → autorización` con timeout 15s y `facturacion.cola_envio` como fallback (backoff `{1, 5, 15, 60, 240}` min via `RetrySchedulerService`). |
| anulacion-nc.md | ✅ Refleja el código actual 2026-07-13 (Fase 2 · G3). Máquina de estados SOLICITADA→APROBADA→EJECUTADA/RECHAZADA, con Flujo A (portal SRI manual) y Flujo B (con NC automática reutilizando G4). |

### docs/billing-service/pendientes/adr/
| Documento | Estado |
|-----------|--------|
| 001-version-xml-sri.md | ✅ Vigente 2026-07-13 (Fase 1 · G1). Decide subir de XML v2.1.0 a v2.24 — mínima que oficializa el código de tarifa 4 (IVA 15%). |

### docs/billing-service/pendientes/
| Documento | Estado |
|-----------|--------|
| roadmap-sri-2026.md | 📋 Roadmap 2026-07-11 — **🔴 División en 6 fases con dependencias.** **Fase 0 ✅ Completada 2026-07-12** (G5 secuencial + G6 catálogos). **Fase 1 ✅ Completada 2026-07-13** (G1 XML v2.24 + G2 transmisión inmediata). **Fase 2 ✅ Completada 2026-07-13** (G4 notas de crédito tipo 04 + G3 anulación fiscal con workflow SOLICITADA→APROBADA→EJECUTADA y flujos A/B). **Fase 3 ✅ Completada 2026-07-13** (G10 bancarización USD 500 con flag `sri.formas_pago.bancarizada` + G9 ATS reescrito contra el XSD oficial del SRI). Fase 4 Complementarios opcionales (G7 ND + G8 retención + G13 guías) — **próxima**. Fase 5 Rediseño frontend. Fase 6 Diferibles (G11 QR + G12 sync finanzas). |
| anulacion-sri.md | 📜 **Archivado** en [`_archive/billing-service/anulacion-sri.md`](_archive/billing-service/anulacion-sri.md) (2026-07-19) — diseño de G3 ya IMPLEMENTADO 2026-07-13. Estado vigente: `docs/billing-service/api/anulaciones.md` + `flows/anulacion-nc.md`. |
| it-end-to-end-sri-pruebas.md | 📋 Pendiente 2026-07-13 — **Test IT contra ambiente de pruebas del SRI** (`celcer.sri.gob.ec`). Documenta checklist de 7 prerrequisitos (certificado P12 real de firma electrónica, RUC válido con dígito verificador, filas en `config_sri` y `certificados_info`, resolver bug de auth Postgres local, receptor válido, conectividad HTTPS) y estructura sugerida del test con `@EnabledIfEnvironmentVariable` para no romper CI. Retomar en próxima sesión cuando estén los prerrequisitos. |
| gap-analysis-sri-2026.md | 📋 Análisis 2026-07-11 — **🔴 13 GAPs identificados** entre normativa SRI 2025-2026 y el código actual. Bloqueantes de producción: ficha técnica del XML es v2.1.0 (SRI publicó v2.32), transmisión inmediata obligatoria desde 2026-01-01 (hoy el primer intento va por cola con delay), secuencial provisto por el cliente (riesgo de duplicados), notas de crédito sin código (BD sí las tiene). Otros GAPs: catálogos SRI hardcoded, ATS solo cubre tipo 01, sin validación de bancarización sobre USD 500, RIDE sin QR. Documento complementa a anulacion-sri.md y prioriza por sprints. |

### docs/platform-service/ y platform-service/CLAUDE.md
| Documento | Estado |
|-----------|--------|
| CLAUDE.md "Endpoints" | ✅ Refleja el código actual — los 10 route groups y sus paths existen. (Salvedad: la lista de rutas "públicas" omite `/planes/publicos` y `/companias/auto-registro`, que también son públicas.) |
| README.md | ✅ Corregido 2026-07-08 — se arregló el error de que "no hay `/health`" (sí existe `/actuator/health`) y la lista de schemas (solo posee `tenant` + `saas`). |
| wizard-bootstrap.md | ✅ Creado 2026-07-21 — Flujo de bootstrap de compañía (`POST /companias/wizard`): 7 pasos secuenciales, auto-seed de 3 métodos de pago (Efectivo, Tarjeta, Transferencia) en `config.metodos_pago`, idempotencia por nombre, consumidores (core-service), arquitectura cross-schema. |

### docs/attendance-service/ y attendance-service/{README,CLAUDE}.md
| Documento | Estado |
|-----------|--------|
| README.md endpoints | ✅ Corregido 2026-07-08 — la tabla decía `/asistencias/check` (público); el endpoint real es `/asistencias/qr` (requiere JWT de cliente). Tabla reescrita con los 16 endpoints reales. |
| CLAUDE.md | ✅ Corregido 2026-07-08 — misma corrección + nota de la regla muerta en `SecurityConfig` (ver abajo). |

> ⚠️ **Hallazgo de código pendiente de revisar (no corregido — decisión de diseño):** `attendance-service/src/main/java/.../config/SecurityConfig.java` marca `.permitAll()` sobre `/api/v1/asistencias/check`, una ruta que **no está mapeada a ningún endpoint**. El endpoint real de check-in por QR es `/asistencias/qr` y exige JWT de cliente (`requireCliente`). La regla es inofensiva pero muerta. Decidir si el flujo requiere una ruta QR pública o si la regla debe eliminarse.

### docs/gym-administrator/specs/
| Documento | Estado |
|-----------|--------|
| auth-service.md, platform-service.md, core-service.md, attendance-service.md | 🟡 Spec de diseño de un servicio ya implementado — el código es la verdad, la spec puede haber divergido |
| billing-service.md | 🟡 Spec de diseño histórica — servicio implementado (Fases SRI 0-3). Encabezado corregido 2026-07-19. Su §9 lista 13 endpoints; hoy son 23 (6 controllers). Fuente de verdad: [docs/billing-service/](billing-service/INDEX.md). |
| finance-service.md | 🟡 Spec de diseño histórica — servicio implementado (5 controllers, 13 endpoints). Encabezado corregido 2026-07-19. Fuente de verdad: [docs/finance-service/](finance-service/INDEX.md). |
| marketing-service.md, inventory-service.md | 📋 Planeado — sin implementar |

### docs/gym-administrator/architecture/
| Documento | Estado |
|-----------|--------|
| overview.md, database-schema.md, roadmap.md | 🟡 Describe arquitectura + estado planeado; verificar contra código para detalle de implementación |

### docs/auth-service-frond-end/
| Documento | Estado |
|-----------|--------|
| design-guidelines.md, INDEX-api.md | 🟡 Referencia — verificar contra código si el detalle importa |
| features-ocultas.md | ✅ Creado 2026-07-21 — opciones del panel ocultas a propósito en la navegación (con cómo restaurarlas). Hoy: "Cuentas App" (`/admin/clientes/app`). |
| pendientes-backlog.md, spec-solicitudes-membresia.md, facturacion-diseno.md | 📋 Planeado — sin implementar |
| impl/, backend-prompts/, preguntas/, member-portal-decisiones, registro-* | 📜 **Archivados** en [`_archive/auth-service-frond-end/`](_archive/README.md) (2026-07-19) |

### docs/gym-member-pwa/
| Documento | Estado |
|-----------|--------|
| pendientes-backlog.md, pendientes-checkin-qr.md | 📜 Backlog vivo — tareas pendientes, no estado actual (no archivados) |
| spec-solicitud-membresia.md | 📋 Sub-doc de la HU `gym-administrator/requirements/solicitudes-membresia.md` |

### docs/_archive/
| Documento | Estado |
|-----------|--------|
| Todo el contenido | 📜 **Histórico** — cómo se construyó o especificaciones superadas. NO usar como estado actual. Ver [`_archive/README.md`](_archive/README.md). |
