# APP USUARIOS API

Base URL: `/api/v1/app-usuarios`

Endpoints para gestionar las cuentas de acceso a la app móvil de los socios (clientes) del gym. Estos endpoints son operados por el staff del gym, no por los clientes directamente.

Los campos de request y response usan **camelCase**.

---

## Índice

- [POST /app-usuarios](#post-app-usuarios)
- [GET /app-usuarios/por-ci/{ci}](#get-app-usuariospor-cici)
- [PATCH /app-usuarios/{id}](#patch-app-usuariosid)
- [PUT /app-usuarios/{id}/activar](#put-app-usuariosidactivar)
- [PUT /app-usuarios/{id}/desactivar](#put-app-usuariosiddesactivar)

---

## POST /app-usuarios

Crea una cuenta de acceso a la app para una persona ya registrada en `identidad.personas`. La persona puede tener como máximo una cuenta por compañía.

**Seguridad:** `tipo=staff`.

### Request

```http
POST /api/v1/app-usuarios
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "idPersona": 10,
  "login": "maria@gmail.com",
  "password": "PrimerAcceso123!",
  "idCompania": null
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `idPersona` | number | Sí | ID de la `Persona` a la que se le crea el acceso |
| `login` | string | Sí | Username de la cuenta. Único dentro de la compañía |
| `password` | string | Sí | Contraseña inicial (mín. 8 caracteres) |
| `idCompania` | number | No | Si es `null`, se toma del JWT del staff que ejecuta la acción |

La cuenta se crea con `requiereCambioPwd=true`. En el próximo login del cliente, el campo `requiereCambioPwd` vendrá en `true` indicando que debe cambiar la contraseña.

### Response 201

```json
{
  "id": 15,
  "login": "maria@gmail.com",
  "activo": true,
  "ultimoAcceso": null
}
```

### Errores

| Código | Cuándo |
|---|---|
| 409 | La persona ya tiene una cuenta app en esta compañía |
| 409 | El `login` ya está en uso en esta compañía |
| 404 | No existe una `Persona` con ese `idPersona` |

---

## GET /app-usuarios/por-ci/{ci}

Busca la cuenta app de un cliente a partir de la CI de la persona vinculada. Útil para el workflow de recepción cuando el staff identifica al socio por su cédula.

**Seguridad:** `tipo=staff`.

### Request

```http
GET /api/v1/app-usuarios/por-ci/1001234567
Authorization: Bearer {accessToken}
```

| Param | Tipo | Descripción |
|---|---|---|
| `ci` | string (path) | CI de la persona |

### Response 200

```json
{
  "id": 15,
  "login": "maria@gmail.com",
  "activo": true,
  "ultimoAcceso": "2026-06-10T10:00:00Z"
}
```

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con esa CI, o la persona no tiene cuenta app en esta compañía |

---

## PATCH /app-usuarios/{id}

Actualiza el login o la contraseña de una cuenta app. Solo se actualizan los campos enviados.

**Seguridad:** `tipo=staff`.

### Request

```http
PATCH /api/v1/app-usuarios/15
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "login": "maria.nuevo@gmail.com",
  "password": "NuevaPassword456!"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `login` | string | No | Nuevo username. Único en la compañía |
| `password` | string | No | Nueva contraseña. Se hashea con bcrypt |

### Response 200

Objeto `AppUsuarioResponse` actualizado.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe la cuenta app o no pertenece a la compañía del token |
| 409 | El nuevo `login` ya está en uso en la compañía |

---

## PUT /app-usuarios/{id}/activar

Reactiva una cuenta app que fue desactivada.

**Seguridad:** `tipo=staff`.

### Request

```http
PUT /api/v1/app-usuarios/15/activar
Authorization: Bearer {accessToken}
```

Sin body.

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe la cuenta app o no pertenece a la compañía del token |

---

## PUT /app-usuarios/{id}/desactivar

Desactiva una cuenta app. El cliente no podrá hacer login mientras la cuenta esté desactivada (recibirá 403).

**Seguridad:** `tipo=staff`.

### Request

```http
PUT /api/v1/app-usuarios/15/desactivar
Authorization: Bearer {accessToken}
```

Sin body.

### Response 200

Sin cuerpo.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe la cuenta app o no pertenece a la compañía del token |

---

## Códigos de error comunes

| Código | Significado |
|---|---|
| 401 | Token ausente o inválido |
| 403 | El token no es de tipo `staff` |
| 404 | Recurso no encontrado |
| 409 | Conflicto (cuenta duplicada, login en uso) |
