# auth-service — Índice de documentación

Puerto único de autenticación y autorización de toda la plataforma (JWT, usuarios, roles, personas). Ver [auth-service/README.md](../../auth-service/README.md) y [auth-service/CLAUDE.md](../../auth-service/CLAUDE.md) para arquitectura, seguridad y convenciones de desarrollo. Este índice cubre solo la documentación de API.

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [auth.md](api/auth.md) | `/api/v1/auth` | Login (3 flujos), refresh, OAuth, registro, reset de contraseña, QR — todo público excepto `/logout` |
| [personas.md](api/personas.md) | `/api/v1/personas` | Identidad global compartida entre gimnasios (CI único) |
| [usuarios-staff.md](api/usuarios-staff.md) | `/api/v1` | Usuarios staff, roles y permisos (RBAC) del panel administrativo |
| [app-usuarios.md](api/app-usuarios.md) | `/api/v1/app-usuarios` | Cuentas de acceso a la app móvil de los socios, gestionadas por staff |
| [platform.md](api/platform.md) | `/api/v1/platform` | Administración de la plataforma SaaS — solo JWT tipo `plataforma` |
| [bitacora.md](api/bitacora.md) | `/api/v1/bitacora` | Auditoría de solo lectura de toda operación de escritura del sistema |

---

## Convenciones de esta carpeta

- Nombres de archivo en `kebab-case` (antes `NOMBRE_API.md`, ahora `nombre.md` dentro de `api/`).
- `auth-service/CLAUDE.md` y `auth-service/README.md` permanecen en la raíz de `auth-service/` y enlazan aquí.
- `auth-service/bd-credentials.md` (credenciales locales de BD) no está versionado — cubierto por `.gitignore` (`bd-credentials.md`, `*credentials*.md`).
