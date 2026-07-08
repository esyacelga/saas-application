# platform-service — Índice de documentación

Gestión del SaaS: empresas, sucursales, planes y suscripciones. Este servicio no tenía documentación de API separada — toda su documentación vive en la propia carpeta del servicio:

| Documento | Contenido |
|-----------|-----------|
| [platform-service/README.md](../../platform-service/README.md) | Requisitos, variables de entorno, Docker (3 formas de levantar), esquema de BD, tests |
| [platform-service/CLAUDE.md](../../platform-service/CLAUDE.md) | Convenciones de código: seguridad (`JwtPrincipal`, `AccessControlService`), patrones de uso de comandos, Cloudinary, caché Redis, job de suscripciones, entidades de dominio, endpoints |

---

## Convenciones de esta carpeta

- `platform-service/CLAUDE.md` y `platform-service/README.md` permanecen en la raíz de `platform-service/` y enlazan aquí.
- Si en el futuro se agrega documentación de API extensa, debe ir en `docs/platform-service/api/` siguiendo el mismo patrón que `auth-service` y `core-service`.
