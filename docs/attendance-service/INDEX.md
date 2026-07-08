# attendance-service — Índice de documentación

Registro de asistencias y mensajería automatizada. Este servicio no tenía documentación de API separada — toda su documentación vive en la propia carpeta del servicio:

| Documento | Contenido |
|-----------|-----------|
| [attendance-service/README.md](../../attendance-service/README.md) | Variables de entorno, Docker, arquitectura, endpoints principales, desarrollo local |
| [attendance-service/CLAUDE.md](../../attendance-service/CLAUDE.md) | Convenciones de código: patrón `Mono.defer()`, mapeo de excepciones a HTTP, `MensajeriaJob`, FK constraints en tests, seguridad (`JwtPrincipal`, `AccessControlService`) |

---

## Convenciones de esta carpeta

- `attendance-service/CLAUDE.md` y `attendance-service/README.md` permanecen en la raíz de `attendance-service/` y enlazan aquí.
- Si en el futuro se agrega documentación de API extensa (más allá del listado de endpoints en README), debe ir en `docs/attendance-service/api/` siguiendo el mismo patrón que `auth-service` y `core-service`.
