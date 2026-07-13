# billing-service — Índice de documentación

Facturación electrónica SRI Ecuador. Emisión, firma digital y autorización de facturas; gestión de certificados; reportes ATS y resumen de ventas. Ver [billing-service/README.md](../../billing-service/README.md) (Docker, variables de entorno) para el resto de la documentación. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [comprobantes.md](api/comprobantes.md) | `/api/v1/comprobantes` | CRUD de facturas y comprobantes: emisión, envío SRI, descargas (XML, RIDE), anulación |
| [notas-credito.md](api/notas-credito.md) | `/api/v1/notas-credito` | G4 · Emisión y consulta de notas de crédito electrónicas (tipo `04`) sobre facturas ya autorizadas |
| [admin.md](api/admin.md) | `/api/v1/admin` | Diagnóstico: ping SRI, estado de certificados, auditoría de emisión |
| [reportes.md](api/reportes.md) | `/api/v1/reportes` | ATS mensual (XML SRI), resumen de ventas por período |
| [integracion.md](api/integracion.md) | — | 📋 **Propuesto:** Cómo core-service debe consumir billing-service; flujo de emisión de factura asíncrono; JWT y multi-tenancy; manejo de errores; checklist de implementación |

---

## flows/ — Procesos asíncrónos

| Documento | Descripción |
|-----------|-------------|
| [sri-submission-retry.md](flows/sri-submission-retry.md) | Flujo de firma, envío y autorización al SRI; cola de reintentos con backoff exponencial (1, 5, 15, 60, 240 min) y scheduler cada 60 seg |

---

## pendientes/ — Trabajos por implementar

**Empezar por [roadmap-sri-2026.md](pendientes/roadmap-sri-2026.md)** — es el backlog ejecutable priorizado por sprints, con checklist por GAP. Los demás documentos son detalle.

| Documento | Prioridad | Descripción |
|-----------|-----------|-------------|
| [roadmap-sri-2026.md](pendientes/roadmap-sri-2026.md) | 🔴 Alta | 📋 **División en 6 fases con dependencias.** Punto de entrada para arrancar el desarrollo. Cada fase apunta al detalle del GAP en gap-analysis. |
| [anulacion-sri.md](pendientes/anulacion-sri.md) | 🔴 Alta | 📋 Anulación fiscal SRI + notas de crédito. Hoy el endpoint `POST /comprobantes/{id}/anular` solo hace UPDATE local; falta cumplir normativa Ecuador (ventana día 7, consumidor final, catálogo de motivos, NC tipo 04, workflow de aprobación). BD ya modeló las 3 tablas necesarias. |
| [gap-analysis-sri-2026.md](pendientes/gap-analysis-sri-2026.md) | 🔴 Alta | 📋 Cruce completo de brechas entre normativa SRI 2025-2026 y estado actual del código. 13 GAPs identificados: ficha técnica v2.1.0 vs v2.32, transmisión inmediata desde 2026-01-01, notas de crédito/débito/retención sin código, secuencial no atómico, catálogos SRI sin usar, ATS incompleto, bancarización sin validar. |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` dentro de `api/` y `flows/`.
- `billing-service/README.md` permanece en la raíz y enlaza aquí.
