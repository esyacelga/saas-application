# finance-service — Índice de documentación

Gestión de ingresos y egresos financieros: categorización, registro, consulta y reportes analíticos (resumen, mensual, proyección). Ver [finance-service/README.md](../../finance-service/README.md) (Docker, variables de entorno) para información de despliegue. Este índice cubre solo la documentación de API.

**Puerto:** 8085

---

## api/ — Referencia de endpoints

| Documento | Base URL | Contenido |
|-----------|----------|-----------|
| [categorias-ingreso.md](api/categorias-ingreso.md) | `/api/v1/finanzas/categorias-ingreso` | Catálogo de categorías para ingresos: crear, listar, desactivar |
| [categorias-egreso.md](api/categorias-egreso.md) | `/api/v1/finanzas/categorias-egreso` | Catálogo de categorías para egresos: crear, listar, desactivar |
| [ingresos.md](api/ingresos.md) | `/api/v1/finanzas/ingresos` | Registro y consulta de ingresos financieros; notar que son **inmutables** |
| [egresos.md](api/egresos.md) | `/api/v1/finanzas/egresos` | Registro y consulta de egresos financieros |
| [reportes.md](api/reportes.md) | `/api/v1/finanzas/reporte` | Reportes de resumen, desglose mensual y proyección de ingresos |

---

## Convenciones

### Autenticación y autorización

Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff o plataforma).

**Roles y permisos:**
- `tipo` (del JWT): `staff` o `plataforma`
- `rol_gym` (solo en staff): `dueno`, `admin_compania`, `recepcion`, `entrenador`
- `rol_plataforma` (solo en plataforma): `super_admin`
- `permisos` (lista): `finanzas:leer`, `finanzas:crear`, `finanzas:exportar`

**Reglas de acceso por endpoint:**
- Lectura (GET ingresos/egresos/categorías): `finanzas:leer` OR `isDueno()` OR `isPlataforma()`
- Escritura (POST/PUT categorías y POST egresos): `finanzas:crear` OR `isDueno()` OR `isPlataforma()`
- Reportes (GET reporte/*): `finanzas:exportar` OR `finanzas:leer` OR `isDueno()` OR `isPlataforma()`
- **Especial:** POST ingresos permite además `isRecepcion()` (rol de recepcionista)

### Multi-tenancy

Todos los endpoints filtran automáticamente por `id_compania` extraído del JWT. **Nunca envíes** `id_compania` en el body ni path.

### Sucursal

El campo `id_sucursal` es opcional en el body de POST. Si no viene, se defaultea al de `id_sucursal` del JWT; si ese tampoco existe, se usa `1`.

### Fechas

- Campos `fecha` (de ingresos/egresos): `LocalDate` — formato ISO `YYYY-MM-DD`
- Campos de auditoría (`creacion_fecha`, `modifica_fecha`): `Instant` — formato ISO-8601 con Z

### Jackson serialization

Todos los JSON usan `snake_case`. En Java, las DTOs tienen campos `camelCase` que Jackson mapea automáticamente.

**Errores HTTP:**
- `400` — validación fallida (campo faltante, formato inválido, etc.)
- `401` — JWT ausente o inválido
- `403` — permiso insuficiente
- `404` — recurso no encontrado
- `409` — conflicto (ej. categoría activa con referencia no pode ser desactivada)
- `422` — error de negocio (ej. cantidad inválida)

### Reglas de negocio

- **RN-01 (Ingresos inmutables):** Una vez registrado, un ingreso **no puede ser modificado ni eliminado**. No hay endpoints PUT ni DELETE para ingresos.
- **RN-02 (Soft-delete):** Egresos pueden ser eliminados lógicamente, pero no se expone DELETE en la API — se maneja internamente.
- **RN-03 (Filtrado por compañía):** Todas las queries filtran `eliminado = false` y `id_compania = {del JWT}`.
- **RN-04 (Desactivación de categorías):** Una categoría solo puede ser desactivada (`activo = false`) si **no hay ingresos ni egresos activos** que la referencien. Si hay, devuelve `409 Conflict`.
- **RN-05 (Defaulteo de sucursal):** `id_sucursal` en body es opcional; se defaultea al del JWT si viene, o a `1` si no.
- **RN-06 (Reportes):** Los agregados usan `DatabaseClient` para sumas y agrupaciones directas en BD.

### Timezone

La JVM corre con timezone **America/Guayaquil** (UTC-5). Fechas `LocalDate` son interpretadas en esa zona.

### Auditoría

Cada modelo (`CategoriaIngreso`, `Ingreso`, `CategoriaEgreso`, `Egreso`) incluye automáticamente:
- `creacion_fecha`, `creacion_usuario` — asignados en INSERT
- `modifica_fecha`, `modifica_usuario` — actualizados en UPDATE (si aplica)

Estos campos **nunca** se envían en el request body; son gestionados por el backend.

---

## Más información

- [finance-service/CLAUDE.md](../../finance-service/CLAUDE.md) — Arquitectura interna (hexagonal, R2DBC, WebFlux), configuración, comandos de build.
- [finance-service/README.md](../../finance-service/README.md) — Despliegue, variables de entorno, estructura de carpetas.
