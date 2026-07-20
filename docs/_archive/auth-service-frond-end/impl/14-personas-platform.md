# IMPL_14 — Administración de Personas (Panel Plataforma)

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../STATUS.md).

> **Módulo exclusivo del perfil operador de plataforma.**  
> Solo usuarios con token tipo `plataforma` pueden acceder a esta pantalla.

---

## Contexto

La tabla `identidad.personas` es la entidad base del sistema: todo cliente, usuario staff y usuario app parte de un registro en esta tabla. El operador de plataforma necesita poder buscar, ver, crear, editar y (opcionalmente) fusionar personas desde un único lugar sin pasar por los flujos de registro de gimnasios.

---

## Campos de la entidad `Persona`

Todos los campos editables (se excluyen los de auditoría):

| Campo             | Tipo          | Requerido | Notas                          |
|-------------------|---------------|-----------|--------------------------------|
| `ci`              | string (20)   | Sí        | Único en la tabla. **Editable desde esta pantalla** |
| `nombre`          | string (150)  | Sí        |                                |
| `telefono`        | string (20)   | No        |                                |
| `correo`          | string (150)  | No        |                                |
| `sexo`            | `'M'` \| `'F'` \| `'O'` | No   | O = otro / no especificado     |
| `fecha_nacimiento`| date (string) | No        | formato `YYYY-MM-DD`           |
| `foto_url`        | string (255)  | No        | URL de imagen de perfil        |

Campos de auditoría que **NO** se muestran ni editan en el frontend:
`creacion_fecha`, `creacion_usuario`, `modifica_fecha`, `modifica_usuario`

---

## Endpoints de backend disponibles (auth-service :8080)

| Método | Ruta                        | Descripción                        | Estado     |
|--------|-----------------------------|------------------------------------|------------|
| GET    | `/api/v1/personas`          | Listar personas con paginación y filtros | **Por crear** |
| GET    | `/api/v1/personas/{id}`     | Obtener persona por ID             | Disponible |
| GET    | `/api/v1/personas/ci/{ci}`  | Buscar persona por CI              | Disponible |
| GET    | `/api/v1/personas/correo/{correo}` | Buscar persona por correo   | Disponible |
| POST   | `/api/v1/personas`          | Crear persona                      | Disponible |
| PUT    | `/api/v1/personas/{id}`     | Actualizar persona                 | Disponible |
| POST   | `/api/v1/personas/{id}/foto`| Subir foto de perfil (multipart)   | Disponible |

### Contrato del endpoint de listado (por implementar en auth-service)

```
GET /api/v1/personas?nombre=&ci=&correo=&sexo=&page=0&size=20

Response 200:
{
  "content": [Persona],
  "totalElements": number,
  "totalPages": number,
  "number": number,       // página actual (0-based)
  "size": number
}
```

Filtros opcionales:
- `nombre` — búsqueda parcial, case-insensitive
- `ci` — búsqueda parcial
- `correo` — búsqueda parcial, case-insensitive
- `sexo` — valor exacto: `M`, `F` u `O`

Ordenamiento por defecto: `nombre ASC`.

---

## Ruta frontend

```
/platform/personas
```

Protegida por `PlatformGuard` (token tipo `plataforma`). Accesible para todos los roles de plataforma (`super_admin`, `soporte`, `viewer`) — `viewer` solo puede ver, no editar.

---

## Pendientes de implementación

### FASE 1 — Pantalla base de listado y búsqueda

- [ ] **Backend:** Solicitar/implementar `GET /api/v1/personas?ci=&nombre=&page=&size=` con paginación  
      _(sin este endpoint el listado no funciona — es el bloqueador principal)_
- [ ] Agregar ruta `/platform/personas` en `src/ui/router/index.tsx`
- [ ] Agregar ítem de navegación "Personas" en `PlatformLayout` (sidebar/nav)
- [ ] Crear página `src/ui/features/platform/pages/PersonasPage.tsx`
  - Tabla con columnas: Foto (avatar), CI, Nombre, Teléfono, Correo, Sexo, Fecha Nacimiento
  - Filtros de búsqueda: **Nombre**, **Cédula (CI)**, **Correo** (inputs con debounce) y **Sexo** (select: Todos / M / F / O)
  - Paginación
  - Botón "Nueva Persona" (solo `super_admin` y `soporte`)
  - Botón de acciones por fila: Ver / Editar (solo `super_admin` y `soporte`)

