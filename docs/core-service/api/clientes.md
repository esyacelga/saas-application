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
