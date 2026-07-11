# billing-service — Índice de documentación

Facturación electrónica SRI Ecuador. Emisión, firma digital y autorización de facturas; gestión de certificados; reportes ATS y resumen de ventas. Ver [billing-service/README.md](../../billing-service/README.md) (Docker, variables de entorno) para el resto de la documentación. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [comprobantes.md](api/comprobantes.md) | `/api/v1/comprobantes` | CRUD de facturas y comprobantes: emisión, envío SRI, descargas (XML, RIDE), anulación |
| [admin.md](api/admin.md) | `/api/v1/admin` | Diagnóstico: ping SRI, estado de certificados, auditoría de emisión |
| [reportes.md](api/reportes.md) | `/api/v1/reportes` | ATS mensual (XML SRI), resumen de ventas por período |
| [integracion.md](api/integracion.md) | — | 📋 **Propuesto:** Cómo core-service debe consumir billing-service; flujo de emisión de factura asíncrono; JWT y multi-tenancy; manejo de errores; checklist de implementación |

---

## flows/ — Procesos asíncrónos

| Documento | Descripción |
|-----------|-------------|
| [sri-submission-retry.md](flows/sri-submission-retry.md) | Flujo de firma, envío y autorización al SRI; cola de reintentos con backoff exponencial (1, 5, 15, 60, 240 min) y scheduler cada 60 seg |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` dentro de `api/` y `flows/`.
- `billing-service/README.md` permanece en la raíz y enlaza aquí.
