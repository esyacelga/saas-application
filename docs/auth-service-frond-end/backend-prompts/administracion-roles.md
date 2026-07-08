# Prompt — Implementar endpoints de Administración de Roles (Panel Plataforma)

## Contexto del proyecto

Estás trabajando en un backend Java (Spring Boot) de un sistema multi-tenant para gimnasios. El sistema tiene dos tipos de usuarios:

- **Staff**: empleados de un gimnasio específico, autenticados con JWT que contiene `id_compania` y `id_sucursal`.
- **Operadores de plataforma**: usuarios administrativos que supervisan todas las compañías, autenticados con JWT que contiene el campo `tipo = "plataforma"` y `rol_plataforma` (valores posibles: `super_admin`, `soporte`, `viewer`).

Ya existe un sistema de roles y permisos para los usuarios staff (`/roles`, `/permisos`). Lo que se pide a continuación son **endpoints nuevos exclusivos para operadores de plataforma** que permiten ver esos mismos roles desde una perspectiva global (todas las compañías).

---

## Lo que debes implementar

Implementa los siguientes tres endpoints REST bajo el prefijo `/platform`. Todos requieren que el JWT del request pertenezca a un operador de plataforma. Si el token es de staff, responde `403`.

---

### Endpoint 1 — Listar todos los roles del sistema

```
GET /platform/roles
```

**Seguridad:** Solo operadores de plataforma (`tipo = "plataforma"`). Cualquier rol de plataforma puede acceder (`super_admin`, `soporte`, `viewer`).

**Query param opcional:**

| Parámetro     | Tipo    | Descripción                                          |
|--------------|---------|------------------------------------------------------|
| `id_compania` | Integer | Si se envía, filtra los roles de esa compañía únicamente. Si se omite, devuelve roles de todas las compañías. |

**Respuesta `200 OK` — lista de objetos:**

```json
[
  {
    "id": 1,
    "nombre": "Administrador",
    "descripcion": "Acceso total al sistema del gimnasio",
    "id_compania": 10,
    "nombre_compania": "GymFit Ecuador",
    "total_usuarios": 3
  },
  {
    "id": 2,
    "nombre": "Recepcionista",
    "descripcion": null,
    "id_compania": 15,
    "nombre_compania": "Iron Gym Quito",
    "total_usuarios": 7
  }
]
```

**Descripción de cada campo:**

| Campo             | Origen                                                                 |
|------------------|------------------------------------------------------------------------|
| `id`             | PK de la tabla de roles                                                |
| `nombre`         | Nombre del rol                                                         |
| `descripcion`    | Descripción del rol, puede ser `null`                                  |
| `id_compania`    | FK de la compañía a la que pertenece el rol                            |
| `nombre_compania`| JOIN con la tabla de compañías para obtener el nombre legible          |
| `total_usuarios` | COUNT de usuarios staff activos (o todos, según decisión de negocio) con ese `id_rol` asignado |

**Lógica de negocio:**
- El conteo `total_usuarios` se obtiene con un `COUNT` sobre la tabla de usuarios staff agrupado por `id_rol`.
- Si se pasa `id_compania`, añade un `WHERE r.id_compania = :id_compania` a la query.
- Ordena los resultados por `nombre_compania ASC`, luego por `nombre ASC`.

**Errores:**

| Código | Cuándo                                                      |
|--------|-------------------------------------------------------------|
| `401`  | Token ausente, inválido o expirado                          |
| `403`  | El token pertenece a un usuario staff, no a un operador de plataforma |

---

### Endpoint 2 — Ver permisos de un rol (solo lectura)

```
GET /platform/roles/{id}/permisos
```

**Seguridad:** Solo operadores de plataforma. Todos los roles de plataforma pueden acceder.

**Path variable:**

| Variable | Tipo    | Descripción       |
|---------|---------|-------------------|
| `id`    | Integer | ID del rol a consultar |

**Respuesta `200 OK`:**

```json
{
  "rol": {
    "id": 1,
    "nombre": "Administrador",
    "descripcion": "Acceso total al sistema del gimnasio",
    "id_compania": 10,
    "nombre_compania": "GymFit Ecuador",
    "total_usuarios": 3
  },
  "permisos": [
    {
      "id": 1,
      "nombre": "usuarios:leer",
      "modulo": "Usuarios",
      "descripcion": "Ver listado de usuarios del sistema"
    },
    {
      "id": 2,
      "nombre": "usuarios:crear",
      "modulo": "Usuarios",
      "descripcion": "Crear nuevos usuarios"
    }
  ]
}
```

**Descripción de campos:**

| Campo                     | Descripción                                                   |
|--------------------------|---------------------------------------------------------------|
| `rol`                    | Mismo shape que un item del endpoint 1                        |
| `permisos`               | Lista de permisos asignados actualmente al rol                |
| `permisos[].id`          | PK del permiso                                                |
| `permisos[].nombre`      | Clave del permiso, ej. `usuarios:leer`                        |
| `permisos[].modulo`      | Módulo al que pertenece, usado para agrupar en la UI          |
| `permisos[].descripcion` | Texto descriptivo del permiso, puede ser `null`               |

