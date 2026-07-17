# AUTH API

> **ESTADO:** ✅ Refleja el código actual (verificado 2026-07-08 contra `ApiRouter.java`). Ver [../../STATUS.md](../../STATUS.md).

Base URL: `/api/v1/auth`

Todos los endpoints de este módulo son **públicos** (no requieren `Authorization` header), excepto `POST /logout`.

Los campos de request y response usan **camelCase**.

---

## Índice

- [POST /auth/platform/login](#post-authplatformlogin)
- [POST /auth/login](#post-authlogin)
- [POST /auth/app/login](#post-authapplogin)
- [POST /auth/app/oauth/google](#post-authappoauthgoogle)
- [POST /auth/app/oauth/facebook](#post-authappoauthfacebook)
- [POST /auth/app/oauth/completar-registro](#post-authappoauthcompletar-registro)
- [POST /auth/app/registro](#post-authappregistro)
- [POST /auth/refresh](#post-authrefresh)
- [POST /auth/logout](#post-authlogout)
- [POST /auth/password/reset-request](#post-authpasswordreset-request)
- [POST /auth/password/reset](#post-authpasswordreset)
- [GET /auth/companias-por-correo](#get-authcompanias-por-correo)
- [GET /auth/gimnasio/by-qr/{qrToken}](#get-authgimnasiobynqrqrtoken)

---

## POST /auth/platform/login

Login de operador de plataforma. Genera un JWT de tipo `plataforma`.

### Request

```http
POST /api/v1/auth/platform/login
Content-Type: application/json
```

```json
{
  "correo": "admin@gymadministrator.com",
  "password": "SuperClave2026"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `correo` | string | Sí | Email del operador |
| `password` | string | Sí | Contraseña en texto plano |

### Response 200 — Login exitoso

```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "dGhpcyBp...",
  "expiresIn": 28800,
  "usuario": {
    "id": 1,
    "nombre": "Santiago Yacelga",
    "rolPlataforma": "super_admin"
  }
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `accessToken` | string | JWT firmado (HS256). Válido por `expiresIn` segundos |
| `refreshToken` | string | Token de renovación. Válido 30 días, un solo uso |
| `expiresIn` | number | Segundos hasta la expiración del `accessToken` (8 horas = 28800) |
| `usuario.id` | number | ID del operador |
| `usuario.nombre` | string | Nombre completo |
| `usuario.rolPlataforma` | string | `super_admin` \| `soporte` \| `viewer` |

### Errores

| Código | Cuándo |
|---|---|
| 401 | Credenciales incorrectas (mensaje genérico — no se revela si el correo existe) |
| 403 | Usuario inactivo |
| 429 | Rate limit superado (demasiados intentos fallidos) |

---

## POST /auth/login

Login de staff del gym. Genera un JWT de tipo `staff` con los permisos resueltos del rol.

### Request

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "correo": "juan@gym.com",
  "password": "MiClave123",
  "idCompania": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `correo` | string | Sí | Email corporativo del empleado |
| `password` | string | Sí | Contraseña en texto plano |
| `idCompania` | number | Sí | ID de la compañía en la que trabaja |

### Response 200 — Login exitoso

```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "dGhpcyBp...",
  "expiresIn": 28800,
  "requiereCambioPwd": false,
  "usuario": {
    "id": 42,
    "nombre": "Juan Pérez",
    "correo": "juan@gym.com",
    "idRol": 3,
    "nombreRol": "Recepción"
  }
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `accessToken` | string | JWT con `permisos[]` embebidos |
| `refreshToken` | string | Token de renovación, un solo uso |
| `expiresIn` | number | Segundos hasta la expiración (8 horas) |
| `requiereCambioPwd` | boolean | `true` si el usuario debe cambiar la contraseña antes de operar |
| `usuario.id` | number | ID del usuario staff |
| `usuario.nombre` | string | Nombre completo (desde `identidad.personas`) |
| `usuario.correo` | string | Email corporativo |
| `usuario.idRol` | number | ID del rol asignado |
| `usuario.nombreRol` | string | Nombre del rol |

### Errores

| Código | Cuándo |
|---|---|
| 401 | Credenciales incorrectas o compañía incorrecta (mensaje genérico) |
| 403 | Usuario inactivo |
| 429 | Rate limit superado |

---

## POST /auth/app/login

Login de cliente en la app móvil. Genera un JWT de tipo `cliente`.

### Request

```http
POST /api/v1/auth/app/login
Content-Type: application/json
```

```json
{
  "login": "maria@gmail.com",
  "password": "MiClave456",
  "idCompania": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `login` | string | Sí | Username de la cuenta app (generalmente el email) |
| `password` | string | Sí | Contraseña en texto plano |
| `idCompania` | number | Sí | ID del gym en el que está registrado |

### Response 200 — Login exitoso

```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "dGhpcyBp...",
  "expiresIn": 604800,
  "persona": {
    "id": 10,
    "nombre": "María López",
    "fotoUrl": "https://res.cloudinary.com/...",
    "sexo": "F"
  },
  "compania": {
    "id": 1,
    "nombre": "Gym Elite",
    "logoUrl": "https://res.cloudinary.com/..."
  }
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `accessToken` | string | JWT de tipo `cliente`. Válido 7 días |
| `expiresIn` | number | 604800 (7 días en segundos) |
| `persona` | object | Datos de la persona vinculada |
| `persona.fotoUrl` | string \| null | URL de foto en Cloudinary |
| `compania` | object | Datos básicos del gym |
| `compania.logoUrl` | string \| null | URL del logo en Cloudinary |

### Errores

| Código | Cuándo |
|---|---|
| 401 | Credenciales incorrectas o gym incorrecto (mensaje genérico) |
| 403 | Cuenta desactivada |
| 429 | Rate limit superado |

---

## POST /auth/app/oauth/google

Verifica un `id_token` de Google. Si el email ya está registrado como cliente en esa compañía, hace login. Si no existe, devuelve `status = "registro_pendiente"` para que el frontend muestre la pantalla de completar registro (CI + teléfono + nombre editable) y luego llame a `POST /auth/app/oauth/completar-registro`.

**Nunca crea persona ni cuenta automáticamente en este endpoint** — el alta requiere que el usuario complete CI y confirme datos.

### Request

```http
POST /api/v1/auth/app/oauth/google
Content-Type: application/json
```

```json
{
  "id_token": "eyJhbGc...",
  "id_compania": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `id_token` | string | Sí | ID Token emitido por Google Sign-In |
| `id_compania` | number | Sí | ID del gym |

### Response 200 — Login exitoso (usuario ya registrado)

```json
{
  "status": "logged_in",
  "access_token": "eyJhbGci...",
  "refresh_token": "dGhpcyBp...",
  "expires_in": 604800,
  "persona": {
    "id": 10,
    "nombre": "María López",
    "foto_url": "https://res.cloudinary.com/...",
    "sexo": "F"
  },
  "compania": {
    "id": 1,
    "nombre": "Gym Elite",
    "logo_url": "https://res.cloudinary.com/..."
  },
  "email": null,
  "nombre": null
}
```

### Response 200 — Registro pendiente (usuario no registrado)

```json
{
  "status": "registro_pendiente",
  "access_token": null,
  "refresh_token": null,
  "expires_in": null,
  "persona": null,
  "compania": null,
  "email": "maria@gmail.com",
  "nombre": "María López"
}
```

El frontend debe usar `email` y `nombre` para pre-llenar el formulario de completar registro. El `nombre` puede llegar `null` si el proveedor no lo expone; en ese caso el usuario deberá escribirlo.

### Errores

| Código | Cuándo |
|---|---|
| 401 | Token de Google inválido o expirado |
| 403 | Cuenta existe pero está desactivada en este gym |
| 429 | Rate limit superado |

---

## POST /auth/app/oauth/facebook

Verifica un `access_token` de Facebook. Comportamiento idéntico a `POST /auth/app/oauth/google`: devuelve `status = "logged_in"` o `status = "registro_pendiente"`.

### Request

```http
POST /api/v1/auth/app/oauth/facebook
Content-Type: application/json
```

```json
{
  "access_token": "EAAxxxxx...",
  "id_compania": 1
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `access_token` | string | Sí | Access Token emitido por Facebook Login (scope `email`) |
| `id_compania` | number | Sí | ID del gym |

### Response 200

Misma estructura que `POST /auth/app/oauth/google` (mismos dos casos: `logged_in` o `registro_pendiente`).

### Errores

| Código | Cuándo |
|---|---|
| 401 | Token de Facebook inválido, expirado o sin permiso `email` |
| 403 | Cuenta existe pero está desactivada en este gym |
| 429 | Rate limit superado |

---

## POST /auth/app/oauth/completar-registro

Completa el alta de un cliente que se autenticó con Google/Facebook y no tenía cuenta en la compañía. Re-verifica el token OAuth para evitar suplantación, crea (o reusa) la `Persona` con la CI real que provee el usuario, crea la `UsuarioApp` y devuelve tokens de sesión.

El `password_hash` de la `UsuarioApp` se genera con un valor aleatorio criptográficamente imposible de adivinar — el cliente OAuth-only **nunca** podrá loguear con `POST /auth/app/login` (contraseña), solo con el mismo proveedor OAuth.

### Request

```http
POST /api/v1/auth/app/oauth/completar-registro
Content-Type: application/json
```

```json
{
  "provider": "google",
  "token": "eyJhbGc...",
  "id_compania": 1,
  "ci": "1720123456",
  "nombre": "María López",
  "telefono": "0991234567"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `provider` | string | Sí | `"google"` o `"facebook"` |
| `token` | string | Sí | `id_token` de Google o `access_token` de Facebook (el mismo que se envió al endpoint OAuth) |
| `id_compania` | number | Sí | ID del gym (entero positivo) |
| `ci` | string | Sí | Cédula (10 dígitos) o RUC (13 dígitos) — validado por regex |
| `nombre` | string | Sí | Nombre completo (3–200 caracteres). Puede diferir del que trae el proveedor |
| `telefono` | string | No | Teléfono de contacto |

### Response 201 — Registro exitoso

Misma estructura que `POST /auth/app/login`:

```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "dGhpcyBp...",
  "expires_in": 604800,
  "persona": {
    "id": 10,
    "nombre": "María López",
    "foto_url": null,
    "sexo": null
  },
  "compania": {
    "id": 1,
    "nombre": "Gym Elite",
    "logo_url": "https://res.cloudinary.com/..."
  }
}
```

### Errores

| Código | Cuándo |
|---|---|
| 400 | Validación falló (CI mal formada, provider distinto de `google`/`facebook`, campo faltante) |
| 401 | Token OAuth inválido o expirado |
| 409 | Ya existe una cuenta con ese correo en el gimnasio, o la persona (por CI/correo) ya tiene cuenta en este gimnasio |
| 429 | Rate limit superado |

---

## POST /auth/app/registro

Registra un nuevo cliente en un gym. Crea la `Persona` y la cuenta app en un solo paso.

### Request

```http
POST /api/v1/auth/app/registro
Content-Type: application/json
```

```json
{
  "nombre": "María López",
  "correo": "maria@gmail.com",
  "password": "MiPassword123",
  "idCompania": 1,
  "telefono": "0991234567"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | Sí | Nombre completo (mín. 2 caracteres) |
| `correo` | string | Sí | Email válido — se usa como `login` y como `correo` de la Persona |
| `password` | string | Sí | Contraseña (mín. 8 caracteres) |
| `idCompania` | number | Sí | ID del gym en el que se registra |
| `telefono` | string | No | Teléfono de contacto |

### Response 201 — Registro exitoso

Misma estructura que `POST /auth/app/login`. El cliente queda autenticado inmediatamente.

### Errores

| Código | Cuándo |
|---|---|
| 409 | El correo ya está registrado como login en este gym |

---

## POST /auth/refresh

Renueva el `accessToken` usando un `refreshToken` vigente. El refresh token anterior queda invalidado (single-use).

### Request

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "dGhpcyBp..."
}
```

| Campo | Tipo | Requerido |
|---|---|---|
| `refreshToken` | string | Sí |

### Response 200

```json
{
  "accessToken": "eyJhbGci...",
  "expiresIn": 28800
}
```

### Errores

| Código | Cuándo |
|---|---|
| 401 | Token inválido, expirado o ya consumido |

---

## POST /auth/logout

Invalida el refresh token del usuario autenticado en la BD.

### Request

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
```

Sin body.

### Response 204

Sin cuerpo.

---

## POST /auth/password/reset-request

Solicita el restablecimiento de contraseña. Genera un token de un solo uso (válido 1 hora) y envía un email con el link de reset.

Siempre devuelve 200 aunque el correo no exista (para evitar enumeración de emails).

### Request

```http
POST /api/v1/auth/password/reset-request
Content-Type: application/json
```

```json
{
  "correo": "juan@gym.com",
  "idCompania": 1,
  "tipo": "staff"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `correo` | string | Sí | Email del usuario |
| `idCompania` | number | Sí | ID del gym |
| `tipo` | string | Sí | `"staff"` o `"cliente"` — indica la tabla donde buscar |

### Response 200

```json
{
  "mensaje": "Si el correo existe, recibirás las instrucciones en tu email"
}
```

---

## POST /auth/password/reset

Aplica el nuevo password usando el token recibido por email. El token queda invalidado tras su uso.

### Request

```http
POST /api/v1/auth/password/reset
Content-Type: application/json
```

```json
{
  "token": "abc123xyz...",
  "nuevaPassword": "NuevaClave789"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `token` | string | Sí | Token de reset recibido por email |
| `nuevaPassword` | string | Sí | Nueva contraseña (mín. 8 caracteres) |

### Response 200

```json
{
  "mensaje": "Contraseña actualizada correctamente"
}
```

### Errores

| Código | Cuándo |
|---|---|
| 400 | Token inválido, expirado o ya consumido |

---

## GET /auth/companias-por-correo

Devuelve la lista de gyms donde un correo está registrado (como staff o como cliente). Útil para mostrar el selector de gym en la pantalla de login.

### Request

```http
GET /api/v1/auth/companias-por-correo?correo=usuario@gmail.com
```

| Param | Tipo | Requerido | Descripción |
|---|---|---|---|
| `correo` | string (query) | Sí | Email a buscar |

### Response 200

```json
[
  { "id": 1, "nombre": "Gym Elite" },
  { "id": 2, "nombre": "PowerFit Centro" }
]
```

Array vacío (`[]`) si el correo no está registrado en ningún gym.

---

## GET /auth/gimnasio/by-qr/{qrToken}

Resuelve un token QR (generado por el módulo de sucursales) a los datos públicos del gym. Usado por la app móvil al escanear el QR de acceso.

### Request

```http
GET /api/v1/auth/gimnasio/by-qr/TOKEN_QR_AQUI
```

| Param | Tipo | Descripción |
|---|---|---|
| `qrToken` | string (path) | Token QR embebido en el código QR físico del gym |

### Response 200

```json
{
  "idCompania": 1,
  "idSucursal": 3,
  "nombreCompania": "Gym Elite",
  "nombreSucursal": "Quito Norte",
  "logoUrl": "https://res.cloudinary.com/..."
}
```

### Errores

| Código | Cuándo |
|---|---|
| 404 | QR inválido o expirado |
