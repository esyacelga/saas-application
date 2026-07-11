# Estado del proyecto — Mapa de implementación

> **Propósito:** Fuente única de verdad sobre **qué está construido hoy** vs. **qué es solo diseño**. Antes de implementar o de confiar en un documento como referencia, consulta aquí su estado.
>
> Última verificación contra el código: **2026-07-10**

---

## ✅ Actualización 2026-07-10 (tarde): consolidación de migraciones

Commits `e5ff46f`, `5d9fc88`, `78579bf`: las tres stories de migración (`202605_GYM-001` core, `202607_GYM-002` facturación, `202608_GYM-003` freemium) se **fusionaron en una única story `202605_GYM-001`** con tres subcarpetas por dominio:

- `ddl/` — 10 schemas base + 46 tablas (saas, identidad, tenant, core, asistencia, finanzas, marketing, inventario, config, seguridad).
- `ddl-facturacion/` — schemas `sri` + `facturacion` y sus 23 tablas.
- `ddl-freemium/` — extras REQ-SAAS-001 (`tenant.pagos_pendientes_validacion`, `saas.config_plataforma`, seed).

Ya no existen scripts `ALTER`: cada tabla se define una sola vez en su `CREATE TABLE`. Totales actuales: **69 tablas / 12 schemas**. IDs de changeset unificados como `GYM-001-XX` (rango extendido a 141 para dar espacio a los inserts de `ddl-freemium/`).

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
| billing-service | 8086 | ✅ presente (~85% implementado) | **Implementado** (3 controllers, 13 endpoints documentados) |
| finance-service | — | ❌ no existe | 📋 Solo especificación |
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
| Schemas `finanzas`, `marketing`, `inventario` | ✅ Tablas creadas en BD, pero 📋 sin servicio que las use aún |
| Schemas `sri`, `facturacion` (billing) | ✅ Tablas creadas en BD (6 + 17 tablas) y consumidas por `billing-service` (~85% implementado) |

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

### docs/billing-service/api/
| Documento | Estado |
|-----------|--------|
| comprobantes.md, admin.md, reportes.md | ✅ Refleja el código actual (verificado 2026-07-11 contra `ComprobanteController`, `AdminController`, `ReporteController`). Total: 13 endpoints documentados (8+3+2). |
| integracion.md | 📋 Propuesto 2026-07-11 — Contrato de integración para que `core-service` consuma `billing-service` al vender membresías. Describe flujo asíncrono, JWT multi-tenancy, manejo de errores, idempotencia, y checklist de implementación. |

### docs/platform-service/ y platform-service/CLAUDE.md
| Documento | Estado |
|-----------|--------|
| CLAUDE.md "Endpoints" | ✅ Refleja el código actual — los 10 route groups y sus paths existen. (Salvedad: la lista de rutas "públicas" omite `/planes/publicos` y `/companias/auto-registro`, que también son públicas.) |
| README.md | ✅ Corregido 2026-07-08 — se arregló el error de que "no hay `/health`" (sí existe `/actuator/health`) y la lista de schemas (solo posee `tenant` + `saas`). |

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
| billing-service.md | ✅ Corregida 2026-07-11 — Sección 9 actualizada con tabla de endpoints reales (`/api/v1/{comprobantes,admin,reportes}`) enlazados a la doc de API. Removed payload nested (spec antigua); ver [docs/billing-service/api/integracion.md](../../billing-service/api/integracion.md) para el contrato real de integración propuesto. |
| finance-service.md, marketing-service.md, inventory-service.md | 📋 Planeado — sin implementar |

### docs/gym-administrator/architecture/
| Documento | Estado |
|-----------|--------|
| overview.md, database-schema.md, roadmap.md | 🟡 Describe arquitectura + estado planeado; verificar contra código para detalle de implementación |

### docs/auth-service-frond-end/
| Documento | Estado |
|-----------|--------|
| impl/*.md | 📜 Histórico — pasos de implementación ya completados |
| backend-prompts/*.md | 📜 Histórico — prompts usados para pedir cambios al backend |
| preguntas/*.md | 📜 Histórico — notas personales de aprendizaje |
| design-guidelines.md, api-index.md | 🟡 Referencia — verificar contra código si el detalle importa |

### docs/gym-member-pwa/
| Documento | Estado |
|-----------|--------|
| pendientes-backlog.md, pendientes-checkin-qr.md | 📜 Histórico / backlog — tareas pendientes, no estado actual |
