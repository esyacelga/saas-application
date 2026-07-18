# gym-administrator — Índice de documentación

Documentación técnica y de negocio del proyecto base: modelo de datos, migraciones Liquibase y especificaciones de cada microservicio/frontend antes de su implementación. Para las guías de desarrollo ya implementadas de cada servicio, ver el [INDEX.md](../../INDEX.md) raíz del monorepo.

---

## architecture/ — Visión general del proyecto

| Documento | Contenido |
|-----------|-----------|
| [overview.md](architecture/overview.md) | Qué es el proyecto, modelo de negocio SaaS multi-tenant, módulos funcionales por plan, reglas de negocio, flujos end-to-end, pipeline CI/CD |
| [database-schema.md](architecture/database-schema.md) | Esquema completo de PostgreSQL: 69 tablas en 12 schemas (saas, identidad, tenant, core, asistencia, config, seguridad, finanzas, marketing, inventario, sri, facturacion), diagramas ASCII |
| [roadmap.md](architecture/roadmap.md) | Orden de construcción de los 7 microservicios y dependencias entre ellos |
| [scheduled-jobs.md](architecture/scheduled-jobs.md) | Los 8 scheduled jobs del monorepo: cron/delay, idempotencia, startup hooks |
| [error-contract.md](architecture/error-contract.md) | Contrato de errores estandarizado (RFC 7807 + `codigo`) para los 6 microservicios — requerimiento/diseño |

## specs/ — Especificaciones por microservicio

Una spec de desarrollo por servicio: modelo de datos, endpoints, reglas de negocio y dependencias con otros servicios.

| Servicio | Documento | Plan | Estado |
|----------|-----------|------|--------|
| auth-service | [auth-service.md](specs/auth-service.md) | Básico | Implementado |
| platform-service | [platform-service.md](specs/platform-service.md) | Básico | Implementado |
| core-service | [core-service.md](specs/core-service.md) | Básico | Implementado |
| attendance-service | [attendance-service.md](specs/attendance-service.md) | Básico | Implementado |
| finance-service | [finance-service.md](specs/finance-service.md) | Premium | Spec lista, sin implementar |
| marketing-service | [marketing-service.md](specs/marketing-service.md) | Premium | Spec lista, sin implementar |
| inventory-service | [inventory-service.md](specs/inventory-service.md) | Premium | Spec lista, sin implementar |
| billing-service | [billing-service.md](specs/billing-service.md) | Premium / add-on | Spec lista, sin implementar — facturación electrónica SRI Ecuador |

## frontend/ — Especificaciones de frontend

| Documento | Contenido |
|-----------|-----------|
| [auth-frontend-spec.md](frontend/auth-frontend-spec.md) | Especificación funcional de pantallas del módulo de autenticación, usuarios, roles y permisos |
| [auth-frontend-impl.md](frontend/auth-frontend-impl.md) | Documento de implementación técnica del mismo módulo (React + Vite) |
| [platform-frontend-prompt.md](frontend/platform-frontend-prompt.md) | Especificación del módulo Platform dentro de `auth-service-frond-end/` |

## requirements/ — Requerimientos SaaS y bitácora de implementación

| Documento | Contenido |
|-----------|-----------|
| [planes-saas-freemium.md](requirements/planes-saas-freemium.md) | REQ-SAAS-001 original: especificación de negocio (Free / Trial / Premium), 8 reglas de negocio, 6 historias de usuario, 4 fases sugeridas |
| [planes-saas-freemium-implementacion.md](requirements/planes-saas-freemium-implementacion.md) | **Bitácora de Sub-fases 1.1–1.5** (implementadas): cambios de BD, modelos de dominio, servicios de negocio, 10 endpoints REST nuevos, notificaciones por email, decisiones arquitectónicas D1–D6, verificación contra código |
| [planes-saas-limitaciones.md](requirements/planes-saas-limitaciones.md) | Limitaciones conocidas en Sub-fases 1.4–1.5: destinatario hardcodeado, fechaFin mal formateada, config_notif no consultada, integración core-service pendiente (Sub-fase 1.4b), checklist para Sub-fase 1.6 |
| [estado-pago-membresias.md](requirements/estado-pago-membresias.md) | HU planeada (revisada por architect + product-owner + code-reviewer contra código, dos rondas): separar venta y pago en `core.membresias` con `estado_pago` (`PENDIENTE`/`PAGADO`), reuso de `eliminado` para soft-delete, motivo obligatorio por catálogo, `POST /confirmar-pago` y `POST /rechazar`, bloqueo en `validar-acceso` con códigos `pago_pendiente`/`membresia_rechazada`, seed multi-tenant del permiso `membresias:confirmar_pago` (sin vincular a roles — nombres no estandarizados), sección "Pipeline de ventas" en admin. Descubre deuda arquitectónica HU-G: `seguridad.permisos` es multi-tenant, no catálogo global. |

## infra/ — Infraestructura y plantillas

| Documento | Contenido |
|-----------|-----------|
| [docker.md](infra/docker.md) | Docker para levantar PostgreSQL y ejecutar migraciones Liquibase |
| [liquibase-azure-template.md](infra/liquibase-azure-template.md) | Plantilla genérica reusable: gestión de esquema PostgreSQL con Liquibase + pipeline Azure DevOps (basada en `tdd-dba-reconciliacion`) |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case`, sin prefijos de servicio redundantes (la carpeta ya agrupa por tema).
- Esta documentación vivía originalmente en `gym-administrator/docs/`; se centralizó aquí porque su contenido es transversal a todo el proyecto (specs de todos los microservicios, no solo de gym-administrator). El código de migraciones (Liquibase) sigue en `gym-administrator/db/`.
- `gym-administrator/CLAUDE.md` permanece en la raíz de ese subproyecto (no aquí) porque Claude Code solo lo carga automáticamente desde ahí; enlaza de vuelta a este índice.
- Toda spec de servicio nuevo va en `specs/<nombre-servicio>.md` con la misma cabecera: `Servicio`, `Esquemas BD`, `Tablas`, `Depende de`, `Estado`.
