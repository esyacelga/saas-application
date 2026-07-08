# Estado del proyecto — Mapa de implementación

> **Propósito:** Fuente única de verdad sobre **qué está construido hoy** vs. **qué es solo diseño**. Antes de implementar o de confiar en un documento como referencia, consulta aquí su estado.
>
> Última verificación contra el código: **2026-07-08**

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
| billing-service | — | ❌ no existe | 📋 Solo especificación |
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
| Migraciones Liquibase (`gym-administrator/db/`) | ✅ Implementadas — 42 tablas, 10 schemas |
| Schemas `finanzas`, `marketing`, `inventario` | ✅ Tablas creadas en BD, pero 📋 sin servicio que las use aún |
| Schemas `sri`, `facturacion` (billing) | 📋 Planeados — no existen en BD todavía |

---

## Estado por documento

> Los veredictos de precisión (✅/🟡) provienen de verificar cada doc contra el código el 2026-07-08.

### docs/auth-service/api/
| Documento | Estado |
|-----------|--------|
| auth.md, personas.md, usuarios-staff.md, app-usuarios.md, platform.md, bitacora.md | ✅ Refleja el código actual — verificado contra `ApiRouter.java`: cada endpoint documentado existe y cada ruta del código está documentada |

### docs/core-service/api/
| Documento | Estado |
|-----------|--------|
| clientes.md | ✅ Refleja el código actual — verificado contra `ClienteController` (se añadió el endpoint `/clientes/plataforma` que faltaba). El README del servicio omite varios endpoints que sí existen; usar este doc como referencia de API. |

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
| billing-service.md, finance-service.md, marketing-service.md, inventory-service.md | 📋 Planeado — sin implementar |

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
