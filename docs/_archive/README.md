# _archive — Documentación histórica

> **Qué es esto:** documentos que describen **cómo se construyó** algo o que fueron **superados** por la implementación actual. **No** describen cómo funciona el sistema hoy.

Se movieron aquí (2026-07-19) durante la reestructuración de documentación para que dejen de aparecer en las búsquedas de estado actual y no causen confusión. El historial de git de cada archivo se preserva (`git mv`).

**Para el estado actual del sistema, NO uses estos archivos.** Consulta:
- [`../STATUS.md`](../STATUS.md) — fuente única de verdad del estado por servicio y por documento.
- Los `INDEX.md` / `CLAUDE.md` / `README.md` vigentes de cada servicio.
- Los `docs/<servicio>/api/` verificados contra el código.

## Contenido

| Carpeta / archivo | Origen | Por qué se archivó |
|---|---|---|
| `auth-service-frond-end/impl/` | `docs/auth-service-frond-end/impl/` | 📜 Bitácora de los pasos de implementación (IMPL-02..18) ya completados del panel admin. |
| `auth-service-frond-end/backend-prompts/` | idem | 📜 Prompts usados para pedir cambios al backend; ya implementados. |
| `auth-service-frond-end/preguntas/` | idem | 📜 Notas personales de setup/aprendizaje, no documentación del sistema. |
| `auth-service-frond-end/member-portal-decisiones.md` | idem | 📜 Decisiones de producto del portal de miembros, ya implementadas. |
| `auth-service-frond-end/registro-quitar-ruc.md`, `registro-mejoras-implementadas.md` | idem | 📜 Diseño + bitácora del cambio de registro (quitar RUC), ya hecho. |
| `billing-service/anulacion-sri.md` | `docs/billing-service/pendientes/` | ✅ Implementado 2026-07-13 (Fase 2 · G3). Estado vigente en `docs/billing-service/api/anulaciones.md` + `flows/anulacion-nc.md`. |
| `billing-service/integracion.md` | `docs/billing-service/api/` | Describía emisión **asíncrona** pre-G2; hoy `POST /facturas` es síncrono (ver `api/comprobantes.md`). `core-service` aún no consume esta integración. |
| `gym-administrator/restructuracion-onboarding-facturacion.md` | `docs/gym-administrator/requirements/` | 📜 Bitácora de una fase de onboarding ya implementada. |
| `gym-administrator/whatsapp-avisos-vencimiento.md` | `docs/gym-administrator/pendientes/` | Feature de avisos WhatsApp **ya implementada** (jobs `NotificacionVencimientoJob`, `WhatsAppQueueProcessorJob`, `MensajeriaJob`, adapter Meta). Estado vigente en [`../gym-administrator/architecture/scheduled-jobs.md`](../gym-administrator/architecture/scheduled-jobs.md) + el código. |
