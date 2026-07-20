# Backend Prompt – Módulo Roles y Permisos (Platform)

> **ESTADO:** 📜 Histórico — prompt usado para pedir un cambio al backend. NO es documentación de estado actual. Ver [../../STATUS.md](../../../STATUS.md).

## Contexto

Este documento describe los endpoints REST necesarios para el módulo de gestión de roles y permisos dentro del panel de plataforma (`/platform/**`). Las tablas involucradas son `seguridad.roles`, `seguridad.permisos` y `seguridad.rol_permisos`.

### Esquema de base de datos relevante

```sql
-- seguridad.permisos
id, id_compania, id_sucursal, nombre, descripcion, modulo, eliminado, 
creacion_fecha, creacion_usuario, modifica_fecha, modifica_usuario
UNIQUE (id_compania, nombre)

-- seguridad.rol_permisos
id_rol (FK → seguridad.roles), id_permiso (FK → seguridad.permisos),
eliminado, creacion_fecha, creacion_usuario, modifica_fecha, modifica_usuario
PK (id_rol, id_permiso)
```

> **Borrado lógico**: nunca eliminar físicamente. Usar `eliminado = true`.

---

## 1. Roles (existentes, referencia)

### `GET /platform/roles`
Lista todos los roles de plataforma (sin filtrar por `eliminado = false` para administración).

**Response 200:**
```json
[
  {
    "id": 1,
    "nombre": "Administrador",
    "descripcion": "Acceso completo",
    "id_compania": 3,
    "nombre_compania": "Empresa ABC",
    "total_usuarios": 5
  }
]
```

### `GET /platform/roles/{id}`
Obtiene el detalle de un rol.

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del rol |

**Response 200:**
```json
{
  "id": 1,
  "nombre": "Administrador",
  "descripcion": "Acceso completo",
  "id_compania": 3,
  "nombre_compania": "Empresa ABC",
  "total_usuarios": 5
}
```
**Response 404:** Rol no encontrado.

---

## 2. Permisos por Rol

### `GET /platform/roles/{id}/permisos/detalle`
Lista los permisos asignados a un rol con información de sucursal (resultado del JOIN).

**Query SQL ejecutado:**
```sql
SELECT p.id,
       s.nombre AS nombre_sucursal,
       p.nombre,
       p.descripcion,
       p.modulo
FROM seguridad.permisos p
INNER JOIN tenant.sucursales s ON s.id = p.id_sucursal
INNER JOIN seguridad.rol_permisos r ON r.id_permiso = p.id
WHERE r.id_rol = :id
  AND r.eliminado = false
  AND p.eliminado = false
```

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del rol |

**Response 200:**
```json
[
  {
    "id": 12,
    "nombre_sucursal": "Sucursal Central",
    "nombre": "usuarios:leer",
    "descripcion": "Permite ver la lista de usuarios",
    "modulo": "usuarios"
  }
]
```
**Response 404:** Rol no encontrado.

---

### `POST /platform/roles/{id}/permisos`
Asigna un permiso existente a un rol (inserta en `seguridad.rol_permisos`).

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del rol |

**Request body:**
```json
{
  "id_permiso": 12
}
```

**Auditoría:** registrar `creacion_usuario` con el usuario autenticado de plataforma.

**Response 201:**
```json
{ "mensaje": "Permiso asignado correctamente" }
```
**Response 400:** Body inválido.  
**Response 404:** Rol o permiso no encontrado.  
**Response 409:** El permiso ya está asignado a este rol (registro activo existente).

---

### `DELETE /platform/roles/{id}/permisos/{idPermiso}`
Quita un permiso de un rol mediante borrado lógico (`eliminado = true`).

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del rol |
| `idPermiso` | path `integer` | ID del permiso |

**Auditoría:** registrar `modifica_usuario` y `modifica_fecha`.

**Response 200:**
```json
{ "mensaje": "Permiso quitado del rol" }
```
**Response 404:** Asignación no encontrada o ya eliminada.

---

## 3. Gestión de Permisos

### `GET /platform/permisos`
Lista todos los permisos disponibles con su información de compañía y sucursal.