### FASE 2 — Crear persona

- [ ] Crear modal `CrearPersonaModal.tsx` en `src/ui/features/platform/pages/PersonasPage/`
  - Campos: CI, Nombre, Teléfono, Correo, Sexo (select M/F), Fecha Nacimiento
  - Schema Zod en `src/ui/features/platform/schemas/crear-persona.schema.ts`
  - Llamar `authRepository.crearPersona(body)`
  - Manejo de error 409 (CI duplicada) con mensaje claro

### FASE 3 — Detalle de persona (página con pestañas)

Al hacer clic en una fila de la tabla se navega a `/platform/personas/:id`. Botón de volver al listado en el encabezado.

- [ ] Crear página `PersonaDetallePage.tsx` en `src/ui/features/platform/pages/`
- [ ] Agregar ruta `/platform/personas/:id` en `src/ui/router/index.tsx`
- [ ] Encabezado fijo: foto grande, nombre y CI de la persona
- [ ] Pestañas: **Datos personales** | **Gimnasios** | **Usuario plataforma** | **Usuario staff** | **Usuario app**

#### Pestaña "Datos personales" (edición)

- [ ] Crear componente `DatosPersonalesTab.tsx`
  - Formulario con todos los campos incluyendo **CI (editable)**
  - CI con validación de unicidad — error claro si ya existe otra persona con esa CI (error 409)
  - Llamar `authRepository.actualizarPersona(id, body)`
  - Para `viewer`: campos en solo lectura, ocultar botón guardar
  - **Selector de sexo:** mismo componente visual de tarjetas con avatar que `CrearPersonaStep`
    - Opciones: M (avatar hombre), F (avatar mujer), O (placeholder `?`)
    - Tarjeta seleccionada resaltada con borde naranja (`#f97316`) y fondo `#fff7ed`
    - `SEXO_OPTIONS` referencia `VITE_AVATAR_HOMBRE_URL` y `VITE_AVATAR_MUJER_URL` del env
    - Al seleccionar M o F, `foto_url` se actualiza automáticamente con el avatar correspondiente

#### Pestaña "Usuario plataforma"

- [ ] Crear componente `UsuarioPlataformaTab.tsx`
- Muestra si la persona tiene un usuario de plataforma asociado (`seguridad.usuarios` con `tipo = 'plataforma'`)
- **CRUD completo:**
  - **Crear** — si no tiene usuario, botón "Crear usuario plataforma" → modal con campos: login, password, rol de plataforma (`super_admin` / `soporte` / `viewer`)
  - **Editar** — si ya tiene, permite modificar login, password y rol
  - **Eliminar** — `ConfirmDialog` antes de eliminar
- Solo `super_admin` puede crear/editar/eliminar usuarios de plataforma
- **Pendiente de backend:** verificar/crear endpoints en `auth-service`:
  - `GET /api/v1/usuarios-plataforma?id_persona={id}`
  - `POST /api/v1/usuarios-plataforma`
  - `PUT /api/v1/usuarios-plataforma/{id}`
  - `DELETE /api/v1/usuarios-plataforma/{id}`

#### Pestaña "Usuario staff"

- [ ] Crear componente `UsuarioStaffTab.tsx`
- Una persona puede tener **múltiples usuarios staff**, uno por gimnasio/compañía — la pestaña muestra una tabla con una fila por cada uno
- Columnas: Login, Compañía, Sucursal, Rol, Estado
- **CRUD completo:**
  - **Crear** — modal con campos: login, password, compañía, sucursal, rol staff
  - **Editar** — permite modificar login, password, compañía, sucursal y rol
  - **Eliminar** — `ConfirmDialog` antes de eliminar
- Solo `super_admin` y `soporte` pueden crear/editar/eliminar
- **Pendiente de backend:** verificar/crear endpoints en `auth-service`:
  - `GET /api/v1/usuarios?id_persona={id}`
  - `POST /api/v1/usuarios`
  - `PUT /api/v1/usuarios/{id}`
  - `DELETE /api/v1/usuarios/{id}`

