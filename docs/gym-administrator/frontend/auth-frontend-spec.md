# Módulo de Autenticación — Frontend (React + Vite)
# Especificación Funcional de Pantallas

> **Módulo:** Autenticación, Usuarios, Roles y Permisos
> **Stack:** React + Vite (SPA)
> **Fuente de contratos API:** AUTH_SERVICE_SPEC.md
> **Estado:** Definición funcional · Pendiente de diseño visual e implementación
> **Fecha:** Mayo 2026

---

## Tabla de Contenidos

1. [Alcance del módulo](#1-alcance-del-módulo)
2. [Tipos de usuario y sus rutas](#2-tipos-de-usuario-y-sus-rutas)
3. [Flujos de navegación](#3-flujos-de-navegación)
4. [Pantallas — Acceso público](#4-pantallas--acceso-público)
5. [Pantallas — Panel Staff (gym)](#5-pantallas--panel-staff-gym)
6. [Pantallas — Panel Plataforma (super_admin)](#6-pantallas--panel-plataforma-super_admin)
7. [Comportamientos globales del módulo](#7-comportamientos-globales-del-módulo)
8. [Estados de sesión y manejo de errores](#8-estados-de-sesión-y-manejo-de-errores)

---

## 1. Alcance del módulo

El módulo de autenticación del frontend cubre todo lo relacionado con:
- Ingreso al sistema (login) de los dos niveles del panel web: **staff de gym** y **operadores de plataforma**
- Recuperación y cambio de contraseña
- Gestión de usuarios staff (empleados del gym)
- Gestión de roles y permisos dentro del gym
- Gestión de cuentas app de clientes (creación, activación/desactivación)
- Bitácora de acciones del sistema
- Gestión de operadores de plataforma (super_admin)

**Fuera de este alcance:**
- App móvil de clientes (tecnología diferente — React Native / Flutter)
- Biometría (fase futura)
- Cualquier funcionalidad de otros módulos (clientes, membresías, finanzas, etc.)

---

## 2. Tipos de usuario y sus rutas

El panel web maneja **dos tipos de usuario** con zonas completamente separadas:

```
/platform/login          → Ingreso de operadores de la plataforma SaaS
  └── /platform/...      → Panel de administración de toda la plataforma

/login                   → Ingreso de staff del gym (empleados)
  └── /admin/...         → Panel operativo del gimnasio
```

> Los JWT de plataforma y de staff **nunca son intercambiables**. Si un token de plataforma intenta acceder a `/admin/...` es rechazado, y viceversa. El frontend también aplica esta separación: cada zona tiene su propio guard de ruta.

---

## 3. Flujos de navegación

### Flujo principal — Staff del gym

```
Usuario entra a /login
        │
        ├─► Ingresa correo + contraseña + id_compania
        │         │
        │         ├─ Éxito + requiere_cambio_pwd = false → /admin/dashboard
        │         ├─ Éxito + requiere_cambio_pwd = true  → /change-password
        │         ├─ 401 → mensaje de error genérico en pantalla
        │         └─ 403 → "Tu cuenta está inactiva. Contacta al administrador."
        │
        └─► Sesión activa: redirect automático a /admin/dashboard

Dentro del panel:
  /admin/dashboard
  /admin/usuarios           → Gestión de empleados
  /admin/roles              → Gestión de roles y permisos
  /admin/clientes/app       → Cuentas app de clientes
  /admin/bitacora           → Registro de acciones

Al expirar el access token:
  → Intento silencioso de refresh
  → Si refresh falla → logout forzado → /login
```

### Flujo principal — Operador de plataforma

```
Usuario entra a /platform/login
        │
        ├─► Ingresa correo + contraseña (sin id_compania)
        │         │
        │         ├─ Éxito → /platform/dashboard
        │         ├─ 401 → mensaje de error genérico
        │         └─ 403 → "Tu cuenta está inactiva."
        │
        └─► Sesión activa: redirect a /platform/dashboard

Dentro del panel de plataforma:
  /platform/dashboard
  /platform/usuarios        → Gestión de operadores de la plataforma
  /platform/companias       → (futuro — otro módulo)
```

### Flujo — Recuperación de contraseña

```
/login → link "¿Olvidaste tu contraseña?"
        │
        ▼
/reset-password
  → Usuario ingresa correo + selecciona tipo (staff / cliente)
  → POST /auth/password/reset-request
  → Pantalla de confirmación: "Si el correo existe, recibirás las instrucciones"

Usuario recibe email con link → /reset-password/confirm?token=abc123
  → Usuario ingresa nueva contraseña + confirmación
  → POST /auth/password/reset
  → Éxito → redirect a /login con mensaje de confirmación
  → 400 (token inválido/expirado) → mensaje de error + link para solicitar nuevo
```

### Flujo — Cambio de contraseña obligatorio

```
Staff hace login → respuesta incluye requiere_cambio_pwd: true
        │
        ▼
/change-password (pantalla bloqueante — no puede navegar a otro lado)
  → Ingresa contraseña actual + nueva contraseña + confirmación
  → PUT /usuarios/me/password  (o el endpoint que defina la implementación)
  → Éxito → /admin/dashboard
```

---

## 4. Pantallas — Acceso público

> Estas pantallas no requieren JWT. Son accesibles sin sesión activa.

---

### P-01 — Login Staff

**Ruta:** `/login`
**Quién la usa:** Empleados del gimnasio (recepción, dueño, entrenador, contador)

**Qué hace:**
Pantalla de ingreso al panel administrativo del gym. El empleado ingresa sus credenciales y queda autenticado dentro del contexto de su compañía.

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Correo electrónico | email | Requerido |
| Contraseña | password | Requerido |
| ID de compañía | número | Requerido — identifica el gym al que pertenece |

> El `id_compania` puede presentarse como un campo numérico o, en iteraciones futuras, como un subdominio o selector de gym por nombre. Por ahora es un campo visible y editable.

**Endpoint:** `POST /auth/login`

**Comportamientos:**
- Al enviar el formulario: mostrar indicador de carga, deshabilitar el botón
- Respuesta 200 + `requiere_cambio_pwd: false` → guardar tokens en memoria → redirect a `/admin/dashboard`
- Respuesta 200 + `requiere_cambio_pwd: true` → guardar tokens → redirect a `/change-password`
- Respuesta 401 → mostrar: *"Correo, contraseña o gimnasio incorrectos"* (mensaje genérico, no distinguir cuál campo)
- Respuesta 403 → mostrar: *"Tu cuenta está desactivada. Contacta al administrador."*
- Si ya hay sesión activa → redirect automático a `/admin/dashboard` sin mostrar el formulario
- Link a `/reset-password` para recuperación de contraseña
- Link a `/platform/login` para operadores de plataforma

---

### P-02 — Login Plataforma

**Ruta:** `/platform/login`
**Quién la usa:** Operadores SaaS (super_admin, soporte, viewer)

**Qué hace:**
Pantalla de ingreso al panel de administración de toda la plataforma. Separada visualmente del login de staff para dejar claro que es un acceso diferente.

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Correo electrónico | email | Requerido |
| Contraseña | password | Requerido |

> Sin campo `id_compania` — los operadores de plataforma tienen acceso global.

**Endpoint:** `POST /auth/platform/login`

**Comportamientos:**
- Respuesta 200 → guardar tokens → redirect a `/platform/dashboard`
- Respuesta 401 → *"Credenciales incorrectas"*
- Respuesta 403 → *"Cuenta inactiva."*
- Si ya hay sesión de plataforma activa → redirect a `/platform/dashboard`
- Link a `/login` para ir al login de staff

---

### P-03 — Solicitar recuperación de contraseña

**Ruta:** `/reset-password`
**Quién la usa:** Staff del gym que olvidó su contraseña

**Qué hace:**
Permite al empleado solicitar un email con instrucciones para restablecer su contraseña.

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Correo electrónico | email | Requerido |
| Tipo de cuenta | select | `staff` o `cliente` |
| ID de compañía | número | Requerido cuando tipo = `staff` |

**Endpoint:** `POST /auth/password/reset-request`

**Comportamientos:**
- Siempre muestra el mismo mensaje de éxito independientemente de si el correo existe: *"Si el correo está registrado, recibirás las instrucciones en breve."*
- No se muestra error de "correo no encontrado" (seguridad — no revelar existencia de emails)
- Botón de volver a `/login`

---

### P-04 — Restablecer contraseña

**Ruta:** `/reset-password/confirm?token={token}`
**Quién la usa:** Staff que recibió el email de recuperación

**Qué hace:**
Formulario final donde el usuario define su nueva contraseña usando el token del email.

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Nueva contraseña | password | Requerido, mínimo 8 caracteres |
| Confirmar contraseña | password | Debe coincidir con el campo anterior |

**Endpoint:** `POST /auth/password/reset`

**Comportamientos:**
- Al cargar la página: el token se extrae automáticamente del query param `?token=`
- Respuesta 200 → mostrar mensaje de éxito + redirect a `/login` después de 3 segundos
- Respuesta 400 (token inválido o expirado) → mostrar error: *"El enlace es inválido o ya expiró. Solicita uno nuevo."* + link a `/reset-password`
- Validar en el cliente que las dos contraseñas coincidan antes de enviar

---

### P-05 — Cambio de contraseña obligatorio

**Ruta:** `/change-password`
**Quién la usa:** Staff cuyo login retornó `requiere_cambio_pwd: true`

**Qué hace:**
Pantalla bloqueante que aparece después del primer login (o cuando el admin asignó una contraseña temporal). El usuario no puede navegar a ninguna otra pantalla del panel hasta completar el cambio.

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Contraseña actual | password | Requerido |
| Nueva contraseña | password | Requerido, mínimo 8 caracteres |
| Confirmar nueva contraseña | password | Debe coincidir |

**Endpoint:** A definir en implementación (PUT sobre el propio perfil del usuario autenticado)

**Comportamientos:**
- Si el usuario intenta navegar a otra ruta mientras `requiere_cambio_pwd = true`, el guard lo redirige de vuelta a esta pantalla
- Respuesta 200 → actualizar flag en estado local → redirect a `/admin/dashboard`
- Respuesta 401 (contraseña actual incorrecta) → mostrar error en el campo correspondiente

---

## 5. Pantallas — Panel Staff (gym)

> Todas las pantallas de esta sección requieren JWT tipo `staff` válido. El guard de ruta verifica esto antes de renderizar. Adicionalmente, cada pantalla verifica que el usuario tenga el permiso específico requerido.

---

### P-06 — Gestión de Usuarios Staff

**Ruta:** `/admin/usuarios`
**Permiso requerido:** `usuarios:leer`
**Quién la usa:** Dueño o quien tenga permiso de gestión de empleados

**Qué hace:**
Pantalla principal para ver y administrar todos los empleados del gimnasio. Permite crear nuevos usuarios, ver sus roles y activar/desactivar cuentas.

**Endpoint principal:** `GET /usuarios`

**Contenido de la pantalla:**

*Listado de usuarios:*
| Columna | Descripción |
|---|---|
| Nombre | Nombre completo del empleado |
| Correo | Email de acceso |
| Rol | Nombre del rol asignado |
| Último acceso | Fecha y hora del último login |
| Estado | Activo / Inactivo (badge visual) |
| Acciones | Ver permisos · Desactivar/Activar |

*Acciones disponibles:*
- **Botón "Nuevo usuario"** → abre panel lateral o modal de creación (P-06a)
- **"Ver permisos"** → despliega panel con los permisos resueltos del usuario (P-06b)
- **"Desactivar"** → confirmación → `PUT /usuarios/{id}/desactivar`
- **"Activar"** → `PUT /usuarios/{id}/activar`

*Casos especiales:*
- Si el usuario es el único con rol Dueño activo, el botón "Desactivar" está deshabilitado con tooltip: *"No puedes desactivar al último administrador activo."*
- Si el usuario logueado no tiene permiso `usuarios:crear`, el botón "Nuevo usuario" no aparece

---

### P-06a — Crear Usuario Staff (formulario)

**Ubicación:** Modal o panel lateral dentro de `/admin/usuarios`
**Permiso requerido:** `usuarios:crear`

**Campos del formulario:**
| Campo | Tipo | Reglas |
|---|---|---|
| Nombre completo | texto | Requerido |
| Correo electrónico | email | Requerido, único dentro del gym |
| Rol | select | Lista desde `GET /roles` |
| Sucursal | select | Lista de sucursales del gym |
| Contraseña temporal | password | Requerida — el usuario debe cambiarla en el primer login |

**Endpoint:** `POST /usuarios`

**Comportamientos:**
- 201 → cerrar modal + refrescar listado + toast de éxito: *"Usuario creado correctamente."*
- 409 → mostrar error en el campo correo: *"Este correo ya está registrado en el sistema."*
- 400 (rol de otra compañía) → error genérico de formulario

---

### P-06b — Permisos del usuario (vista detalle)

**Ubicación:** Panel lateral dentro de `/admin/usuarios`

**Qué muestra:**
- Nombre del usuario y rol asignado
- Lista completa de permisos resueltos (los que hereda del rol)
- Agrupados por módulo: `clientes`, `membresías`, `finanzas`, etc.

**Endpoint:** `GET /usuarios/{id}/permisos`

---

### P-07 — Gestión de Roles

**Ruta:** `/admin/roles`
**Permiso requerido:** `roles:leer`
**Quién la usa:** Dueño o administrador del gym

**Qué hace:**
Pantalla para ver, crear y configurar los roles del gimnasio. Cada rol define un conjunto de permisos que se asignan a los empleados.

**Endpoint principal:** `GET /roles`

**Contenido de la pantalla:**

*Listado de roles:*
| Columna | Descripción |
|---|---|
| Nombre | Ej: Dueño, Recepción, Entrenador |
| Descripción | Descripción del rol |
| Usuarios asignados | Cantidad de empleados con este rol |
| Acciones | Ver/Editar permisos · Eliminar |

*Acciones:*
- **"Nuevo rol"** → modal de creación (P-07a)
- **"Ver permisos"** → abre editor de permisos del rol (P-07b)
- **"Eliminar"** → confirmación → `DELETE /roles/{id}`
  - Si el rol tiene usuarios: *"No puedes eliminar un rol que tiene empleados asignados."*

---

### P-07a — Crear Rol (formulario)

**Ubicación:** Modal dentro de `/admin/roles`
**Permiso requerido:** `roles:crear`

**Campos:**
| Campo | Tipo | Reglas |
|---|---|---|
| Nombre del rol | texto | Requerido, único en el gym |
| Descripción | texto | Opcional |

**Endpoint:** `POST /roles`

**Comportamientos:**
- 201 → cerrar modal + refrescar listado
- 409 → *"Ya existe un rol con ese nombre."*

---

### P-07b — Editor de Permisos del Rol

**Ubicación:** Panel lateral o página dentro de `/admin/roles/{id}/permisos`
**Permiso requerido:** `roles:crear`

**Qué hace:**
Permite asignar o quitar permisos a un rol. Muestra todos los permisos disponibles del gym agrupados por módulo, con checkboxes para seleccionar cuáles tiene el rol.

**Endpoints:**
- `GET /roles/{id}/permisos` — carga los permisos actuales del rol
- `GET /permisos` — carga todos los permisos disponibles
- `PUT /roles/{id}/permisos` — guarda los permisos seleccionados (reemplaza completo)

**Contenido:**

Permisos agrupados por módulo con checkboxes:

```
[ ] Clientes
    [x] clientes:leer     — Ver listado y ficha de clientes
    [x] clientes:crear    — Registrar nuevo cliente
    [ ] clientes:editar   — Editar datos de cliente

[ ] Membresías
    [x] membresias:leer   — Ver membresías
    [ ] membresias:crear  — Vender membresía

[ ] Finanzas
    [ ] finanzas:leer     — Ver reportes financieros
    [ ] finanzas:exportar — Exportar reportes
```

**Comportamientos:**
- Botón "Guardar cambios" → `PUT /roles/{id}/permisos` con array de ids seleccionados
- 200 → toast de éxito: *"Permisos actualizados."*
- Los roles predefinidos (Dueño, Recepción, Entrenador, Contador) pueden editarse pero no eliminarse (o solo con confirmación especial)

---

### P-08 — Cuentas App de Clientes

**Ruta:** `/admin/clientes/app`
**Permiso requerido:** `clientes:crear` (o el permiso que se defina para esta acción)
**Quién la usa:** Recepción o administrador al registrar un cliente nuevo

**Qué hace:**
Permite crear y administrar las credenciales de acceso a la app móvil para los clientes del gym. Generalmente se usa junto al módulo de clientes (otro módulo), pero la gestión de credenciales vive aquí.

**Flujo principal de esta pantalla:**

```
1. Buscar persona por CI → GET /personas/ci/{ci}
   ├─ Encontrada: muestra datos de la persona
   └─ No encontrada: muestra formulario de creación de persona

2. Si la persona no existe → crear persona → POST /personas

3. Crear cuenta app → POST /app-usuarios
   → Con id_persona de la persona encontrada/creada
```

**Buscar persona:**
- Campo de búsqueda por cédula (CI)
- Resultado: nombre, correo, teléfono de la persona

**Formulario creación de persona (si no existe):**
| Campo | Tipo | Reglas |
|---|---|---|
| Cédula / Pasaporte | texto | Requerido, único |
| Nombre completo | texto | Requerido |
| Teléfono | texto | Opcional |
| Correo | email | Opcional |
| Fecha de nacimiento | fecha | Opcional |

**Formulario de cuenta app:**
| Campo | Tipo | Reglas |
|---|---|---|
| Login (correo de acceso) | email | Requerido, único en el gym |
| Contraseña inicial | password | Requerida |

**Endpoints involucrados:**
- `GET /personas/ci/{ci}` — buscar persona
- `POST /personas` — crear persona si no existe
- `POST /app-usuarios` — crear cuenta app
- `PUT /app-usuarios/{id}/activar` — activar cuenta desactivada
- `PUT /app-usuarios/{id}/desactivar` — desactivar cuenta

**Listado de cuentas app existentes** (tabla secundaria en la misma pantalla):
| Columna | Descripción |
|---|---|
| Nombre del cliente | Nombre de la persona |
| Login | Email de acceso a la app |
| Estado | Activo / Inactivo |
| Acciones | Activar · Desactivar |

---

### P-09 — Bitácora de Acciones

**Ruta:** `/admin/bitacora`
**Permiso requerido:** `usuarios:leer` (o rol Dueño)
**Quién la usa:** Dueño o administrador para auditoría

**Qué hace:**
Muestra el historial completo de acciones realizadas en el sistema: quién hizo qué y cuándo. Solo lectura — no se puede modificar.

**Endpoint:** `GET /bitacora`

**Filtros disponibles:**
| Filtro | Tipo | Descripción |
|---|---|---|
| Módulo | select | seguridad, clientes, membresías, etc. |
| Fecha desde | fecha | Inicio del rango |
| Fecha hasta | fecha | Fin del rango |
| Usuario | select | Filtrar por empleado específico |

**Tabla de resultados:**
| Columna | Descripción |
|---|---|
| Fecha y hora | Cuándo ocurrió la acción |
| Usuario | Quién la realizó |
| Módulo | Área del sistema |
| Acción | Qué se hizo (ej: crear_usuario, login_exitoso) |
| IP | Dirección IP del usuario |
| Detalle | Botón para ver snapshot JSONB si aplica |

**Comportamientos:**
- Paginación: mostrar N registros por página con navegación
- Exportar a CSV (si el usuario tiene permiso `usuarios:leer`)
- No hay botones de crear, editar ni eliminar — es solo lectura

---

## 6. Pantallas — Panel Plataforma (super_admin)

> Requieren JWT tipo `plataforma`. Zona completamente separada del panel staff.

---

### P-10 — Gestión de Operadores de Plataforma

**Ruta:** `/platform/usuarios`
**Acceso:** JWT tipo `plataforma` con `rol_plataforma = super_admin`
**Quién la usa:** Super administrador de la plataforma SaaS

**Qué hace:**
Permite ver y administrar los usuarios que operan la plataforma SaaS (los que gestionan todos los gyms). Es equivalente al nivel más alto de acceso del sistema.

**Endpoint principal:** `GET /platform/usuarios`

**Listado:**
| Columna | Descripción |
|---|---|
| Nombre | Nombre del operador |
| Correo | Email de acceso |
| Rol | super_admin · soporte · viewer |
| Estado | Activo / Inactivo |
| Último acceso | Fecha y hora |
| Acciones | Desactivar (solo super_admin puede) |

**Acciones:**
- **"Nuevo operador"** → modal de creación (P-10a)
- **"Desactivar"** → `PUT /platform/usuarios/{id}/desactivar`
  - Si es el último super_admin activo: botón deshabilitado con tooltip de advertencia

---

### P-10a — Crear Operador de Plataforma

**Ubicación:** Modal dentro de `/platform/usuarios`
**Acceso:** Solo `super_admin`

**Campos:**
| Campo | Tipo | Reglas |
|---|---|---|
| Nombre completo | texto | Requerido |
| Correo electrónico | email | Requerido |
| Contraseña | password | Requerida |
| Rol | select | super_admin · soporte · viewer |

**Endpoint:** `POST /platform/usuarios`

**Comportamientos:**
- 201 → cerrar modal + refrescar listado
- 409 → *"Este correo ya está registrado."*
- Los usuarios con rol `soporte` o `viewer` solo ven esta opción si tienen permisos (el `viewer` no puede crear)

---

## 7. Comportamientos globales del módulo

### Manejo de tokens

| Evento | Comportamiento |
|---|---|
| Login exitoso | Guardar `access_token` en memoria (no localStorage por seguridad XSS) y `refresh_token` en httpOnly cookie |
| Request a la API | Adjuntar `Authorization: Bearer {access_token}` automáticamente en todos los requests |
| Respuesta 401 en cualquier endpoint protegido | Intentar refresh silencioso → si falla → logout forzado → redirect a login |
| Logout del usuario | `POST /auth/logout` → limpiar tokens en memoria y cookie → redirect a `/login` |
| Tab cerrado / sesión de navegador cerrada | El access token en memoria se pierde → próxima visita pide login nuevamente |

### Guards de ruta

Hay tres guards que protegen las rutas:

| Guard | Qué verifica | Ruta de redirect |
|---|---|---|
| `authGuard` | JWT válido + tipo `staff` | `/login` |
| `platformGuard` | JWT válido + tipo `plataforma` | `/platform/login` |
| `permissionGuard` | Permiso específico en el JWT | `/admin/sin-acceso` (403) |

### Pantalla de sin acceso (403)

**Ruta:** `/admin/sin-acceso`

Pantalla simple que informa al usuario que no tiene permiso para ver la sección solicitada. Muestra el permiso faltante y un botón para volver al dashboard.

### Navegación según permisos

El menú lateral del panel solo muestra las opciones a las que el usuario tiene acceso. Si no tiene `usuarios:leer`, la opción de "Usuarios" no aparece en el menú. Esto se calcula leyendo el array `permisos` del JWT almacenado, sin hacer requests adicionales.

---

## 8. Estados de sesión y manejo de errores

### Mensajes de error estándar

| Situación | Mensaje al usuario |
|---|---|
| 401 en login | *"Correo, contraseña o identificador de gimnasio incorrectos."* |
| 403 en login (inactivo) | *"Tu cuenta está desactivada. Contacta al administrador."* |
| 401 en endpoint protegido | Refresh silencioso → si falla: redirect a login |
| 403 en endpoint protegido | *"No tienes permiso para realizar esta acción."* |
| 409 en creación de recurso | Mensaje específico en el campo correspondiente |
| 500 o red caída | *"Ocurrió un error. Por favor intenta de nuevo."* con botón de reintentar |
| Formulario inválido | Mensajes en línea debajo de cada campo incorrecto antes de hacer el request |

### Indicadores de carga

- Botones de formulario muestran spinner y se deshabilitan durante el request
- Las tablas de datos muestran skeleton loading al cargar
- Las acciones destructivas (desactivar, eliminar) muestran diálogo de confirmación antes de ejecutar

### Rate limiting

Si el servidor responde 429 (demasiados intentos de login):
- Mostrar: *"Demasiados intentos fallidos. Espera {N} minutos antes de intentar de nuevo."*
- Deshabilitar el botón de login durante el tiempo de espera indicado en la respuesta

---

## Resumen de pantallas y endpoints

| ID | Pantalla | Ruta | Endpoints |
|---|---|---|---|
| P-01 | Login Staff | `/login` | POST /auth/login |
| P-02 | Login Plataforma | `/platform/login` | POST /auth/platform/login |
| P-03 | Solicitar reset contraseña | `/reset-password` | POST /auth/password/reset-request |
| P-04 | Restablecer contraseña | `/reset-password/confirm` | POST /auth/password/reset |
| P-05 | Cambio obligatorio de contraseña | `/change-password` | PUT /usuarios/me/password |
| P-06 | Listado de usuarios staff | `/admin/usuarios` | GET /usuarios · PUT /usuarios/{id}/desactivar |
| P-06a | Crear usuario staff | modal en P-06 | POST /usuarios |
| P-06b | Ver permisos de usuario | panel en P-06 | GET /usuarios/{id}/permisos |
| P-07 | Listado de roles | `/admin/roles` | GET /roles · DELETE /roles/{id} |
| P-07a | Crear rol | modal en P-07 | POST /roles |
| P-07b | Editor de permisos del rol | panel en P-07 | GET /roles/{id}/permisos · GET /permisos · PUT /roles/{id}/permisos |
| P-08 | Cuentas app de clientes | `/admin/clientes/app` | GET /personas/ci/{ci} · POST /personas · POST /app-usuarios · PUT /app-usuarios/{id}/activar|desactivar |
| P-09 | Bitácora | `/admin/bitacora` | GET /bitacora |
| P-10 | Operadores de plataforma | `/platform/usuarios` | GET /platform/usuarios · PUT /platform/usuarios/{id}/desactivar |
| P-10a | Crear operador de plataforma | modal en P-10 | POST /platform/usuarios |

---

*Frontend Auth Spec v1.0 · Gym Administrator · Mayo 2026*
