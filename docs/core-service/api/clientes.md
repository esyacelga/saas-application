# CLIENTES API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado 2026-07-08 contra `ClienteController`). Ver [../../STATUS.md](../../STATUS.md).

Todos los endpoints requieren `Authorization: Bearer {token}`.  
Las respuestas usan **snake_case** (Jackson `SNAKE_CASE` global configurado en `application.yml`).

---

## Endpoints existentes (sin cambios)

| Método | Ruta                    | Descripción                                      |
|--------|-------------------------|--------------------------------------------------|
| GET    | `/api/v1/clientes`      | Lista clientes paginada (filtros: estado, buscar, sin_membresia) |
| POST   | `/api/v1/clientes`      | Registra un nuevo cliente (crea persona si no existe) |
| GET    | `/api/v1/clientes/{id}` | Detalle de cliente con membresía activa          |
| PUT    | `/api/v1/clientes/{id}` | Actualiza peso, altura, objetivos, lesiones      |

### POST /api/v1/clientes — opt-in de WhatsApp del socio (2026-07-21)

El body acepta `acepta_whatsapp` (booleano, **opcional** — ausente ⇒ `false`). Es el punto de captura de **recepción**, uno de los tres que declara el DDL de `identidad.personas` (registro público, recepción, perfil PWA); antes solo existía el del perfil PWA, así que todo socio dado de alta presencialmente quedaba en `false` y **nunca recibía el aviso de vencimiento de su membresía** — falla silenciosa, porque el job simplemente no lo incluye.

Comportamiento según si la persona ya existe:

| Caso | Efecto |
|---|---|
| Persona nueva | El `INSERT` graba `acepta_whatsapp` y sella `fecha_consentimiento_wa = now()` **solo** si es `true` |
| Persona existente (se afilia a otro gym) y `true` | `UPDATE ... WHERE acepta_whatsapp = false` — si ya había consentido se **conserva su fecha original** (es la prueba del opt-in ante Meta) |
| Persona existente y `false` | **No se toca nada.** Recepción nunca revoca un consentimiento que el socio dio en otro gym |

El opt-out es exclusivo del socio desde su perfil PWA (`PATCH /api/v1/personas/{id}/consentimiento-wa` en auth-service).

`POST /api/v1/clientes/plataforma` **no** expone el campo: va fijo en `false` porque el operador de plataforma no puede consentir por el socio.

En el panel, la casilla nace **desmarcada** y se deshabilita si no hay teléfono (un consentimiento sin número al que enviar no significa nada). Meta exige opt-in afirmativo: una casilla pre-marcada no tiene valor probatorio y arriesga el bloqueo del número compartido de la plataforma.

### GET /api/v1/clientes — campos de foto y sexo (2026-07-16)

Cada item del listado incluye ahora `foto_url` y `sexo` (JOIN con `identidad.personas` para la foto; `sexo` viene de `core.clientes`):

```json
{
  "id": 10,
  "nombre": "Juan Pérez",
  "ci": "1712345678",
  "telefono": "0991234567",
  "estado": "activo",
  "foto_url": "https://res.cloudinary.com/.../foto.jpg",
  "sexo": "M",
  "membresia_activa": { "...": "..." }
}
```

`foto_url` puede ser `null` — el frontend cae al avatar genérico por sexo (`VITE_AVATAR_HOMBRE_URL` / `VITE_AVATAR_MUJER_URL`).

### GET /api/v1/clientes/{id} — campos añadidos (2026-07-16)

El detalle expone ahora `id_persona` (raíz) y, dentro de `persona`: `foto_url` (antes se serializaba `null` fijo) y `fecha_nacimiento`:

```json
{
  "id": 10,
  "id_persona": 5,
  "persona": {
    "ci": "1712345678",
    "nombre": "Juan Pérez",
    "telefono": "0991234567",
    "correo": "juan@mail.com",
    "foto_url": "https://res.cloudinary.com/.../foto.jpg",
    "fecha_nacimiento": "1990-05-20"
  },
  "sexo": "M",
  "...": "..."
}
```

`id_persona` permite al frontend editar los datos de identidad y subir la foto vía auth-service (`PUT /api/v1/personas/{id_persona}` y `POST /api/v1/personas/{id_persona}/foto`).

