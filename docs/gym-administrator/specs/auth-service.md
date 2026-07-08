# Auth Service — Especificación de Desarrollo

> **ESTADO:** 🟡 Spec de diseño de un servicio YA implementado. El **código es la fuente de verdad**; esta spec puede haber divergido. Para la API real ver `docs/<servicio>/` y el CLAUDE.md del servicio. Ver [../../STATUS.md](../../STATUS.md).

> **Servicio:** auth-service  
> **Esquemas BD:** `saas` · `identidad` · `seguridad`  
> **Tablas:** 9 tablas (1 saas + 3 identidad + 5 seguridad)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Dos tipos de usuario](#2-dos-tipos-de-usuario)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [JWT — Diseño del token](#4-jwt--diseño-del-token)
5. [RBAC — Control de acceso basado en roles](#5-rbac--control-de-acceso-basado-en-roles)
6. [API — Contratos de endpoints](#6-api--contratos-de-endpoints)
7. [Flujos principales](#7-flujos-principales)
8. [Casos de prueba](#8-casos-de-prueba)
9. [Datos semilla (seeds)](#9-datos-semilla-seeds)
10. [Variables de entorno requeridas](#10-variables-de-entorno-requeridas)
11. [Reglas de negocio críticas](#11-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Auth Service es el **único punto de entrada para autenticación y autorización** en toda la plataforma. Ningún otro servicio valida identidades — todos confían en el JWT emitido por este servicio.

**Responsabilidades:**
- Autenticar empleados del gimnasio (panel web)
- Autenticar clientes del gimnasio (app móvil)
- Emitir y validar JWT con permisos embebidos
- Gestionar roles y permisos por compañía
- Registrar en bitácora toda acción de escritura
- Gestionar biometría (fase futura — tablas ya diseñadas)

**Fuera de alcance de este servicio:**
- Validación de membresías (→ Core Service)
- Registro de asistencia biométrica (→ Attendance Service)
- Lógica de planes SaaS (→ Platform Service)

---

## 2. Tres niveles de usuario

Este servicio maneja **tres niveles de usuario completamente independientes** con tablas y JWT distintos:

```
┌──────────────────────────────────────────────────────────────────┐
│  NIVEL 1: OPERADOR DE PLATAFORMA (super_admin)                   │
│  Tabla: saas.usuarios_plataforma                                  │
│  Acceso: Panel de administración de la plataforma SaaS           │
│  Auth: correo + contraseña → JWT sin id_compania                 │
│  Roles: super_admin | soporte | viewer                           │
│  Puede: ver/crear/gestionar TODAS las compañías y planes         │
├──────────────────────────────────────────────────────────────────┤
│  NIVEL 2: STAFF DEL GYM (empleados)                              │
│  Tabla: seguridad.usuarios                                        │
│  Acceso: Panel web del gimnasio                                   │
│  Auth: correo + contraseña → JWT con permisos RBAC               │
│  Contexto en token: id_compania, id_sucursal, id_rol, permisos   │
│  Puede: operar solo dentro de su id_compania                     │
├──────────────────────────────────────────────────────────────────┤
│  NIVEL 3: CLIENTE (socios del gym)                               │
│  Tabla: identidad.usuarios_app                                    │
│  Acceso: App móvil                                                │
│  Auth: login + contraseña → JWT mínimo                           │
│  Contexto en token: id_compania, id_persona                      │
│  Puede: ver solo sus propios datos                               │
└──────────────────────────────────────────────────────────────────┘
```

> **Regla crítica:** Los tres tipos de JWT (`plataforma`, `staff`, `cliente`) nunca son intercambiables. El campo `tipo` del token determina qué endpoints puede llamar, independientemente de los otros campos.

---

## 3. Tablas involucradas

### saas.usuarios_plataforma
```sql
id            INT     PK, identity
id_persona    INT     FK → identidad.personas(id)      -- nombre y foto viven en personas
correo        VARCHAR(150) NOT NULL UNIQUE              -- puede ser corporativo
password_hash VARCHAR(255) NOT NULL
rol           VARCHAR(30)  NOT NULL DEFAULT 'super_admin'
                CHECK (rol IN ('super_admin','soporte','viewer'))
activo        BOOLEAN      NOT NULL DEFAULT TRUE
ultimo_acceso TIMESTAMPTZ
created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
UNIQUE (id_persona)
```
> Global — no tiene `id_compania`. Un usuario de este nivel ve toda la plataforma.
>
> | Rol | Capacidades |
> |---|---|
> | `super_admin` | Lectura y escritura total — registrar gyms, cambiar planes, gestionar todo |
> | `soporte` | Lectura total + acciones de soporte (resetear passwords, reactivar cuentas) |
> | `viewer` | Solo lectura — para auditores o stakeholders externos |

---

### identidad.personas
```sql
id               INT     PK, identity
ci               VARCHAR(20)  UNIQUE NOT NULL       -- Cédula / pasaporte
nombre           VARCHAR(150) NOT NULL
telefono         VARCHAR(20)
correo           VARCHAR(150)
foto_url         VARCHAR(255)
fecha_nacimiento DATE
created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
```
> Global — no tiene `id_compania`. La misma persona puede ser cliente en múltiples gyms.

### identidad.usuarios_app
```sql
id                  INT     PK, identity
id_persona          INT     FK → identidad.personas(id)
id_compania         INT     NOT NULL                 -- gym al que pertenece este login
login               VARCHAR(150)                     -- generalmente el correo
password_hash       VARCHAR(255)
requiere_cambio_pwd BOOLEAN DEFAULT TRUE
activo              BOOLEAN DEFAULT TRUE
ultimo_acceso       TIMESTAMPTZ
token_recuperacion  VARCHAR(100)                     -- para reset de contraseña
token_expira        TIMESTAMPTZ
UNIQUE (id_persona, id_compania)                     -- una cuenta por gym por persona
UNIQUE (id_compania, login)                          -- login único dentro del gym
```

### identidad.biometria
```sql
id           INT     PK, identity
id_persona   INT     FK → identidad.personas(id)
id_compania  INT     NOT NULL
tipo         VARCHAR(20) CHECK IN ('huella','facial','iris')
hash_datos   BYTEA   NOT NULL                        -- plantilla AES-256 encriptada
activo       BOOLEAN DEFAULT TRUE
UNIQUE (id_persona, id_compania, tipo)               -- un perfil biométrico por tipo por gym
```

### seguridad.roles
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(50)  NOT NULL
descripcion  VARCHAR(255)
UNIQUE (id_compania, nombre)
```

### seguridad.permisos
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(100) NOT NULL                   -- formato: modulo:accion  ej: clientes:crear
descripcion  VARCHAR(255)
modulo       VARCHAR(50)  NOT NULL                   -- clientes | membresias | finanzas | ...
UNIQUE (id_compania, nombre)
```

### seguridad.rol_permisos
```sql
id_rol     INT  FK → seguridad.roles(id)
id_permiso INT  FK → seguridad.permisos(id)
PRIMARY KEY (id_rol, id_permiso)
```

### seguridad.usuarios
```sql
id            INT     PK, identity
id_compania   INT     NOT NULL
id_sucursal   INT     NOT NULL
id_rol        INT     FK → seguridad.roles(id)
id_persona    INT     FK → identidad.personas(id)       -- nombre y foto viven en personas
correo        VARCHAR(150) NOT NULL                     -- puede ser corporativo (ej: juan@mygym.com)
password_hash VARCHAR(255) NOT NULL
activo        BOOLEAN DEFAULT TRUE
ultimo_acceso TIMESTAMPTZ
UNIQUE (id_persona, id_compania)                       -- un empleado una cuenta por gym
UNIQUE (id_compania, correo)
```

### seguridad.bitacora_accesos
```sql
id          BIGINT  PK, identity
id_compania INT     NOT NULL
id_sucursal INT     NOT NULL
id_usuario  INT     FK → seguridad.usuarios(id)
modulo      VARCHAR(50)   NOT NULL                   -- seguridad | core | finanzas | ...
accion      VARCHAR(100)  NOT NULL                   -- crear_usuario | actualizar_rol | ...
entidad_id  INT                                      -- ID del registro afectado
detalle     JSONB                                    -- snapshot antes/después del cambio
ip          VARCHAR(45)
fecha       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
```

---

## 4. JWT — Diseño del token

### Token de Plataforma (super_admin / soporte / viewer)

```json
{
  "sub": "1",
  "tipo": "plataforma",
  "rol_plataforma": "super_admin",
  "nombre": "Santiago Yacelga",
  "iat": 1716000000,
  "exp": 1716086400
}
```
> Sin `id_compania` — ausencia del campo indica acceso a toda la plataforma.

### Token de Staff (panel administrativo)

```json
{
  "sub": "42",
  "tipo": "staff",
  "id_compania": 1,
  "id_sucursal": 2,
  "id_rol": 3,
  "nombre": "Juan Pérez",
  "permisos": [
    "clientes:leer",
    "clientes:crear",
    "membresias:leer",
    "membresias:crear"
  ],
  "iat": 1716000000,
  "exp": 1716086400
}
```

### Token de Cliente (app móvil)

```json
{
  "sub": "15",
  "tipo": "cliente",
  "id_compania": 1,
  "id_persona": 10,
  "nombre": "María López",
  "iat": 1716000000,
  "exp": 1716432000
}
```

### Configuración de expiración

| Token | Duración | Refresh |
|---|---|---|
| Access token (plataforma) | 8 horas | Sí, con refresh token |
| Access token (staff) | 8 horas | Sí, con refresh token |
| Access token (cliente) | 7 días | Sí, con refresh token |
| Refresh token | 30 días | No, re-login |
| Token recuperación pwd | 1 hora | No, re-solicitar |

---

## 5. RBAC — Control de acceso basado en roles

### Formato de permisos

```
{modulo}:{accion}

Módulos:    clientes | membresias | asistencia | finanzas | marketing
            inventario | usuarios | roles | reportes | config

Acciones:   leer | crear | editar | eliminar | exportar
```

### Roles predefinidos (seeds obligatorios por gym)

| Rol | Permisos típicos |
|---|---|
| `Dueño` | Todos los permisos |
| `Recepción` | clientes:leer/crear/editar, membresias:leer/crear, asistencia:leer/crear |
| `Entrenador` | clientes:leer, asistencia:leer/crear |
| `Contador` | finanzas:leer/exportar, reportes:leer |

> Los gyms pueden crear roles adicionales con permisos personalizados.

### Resolución del permiso en cada request

```
Request con JWT
      │
      ├─ Middleware decodifica JWT → extrae array "permisos"
      │
      ├─ Verifica tipo == "staff" (si es endpoint del panel)
      │
      ├─ Verifica id_compania del JWT == id_compania del recurso solicitado
      │
      └─ Verifica que permisos[] contiene el permiso requerido
             │
             ├─ Sí → continúa
             └─ No → HTTP 403 Forbidden
```

---

## 6. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 6.1 Autenticación

#### `POST /auth/platform/login` — Login de operador de plataforma
```json
// Request
{
  "correo": "admin@gymadministrator.com",
  "password": "SuperClave2026"
}

// Response 200
{
  "access_token": "eyJhbGci...",
  "refresh_token": "dGhpcyBp...",
  "expires_in": 28800,
  "usuario": {
    "id": 1,
    "nombre": "Santiago Yacelga",
    "foto_url": "https://cdn.gymadministrator.com/platform/1.jpg",
    "rol_plataforma": "super_admin"
  }
}

// Errores
// 401 → credenciales incorrectas (mensaje genérico)
// 403 → usuario inactivo
```

#### `POST /auth/login` — Login de staff
```json
// Request
{
  "correo": "juan@gym.com",
  "password": "MiClave123",
  "id_compania": 1
}

// Response 200
{
  "access_token": "eyJhbGci...",
  "refresh_token": "dGhpcyBp...",
  "expires_in": 28800,
  "requiere_cambio_pwd": false,
  "usuario": {
    "id": 42,
    "nombre": "Juan Pérez",
    "correo": "juan@gym.com",
    "foto_url": null,
    "id_rol": 3,
    "nombre_rol": "Recepción"
  }
}

// Errores posibles
// 401 → credenciales incorrectas (mensaje genérico, no revelar cuál campo)
// 403 → usuario inactivo
```

#### `POST /auth/app/login` — Login de cliente (app móvil)
```json
// Request
{
  "login": "maria@gmail.com",
  "password": "MiClave456",
  "id_compania": 1
}

// Response 200
{
  "access_token": "eyJhbGci...",
  "refresh_token": "dGhpcyBp...",
  "expires_in": 604800,
  "persona": {
    "id": 10,
    "nombre": "María López",
    "foto_url": "https://..."
  }
}
```

#### `POST /auth/refresh` — Renovar access token
```json
// Request
{
  "refresh_token": "dGhpcyBp..."
}

// Response 200
{
  "access_token": "eyJhbGci...",
  "expires_in": 28800
}

// Errores
// 401 → token inválido o expirado
```

#### `POST /auth/logout`
```
// Headers: Authorization: Bearer {access_token}
// Response 204 No Content
// Acción: invalida el refresh_token en BD
```

#### `POST /auth/password/reset-request` — Solicitar reset
```json
// Request
{
  "correo": "juan@gym.com",
  "id_compania": 1,
  "tipo": "staff"
}
// Response 200 (siempre, no revelar si el email existe)
{
  "mensaje": "Si el correo existe, recibirás las instrucciones"
}
```

#### `POST /auth/password/reset` — Aplicar reset
```json
// Request
{
  "token": "abc123xyz",
  "nueva_password": "NuevaClave789"
}
// Response 200 OK | 400 token inválido/expirado
```

---

### 6.2 Usuarios de Plataforma

**Requiere:** JWT tipo `plataforma` con `rol_plataforma = super_admin`

#### `GET /platform/usuarios` — Listar operadores de plataforma
```json
// Response 200
[
  { "id": 1, "nombre": "Santiago Yacelga", "correo": "admin@gymadministrator.com", "foto_url": null, "rol_plataforma": "super_admin", "activo": true }
]
```

#### `POST /platform/usuarios` — Crear operador
```json
// Request — primero buscar/crear la persona via POST /personas
{
  "id_persona": 5,
  "correo": "ana@gymadministrator.com",
  "password": "TempPass2026",
  "rol": "soporte"
}
// Response 201
// 409 si el correo ya existe
// 409 si id_persona ya tiene una cuenta de plataforma
```

#### `PUT /platform/usuarios/{id}` — Actualizar correo o rol
```json
// Request (solo campos a cambiar — nombre y foto se actualizan en PUT /personas/{id})
{
  "correo": "ana.soporte@gymadministrator.com",
  "rol": "viewer"
}
// Response 200
```

#### `PUT /platform/usuarios/{id}/desactivar`
```
// Response 200
// No se puede desactivar al último super_admin activo
```

---

### 6.3 Personas

#### `GET /personas/ci/{ci}` — Buscar por cédula
```json
// Response 200
{
  "id": 10,
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "foto_url": null,
  "fecha_nacimiento": "1990-05-15"
}
// 404 si no existe
```

#### `POST /personas` — Crear persona
```json
// Request
{
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "fecha_nacimiento": "1990-05-15"
}
// Response 201
// 409 si la CI ya existe
```

#### `PUT /personas/{id}` — Actualizar persona
```json
// Request (solo campos a cambiar)
{
  "telefono": "0999999999",
  "correo": "nuevocorreo@gmail.com"
}
// Response 200
// Nota: CI no se puede cambiar
```

---

### 6.4 Roles

**Requiere:** JWT staff + permiso `roles:leer` o `roles:crear` según operación

#### `GET /roles` — Listar roles de la compañía
```json
// Response 200 (filtrado automático por id_compania del JWT)
[
  { "id": 1, "nombre": "Dueño", "descripcion": "Acceso total" },
  { "id": 2, "nombre": "Recepción", "descripcion": "Atención al cliente" }
]
```

#### `POST /roles` — Crear rol
```json
// Request
{
  "nombre": "Entrenador",
  "descripcion": "Acceso a clientes y asistencia"
}
// Response 201
// 409 si ya existe un rol con ese nombre en la compañía
```

#### `GET /roles/{id}/permisos` — Ver permisos del rol
```json
// Response 200
{
  "rol": { "id": 2, "nombre": "Recepción" },
  "permisos": [
    { "id": 5, "nombre": "clientes:leer", "modulo": "clientes" },
    { "id": 6, "nombre": "clientes:crear", "modulo": "clientes" }
  ]
}
```

#### `PUT /roles/{id}/permisos` — Reemplazar permisos del rol (bulk)
```json
// Request
{
  "id_permisos": [5, 6, 9, 12]
}
// Response 200
// 400 si algún id_permiso no pertenece a la misma compañía
```

#### `DELETE /roles/{id}` — Eliminar rol
```
// Response 204
// 409 si hay usuarios asignados a este rol
```

---

### 6.5 Permisos

#### `GET /permisos` — Listar permisos disponibles de la compañía
```json
// Response 200
[
  { "id": 5, "nombre": "clientes:leer", "modulo": "clientes", "descripcion": "Ver listado y ficha" },
  { "id": 6, "nombre": "clientes:crear", "modulo": "clientes", "descripcion": "Registrar nuevo cliente" }
]
```

---

### 6.6 Usuarios Staff

**Requiere:** permiso `usuarios:leer` / `usuarios:crear` / `usuarios:editar`

#### `GET /usuarios` — Listar staff de la compañía
```json
// Response 200
[
  {
    "id": 42,
    "nombre": "Juan Pérez",
    "correo": "juan@gym.com",
    "foto_url": null,
    "id_rol": 2,
    "nombre_rol": "Recepción",
    "activo": true,
    "ultimo_acceso": "2026-05-18T14:30:00Z"
  }
]
```

#### `POST /usuarios` — Crear usuario staff
```json
// Request — primero buscar/crear la persona via POST /personas
{
  "id_persona": 15,
  "correo": "ana@gym.com",
  "id_rol": 2,
  "id_sucursal": 1,
  "password_temporal": "TempAna2026"
}
// Response 201
// 409 si correo ya existe en la compañía
// 409 si id_persona ya tiene cuenta en esta compañía
// 400 si id_rol pertenece a otra compañía
```

#### `PUT /usuarios/{id}` — Actualizar correo o rol del staff
```json
// Request (solo campos a cambiar — nombre y foto se actualizan en PUT /personas/{id})
{
  "correo": "ana.torres@gym.com",
  "id_rol": 3
}
// Response 200
// 400 si id_rol pertenece a otra compañía
```

#### `GET /usuarios/{id}/permisos` — Permisos resueltos del usuario
```json
// Response 200
{
  "usuario": { "id": 42, "nombre": "Juan Pérez" },
  "rol": { "id": 2, "nombre": "Recepción" },
  "permisos": ["clientes:leer", "clientes:crear", "membresias:leer"]
}
```

#### `PUT /usuarios/{id}/desactivar` — Desactivar usuario
```
// Response 200
// No se puede desactivar al último dueño activo
```

---

### 6.7 Usuarios App (clientes)

#### `POST /app-usuarios` — Crear acceso app para cliente
```json
// Request
{
  "id_persona": 10,
  "login": "maria@gmail.com",
  "password": "PrimerAcceso123"
}
// Response 201 — se crea con requiere_cambio_pwd=true
// id_compania se toma del JWT del staff que lo crea
// 409 si la persona ya tiene cuenta en esta compañía
```

#### `PUT /app-usuarios/{id}/activar` / `desactivar`
```
// Response 200
```

---

### 6.8 Bitácora

#### `GET /bitacora` — Consultar log de acciones
```
// Query params: ?modulo=seguridad&desde=2026-05-01&hasta=2026-05-31&id_usuario=42
// Requiere: permiso usuarios:leer o rol Dueño

// Response 200
{
  "total": 150,
  "pagina": 1,
  "datos": [
    {
      "id": 1001,
      "id_usuario": 42,
      "nombre_usuario": "Juan Pérez",
      "modulo": "seguridad",
      "accion": "crear_usuario",
      "entidad_id": 45,
      "ip": "192.168.1.10",
      "fecha": "2026-05-18T14:32:00Z"
    }
  ]
}
```

---

## 7. Flujos principales

### Flujo: Login staff + verificación de permiso

```
Cliente HTTP
    │
    ├─► POST /auth/login  {correo, password, id_compania}
    │        │
    │        ├─ Busca seguridad.usuarios WHERE correo = ? AND id_compania = ?
    │        ├─ Verifica password_hash (bcrypt)
    │        ├─ Verifica activo = true
    │        ├─ Resuelve permisos: roles → rol_permisos → permisos.nombre[]
    │        ├─ Genera JWT con permisos embebidos
    │        ├─ UPDATE ultimo_acceso = NOW()
    │        └─ INSERT bitacora (accion='login_exitoso')
    │
    └─► GET /clientes  {Authorization: Bearer JWT}
             │
             ├─ Middleware: decodifica JWT
             ├─ Verifica tipo == "staff"
             ├─ Verifica id_compania coincide con recurso
             ├─ Verifica "clientes:leer" en permisos[]
             └─ Pasa al handler → 200 OK
```

### Flujo: Reset de contraseña

```
1. POST /auth/password/reset-request {correo, id_compania, tipo}
   → Genera token aleatorio (32 bytes hex)
   → UPDATE usuarios_app SET token_recuperacion=?, token_expira=NOW()+1h
   → Envía email con link (responsabilidad del servicio de notificaciones)
   → Responde 200 siempre (no revelar si el email existe)

2. POST /auth/password/reset {token, nueva_password}
   → Busca el token en BD
   → Verifica token_expira > NOW()
   → UPDATE password_hash = bcrypt(nueva_password)
   → UPDATE token_recuperacion=NULL, token_expira=NULL
   → Responde 200
```

---

## 8. Casos de prueba

### TC-PLAT — Operador de Plataforma

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-PLAT-001 | Login super_admin exitoso | credenciales válidas | 200 + JWT tipo `plataforma` sin id_compania |
| TC-PLAT-002 | Login con cuenta inactiva | activo=false | 403 |
| TC-PLAT-003 | Login con credenciales incorrectas | password inválido | 401 genérico |
| TC-PLAT-004 | JWT plataforma accede endpoint staff `/usuarios` | token tipo=plataforma | 403 (tipos no intercambiables) |
| TC-PLAT-005 | JWT staff intenta acceder `/platform/usuarios` | token tipo=staff | 403 |
| TC-PLAT-006 | Crear operador soporte | rol=soporte, datos válidos | 201 |
| TC-PLAT-007 | Crear operador con correo duplicado | correo ya existente | 409 |
| TC-PLAT-008 | Desactivar último super_admin activo | solo queda 1 | 409 |
| TC-PLAT-009 | Viewer intenta crear operador | rol_plataforma=viewer | 403 |
| TC-PLAT-010 | super_admin puede ver datos de cualquier compañía | sin id_compania en JWT | 200 con datos de todas |

### TC-AUTH — Autenticación

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-AUTH-001 | Login staff exitoso | correo + pwd válidos | 200 + JWT con permisos |
| TC-AUTH-002 | Contraseña incorrecta | pwd inválido | 401 (mensaje genérico) |
| TC-AUTH-003 | Usuario inexistente | correo no registrado | 401 (mismo mensaje que TC-AUTH-002) |
| TC-AUTH-004 | Usuario inactivo | activo=false | 403 |
| TC-AUTH-005 | Usuario de otra compañía | id_compania=99 pero usuario en compañía 1 | 401 |
| TC-AUTH-006 | requiere_cambio_pwd=true | login válido | 200 + `requiere_cambio_pwd: true` en response |
| TC-AUTH-007 | Login cliente exitoso | login + pwd app válidos | 200 + JWT tipo cliente |
| TC-AUTH-008 | Login cliente gym incorrecto | id_compania errado | 401 |
| TC-AUTH-009 | Refresh con token válido | refresh_token activo | 200 + nuevo access_token |
| TC-AUTH-010 | Refresh con token expirado | refresh_token vencido | 401 |
| TC-AUTH-011 | Refresh con token malformado | string aleatorio | 401 |
| TC-AUTH-012 | Logout invalida refresh | logout → intento de refresh | 401 en el refresh |
| TC-AUTH-013 | JWT de cliente en endpoint staff | token tipo=cliente en GET /clientes | 403 |
| TC-AUTH-014 | Rate limit en login | 10 intentos fallidos seguidos | 429 desde el intento 6 |

### TC-PER — Personas

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-PER-001 | Crear persona nueva | CI único, datos completos | 201 + persona creada |
| TC-PER-002 | CI duplicado | CI ya existente | 409 Conflict |
| TC-PER-003 | Buscar por CI existente | GET /personas/ci/1001234567 | 200 + datos |
| TC-PER-004 | Buscar por CI inexistente | CI no registrado | 404 |
| TC-PER-005 | CI vacío o nulo | `"ci": ""` | 400 Bad Request |
| TC-PER-006 | Actualizar teléfono | PUT con nuevo teléfono | 200 + datos actualizados |
| TC-PER-007 | Intentar cambiar CI | PUT con nuevo ci | 400 (CI inmutable) |

### TC-ROL — Roles

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-ROL-001 | Crear rol nuevo | nombre único en la compañía | 201 |
| TC-ROL-002 | Nombre rol duplicado | mismo nombre en la misma compañía | 409 |
| TC-ROL-003 | Listar roles (solo los de mi gym) | JWT compañía 1 | Solo roles de compañía 1 |
| TC-ROL-004 | Ver roles de otra compañía | endpoint con id de otra compañía | 403 |
| TC-ROL-005 | Asignar permisos a rol | ids de permisos válidos | 200 |
| TC-ROL-006 | Asignar permiso de otra compañía | id_permiso de compañía diferente | 400 |
| TC-ROL-007 | Eliminar rol sin usuarios | rol vacío | 204 |
| TC-ROL-008 | Eliminar rol con usuarios asignados | rol con N usuarios | 409 + detalle |
| TC-ROL-009 | Sin permiso `roles:crear` | JWT sin ese permiso | 403 |

### TC-USU — Usuarios Staff

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-USU-001 | Crear usuario staff | datos válidos + rol de la compañía | 201 + `requiere_cambio_pwd: true` |
| TC-USU-002 | Email duplicado | correo ya en la compañía | 409 |
| TC-USU-003 | Rol de otra compañía | id_rol no pertenece a la compañía | 400 |
| TC-USU-004 | Ver permisos resueltos | GET /usuarios/{id}/permisos | 200 + lista plana de permisos |
| TC-USU-005 | Desactivar usuario activo | PUT /usuarios/{id}/desactivar | 200 |
| TC-USU-006 | Desactivar único dueño activo | último usuario con rol Dueño | 409 |
| TC-USU-007 | Crear usuario sin permiso | JWT sin `usuarios:crear` | 403 |
| TC-USU-008 | Listar usuarios de otra compañía | query con id_compania diferente | Solo ve los suyos (filtro automático) |

### TC-APP — Usuarios App (clientes)

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-APP-001 | Crear acceso app para persona existente | id_persona válido | 201 + requiere_cambio_pwd=true |
| TC-APP-002 | Persona ya tiene cuenta en este gym | id_persona duplicado mismo gym | 409 |
| TC-APP-003 | Persona no existe | id_persona inválido | 404 |
| TC-APP-004 | Desactivar cuenta app | PUT /app-usuarios/{id}/desactivar | 200 |
| TC-APP-005 | Login con cuenta desactivada | activo=false | 403 |

### TC-LOG — Bitácora

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-LOG-001 | Crear usuario genera log | POST /usuarios exitoso | Registro en bitacora con accion='crear_usuario' |
| TC-LOG-002 | Login exitoso genera log | POST /auth/login exitoso | Registro con accion='login_exitoso' |
| TC-LOG-003 | Consultar log con filtros | ?modulo=seguridad&desde=2026-05-01 | 200 + resultados filtrados |
| TC-LOG-004 | No se puede crear/editar log | POST /bitacora | 405 Method Not Allowed |
| TC-LOG-005 | No se puede ver log de otra compañía | JWT compañía 1 consultando log compañía 2 | 403 |

### TC-SEC — Seguridad transversal

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-SEC-001 | Request sin Authorization header | cualquier endpoint protegido | 401 |
| TC-SEC-002 | JWT con firma inválida | token manipulado | 401 |
| TC-SEC-003 | JWT expirado | token con exp en el pasado | 401 |
| TC-SEC-004 | JWT de compañía A en recurso de compañía B | cross-tenant access | 403 |
| TC-SEC-005 | Inyección SQL en login | `"correo": "' OR '1'='1"` | 400 o 401 (nunca 200) |

---

## 9. Datos semilla (seeds)

> Estos registros deben existir en la BD antes de ejecutar cualquier prueba.

```sql
-- Super admin inicial de la plataforma (password: SuperAdmin2026! en bcrypt)
INSERT INTO saas.usuarios_plataforma (nombre, correo, password_hash, rol) VALUES
  ('Administrador Plataforma', 'admin@gymadministrator.com',
   '$2b$12$xYzHASH_PLACEHOLDER_CHANGE_BEFORE_USE', 'super_admin');

-- Compañía de prueba (insertar en tenant.companias antes)
-- id_compania = 1, id_sucursal = 1

-- Permisos del sistema (creados por el operador de la plataforma)
INSERT INTO seguridad.permisos (id_compania, id_sucursal, nombre, modulo, descripcion) VALUES
  (1, 1, 'clientes:leer',       'clientes',   'Ver listado y ficha de clientes'),
  (1, 1, 'clientes:crear',      'clientes',   'Registrar nuevo cliente'),
  (1, 1, 'clientes:editar',     'clientes',   'Editar datos de cliente'),
  (1, 1, 'membresias:leer',     'membresias', 'Ver membresías'),
  (1, 1, 'membresias:crear',    'membresias', 'Vender membresía'),
  (1, 1, 'asistencia:leer',     'asistencia', 'Ver registro de asistencia'),
  (1, 1, 'asistencia:crear',    'asistencia', 'Registrar asistencia manual'),
  (1, 1, 'finanzas:leer',       'finanzas',   'Ver reportes financieros'),
  (1, 1, 'finanzas:exportar',   'finanzas',   'Exportar reportes'),
  (1, 1, 'usuarios:leer',       'usuarios',   'Ver lista de empleados'),
  (1, 1, 'usuarios:crear',      'usuarios',   'Crear nuevo empleado'),
  (1, 1, 'roles:leer',          'roles',      'Ver roles y permisos'),
  (1, 1, 'roles:crear',         'roles',      'Crear y editar roles');

-- Rol Dueño
INSERT INTO seguridad.roles (id_compania, id_sucursal, nombre, descripcion) VALUES
  (1, 1, 'Dueño', 'Acceso total al sistema');

-- Asignar todos los permisos al rol Dueño
INSERT INTO seguridad.rol_permisos (id_rol, id_permiso)
  SELECT 1, id FROM seguridad.permisos WHERE id_compania = 1;

-- Rol Recepción
INSERT INTO seguridad.roles (id_compania, id_sucursal, nombre, descripcion) VALUES
  (1, 1, 'Recepción', 'Atención al cliente y registro de asistencia');

-- Usuario dueño inicial (password: Admin2026! en bcrypt)
INSERT INTO seguridad.usuarios (id_compania, id_sucursal, id_rol, nombre, correo, password_hash, activo) VALUES
  (1, 1, 1, 'Administrador', 'admin@gym.com',
   '$2b$12$xYzHASH_PLACEHOLDER_CHANGE_BEFORE_USE', true);

-- Persona de prueba
INSERT INTO identidad.personas (ci, nombre, telefono, correo) VALUES
  ('1001234567', 'María López', '0991234567', 'maria@test.com');

-- Usuario app de prueba (password: Test2026! en bcrypt)
INSERT INTO identidad.usuarios_app (id_persona, id_compania, login, password_hash, activo) VALUES
  (1, 1, 'maria@test.com', '$2b$12$xYzHASH_PLACEHOLDER_CHANGE_BEFORE_USE', true);
```

> **Importante:** Reemplaza `$2b$12$xYzHASH_PLACEHOLDER_CHANGE_BEFORE_USE` con hashes bcrypt reales generados con la misma librería que usará el servicio.

---

## 10. Variables de entorno requeridas

```env
# Base de datos
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gym_administrator
DB_USER=gym_user
DB_PASSWORD=***

# JWT
JWT_SECRET=clave-secreta-minimo-256-bits
JWT_EXPIRY_STAFF=28800         # 8 horas en segundos
JWT_EXPIRY_CLIENTE=604800      # 7 días en segundos
JWT_REFRESH_EXPIRY=2592000     # 30 días en segundos

# Seguridad
BCRYPT_ROUNDS=12
MAX_LOGIN_ATTEMPTS=5           # intentos antes de rate limit
LOGIN_LOCKOUT_MINUTES=15

# Recuperación de contraseña
PWD_RESET_TOKEN_EXPIRY_HOURS=1
```

---

## 11. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | Los tres tipos de JWT (`plataforma`, `staff`, `cliente`) nunca son intercambiables | Middleware de autenticación |
| RN-02 | JWT tipo `plataforma` no tiene `id_compania` — su ausencia es lo que indica acceso global | Middleware de tenant |
| RN-02b | JWT tipo `staff` y `cliente` deben tener `id_compania` y este debe coincidir con el recurso | Middleware de tenant |
| RN-03 | Contraseña debe ser bcrypt con factor ≥ 12 | Al crear o cambiar password |
| RN-04 | Login fallido no revela si el email existe o no (misma respuesta 401) | POST /auth/login |
| RN-05 | No se puede eliminar el último usuario activo con rol Dueño | DELETE / desactivar usuario |
| RN-06 | No se puede asignar a un rol un permiso de otra compañía | PUT /roles/{id}/permisos |
| RN-07 | `CI` de persona es inmutable — nunca se permite UPDATE de ese campo | PUT /personas/{id} |
| RN-08 | Una persona puede tener como máximo una cuenta app por compañía | POST /app-usuarios |
| RN-11 | No se puede desactivar al último `super_admin` activo de la plataforma | PUT /platform/usuarios/{id}/desactivar |
| RN-09 | Toda operación de escritura (POST, PUT, DELETE) genera registro en `bitacora_accesos` | Middleware de auditoría |
| RN-10 | El token de recuperación de contraseña expira en 1 hora y es de un solo uso | POST /auth/password/reset |

---

*Auth Service Spec v1.0 · Gym Administrator · Mayo 2026*
