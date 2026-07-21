# PERSONAS API

> **ESTADO:** ✅ Refleja el código actual (verificado 2026-07-08 contra `ApiRouter.java`; comportamiento ci_validada recalc-on-update verificado 2026-07-21 contra `PersonaMapper.java`). Ver [../../STATUS.md](../../STATUS.md).

Base URL: `/api/v1/personas`

Los campos de request y response usan **camelCase**.

La entidad `Persona` es el registro de identidad centralizado. Todos los tipos de usuario (staff, clientes, operadores de plataforma) referencian a una `Persona` mediante `idPersona`. El `nombre` y `fotoUrl` siempre se obtienen desde aquí y nunca se duplican en las tablas de usuario.

---

## Índice

- [GET /personas](#get-personas)
- [GET /personas/{id}](#get-personasid)
- [GET /personas/ci/{ci}](#get-personascici)
- [GET /personas/correo/{correo}](#get-personascorreocorreo)
- [POST /personas](#post-personas)
- [PUT /personas/{id}](#put-personasid)
- [POST /personas/{id}/foto](#post-personasidfoto)
- [PATCH /personas/{id}/consentimiento-wa](#patch-personasidconsentimiento-wa)
- [GET /personas/{idPersona}/usuarios-staff](#get-personasidpersonausuarios-staff)
- [GET /personas/{idPersona}/usuarios-app](#get-personasidpersonausuarios-app)
- [GET /personas/{idPersona}/usuarios-plataforma](#get-personasidpersonausuarios-plataforma)

---

## GET /personas

Lista todas las personas con paginación y filtros opcionales.

**Seguridad:** requiere token `tipo=plataforma`.

### Request

```http
GET /api/v1/personas?nombre=María&page=0&size=20
Authorization: Bearer {accessToken}
```

| Param (query) | Tipo | Requerido | Descripción |
|---|---|---|---|
| `nombre` | string | No | Búsqueda parcial, sin distinción de mayúsculas |
| `ci` | string | No | Búsqueda parcial |
| `correo` | string | No | Búsqueda parcial, sin distinción de mayúsculas |
| `sexo` | string | No | Valor exacto: `M`, `F` u `O` |
| `page` | number | No | Página base-0 (default: `0`) |
| `size` | number | No | Registros por página (default: `20`) |

### Response 200

```json
{
  "content": [
    {
      "id": 1,
      "ci": "1234567890",
      "nombre": "María López",
      "telefono": "0991234567",
      "correo": "maria@gmail.com",
      "fotoUrl": "https://res.cloudinary.com/...",
      "sexo": "F",
      "fechaNacimiento": "1990-05-15",
      "ciValidada": true,
      "createdAt": "2026-05-18T10:00:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

---

## GET /personas/{id}

Obtiene una persona por su ID interno.

**Seguridad:** requiere token autenticado (cualquier tipo).

### Request

```http
GET /api/v1/personas/10
Authorization: Bearer {accessToken}
```

### Response 200

```json
{
  "id": 10,
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "fotoUrl": null,
  "sexo": "F",
  "fechaNacimiento": "1990-05-15",
  "ciValidada": true,
  "createdAt": "2026-05-18T10:00:00Z"
}
```

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con ese ID |

---

## GET /personas/ci/{ci}

Busca una persona por su CI (cédula de identidad / pasaporte). Búsqueda exacta.

**Seguridad:** público (sin token). Se usa en el flujo de registro de staff y clientes.

### Request

```http
GET /api/v1/personas/ci/1001234567
```

### Response 200

Misma estructura que `GET /personas/{id}`.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con esa CI |

---

## GET /personas/correo/{correo}

Busca una persona por su correo personal (el de `identidad.personas`, no el corporativo).

**Seguridad:** requiere token autenticado (cualquier tipo).

### Request

```http
GET /api/v1/personas/correo/maria@gmail.com
Authorization: Bearer {accessToken}
```

### Response 200

Misma estructura que `GET /personas/{id}`.

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con ese correo |

---

## POST /personas

Crea un nuevo registro de persona. Al crear, el servidor calcula automáticamente el flag `ciValidada` aplicando el algoritmo de dígito verificador ecuatoriano (módulo 10).

**Seguridad:** público. Se usa en el flujo de onboarding de nuevos clientes o empleados.

### Request

```http
POST /api/v1/personas
Content-Type: application/json
```

```json
{
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "sexo": "F",
  "fotoUrl": null,
  "fechaNacimiento": "1990-05-15"
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `ci` | string | Sí | Cédula de identidad, pasaporte o documento numérico. Único y global. Inmutable tras creación |
| `nombre` | string | Sí | Nombre completo |
| `telefono` | string | No | Teléfono de contacto |
| `correo` | string | No | Correo personal (puede diferir del correo corporativo de las cuentas de usuario) |
| `sexo` | string | No | `M`, `F` u `O` |
| `fotoUrl` | string | No | URL de foto (se actualiza luego via `POST /{id}/foto`) |
| `fechaNacimiento` | string (date) | No | Formato `YYYY-MM-DD` |

### Response 201

```json
{
  "id": 10,
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "fotoUrl": null,
  "sexo": "F",
  "fechaNacimiento": "1990-05-15",
  "ciValidada": true,
  "createdAt": "2026-06-19T10:00:00Z"
}
```

| Campo | Descripción |
|---|---|
| `ciValidada` | `true` si el `ci` es una cédula ecuatoriana válida (10 dígitos, provincia 01–24 o 30, tercer dígito < 6, dígito verificador correcto). `false` para pasaportes, RUCs, documentos extranjeros o cédulas inválidas. El servidor **nunca rechaza** el registro por este campo — es informativo |

### Errores

| Código | Cuándo |
|---|---|
| 409 | Ya existe una persona con esa CI |
| 400 | `ci` o `nombre` vacíos |

---

## PUT /personas/{id}

Actualiza los datos de una persona. Todos los campos son opcionales; solo se actualizan los que se envían.

La `ci` puede enviarse — si es diferente a la actual, se valida unicidad. El servidor recalcula automáticamente `ciValidada` aplicando el algoritmo de módulo 10 sobre la `ci` actual, de la misma manera que en la creación.

**Seguridad:** público (o token autenticado, según configuración de SecurityConfig).

### Request

```http
PUT /api/v1/personas/10
Content-Type: application/json
```

```json
{
  "nombre": "María López García",
  "telefono": "0999999999",
  "correo": "nuevocorreo@gmail.com",
  "sexo": "F",
  "fechaNacimiento": "1990-05-15"
}
```

Todos los campos son opcionales. No enviar un campo equivale a no actualizarlo.

### Response 200

Objeto `PersonaResponse` con los datos actualizados. El campo `ciValidada` refleja el recálculo desde la `ci` actual (si se corrigió la cédula a una válida, `ciValidada` pasa a `true`; si se edita a un documento inválido, pasa a `false`).

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con ese ID |
| 409 | La CI enviada ya pertenece a otra persona |
| 400 | Intento de cambiar la CI (si la regla de inmutabilidad está activa) |

---

## POST /personas/{id}/foto

Sube o reemplaza la foto de perfil de una persona. La imagen se almacena en Cloudinary y el campo `fotoUrl` de la persona se actualiza.

**Seguridad:** requiere token autenticado.

### Request

```http
POST /api/v1/personas/10/foto
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| Campo (form) | Tipo | Descripción |
|---|---|---|
| `file` | binary | Imagen en cualquier formato soportado por Cloudinary (JPEG, PNG, WebP, etc.) |

### Response 200

Objeto `PersonaResponse` con `fotoUrl` actualizado a la nueva URL de Cloudinary.

---

## PATCH /personas/{id}/consentimiento-wa

Actualiza el opt-in de consentimiento para recibir mensajes por WhatsApp. Sella o limpia la fecha de consentimiento según el valor de `acepta`.

**Seguridad:** requiere token autenticado (cualquier tipo: staff, cliente, plataforma).

### Request

```http
PATCH /api/v1/personas/10/consentimiento-wa
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "acepta": true
}
```

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `acepta` | boolean | Sí | `true` para opt-in, `false` para opt-out |

### Response 200

```json
{
  "idPersona": 10,
  "aceptaWhatsapp": true,
  "fechaConsentimientoWa": "2026-07-16T14:30:00Z"
}
```

| Campo | Descripción |
|---|---|
| `idPersona` | ID de la persona |
| `aceptaWhatsapp` | `true` si ha dado consentimiento; `false` si lo revocó |
| `fechaConsentimientoWa` | Timestamp del momento en que aceptó/revocó. `null` si `aceptaWhatsapp=false` |

### Errores

| Código | Cuándo |
|---|---|
| 404 | No existe una persona con ese ID |
| 400 | `acepta` inválido o ausente |

---

## GET /personas/{idPersona}/usuarios-staff

Lista todas las cuentas de staff asociadas a una persona (puede ser en múltiples gyms).

**Seguridad:** requiere token `tipo=plataforma`.

### Request

```http
GET /api/v1/personas/10/usuarios-staff
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 42,
    "idPersona": 10,
    "nombre": "María López",
    "correo": "maria@gym1.com",
    "fotoUrl": "https://res.cloudinary.com/...",
    "idRol": 2,
    "nombreRol": "Recepción",
    "activo": true,
    "ultimoAcceso": "2026-05-18T14:30:00Z"
  },
  {
    "id": 87,
    "idPersona": 10,
    "nombre": "María López",
    "correo": "maria@gym2.com",
    "fotoUrl": "https://res.cloudinary.com/...",
    "idRol": 5,
    "nombreRol": "Dueño",
    "activo": true,
    "ultimoAcceso": "2026-06-01T09:00:00Z"
  }
]
```

Array vacío si la persona no tiene cuentas de staff.

---

## GET /personas/{idPersona}/usuarios-app

Lista todas las cuentas app asociadas a una persona (puede ser en múltiples gyms).

**Seguridad:** requiere token `tipo=plataforma`.

### Request

```http
GET /api/v1/personas/10/usuarios-app
Authorization: Bearer {accessToken}
```

### Response 200

```json
[
  {
    "id": 15,
    "login": "maria@gmail.com",
    "activo": true,
    "ultimoAcceso": "2026-06-10T10:00:00Z"
  }
]
```

Array vacío si la persona no tiene cuentas app.

---

## GET /personas/{idPersona}/usuarios-plataforma

Lista las cuentas de operador de plataforma asociadas a una persona (máximo una, por la restricción UNIQUE).

**Seguridad:** requiere token `tipo=plataforma`.

### Request

```http
GET /api/v1/personas/1/usuarios-plataforma
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
    "ultimoAcceso": "2026-06-19T07:00:00Z",
    "fotoUrl": null
  }
]
```

---

## Códigos de error comunes

| Código | Significado |
|---|---|
| 400 | Datos de request inválidos (campo requerido vacío, etc.) |
| 401 | Token ausente o inválido |
| 403 | El tipo de token no tiene acceso a esta operación |
| 404 | Persona no encontrada |
| 409 | CI duplicada |
