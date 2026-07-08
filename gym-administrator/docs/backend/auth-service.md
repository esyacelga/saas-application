# Auth Service — Referencia de arquitectura

Repositorio: `C:\Respos\own-aplications\auth-service`
Documentación completa del proyecto: `C:\Respos\own-aplications\auth-service\CLAUDE.md`
Colección Postman: `C:\Respos\own-aplications\auth-service\auth-service.postman_collection.json`

---

## Stack

| Elemento | Valor |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 + Spring WebFlux (reactivo) |
| Acceso a BD | Spring Data R2DBC (no bloqueante) |
| Base de datos | PostgreSQL |
| Seguridad | Spring Security stateless + JJWT 0.12.6 |
| Arquitectura | Hexagonal (Ports & Adapters) |

Todo método retorna `Mono<T>` o `Flux<T>`. No hay llamadas bloqueantes.

---

## Capas (Hexagonal)

```
Handler (in/web)  →  UseCase port (in)  →  ApplicationService  →  Port (out)  →  PersistenceAdapter
```

Cuando se agrega un endpoint, hay que tocar las cuatro capas:
1. `domain/port/in/` — interfaz del caso de uso
2. `application/service/` — implementación
3. `infrastructure/adapter/out/persistence/` — adapter + repositorio R2DBC
4. `infrastructure/adapter/in/web/` — handler + registro en `ApiRouter`

---

## Tres niveles de usuario

| Nivel | Tabla | JWT `tipo` | Claims clave |
|---|---|---|---|
| Operador de plataforma | `saas.usuarios_plataforma` | `"plataforma"` | `rol_plataforma` (sin `id_compania`) |
| Staff del gimnasio | `seguridad.usuarios` | `"staff"` | `id_compania`, `id_sucursal`, `id_rol`, `permisos[]` |
| Cliente app móvil | `identidad.usuarios_app` | `"cliente"` | `id_compania`, `id_persona` |

Los tokens **no son intercambiables**. La ausencia de `id_compania` en el token de plataforma indica acceso global.

---

## Identidad centralizada en `identidad.personas`

**Regla de diseño crítica:** `nombre` y `foto_url` nunca se almacenan en las tablas de usuario. Siempre vienen de `identidad.personas` vía JOIN.

- Antes de crear cualquier usuario (staff, plataforma, app), la persona debe existir en `identidad.personas`.
- Los persistence adapters hacen `LEFT/INNER JOIN identidad.personas p ON *.id_persona = p.id` y exponen los alias `nombre_persona` / `foto_url_persona`.
- El `correo` en las tablas de usuario es el correo corporativo/login y puede diferir del `personas.correo` (correo personal).
- Para actualizar nombre o foto, se usa la API de Persona, no los endpoints de usuario.

Constraints únicos relacionados:
- `UsuarioStaff`: `UNIQUE (id_persona, id_compania)` — una persona puede ser staff de múltiples gimnasios
- `UsuarioPlataforma`: `UNIQUE (id_persona)` — una persona, un operador de plataforma
- `UsuarioApp`: `UNIQUE (id_persona, id_compania)` — una persona, una cuenta de cliente por gimnasio

---

## Prefijos de rutas

| Prefijo | Acceso |
|---|---|
| `/api/v1/auth/*` | Público (login, refresh, reset password) |
| `/api/v1/personas/*` | Público |
| `/api/v1/platform/*` | Solo `tipo=plataforma` |
| `/api/v1/usuarios`, `/roles`, `/permisos`, `/bitacora` | Staff con permisos |
| `/api/v1/app-usuarios/*` | Plataforma o staff con permisos |

---

## Permisos (RBAC)

Formato: `modulo:accion` (ej. `socios:leer`, `pagos:escribir`).
Los JWTs de staff incluyen la lista completa de permisos al momento del login — los otros microservicios pueden verificar acceso sin llamar al auth-service.

Helpers en `SecurityUtils`:
- `requirePlataforma(principal)` — solo operadores de plataforma
- `requireStaff(principal)` — solo staff
- `requirePermiso(principal, "modulo:accion")` — permiso granular

---

## Flujo de creación de usuario (nuevo patrón con id_persona)

```
1. POST /api/v1/personas           → crear o recuperar Persona por CI
2. POST /api/v1/usuarios           → crear UsuarioStaff con { id_persona, correo, id_rol, password_temporal }
   POST /api/v1/platform/usuarios  → crear UsuarioPlataforma con { id_persona, correo, password, rol }
   POST /api/v1/app-usuarios       → crear UsuarioApp con { id_persona, id_compania, login, password }
```

Si se envía un `id_persona` inexistente → 404 `Persona no encontrada`.

---

## Variables de entorno clave

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
JWT_SECRET, JWT_EXPIRY_STAFF, JWT_EXPIRY_CLIENTE, JWT_EXPIRY_REFRESH
BCRYPT_ROUNDS, MAX_LOGIN_ATTEMPTS, LOCKOUT_DURATION_MINUTES
```

---

## Reglas de negocio a recordar

- No se puede desactivar al último `super_admin` activo.
- No se puede desactivar al último staff con rol "Dueño" en una compañía.
- `Persona.ci` es inmutable después de la creación.
- Login fallido retorna 401 independientemente de si el correo existe (evita enumeración de usuarios).
- Los refresh tokens son de un solo uso — se invalidan en cada renovación.
- Todas las escrituras deben generar una entrada en `seguridad.bitacora_accesos`.
