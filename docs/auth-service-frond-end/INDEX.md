# auth-service-frond-end — Índice de documentación

Panel de administración y staff (web). Ver [auth-service-frond-end/README.md](../../auth-service-frond-end/README.md) (stack, catálogo de permisos, inicio rápido) y [auth-service-frond-end/CLAUDE.md](../../auth-service-frond-end/CLAUDE.md) (arquitectura, patrones de código, JWT, guards) para la documentación de desarrollo.

> Para la clasificación de estado de cada documento (✅ vigente / 🟡 parcial / 📋 planeado / 📜 histórico), ver [../STATUS.md](../STATUS.md).

---

## Referencia vigente

| Documento | Estado | Contenido |
|-----------|--------|-----------|
| [INDEX-api.md](INDEX-api.md) | 🟡 Referencia | Índice de endpoints que este frontend consume del auth-service (verificar contra código si el detalle importa). |
| [design-guidelines.md](design-guidelines.md) | 🟡 Referencia | Directrices de diseño: sistema de 6 temas, tipografía, tokens, 17 variables CSS por tema. |

## Planeado (spec definida, sin implementar)

| Documento | Contenido |
|-----------|-----------|
| [pendientes-backlog.md](pendientes-backlog.md) | Backlog de features pendientes del panel (Solicitudes de membresía en dashboard). |
| [spec-solicitudes-membresia.md](spec-solicitudes-membresia.md) | UI detallada para gestión de solicitudes de membresía (cliente PWA) en dashboard staff. Sub-doc de la HU [`../gym-administrator/requirements/solicitudes-membresia.md`](../gym-administrator/requirements/solicitudes-membresia.md). |
| [facturacion-diseno.md](facturacion-diseno.md) | Spec de diseño del módulo de Facturación Electrónica SRI (no existe ninguna pantalla hoy). Incluye la frontera `src/lib/sri/` para replicar el módulo en otros SaaS ecuatorianos. |

## Especificaciones transversales (viven en gym-administrator)

La especificación funcional y de implementación técnica del módulo de auth son transversales al proyecto y viven en [../gym-administrator/frontend/](../gym-administrator/frontend/), no aquí.

---

## Histórico archivado

Los pasos de implementación ya completados (`impl/`), los prompts al backend (`backend-prompts/`), las notas personales (`preguntas/`), las decisiones del portal de miembro y la bitácora del cambio de registro se movieron a [../_archive/auth-service-frond-end/](../_archive/auth-service-frond-end/) (2026-07-19). Describen cómo se construyó el panel, no cómo funciona hoy.

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case`; `INDEX.md`, `README.md` y `CLAUDE.md` en MAYÚSCULAS (anclas).
- `auth-service-frond-end/CLAUDE.md` y `README.md` permanecen en la raíz de `auth-service-frond-end/` y enlazan aquí.
