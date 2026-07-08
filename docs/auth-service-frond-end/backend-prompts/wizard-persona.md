# Backend Spec — Wizard: búsqueda y creación de persona

## Contexto

El endpoint `POST /api/v1/companias/wizard` crea una compañía completa
(empresa + sede + suscripción + usuario administrador) en una sola transacción.

El frontend ahora aplica un flujo **"buscar o crear"** para el administrador:

1. El operador escribe la cédula/CI del administrador.
2. El frontend consulta `GET /api/v1/personas/ci/{ci}` (auth-service).
   - **200** → persona existe → se usa su `id`.
   - **404** → persona no existe → el operador ingresa nombre, correo y teléfono.
3. El wizard envía el payload con los datos resultantes al endpoint.

El backend debe resolver ambos casos **dentro de la misma transacción**.

---

## Cambio en el DTO de entrada

### Antes

```json
{
  "usuarioPrincipal": {
    "nombre":   "Carlos Mendoza",
    "correo":   "carlos@gym.com",
    "password": "Abc12345"
  }
}
```

### Ahora

```json
{
  "usuarioPrincipal": {
    "id_persona": 42,          // opcional — presente si ya existía
    "ci":         "1234567890", // requerido siempre
    "nombre":     "Carlos Mendoza",
    "correo":     "carlos@gym.com",
    "telefono":   "0991234567", // opcional
    "password":   "Abc12345"
  }
}
```

| Campo         | Tipo    | Requerido | Descripción                                      |
|---------------|---------|-----------|--------------------------------------------------|
| `id_persona`  | Long    | No        | ID de persona existente. Si viene, se usa directo |
| `ci`          | String  | Sí        | Cédula/CI. Requerida siempre para auditoría      |
| `nombre`      | String  | Sí        | Nombre completo                                  |
| `correo`      | String  | Sí        | Correo de acceso al panel                        |
| `telefono`    | String  | No        | Teléfono (solo se usa si se crea persona nueva)  |
| `password`    | String  | Sí        | Contraseña inicial (mínimo 8 caracteres)         |

---

## Lógica que debe implementar el backend

```
INICIO TRANSACCIÓN

1. Resolver persona:
   SI id_persona != null:
     buscar persona por id_persona
     si no existe → lanzar 422 con mensaje "persona_no_encontrada"
   SINO:
     buscar persona por ci
     SI existe → usar esa persona (id obtenido)
     SI no existe → crear persona con { ci, nombre, correo, telefono }
                    → obtener id de la persona creada

2. Crear compañía (nombre, ruc, correo, telefono, whatsapp)

3. Crear sede principal (nombreSucursal, direccionSucursal, id_compania)

4. Crear suscripción (id_compania, id_plan)
   → generar qr_token

5. Buscar rol SUPER_ADMIN de la compañía (o crearlo si no existe)

6. Crear usuario staff:
   { id_persona, correo, id_rol=SUPER_ADMIN, id_compania, id_sucursal, password }

7. (Opcional) Crear usuarios adicionales de la misma forma

COMMIT
```

---

## Validaciones requeridas

| Caso                                  | HTTP | Código de error              |
|---------------------------------------|------|------------------------------|
| `ci` vacío o nulo                     | 400  | `ci_requerido`               |
| `nombre` vacío o nulo                 | 400  | `nombre_requerido`           |
| `correo` inválido                     | 400  | `correo_invalido`            |
| `password` menor a 8 caracteres       | 400  | `password_muy_corto`         |
| `id_persona` proporcionado no existe  | 422  | `persona_no_encontrada`      |
| `ci` ya tiene un usuario en esa compañía | 409 | `usuario_duplicado`       |
| `correo` ya está registrado como usuario | 409 | `correo_duplicado`        |
| `ruc` de compañía ya existe           | 409  | `ruc_duplicado`              |

---

## Endpoint de búsqueda de persona (ya existe)

El frontend usa este endpoint directamente antes de llamar al wizard.
Solo se documenta aquí para referencia de contrato:

```
GET /api/v1/personas/ci/{ci}
Authorization: Bearer <token_plataforma>

200 OK:
{
  "id": 42,
  "ci": "1234567890",
  "nombre": "Carlos Mendoza",
  "correo": "carlos@gym.com",
  "telefono": "0991234567",
  "foto_url": null,
  "fecha_nacimiento": null
}

404 Not Found:
{
  "status": 404,
  "mensaje": "Persona no encontrada"
}
```

---

## Payload completo del wizard (nuevo contrato)

```json
POST /api/v1/companias/wizard
Authorization: Bearer <token super_admin plataforma>
Content-Type: application/json

{
  "nombre":             "Gym Centro Norte",
  "ruc":                "1234567890001",
  "correo":             "info@gymcentro.com",
  "telefono":           "022345678",
  "whatsapp":           "0991234567",
  "idPlan":             3,
  "nombreSucursal":     "Sede Principal",
  "direccionSucursal":  "Av. Amazonas 123",
  "usuarioPrincipal": {
    "id_persona": 42,
    "ci":         "1234567890",
    "nombre":     "Carlos Mendoza",
    "correo":     "carlos@gymcentro.com",
    "telefono":   "0991234567",
    "password":   "Admin2025!"
  },
  "usuariosAdicionales": [
    {
      "nombre":   "Ana Torres",
      "correo":   "ana@gymcentro.com",
      "password": "Staff2025!"
    }
  ]
}
```

### Respuesta esperada (sin cambios)

```json
201 Created
{
  "id_compania":      15,
  "id_compania_plan": 22,
  "id_sucursal":      8,
  "qr_token":         "eyJhbGci...",
  "usuario_principal": {
    "id":     31,
    "nombre": "Carlos Mendoza",
    "correo": "carlos@gymcentro.com"
  },
  "usuarios_creados": 2
}
```

---

## Notas de migración

- El campo `id_persona` es **opcional** — el endpoint debe ser backwards-compatible:
  si el cliente no lo envía, el backend resuelve la persona por `ci`.
- `nombre` y `correo` siguen siendo obligatorios aunque `id_persona` esté presente,
  porque se usan para crear el usuario de acceso (no la persona).
- La persona creada por el wizard (caso 404) pertenece al esquema `identidad.personas`
  del auth-service, no al core-service.
