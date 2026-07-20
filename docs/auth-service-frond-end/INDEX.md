# auth-service-frond-end — Índice de documentación

Panel de administración y staff (web). Ver [auth-service-frond-end/README.md](../../auth-service-frond-end/README.md) (stack, catálogo de permisos, inicio rápido) y [auth-service-frond-end/CLAUDE.md](../../auth-service-frond-end/CLAUDE.md) (arquitectura, patrones de código, JWT, guards) para el resto de la documentación.

---

## impl/ — Pasos de implementación por módulo

Specs de implementación en orden de construcción. Estado real: todos completados salvo que se indique lo contrario en el propio archivo.

| # | Documento | Módulo |
|---|-----------|--------|
| 02 | [login-plataforma.md](impl/02-login-plataforma.md) | Login de operadores de plataforma |
| 03 | [reset-solicitud.md](impl/03-reset-solicitud.md) | Solicitud de reset de contraseña |
| 04 | [reset-confirmar.md](impl/04-reset-confirmar.md) | Confirmación de reset |
| 05 | [cambio-password.md](impl/05-cambio-password.md) | Cambio de contraseña |
| 06 | [admin-layout.md](impl/06-admin-layout.md) | Layout del panel admin |
| 07 | [usuarios.md](impl/07-usuarios.md) | Gestión de usuarios |
| 08 | [bitacora.md](impl/08-bitacora.md) | Bitácora de actividad |
| 09 | [clientes-app.md](impl/09-clientes-app.md) | Clientes de la app |
| 10 | [roles-permisos.md](impl/10-roles-permisos.md) | Roles y permisos |
| 11 | [plataforma.md](impl/11-plataforma.md) | Módulo de plataforma |
| 12 | [member-portal-backend.md](impl/12-member-portal-backend.md) | Backend del portal de miembro |
| 13 | [member-portal-pwa.md](impl/13-member-portal-pwa.md) | PWA del portal de miembro |
| 14 | [personas-platform.md](impl/14-personas-platform.md) | Personas en plataforma |
| 15 | [auto-registro-staff.md](impl/15-auto-registro-staff.md) | Auto-registro de staff |
| 16 | [global-spinner.md](impl/16-global-spinner.md) | Spinner global |
| 17 | [asignar-membresia-desde-dashboard.md](impl/17-asignar-membresia-desde-dashboard.md) | Asignar membresía desde dashboard |
| 18 | [proximos-vencer-dashboard.md](impl/18-proximos-vencer-dashboard.md) | Próximos a vencer en dashboard |

La especificación funcional (`FRONTEND_AUTH_SPEC.md`) y de implementación técnica del módulo de auth (`FRONTEND_AUTH_IMPL.md`) viven en [../gym-administrator/frontend/](../gym-administrator/frontend/) — son transversales al proyecto, no específicas de este frontend.

## backend-prompts/ — Prompts de especificación para el backend

Documentos usados para pedir/especificar cambios de API al backend desde la perspectiva del frontend.

| Documento | Contenido |
|-----------|-----------|
| [roles-permisos.md](backend-prompts/roles-permisos.md) | Prompt: módulo Roles y Permisos (Platform) |
| [sucursales-by-compania.md](backend-prompts/sucursales-by-compania.md) | Prompt: endpoint de sucursales por compañía |
| [wizard-persona.md](backend-prompts/wizard-persona.md) | Spec: wizard de búsqueda y creación de persona |
| [administracion-roles.md](backend-prompts/administracion-roles.md) | Prompt: endpoints de administración de roles (panel plataforma) |

## Otros documentos

| Documento | Contenido |
|-----------|-----------|
| [design-guidelines.md](design-guidelines.md) | Directrices de diseño: sistema de temas, tipografía, tokens |
| [api-index.md](api-index.md) | Índice de endpoints consumidos del auth-service |
| [pendientes-backlog.md](pendientes-backlog.md) | 📜 Backlog de features pendientes: Solicitudes de membresía en dashboard. |
| [spec-solicitudes-membresia.md](spec-solicitudes-membresia.md) | 📋 **Pendiente — spec definida** — UI detallada para gestión de solicitudes de membresía (cliente PWA) en dashboard staff: filtros por origen, modal de pago, widget contador. |
| [facturacion-diseno.md](facturacion-diseno.md) | 📋 **Planeado** — Spec de diseño del módulo de Facturación Electrónica SRI (no existe ninguna pantalla hoy). Incluye la frontera `src/lib/sri/` para replicar el módulo en otros SaaS ecuatorianos. |
| [registro-quitar-ruc.md](registro-quitar-ruc.md) | ✅ **Implementado (2026-07-14)** — Diseño del adelgazamiento del registro: sacar del Paso 1 el **RUC** (va al wizard de facturación), el **teléfono** y el **WhatsApp**; suavizar el Paso 2. Disclosure progresivo. |
| [registro-mejoras-implementadas.md](registro-mejoras-implementadas.md) | ✅ **Implementado (2026-07-14)** — Registro de qué se cambió en el auto-registro público (accesibilidad, "(opcional)", pantalla de éxito, `lib/sri/` validadores, validación de cédula, Paso 2 pre-llenado, RUC opcional front+backend + esquema plegado en la baseline `GYM-001`). Para consultas futuras. |
| [member-portal-decisiones.md](member-portal-decisiones.md) | Decisiones de producto previas a implementar el portal de miembro |

## preguntas/ — Notas personales de aprendizaje

No son documentación de arquitectura — son notas del usuario sobre dudas puntuales de la herramienta/framework.

| Documento | Contenido |
|-----------|-----------|
| [01-primera-pagina.md](preguntas/01-primera-pagina.md) | Cómo decide la app cuál es la primera página a mostrar |
| [02-webstorm-play-button.md](preguntas/02-webstorm-play-button.md) | Cómo habilitar el botón Play en WebStorm |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` (antes `IMPL_NN_NOMBRE.md` / `BACKEND_PROMPT_NOMBRE.md`, ahora `nn-nombre.md` / `nombre.md` dentro de la subcarpeta correspondiente).
- `auth-service-frond-end/CLAUDE.md` y `auth-service-frond-end/README.md` permanecen en la raíz de `auth-service-frond-end/` y enlazan aquí.
- `PLATFORM_FRONTEND_PROMPT.md`, `FRONTEND_AUTH_SPEC.md` y `FRONTEND_AUTH_IMPL.md` que vivían duplicados en `auth-service-frond-end/documentacion/` se eliminaron — su única fuente de verdad es `docs/gym-administrator/frontend/`.