#### Pestaña "Usuario app"

- [ ] Crear componente `UsuarioAppTab.tsx`
- Una persona puede tener **múltiples cuentas app**, una por gimnasio — la pestaña muestra una tabla con una fila por cada cuenta
- Columnas: Login, Gimnasio (compañía), Activo, Último acceso
- **CRUD completo:**
  - **Crear** — modal con campos: login, password, gimnasio (compañía)
  - **Editar** — permite modificar login, password y estado (activo/inactivo)
  - **Eliminar** — `ConfirmDialog` antes de eliminar
- Solo `super_admin` y `soporte` pueden crear/editar/eliminar
- **Pendiente de backend:** verificar/crear endpoints en `auth-service`:
  - `GET /api/v1/app-usuarios?id_persona={id}` (actualmente existe `GET /api/v1/app-usuarios/por-ci/{ci}`)
  - `POST /api/v1/app-usuarios`
  - `PATCH /api/v1/app-usuarios/{id}` (ya existe para login y password)
  - `DELETE /api/v1/app-usuarios/{id}`

#### Pestaña "Gimnasios" (clientes)

- [ ] Crear componente `GimnasiosTab.tsx`
  - Muestra los registros de la tabla `core.clientes` asociados a esta persona (`id_persona`)
  - Columnas: Nombre del gimnasio (compañía), Sucursal, Estado del cliente, Fecha de registro
  - **Acciones completas CRUD:**
    - **Agregar** — botón "Agregar cliente" → modal con:
      1. Select de **compañía** (carga todas las compañías desde platform-service)
      2. Select de **sucursal** filtrado por la compañía seleccionada
      3. Al confirmar se crea el registro `core.clientes` con `id_persona` de la persona actual
    - **Editar** — botón por fila → modal para editar el registro de cliente: permite cambiar **compañía** (modifica el registro existente con PUT) y **estado**
    - **Eliminar** — botón por fila → `ConfirmDialog` antes de eliminar
  - Solo `super_admin` y `soporte` pueden agregar/editar/eliminar; `viewer` solo ve
  - **Pendientes de backend (core-service :8083):**
    - `GET /api/v1/clientes?id_persona={id}` — listar clientes de una persona
    - `POST /api/v1/clientes` — crear registro cliente (ya existe, verificar si acepta `id_persona` directo)
    - `PUT /api/v1/clientes/{id}` — editar registro cliente
    - `DELETE /api/v1/clientes/{id}` — eliminar registro cliente

### FASE 4 — Foto de perfil

- [ ] Sección de foto dentro de la pestaña **Datos personales** en `PersonaDetallePage`
  - Mostrar avatar actual (`foto_url`) o placeholder en el encabezado de la página
  - Botón "Cambiar foto" sobre el avatar → input file → POST `/personas/{id}/foto` (multipart)
  - Preview de la imagen seleccionada antes de confirmar la subida
  - Al subir exitosamente, refrescar el avatar en el encabezado de la página
  - Solo `super_admin` y `soporte` pueden cambiar la foto; para `viewer` el botón no se renderiza

---

## Archivos a crear / modificar

| Archivo | Acción |
|---------|--------|
| `src/ui/router/index.tsx` | Agregar rutas `/platform/personas` y `/platform/personas/:id` |
| `src/ui/features/platform/pages/PersonasPage.tsx` | Crear — listado con filtros |
| `src/ui/features/platform/pages/PersonasPage/CrearPersonaModal.tsx` | Crear |
| `src/ui/features/platform/pages/PersonaDetallePage.tsx` | Crear — página de detalle con pestañas |
| `src/ui/features/platform/pages/PersonaDetallePage/DatosPersonalesTab.tsx` | Crear |
| `src/ui/features/platform/pages/PersonaDetallePage/GimnasiosTab.tsx` | Crear |
| `src/ui/features/platform/pages/PersonaDetallePage/UsuarioPlataformaTab.tsx` | Crear |
| `src/ui/features/platform/pages/PersonaDetallePage/UsuarioStaffTab.tsx` | Crear |
| `src/ui/features/platform/pages/PersonaDetallePage/UsuarioAppTab.tsx` | Crear |
| `src/ui/features/platform/schemas/crear-persona.schema.ts` | Crear |
| `src/infrastructure/http/auth/AuthHttpRepository.ts` | Agregar `listarPersonas(params)` |
| `src/infrastructure/http/auth/auth.dto.ts` | Agregar `ListarPersonasParams` y `PersonasPageResponse` types |