**Response 200:**
```json
[
  {
    "id": 12,
    "nombre": "usuarios:leer",
    "modulo": "usuarios",
    "descripcion": "Permite ver la lista de usuarios",
    "id_compania": 3,
    "id_sucursal": 7,
    "nombre_sucursal": "Sucursal Central"
  }
]
```

---

### `GET /platform/permisos/{id}`
Obtiene el detalle de un permiso.

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del permiso |

**Response 200:**
```json
{
  "id": 12,
  "nombre": "usuarios:leer",
  "modulo": "usuarios",
  "descripcion": "Permite ver la lista de usuarios",
  "id_compania": 3,
  "id_sucursal": 7,
  "nombre_sucursal": "Sucursal Central"
}
```
**Response 404:** Permiso no encontrado.

---

### `POST /platform/permisos`
Crea un nuevo permiso en `seguridad.permisos`.

**Request body:**
```json
{
  "nombre": "usuarios:leer",
  "modulo": "usuarios",
  "descripcion": "Permite ver la lista de usuarios",
  "id_compania": 3,
  "id_sucursal": 7
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `nombre` | `string` (max 100) | Sí | Nombre único dentro de la compañía |
| `modulo` | `string` (max 50) | Sí | Módulo al que pertenece |
| `descripcion` | `string` (max 255) | No | Descripción legible |
| `id_compania` | `integer` | Sí | FK a la compañía |
| `id_sucursal` | `integer` | Sí | FK a la sucursal |

**Validación:** unicidad `(id_compania, nombre)`.

**Auditoría:** registrar `creacion_usuario` con el usuario autenticado.

**Response 201:**
```json
{
  "id": 13,
  "nombre": "usuarios:leer",
  "modulo": "usuarios",
  "descripcion": "Permite ver la lista de usuarios",
  "id_compania": 3,
  "id_sucursal": 7,
  "nombre_sucursal": "Sucursal Central"
}
```
**Response 400:** Campos requeridos faltantes o inválidos.  
**Response 409:** Ya existe un permiso con ese nombre en la compañía.  
**Response 500:** Error interno del servidor.

---

### `PUT /platform/permisos/{id}`
Actualiza los datos de un permiso existente.

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del permiso |

**Request body (todos opcionales, al menos uno requerido):**
```json
{
  "nombre": "usuarios:listar",
  "descripcion": "Descripción actualizada",
  "modulo": "usuarios"
}
```

**Auditoría:** registrar `modifica_usuario` y `modifica_fecha`.

**Response 200:**
```json
{
  "id": 13,
  "nombre": "usuarios:listar",
  "modulo": "usuarios",
  "descripcion": "Descripción actualizada",
  "id_compania": 3,
  "id_sucursal": 7,
  "nombre_sucursal": "Sucursal Central"
}
```
**Response 400:** Body vacío o campos inválidos.  
**Response 404:** Permiso no encontrado.  
**Response 409:** Conflicto de nombre único.

---

### `DELETE /platform/permisos/{id}`
Marca un permiso como eliminado (`eliminado = true`). Borrado lógico.

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | path `integer` | ID del permiso |

**Auditoría:** registrar `modifica_usuario` y `modifica_fecha`.

**Response 200:**
```json
{ "mensaje": "Permiso eliminado" }
```
**Response 404:** Permiso no encontrado.  
**Response 409:** El permiso está asignado a uno o más roles activos (considerar bloquear o des-asignar en cascada).

---

## 4. Códigos de estado HTTP resumidos

| Código | Significado |
|--------|-------------|
| 200 | Operación exitosa (GET, PUT, DELETE) |
| 201 | Recurso creado (POST) |
| 400 | Datos de entrada inválidos |
| 404 | Recurso no encontrado |
| 409 | Conflicto (duplicado o restricción de integridad) |
| 500 | Error interno del servidor |

---

## 5. Notas de implementación

- **Borrado lógico obligatorio**: siempre `eliminado = true`, nunca `DELETE` físico.
- **Auditoría**: los campos `creacion_usuario` / `modifica_usuario` deben llenarse con el identificador del operador de plataforma autenticado (extraído del JWT).
- **Filtrado por defecto**: todos los listados deben excluir registros con `eliminado = true` salvo que se indique lo contrario.
- **Soft delete en cascada**: al eliminar un permiso, considerar marcar también sus registros en `seguridad.rol_permisos` como eliminados para mantener consistencia.