> **Nota sobre `sexo`:** existe en dos tablas con constraints distintos — `core.clientes.sexo` acepta `M/F/O`, pero `identidad.personas.sexo` solo `M/F` (CHECK). El PUT de personas en auth-service fallaría con `O`; por eso el modal de edición del panel admin solo ofrece M/F.
| GET    | `/api/v1/clientes/mi-perfil` | Perfil del cliente autenticado             |
| GET    | `/api/v1/clientes/my-id`    | ID del cliente autenticado                  |
| POST   | `/api/v1/clientes/app`      | Registro desde app móvil                    |
| GET    | `/api/v1/clientes/ci/{ci}`  | Busca por CI (verifica si ya es cliente)    |
| POST   | `/api/v1/clientes/plataforma` | Registro de cliente desde el panel de plataforma |

---

## Endpoints nuevos (módulo Administración de Personas)

### GET /api/v1/clientes/por-persona/{idPersona}
Lista todos los registros de cliente de una persona en todos los gimnasios. Requiere token autenticado.

**Path param:** `idPersona` — ID de la persona en `identidad.personas`

**Response 200:**
```json
[
  {
    "id": 10,
    "id_persona": 5,
    "peso_kg": 75.5,
    "altura_cm": 175.0,
    "objetivos": "Perder peso",
    "lesiones": null,
    "estado": "activo",
    "fecha_ingreso": "2025-01-15",
    "codigo_carnet": "GYM1-00010",
    "sexo": "M"
  }
]
```

> **Nota:** Este endpoint no filtra por compañía — devuelve los registros de todos los gimnasios. Usado exclusivamente desde el panel plataforma.

---

### PUT /api/v1/clientes/plataforma/{id}
Actualiza compañía y/o estado de un registro de cliente. Solo para uso del panel plataforma. Requiere token autenticado.

**Path param:** `id` — ID del registro en `core.clientes`

**Request body:**
```json
{
  "idCompania": 3,
  "estado": "activo"
}
```

Valores válidos de `estado`: `activo`, `proximo_vencer`, `vencido`, `congelado`, `riesgo_abandono`

**Response 200:**
```json
{
  "id": 10,
  "id_persona": 5,
  "peso_kg": 75.5,
  "altura_cm": 175.0,
  "objetivos": "Perder peso",
  "lesiones": null,
  "estado": "activo",
  "fecha_ingreso": "2025-01-15",
  "codigo_carnet": "GYM1-00010",
  "sexo": "M"
}
```

**Response 404:** `{ "message": "Cliente not found: {id}" }`

---

### DELETE /api/v1/clientes/{id}
Eliminación lógica de un registro de cliente (`eliminado = true`). Requiere token autenticado.

**Path param:** `id` — ID del registro en `core.clientes`

**Response 204:** sin body  
**Response 404:** `{ "message": "Cliente not found: {id}" }`

> **Importante:** La eliminación es lógica — el registro permanece en la base de datos con `eliminado = true`. No se eliminan las membresías asociadas.

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/clientes` | GET | `requireGymStaff()` |
| `/clientes` | POST | `requireRecepcionOrAbove()` |
| `/clientes/{id}` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/clientes/{id}` | PUT | `requireRecepcionOrAbove()` |
| `/clientes/{id}` | DELETE | any JWT (no role check) |
| `/clientes/mi-perfil` | GET | `requireCliente()` |
| `/clientes/my-id` | GET | `requireCliente()` |
| `/clientes/app` | POST | `requireCliente()` |
| `/clientes/ci/{ci}` | GET | `requireGymStaff()` |
| `/clientes/plataforma` | POST | any auth (platform service) |
| `/clientes/por-persona/{idPersona}` | GET | `requireGymStaff()` |
| `/clientes/plataforma/{id}` | PUT | any auth (platform service) |

---

## Códigos de error comunes

| Código | Significado                       |
|--------|-----------------------------------|
| 400    | Datos de request inválidos        |
| 401    | Token ausente o inválido          |
| 403    | Sin permisos para esta operación  |
| 404    | Recurso no encontrado             |
| 409    | Conflicto (cliente ya existe)     |
