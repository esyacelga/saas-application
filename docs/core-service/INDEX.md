# core-service — Índice de documentación

Gestión de clientes y membresías; validación de acceso al gym. Ver [core-service/README.md](../../core-service/README.md) (Docker, variables de entorno, endpoints) y [core-service/CLAUDE.md](../../core-service/CLAUDE.md) (arquitectura, testing) para el resto de la documentación. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [clientes.md](api/clientes.md) | `/api/v1/clientes` | CRUD de clientes: registro, búsqueda, perfil, acceso desde plataforma |
| [membresias.md](api/membresias.md) | `/api/v1/clientes/{id}/membresias` | Venta, validación de acceso, historial, anulación de membresías |
| [tipos-membresia.md](api/tipos-membresia.md) | `/api/v1/tipos-membresia` | Catálogo de tipos de membresía (calendario y por accesos) |
| [congelamientos.md](api/congelamientos.md) | `/api/v1/congelamientos` | Congelamiento y reactivación de membresías |
| [internal.md](api/internal.md) | `/internal/v1` | Endpoints privados: conteo de clientes activos para platform-service |

---

## spec/ — Especificaciones de features en diseño

| Documento | Estado | Contenido |
|-----------|--------|-----------|
| [solicitudes-membresia.md](spec/solicitudes-membresia.md) | ✅ Implementado (backend) | Cliente PWA solicita membresía autoservicio → staff completa venta. Endpoints, decisiones PO y contrato ya reflejados en [api/membresias.md](api/membresias.md) (§`POST /clientes/me/membresias/solicitar`, §`GET /companias/{id}/membresias/pendientes/contador`, §`POST /membresias/{id}/confirmar-pago` con body condicional). Schema Liquibase en baseline GYM-001. |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` (antes `NOMBRE_API.md`, ahora `nombre.md` dentro de `api/`).
- `core-service/CLAUDE.md` y `core-service/README.md` permanecen en la raíz de `core-service/` y enlazan aquí.
- Specs de features no implementados en `spec/` — se mueven a `api/` una vez que el código esté escrito.
