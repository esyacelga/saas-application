# PLATFORM API

> **ESTADO:** ✅ Refleja el código actual (verificado 2026-07-08 contra `ApiRouter.java`). Ver [../../STATUS.md](../../STATUS.md).

Base URL: `/api/v1/platform`

Endpoints de administración de la plataforma SaaS. Solo accesibles con un JWT de tipo `plataforma`.

Los campos de request y response usan **camelCase**, excepto donde se indica (algunos campos de `PermisoPlataformaResponse` usan `snake_case` por `@JsonProperty`).

---

## Índice

**Usuarios de Plataforma**
- [GET /platform/usuarios](#get-platformusuarios)
- [POST /platform/usuarios](#post-platformusuarios)
- [PATCH /platform/usuarios/{id}](#patch-platformusuariosid)
- [POST /platform/usuarios/{id}/foto](#post-platformusuariosidfoto)
- [PUT /platform/usuarios/{id}/desactivar](#put-platformusuariosiddesactivar)

**Roles de Plataforma**
- [GET /platform/roles](#get-platformroles)
- [POST /platform/roles](#post-platformroles)
- [PUT /platform/roles/{id}](#put-platformrolesid)
- [DELETE /platform/roles/{id}](#delete-platformrolesid)
- [GET /platform/roles/{id}/permisos](#get-platformrolesidpermisos)
- [PUT /platform/roles/{id}/permisos](#put-platformrolesidpermisos)
- [GET /platform/roles/{id}/permisos/detalle](#get-platformrolesidpermisosdetalle)
- [POST /platform/roles/{id}/permisos](#post-platformrolesidpermisos)
- [DELETE /platform/roles/{id}/permisos/{idPermiso}](#delete-platformrolesidpermisosidpermiso)

**Permisos de Plataforma**
- [GET /platform/permisos](#get-platformpermisos)
- [POST /platform/permisos](#post-platformpermisos)
- [PUT /platform/permisos/{id}](#put-platformpermisosid)
- [DELETE /platform/permisos/{id}](#delete-platformpermisosid)

**Compañías y Sucursales**
- [GET /platform/companias](#get-platformcompanias)
- [GET /platform/companias/{idCompania}/sucursales](#get-platformcompaniasidcompaniasucursales)
- [GET /platform/companias/{idCompania}/usuarios](#get-platformcompaniasidcompaniausuarios)
- [PUT /platform/companias/{idCompania}/usuarios/{id}/password](#put-platformcompaniasidcompaniausuariosidpassword)

---

## GET /platform/usuarios

Lista todos los operadores de plataforma.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/usuarios
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 1,
    "nombre": "Santiago Yacelga",
    "correo": "admin@gymadministrator.com",
    "rolPlataforma": "super_admin",
    "activo": true,
    "ultimoAcceso": "2026-06-19T07:43:00Z",
    "fotoUrl": null
  },
  {
    "id": 2,
    "nombre": "Ana Soporte",
    "correo": "ana@gymadministrator.com",
    "rolPlataforma": "soporte",
    "activo": true,
    "ultimoAcceso": "2026-06-15T09:00:00Z",
    "fotoUrl": "https://res.cloudinary.com/..."
  }
]
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | ID del operador |
| `nombre` | string | Nombre completo (desde `identidad.personas`) |
| `correo` | string | Email de acceso a la plataforma |
| `rolPlataforma` | string | `super_admin` \| `soporte` \| `viewer` |
| `activo` | boolean | Si la cuenta está habilitada |
| `ultimoAcceso` | string \| null | Timestamp del último login exitoso |
| `fotoUrl` | string \| null | URL de foto (desde `identidad.personas`) |

---

## POST /platform/usuarios

Crea un nuevo operador de plataforma. La `Persona` referenciada debe existir en `identidad.personas`.

**Seguridad:** `tipo=plataforma`.

### Request

```http
POST /api/v1/platform/usuarios
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPersona": 5,
  "correo": "ana@gymadministrator.com",
  "password": "TempPass2026!",
  "rol": "soporte"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `idPersona` | number | Sí | ID de la `Persona` vinculada |
| `correo` | string | Sí | Email de acceso. Único en `saas.usuarios_plataforma` |
| `password` | string | Sí | Contraseña inicial (mín. 8 caracteres) |
| `rol` | string | Sí | `super_admin` \| `soporte` \| `viewer` |

### Response 201

Objeto `PlatformUsuarioResponse` (mismo esquema que `GET /platform/usuarios`, `ultimoAcceso` es `null`).

### Errores

| Código | Cuándo |
|---|---|
| 409 | El correo ya está en uso |
| 404 | No existe la `Persona` con ese `idPersona` |

---

## PATCH /platform/usuarios/{id}

Cambia el rol de un operador de plataforma.

**Seguridad:** `tipo=plataforma`.

### Request

```http
PATCH /api/v1/platform/usuarios/2
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "rol": "viewer"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `rol` | string | Sí | Nuevo rol: `super_admin` \| `soporte` \| `viewer` |

### Response 200

Objeto `PlatformUsuarioResponse` actualizado.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe el operador |

---

## POST /platform/usuarios/{id}/foto

Sube o reemplaza la foto del operador. La foto se guarda en Cloudinary y se actualiza en la `Persona` vinculada.

**Seguridad:** `tipo=plataforma`.

### Request

```http
POST /api/v1/platform/usuarios/1/foto
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| Campo (form) | Tipo | Descripción |
|---|---|---|
| `file` | binary | Imagen (JPEG, PNG, WebP, etc.) |

### Response 200

Objeto `PlatformUsuarioResponse` con `fotoUrl` actualizado.

---

## PUT /platform/usuarios/{id}/desactivar

Desactiva la cuenta de un operador de plataforma.

**Seguridad:** `tipo=plataforma`.

> **Regla:** No se puede desactivar al último `super_admin` activo de la plataforma.

### Request

```http
PUT /api/v1/platform/usuarios/2/desactivar
Authorization: Bearer {accessToken}
```

Sin body.

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 409 | El operador es el único `super_admin` activo |

---

---

## GET /platform/roles

Lista todos los roles de staff de todas las compañías.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/roles
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 1,
    "nombre": "Dueño",
    "descripcion": "Acceso total",
    "idCompania": 1,
    "nombreCompania": "Gym Elite",
    "totalUsuarios": 2
  },
  {
    "id": 2,
    "nombre": "Recepción",
    "descripcion": "Atención al cliente",
    "idCompania": 1,
    "nombreCompania": "Gym Elite",
    "totalUsuarios": 5
  }
]
```

---

## POST /platform/roles

Crea un rol de staff para una compañía específica.

**Seguridad:** `tipo=plataforma`.

### Request

```http
POST /api/v1/platform/roles
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nombre": "Contador",
  "descripcion": "Acceso a módulo financiero",
  "idCompania": 1,
  "idSucursal": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | Sí | Nombre del rol. Único dentro de la compañía |
| `descripcion` | string | No | Descripción |
| `idCompania` | number | Sí | ID de la compañía propietaria del rol |
| `idSucursal` | number | No | ID de la sucursal |

### Response 201

Objeto `RolPlataformaResponse` (mismo esquema que los elementos de `GET /platform/roles`).

### Errores

| Código | Cuándo |
|---|---|
| 409 | Ya existe un rol con ese nombre en la compañía |

---

## PUT /platform/roles/{id}

Actualiza el nombre y la descripción de un rol.

**Seguridad:** `tipo=plataforma`.

### Request

```http
PUT /api/v1/platform/roles/3
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nombre": "Contador Senior",
  "descripcion": "Acceso total al módulo financiero"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | Sí | Nuevo nombre |
| `descripcion` | string | No | Nueva descripción |

### Response 200

Objeto `RolPlataformaResponse` actualizado.

---

## DELETE /platform/roles/{id}

Elimina un rol. Solo posible si ningún usuario lo tiene asignado.

**Seguridad:** `tipo=plataforma`.

### Request

```http
DELETE /api/v1/platform/roles/3
Authorization: Bearer {accessToken}
```

### Response 204

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 409 | Hay usuarios staff con este rol asignado |

---

## GET /platform/roles/{id}/permisos

Devuelve el rol junto con sus permisos asignados.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/roles/2/permisos
Authorization: Bearer {accessToken}
```

### Response 200

```json
{
  "rol": {
    "id": 2,
    "nombre": "Recepción",
    "descripcion": "Atención al cliente",
    "idCompania": 1,
    "nombreCompania": "Gym Elite",
    "totalUsuarios": 5
  },
  "permisos": [
    { "id": 5, "nombre": "clientes:leer", "modulo": "clientes", "descripcion": "Ver listado y ficha" },
    { "id": 6, "nombre": "clientes:crear", "modulo": "clientes", "descripcion": "Registrar nuevo cliente" }
  ]
}
```

---

## PUT /platform/roles/{id}/permisos

Reemplaza en bloque todos los permisos del rol.

**Seguridad:** `tipo=plataforma`.

### Request

```http
PUT /api/v1/platform/roles/2/permisos
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPermisos": [5, 6, 9, 12]
}
```

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 400 | Algún permiso no pertenece a la misma compañía del rol |

---

## GET /platform/roles/{id}/permisos/detalle

Devuelve los permisos del rol con información adicional de sucursal.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/roles/2/permisos/detalle
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 5,
    "nombre_sucursal": "Quito Centro",
    "nombre": "clientes:leer",
    "descripcion": "Ver listado y ficha",
    "modulo": "clientes"
  }
]
```

> Nota: `nombre_sucursal` se serializa en snake_case por `@JsonProperty`.

---

## POST /platform/roles/{id}/permisos

Agrega un solo permiso a un rol (sin reemplazar los existentes).

**Seguridad:** `tipo=plataforma`.

### Request

```http
POST /api/v1/platform/roles/2/permisos
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPermiso": 12
}
```

### Response 201

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 400 | El permiso no pertenece a la misma compañía del rol |
| 409 | El permiso ya estaba asignado al rol |

---

## DELETE /platform/roles/{id}/permisos/{idPermiso}

Elimina un permiso específico de un rol.

**Seguridad:** `tipo=plataforma`.

### Request

```http
DELETE /api/v1/platform/roles/2/permisos/12
Authorization: Bearer {accessToken}
```

### Response 204

Sin cuerpo.

---

---

## GET /platform/permisos

Lista todos los permisos de staff de todas las compañías.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/permisos
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 5,
    "nombre": "clientes:leer",
    "modulo": "clientes",
    "descripcion": "Ver listado y ficha de clientes",
    "id_compania": 1,
    "id_sucursal": 1,
    "nombre_sucursal": "Quito Centro"
  }
]
```

> Nota: `id_compania`, `id_sucursal` y `nombre_sucursal` se serializan en snake_case por `@JsonProperty`.

---

## POST /platform/permisos

Crea un nuevo permiso para una compañía.

**Seguridad:** `tipo=plataforma`.

### Request

```http
POST /api/v1/platform/permisos
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nombre": "usuarios:editar",
  "modulo": "usuarios",
  "descripcion": "Editar datos de un empleado",
  "idCompania": 1,
  "idSucursal": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | Sí | Formato `modulo:accion`. Único dentro de la compañía. Máx. 100 caracteres |
| `modulo` | string | Sí | Módulo al que aplica. Máx. 50 caracteres |
| `descripcion` | string | No | Descripción legible. Máx. 255 caracteres |
| `idCompania` | number | Sí | ID de la compañía propietaria |
| `idSucursal` | number | Sí | ID de la sucursal |

### Response 201

Objeto `PermisoPlataformaResponse` (mismo esquema que elementos de `GET /platform/permisos`).

---

## PUT /platform/permisos/{id}

Actualiza nombre, módulo o descripción de un permiso existente.

**Seguridad:** `tipo=plataforma`.

### Request

```http
PUT /api/v1/platform/permisos/5
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nombre": "clientes:ver",
  "modulo": "clientes",
  "descripcion": "Ver listado, ficha y detalle del cliente"
}
```

Todos los campos son opcionales.

### Response 200

Objeto `PermisoPlataformaResponse` actualizado.

---

## DELETE /platform/permisos/{id}

Elimina un permiso. Si el permiso está asignado a algún rol, la asignación se elimina en cascada.

**Seguridad:** `tipo=plataforma`.

### Request

```http
DELETE /api/v1/platform/permisos/5
Authorization: Bearer {accessToken}
```

### Response 204

Sin cuerpo.

---

---

## GET /platform/companias

Lista todas las compañías (gyms) registradas en la plataforma.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/companias
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  { "id": 1, "nombre": "Gym Elite" },
  { "id": 2, "nombre": "PowerFit Centro" }
]
```

---

## GET /platform/companias/{idCompania}/sucursales

Lista las sucursales de una compañía específica.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/companias/1/sucursales
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  { "id": 1, "nombre": "Quito Centro" },
  { "id": 2, "nombre": "Quito Norte" }
]
```

---

## GET /platform/companias/{idCompania}/usuarios

Lista todos los usuarios staff de una compañía específica.

**Seguridad:** `tipo=plataforma`.

### Request

```http
GET /api/v1/platform/companias/1/usuarios
Authorization: Bearer {accessToken}
```

### Response 200

Array de `UsuarioStaffResponse`:

```json
[
  {
    "id": 42,
    "idPersona": 10,
    "nombre": "Juan Pérez",
    "correo": "juan@gym.com",
    "fotoUrl": null,
    "idRol": 2,
    "nombreRol": "Recepción",
    "activo": true,
    "ultimoAcceso": "2026-05-18T14:30:00Z"
  }
]
```

---

## PUT /platform/companias/{idCompania}/usuarios/{id}/password

Restablece la contraseña de un usuario staff directamente (acción de soporte de plataforma, no requiere token de reset).

**Seguridad:** `tipo=plataforma`.

### Request

```http
PUT /api/v1/platform/companias/1/usuarios/42/password
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "password": "NuevaPassword123!"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `password` | string | Sí | Nueva contraseña. Se hashea con bcrypt (mín. 12 rounds) |

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 404 | El usuario no existe o no pertenece a la compañía indicada |

---

## Códigos de error comunes

| Código | Significado |
|---|---|
| 401 | Token ausente o inválido |
| 403 | El token no es de tipo `plataforma` |
| 404 | Recurso no encontrado |
| 409 | Conflicto de unicidad o regla de negocio violada |
