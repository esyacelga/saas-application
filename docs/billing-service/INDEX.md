# billing-service — Índice de documentación

Facturación electrónica SRI Ecuador. Emisión, firma digital y autorización de facturas; gestión de certificados; reportes ATS y resumen de ventas. Ver [billing-service/README.md](../../billing-service/README.md) (Docker, variables de entorno) para el resto de la documentación. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [comprobantes.md](api/comprobantes.md) | `/api/v1/comprobantes` | CRUD de facturas y comprobantes: emisión, envío SRI, descargas (XML, RIDE), anulación |
| [notas-credito.md](api/notas-credito.md) | `/api/v1/notas-credito` | G4 · Emisión y consulta de notas de crédito electrónicas (tipo `04`) sobre facturas ya autorizadas |
| [anulaciones.md](api/anulaciones.md) | `/api/v1/anulaciones`, `/api/v1/sri/motivos-anulacion` | G3 · Anulación fiscal SRI: máquina de estados (solicitar → aprobar/rechazar → confirmar-sri o NC AUTORIZADA), autorización por rol, catálogo de motivos |
| [admin.md](api/admin.md) | `/api/v1/admin` | Diagnóstico: ping SRI, estado de certificados, auditoría de emisión |
| [reportes.md](api/reportes.md) | `/api/v1/reportes` | ATS mensual (XML SRI), resumen de ventas por período |
| [integracion.md](api/integracion.md) | — | 📋 **Propuesto:** Cómo core-service debe consumir billing-service; flujo de emisión de factura asíncrono; JWT y multi-tenancy; manejo de errores; checklist de implementación |

---

## flows/ — Procesos asíncrónos

| Documento | Descripción |
|-----------|-------------|
| [sri-submission-retry.md](flows/sri-submission-retry.md) | Flujo de firma, envío y autorización al SRI; cola de reintentos con backoff exponencial (1, 5, 15, 60, 240 min) y scheduler cada 60 seg |
| [anulacion-nc.md](flows/anulacion-nc.md) | G3 · Máquina de estados de anulación fiscal (SOLICITADA → APROBADA → EJECUTADA/RECHAZADA), diagramas de Flujo A (portal SRI) y Flujo B (con NC), autorización por rol, notificaciones |

---

## pendientes/ — Trabajos por implementar

**Empezar por [roadmap-sri-2026.md](pendientes/roadmap-sri-2026.md)** — es el backlog ejecutable priorizado por sprints, con checklist por GAP. Los demás documentos son detalle.

| Documento | Prioridad | Descripción |
|-----------|-----------|-------------|
| [roadmap-sri-2026.md](pendientes/roadmap-sri-2026.md) | 🔴 Alta | 📋 **División en 6 fases con dependencias.** Fases 0, 1 y 2 ya completadas. Cada fase apunta al detalle del GAP en gap-analysis. |
| [it-end-to-end-sri-pruebas.md](pendientes/it-end-to-end-sri-pruebas.md) | 🟡 Media | 📋 Test IT que envía factura real al ambiente de pruebas del SRI (`celcer.sri.gob.ec`). Incluye checklist de prerrequisitos: certificado P12 real, RUC válido, BD operacional, receptor válido, red. Para retomar en próxima sesión. |
| [anulacion-sri.md](pendientes/anulacion-sri.md) | ✅ | ✅ IMPLEMENTADO 2026-07-13 — histórico del diseño de G3. Ver [api/anulaciones.md](api/anulaciones.md) y [flows/anulacion-nc.md](flows/anulacion-nc.md). |
| [gap-analysis-sri-2026.md](pendientes/gap-analysis-sri-2026.md) | 🔴 Alta | 📋 Cruce completo de brechas entre normativa SRI 2025-2026 y estado actual del código. 13 GAPs identificados; G1–G6 resueltos, resto pendiente. |
| [adr/001-version-xml-sri.md](pendientes/adr/001-version-xml-sri.md) | — | ADR de la decisión de subir el XML de factura a v2.24 (mínima que oficializa el código IVA 15%). |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` dentro de `api/` y `flows/`.
- `billing-service/README.md` permanece en la raíz y enlaza aquí.
