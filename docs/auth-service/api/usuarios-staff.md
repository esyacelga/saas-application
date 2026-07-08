# USUARIOS STAFF, ROLES Y PERMISOS API

Base URL: `/api/v1`

Estos endpoints son utilizados por el panel administrativo del gym. Todos requieren un JWT de tipo `staff` con los permisos RBAC correspondientes.

Los campos de request y response usan **camelCase**.

---

## Índice

**Usuarios Staff**
- [GET /usuarios](#get-usuarios)
- [POST /usuarios](#post-usuarios)
- [PATCH /usuarios/{id}](#patch-usuariosid)
- [GET /usuarios/{id}/permisos](#get-usuariosidpermisos)
- [PUT /usuarios/{id}/activar](#put-usuariosidactivar)
- [PUT /usuarios/{id}/desactivar](#put-usuariosiddesactivar)

**Roles**
- [GET /roles](#get-roles)
- [POST /roles](#post-roles)
- [GET /roles/{id}](#get-rolesid)
- [GET /roles/{id}/permisos](#get-rolesidpermisos)
- [PUT /roles/{id}/permisos](#put-rolesidpermisos)
- [DELETE /roles/{id}](#delete-rolesid)

**Permisos**
- [GET /permisos](#get-permisos)
- [GET /permisos/by-rol/{idRol}](#get-permisosby-rolidrol)

---

## GET /usuarios

Lista todos los usuarios staff de la compañía del token. El filtrado por `idCompania` es automático — nunca se pueden ver usuarios de otra compañía.

**Seguridad:** `tipo=staff` + permiso `usuarios:leer`.

### Request

```http
GET /api/v1/usuarios
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 42,
    "idPersona": 10,
    "nombre": "Juan Pérez",
    "correo": "juan@gym.com",
    "fotoUrl": "https://res.cloudinary.com/...",
    "idRol": 2,
    "nombreRol": "Recepción",
    "activo": true,
    "ultimoAcceso": "2026-05-18T14:30:00Z"
  }
]
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | ID del usuario staff |
| `idPersona` | number | ID de la `Persona` vinculada |
| `nombre` | string | Nombre completo (desde `identidad.personas`) |
| `correo` | string | Email corporativo del empleado |
| `fotoUrl` | string \| null | URL de foto (desde `identidad.personas`) |
| `idRol` | number | ID del rol asignado |
| `nombreRol` | string | Nombre del rol |
| `activo` | boolean | Si la cuenta está habilitada |
| `ultimoAcceso` | string \| null | Timestamp del último login exitoso |

---

## POST /usuarios

Crea un nuevo usuario staff en la compañía. La `Persona` referenciada debe existir previamente.

**Seguridad:** `tipo=staff` + permiso `usuarios:crear`.

### Request

```http
POST /api/v1/usuarios
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPersona": 10,
  "correo": "juan@gym.com",
  "idRol": 2,
  "idSucursal": 1,
  "passwordTemporal": "TempPass2026!",
  "idCompania": null
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `idPersona` | number | Sí | ID de la `Persona` a vincular |
| `correo` | string | Sí | Email corporativo. Único dentro de la compañía |
| `idRol` | number | Sí | ID del rol. Debe pertenecer a la misma compañía |
| `idSucursal` | number | No | ID de la sucursal asignada |
| `passwordTemporal` | string | Sí | Password inicial. El usuario verá `requiereCambioPwd=true` en el login |
| `idCompania` | number | No | Si se omite o es `null`, se toma del JWT automáticamente |

### Response 201

Objeto `UsuarioStaffResponse` (mismo esquema que `GET /usuarios`). El campo `ultimoAcceso` será `null`.

### Errores

| Código | Cuándo |
|---|---|
| 409 | El correo ya está registrado en esta compañía |
| 400 | El `idRol` no pertenece a la compañía del token |
| 404 | No existe una `Persona` con ese `idPersona` |

---

## PATCH /usuarios/{id}

Actualiza el correo o el rol de un usuario staff. Solo se actualizan los campos enviados.

**Seguridad:** `tipo=staff` + permiso `usuarios:editar`.

### Request

```http
PATCH /api/v1/usuarios/42
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "correo": "juannuevo@gym.com",
  "idRol": 3
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `correo` | string | No | Nuevo email corporativo. Debe ser válido y único en la compañía |
| `idRol` | number | No | Nuevo rol. Debe pertenecer a la misma compañía |

### Response 200

Objeto `UsuarioStaffResponse` actualizado.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe el usuario o no pertenece a la compañía del token |
| 409 | El nuevo correo ya está en uso en la compañía |
| 400 | El `idRol` no pertenece a la compañía |

---

## GET /usuarios/{id}/permisos

Devuelve el rol y la lista de permisos resueltos del usuario — los mismos que quedarán embebidos en el JWT en el próximo login.

**Seguridad:** `tipo=staff` + permiso `usuarios:leer`.

### Request

```http
GET /api/v1/usuarios/42/permisos
Authorization: Bearer {accessToken}
```

### Response 200

```json
{
  "usuario": {
    "id": 42,
    "nombre": "Juan Pérez"
  },
  "rol": {
    "id": 2,
    "nombre": "Recepción",
    "descripcion": "Atención al cliente"
  },
  "permisos": [
    "clientes:leer",
    "clientes:crear",
    "membresias:leer"
  ]
}
```

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe el usuario o no pertenece a la compañía |

---

## PUT /usuarios/{id}/activar

Reactiva una cuenta staff desactivada.

**Seguridad:** `tipo=staff` + permiso `usuarios:crear`.

### Request

```http
PUT /api/v1/usuarios/42/activar
Authorization: Bearer {accessToken}
```

Sin body.

### Response 200

Sin cuerpo.

---

## PUT /usuarios/{id}/desactivar

Desactiva una cuenta staff. La cuenta no puede eliminarse definitivamente.

**Seguridad:** `tipo=staff` + permiso `usuarios:crear`.

> **Regla:** No se puede desactivar al último usuario activo con el rol "Dueño" en la compañía.

### Request

```http
PUT /api/v1/usuarios/42/desactivar
Authorization: Bearer {accessToken}
```

Sin body.

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 409 | El usuario es el único Dueño activo de la compañía |

---

---

## GET /roles

Lista todos los roles de la compañía del token.

**Seguridad:** `tipo=staff` + permiso `roles:leer`.

### Request

```http
GET /api/v1/roles
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  { "id": 1, "nombre": "Dueño", "descripcion": "Acceso total al sistema" },
  { "id": 2, "nombre": "Recepción", "descripcion": "Atención al cliente" }
]
```

---

## POST /roles

Crea un nuevo rol en la compañía.

**Seguridad:** `tipo=staff` + permiso `roles:crear`.

### Request

```http
POST /api/v1/roles
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nombre": "Entrenador",
  "descripcion": "Acceso a clientes y registro de asistencia",
  "idCompania": null,
  "idSucursal": null
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | Sí | Nombre del rol. Único dentro de la compañía |
| `descripcion` | string | No | Descripción del rol |
| `idCompania` | number | No | Si es `null`, se toma del JWT |
| `idSucursal` | number | No | Si es `null`, se toma del JWT |

### Response 201

```json
{ "id": 3, "nombre": "Entrenador", "descripcion": "Acceso a clientes y registro de asistencia" }
```

### Errores

| Código | Cuándo |
|---|---|
| 409 | Ya existe un rol con ese nombre en la compañía |

---

## GET /roles/{id}

Obtiene un rol por ID.

**Seguridad:** `tipo=staff` + permiso `roles:leer`.

### Request

```http
GET /api/v1/roles/2
Authorization: Bearer {accessToken}
```

### Response 200

```json
{ "id": 2, "nombre": "Recepción", "descripcion": "Atención al cliente" }
```

### Errores

| Código | Cuándo |
|---|---|
| 404 | El rol no existe o no pertenece a la compañía |

---

## GET /roles/{id}/permisos

Devuelve el rol junto con todos sus permisos asignados.

**Seguridad:** `tipo=staff` + permiso `roles:leer`.

### Request

```http
GET /api/v1/roles/2/permisos
Authorization: Bearer {accessToken}
```

### Response 200

```json
{
  "rol": {
    "id": 2,
    "nombre": "Recepción",
    "descripcion": "Atención al cliente"
  },
  "permisos": [
    { "id": 5, "nombre": "clientes:leer", "modulo": "clientes", "descripcion": "Ver listado y ficha" },
    { "id": 6, "nombre": "clientes:crear", "modulo": "clientes", "descripcion": "Registrar nuevo cliente" },
    { "id": 9, "nombre": "membresias:leer", "modulo": "membresias", "descripcion": "Ver membresías activas" }
  ]
}
```

---

## PUT /roles/{id}/permisos

Reemplaza en bloque todos los permisos asignados al rol. Los permisos que no estén en la lista quedan eliminados.

**Seguridad:** `tipo=staff` + permiso `roles:crear`.

### Request

```http
PUT /api/v1/roles/2/permisos
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPermisos": [5, 6, 9, 12]
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `idPermisos` | number[] | Sí | Lista de IDs de permisos. Puede ser vacía para quitar todos los permisos |

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 400 | Algún `idPermiso` no pertenece a la misma compañía del rol |

---

## DELETE /roles/{id}

Elimina un rol. Solo es posible si ningún usuario staff lo tiene asignado actualmente.

**Seguridad:** `tipo=staff` + permiso `roles:crear`.

### Request

```http
DELETE /api/v1/roles/3
Authorization: Bearer {accessToken}
```

### Response 204

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 409 | Hay usuarios con este rol asignado |

---

---

## GET /permisos

Lista todos los permisos disponibles de la compañía del token. Se usan para saber qué IDs pasar al actualizar los permisos de un rol.

**Seguridad:** `tipo=staff` + permiso `roles:leer`.

### Request

```http
GET /api/v1/permisos
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  { "id": 5, "nombre": "clientes:leer", "modulo": "clientes", "descripcion": "Ver listado y ficha" },
  { "id": 6, "nombre": "clientes:crear", "modulo": "clientes", "descripcion": "Registrar nuevo cliente" },
  { "id": 9, "nombre": "membresias:leer", "modulo": "membresias", "descripcion": "Ver membresías activas" },
  { "id": 10, "nombre": "usuarios:leer", "modulo": "usuarios", "descripcion": "Ver lista de empleados" }
]
```

El formato de `nombre` siempre es `modulo:accion`.

---

## GET /permisos/by-rol/{idRol}

Devuelve los permisos asignados a un rol específico.

**Seguridad:** `tipo=staff` + permiso `roles:leer`.

### Request

```http
GET /api/v1/permisos/by-rol/2
Authorization: Bearer {accessToken}
```

### Response 200

Array de `PermisoResponse` (mismo esquema que `GET /permisos`), filtrado al rol indicado.

---

## Códigos de error comunes

| Código | Significado |
|---|---|
| 400 | Datos inválidos (campo requerido vacío, rol/permiso de otra compañía, etc.) |
| 401 | Token ausente o inválido |
| 403 | El token no es de tipo `staff` o le falta el permiso requerido |
| 404 | Recurso no encontrado |
| 409 | Conflicto de unicidad o regla de negocio violada |
