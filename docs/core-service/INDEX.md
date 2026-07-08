# core-service — Índice de documentación

Gestión de clientes y membresías; validación de acceso al gym. Ver [core-service/README.md](../../core-service/README.md) (Docker, variables de entorno, endpoints) y [core-service/CLAUDE.md](../../core-service/CLAUDE.md) (arquitectura, testing) para el resto de la documentación. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [clientes.md](api/clientes.md) | `/api/v1/clientes` | API de clientes: registro, historial, membresías, respuestas en snake_case |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` (antes `NOMBRE_API.md`, ahora `nombre.md` dentro de `api/`).
- `core-service/CLAUDE.md` y `core-service/README.md` permanecen en la raíz de `core-service/` y enlazan aquí.
