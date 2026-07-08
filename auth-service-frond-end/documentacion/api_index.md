# Índice de endpoints — Auth Service

Base URL: `http://localhost:8080/api/v1`  
Repositorio: `src/infrastructure/http/auth/AuthHttpRepository.ts`  
Port: `src/domain/auth/ports/AuthRepository.port.ts`

---

## Autenticación

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `loginStaff` | POST | `/auth/login` | — |
| `loginPlatform` | POST | `/auth/platform/login` | — |
| `loginApp` | POST | `/auth/app/login` | — |
| `logout` | POST | `/auth/logout` | staff |
| `refreshToken` | POST | `/auth/refresh` | — |
| `requestPasswordReset` | POST | `/auth/password/reset-request` | — |
| `confirmPasswordReset` | POST | `/auth/password/reset` | — |
| `changePassword` | POST | `/auth/password/change` | staff |

---

## Usuarios Staff

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getUsuarios` | GET | `/usuarios` | staff |
| `crearUsuario` | POST | `/usuarios` | staff |
| `editarUsuario` | PATCH | `/usuarios/{id}` | staff |
| `getPermisosUsuario` | GET | `/usuarios/{id}/permisos` | staff |
| `desactivarUsuario` | PUT | `/usuarios/{id}/desactivar` | staff |
| `activarUsuario` | PUT | `/usuarios/{id}/activar` | staff |

---

## Roles (staff)

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getRoles` | GET | `/roles` | staff |
| `getRolById` | GET | `/roles/{id}` | staff |
| `crearRol` | POST | `/roles` | staff |
| `getRolPermisos` | GET | `/roles/{id}/permisos` | staff |
| `actualizarRolPermisos` | PUT | `/roles/{id}/permisos` | staff |
| `eliminarRol` | DELETE | `/roles/{id}` | staff |

---

## Permisos

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getPermisos` | GET | `/permisos` | staff |
| `getPermisosByRol` | GET | `/permisos/by-rol/{id}` | staff |

---

## Personas

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `buscarPersonaPorCI` | GET | `/personas/ci/{ci}` | staff |
| `crearPersona` | POST | `/personas` | staff |
| `actualizarPersona` | PUT | `/personas/{id}` | staff |

---

## App Usuarios (clientes móvil)

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `crearUsuarioApp` | POST | `/app-usuarios` | staff |
| `activarUsuarioApp` | PUT | `/app-usuarios/{id}/activar` | staff |
| `desactivarUsuarioApp` | PUT | `/app-usuarios/{id}/desactivar` | staff |

---

## Bitácora

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getBitacora` | GET | `/bitacora` | staff |

---

## Plataforma — Operadores

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getOperadoresPlataforma` | GET | `/platform/usuarios` | platform |
| `crearOperadorPlataforma` | POST | `/platform/usuarios` | platform (super_admin) |
| `desactivarOperadorPlataforma` | PUT | `/platform/usuarios/{id}/desactivar` | platform (super_admin) |

---

## Plataforma — Roles

| Método repo | HTTP | Endpoint | Token requerido |
|---|---|---|---|
| `getRolesPlataforma` | GET | `/platform/roles` | platform |
| `getCompaniasBasicas` | GET | `/platform/companias` | platform |
| `getSucursalesByCompania` | GET | `/platform/companias/{id}/sucursales` | platform |
| `getRolPermisosPlataforma` | GET | `/platform/roles/{id}/permisos` | platform |
| `crearRolPlataforma` | POST | `/platform/roles` | platform (super_admin) |
| `actualizarRolPlataforma` | PATCH | `/platform/roles/{id}` | platform (super_admin) |
| `eliminarRolPlataforma` | DELETE | `/platform/roles/{id}` | platform (super_admin) |
| `actualizarRolPermisosPlataforma` | PUT | `/platform/roles/{id}/permisos` | platform (super_admin) |