**Lógica de negocio:**
- Busca el rol por `id`. Si no existe, responde `404`.
- Obtiene los permisos asignados al rol desde la tabla de relación `rol_permiso` (o como esté nombrada en tu esquema).
- El campo `total_usuarios` del objeto `rol` se calcula igual que en el endpoint 1.

**Errores:**

| Código | Cuándo                                              |
|--------|-----------------------------------------------------|
| `401`  | Token ausente, inválido o expirado                  |
| `403`  | Token no pertenece a operador de plataforma         |
| `404`  | No existe un rol con ese `id`                       |

---

### Endpoint 3 — Listar compañías (para filtro desplegable)

```
GET /platform/companias
```

**Seguridad:** Solo operadores de plataforma.

**Sin parámetros.**

**Respuesta `200 OK`:**

```json
[
  { "id": 10, "nombre": "GymFit Ecuador" },
  { "id": 15, "nombre": "Iron Gym Quito" },
  { "id": 22, "nombre": "PowerHouse Guayaquil" }
]
```

**Lógica de negocio:**
- Devuelve solo `id` y `nombre` de las compañías activas. No exponer datos sensibles (contratos, configuración interna, etc.).
- Ordena por `nombre ASC`.
- Si ya existe un endpoint o servicio de compañías, reutiliza la entidad/repositorio existente.

**Errores:**

| Código | Cuándo                                      |
|--------|---------------------------------------------|
| `401`  | Token ausente, inválido o expirado          |
| `403`  | Token no pertenece a operador de plataforma |

---

## DTOs Java esperados

Crea los siguientes DTOs de respuesta. Usa las anotaciones que ya uses en el proyecto (`@JsonProperty`, Lombok, Jackson, etc.).

```java
// GET /platform/roles  →  List<RolPlataformaResponse>
public class RolPlataformaResponse {
    private Integer id;
    private String nombre;
    private String descripcion;       // nullable
    private Integer idCompania;
    private String nombreCompania;
    private Long totalUsuarios;
}

// GET /platform/roles/{id}/permisos  →  RolConPermisosPlataformaResponse
public class RolConPermisosPlataformaResponse {
    private RolPlataformaResponse rol;
    private List<PermisoResponse> permisos;
}

public class PermisoResponse {
    private Integer id;
    private String nombre;
    private String modulo;
    private String descripcion;       // nullable
}

// GET /platform/companias  →  List<CompaniaBasicaResponse>
public class CompaniaBasicaResponse {
    private Integer id;
    private String nombre;
}
```

---

## Seguridad — cómo validar que es operador de plataforma

En el filtro de seguridad JWT ya existente, al decodificar el token verifica que el claim `tipo` sea igual a `"plataforma"`. Si el token tiene `tipo = "staff"` debe retornar `403` antes de llegar al controlador.

Puedes implementarlo como:
- Un `@PreAuthorize` custom, o
- Un filtro/interceptor que comprueba el claim `tipo` del JWT y rechaza si no es `"plataforma"`, o
- Una anotación custom `@RequierePlataforma` que encapsule esa validación.

El campo `rol_plataforma` (`super_admin`, `soporte`, `viewer`) **no restringe** el acceso a estos endpoints — los tres roles de plataforma pueden leer. No hace falta validarlo por ahora.

---

## Query de referencia (SQL)

Si usas JPQL o queries nativas, la lógica central del endpoint 1 sería equivalente a:

```sql
SELECT
    r.id,
    r.nombre,
    r.descripcion,
    c.id        AS id_compania,
    c.nombre    AS nombre_compania,
    COUNT(u.id) AS total_usuarios
FROM roles r
JOIN companias c ON c.id = r.id_compania
LEFT JOIN usuarios u ON u.id_rol = r.id   -- LEFT JOIN para incluir roles sin usuarios
-- WHERE r.id_compania = :idCompania       -- solo si se pasa el parámetro
GROUP BY r.id, r.nombre, r.descripcion, c.id, c.nombre
ORDER BY c.nombre ASC, r.nombre ASC
```

Adapta los nombres de tablas y columnas a los que ya existen en tu esquema.

---

## Checklist de entrega

- [ ] `GET /platform/roles` devuelve `200` con la lista de roles de todas las compañías
- [ ] `GET /platform/roles?id_compania=10` filtra correctamente por compañía
- [ ] `GET /platform/roles/{id}/permisos` devuelve `200` con rol + permisos
- [ ] `GET /platform/roles/{id}/permisos` devuelve `404` si el rol no existe
- [ ] `GET /platform/companias` devuelve `200` con id y nombre de compañías activas
- [ ] Los tres endpoints devuelven `401` con token inválido o expirado
- [ ] Los tres endpoints devuelven `403` si el token es de un usuario staff
- [ ] Un operador con rol `viewer` puede acceder (no está bloqueado)
- [ ] Los nombres de campos en el JSON siguen el formato `camelCase` (o el que use el proyecto)