---

## Notas de diseño

- Seguir `DESIGN_GUIDELINES.md`: usar variables CSS (`--page-bg`, `--page-text`, etc.), no colores hardcodeados.
- Tabla con `PrimeReact DataTable` (consistente con el resto del panel plataforma).
- Usar `PageHeader` de `src/ui/components/` para el encabezado de la página.
- Usar `ConfirmDialog` de `src/ui/components/` para confirmaciones destructivas.
- Formularios con React Hook Form + Zod (patrón ya establecido en el proyecto).
- Textos en i18n (`src/i18n/locales/`) bajo namespace `personas`.

### Principios UX aplicados a este módulo

- **Foto prominente:** el avatar de la persona debe ser el elemento visual más destacado tanto en la tabla como en el encabezado del detalle — ayuda a identificar rápidamente a quién se está editando.
- **Filtros siempre visibles:** la barra de filtros no se oculta detrás de un botón, está expuesta directamente sobre la tabla para acceso inmediato.
- **Feedback inmediato:** estados de carga (skeleton en tabla), mensajes de éxito/error con Sonner toast, y deshabilitado del botón guardar mientras se envía la petición.
- **Jerarquía clara en el detalle:** encabezado fijo con foto + nombre + CI, pestañas bien diferenciadas, contenido de cada pestaña sin scroll horizontal.
- **Acciones contextuales:** botones de editar/eliminar visibles solo al hacer hover sobre la fila (no saturan la tabla en reposo).
- **Selects en cascada con estado claro:** al cambiar compañía, el select de sucursal se resetea y muestra "Selecciona primero una compañía" mientras no hay compañía elegida.
- **Confirmación antes de destruir:** eliminar un registro de cliente siempre pasa por `ConfirmDialog` con texto descriptivo de qué se va a eliminar.
- **Roles reflejados en la UI:** para `viewer`, los campos aparecen en modo lectura con estilo diferenciado (no solo `disabled`), y los botones de acción directamente no se renderizan.

---

## Convenciones de implementación

### SOLID y Clean Code (backend)
- Cada nuevo endpoint debe tener su propio **Handler** (o Controller), **UseCase/Service**, y **Repository** — sin mezclar responsabilidades.
- Los Use Cases deben ser clases con un único método público (`execute` o equivalente).
- Los Handlers solo orquestan: validan la request, llaman al Use Case y mapean la respuesta. Sin lógica de negocio.
- Repositorios solo acceden a datos — sin lógica de negocio ni transformaciones de presentación.

### Convención de nombres (camelCase)
- **Todos los DTOs de request y response**, tanto en backend (Java records) como en frontend (TypeScript interfaces), usan **camelCase**.
- Esto aplica a los endpoints nuevos de este módulo: `idPersona`, `fotoUrl`, `fechaNacimiento`, `rolPlataforma`, etc.
- El frontend enviará y recibirá camelCase — no se depende de la conversión automática de Jackson para estos campos.
- **Excepción:** los endpoints ya existentes que usan snake_case (`foto_url`, `fecha_nacimiento`) no se modifican para no romper compatibilidad.

### Documentación de endpoints en cada repositorio
Al finalizar la creación de cada endpoint, se debe generar un documento en el repositorio correspondiente:
- **auth-service** → `docs/PERSONAS_API.md` con todos los endpoints nuevos de personas, usuarios plataforma, staff y app-usuarios.
- **core-service** → `docs/CLIENTES_API.md` con los endpoints nuevos de clientes.

Cada entrada debe incluir: método, ruta, descripción, request body (si aplica), response body y posibles errores (4xx).

---

## Bloqueador actual

> El backend no expone un endpoint de **listado paginado** de personas.  
> **Acción:** Implementar `GET /api/v1/personas` en `auth-service` según el contrato definido arriba antes de iniciar la FASE 1 del frontend.
