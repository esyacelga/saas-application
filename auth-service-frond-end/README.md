# Gym Admin — Frontend

Panel de administración para gimnasios. Construido con React 19 + TypeScript, arquitectura hexagonal.

---

## Requisitos previos

- **Node.js** v18 o superior
- **npm** v9 o superior
- Backend corriendo en `http://localhost:8080`

---

## Inicio rápido

### 1. Instalar dependencias

```bash
npm install
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env.local
```

Edita `.env.local` con los valores correctos:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_NAME=Gym Admin
```

### 3. Iniciar el servidor de desarrollo

```bash
npm run dev
```

Abre **http://localhost:5173** en el navegador.

---

## Scripts

| Comando | Descripción |
|---|---|
| `npm run dev` | Servidor de desarrollo con hot-reload |
| `npm run build` | Build de producción en `dist/` |
| `npm run preview` | Previsualizar el build de producción |

---

## Estructura del proyecto

```
src/
├── domain/                  # Entidades de negocio e interfaces (puertos)
│   └── auth/
│       ├── entities/        # User.entity.ts, Token.entity.ts
│       └── ports/           # AuthRepository.port.ts, AuthStore.port.ts
│
├── application/             # Casos de uso — orquestación pura
│   └── auth/
│       ├── LoginStaff.usecase.ts
│       ├── LoginPlatform.usecase.ts
│       ├── RefreshToken.usecase.ts
│       └── Logout.usecase.ts
│
├── infrastructure/          # Adaptadores concretos
│   ├── http/
│   │   └── auth/            # AuthHttpRepository.ts + auth.dto.ts
│   └── store/
│       └── auth/            # auth.store.ts (Zustand)
│
├── ui/                      # Capa de presentación React
│   ├── router/              # Router + guards (Auth, Platform, Permission)
│   └── components/          # FullScreenLoader, ConfirmDialog, PageHeader
│
└── lib/                     # Utilidades transversales
    ├── utils.ts             # cn() para clases Tailwind
    ├── jwt.ts               # decodeJwt, isTokenExpired
    └── api-error.ts         # getApiErrorMessage, getApiErrorStatus
```

**Regla de dependencia:** `Domain` ← `Application` ← `Infrastructure` ← `UI`  
Las capas internas nunca importan de las externas.

---

## Stack tecnológico

| Categoría | Librería |
|---|---|
| Framework | React 19 + TypeScript |
| Bundler | Vite 6 |
| Routing | React Router DOM v7 |
| Estado global | Zustand v5 |
| HTTP | Axios v1 |
| Formularios | React Hook Form + Zod |
| UI Components | shadcn/ui |
| Estilos | Tailwind CSS v4 |
| Notificaciones | Sonner |
| Tablas | TanStack Table v8 |
| Iconos | Lucide React |

---

## Documentación de implementación

Los pasos de implementación están en [`documentacion/`](./documentacion/):

| Archivo | Contenido | Estado |
|---|---|---|
| [IMPL_00_BASE_SETUP.md](./documentacion/IMPL_00_BASE_SETUP.md) | Configuración base + arquitectura hexagonal | ✅ |
| [IMPL_01_LOGIN_STAFF.md](./documentacion/IMPL_01_LOGIN_STAFF.md) | Login de staff | ⬜ |
| [IMPL_02_LOGIN_PLATAFORMA.md](./documentacion/IMPL_02_LOGIN_PLATAFORMA.md) | Login de plataforma | ⬜ |
| [IMPL_03_RESET_SOLICITUD.md](./documentacion/IMPL_03_RESET_SOLICITUD.md) | Solicitud de reset de contraseña | ⬜ |
| [IMPL_04_RESET_CONFIRMAR.md](./documentacion/IMPL_04_RESET_CONFIRMAR.md) | Confirmación de reset | ⬜ |
| [IMPL_05_CAMBIO_PASSWORD.md](./documentacion/IMPL_05_CAMBIO_PASSWORD.md) | Cambio de contraseña | ⬜ |
| [IMPL_06_ADMIN_LAYOUT.md](./documentacion/IMPL_06_ADMIN_LAYOUT.md) | Layout del panel admin | ⬜ |
| [IMPL_07_USUARIOS.md](./documentacion/IMPL_07_USUARIOS.md) | Gestión de usuarios | ⬜ |
| [IMPL_08_BITACORA.md](./documentacion/IMPL_08_BITACORA.md) | Bitácora de actividad | ⬜ |
| [IMPL_09_CLIENTES_APP.md](./documentacion/IMPL_09_CLIENTES_APP.md) | Clientes de la app | ⬜ |
| [IMPL_10_ROLES_PERMISOS.md](./documentacion/IMPL_10_ROLES_PERMISOS.md) | Roles y permisos | ⬜ |
| [IMPL_11_PLATAFORMA.md](./documentacion/IMPL_11_PLATAFORMA.md) | Módulo de plataforma | ⬜ |

Para la especificación del API backend ver [`documentacion/FRONTEND_AUTH_SPEC.md`](./documentacion/FRONTEND_AUTH_SPEC.md).

---

## Catálogo de permisos

### Panel Admin (token tipo `staff`)

Los permisos viven en el array `permisos[]` del JWT. El sidebar oculta las entradas si el usuario no tiene el permiso correspondiente.

| Ruta | Permiso requerido | Descripción |
|---|---|---|
| `/admin/dashboard` | — *(solo requiere token `staff`)* | Dashboard principal |
| `/admin/clientes` | `clientes:leer` | Clientes del gimnasio |
| `/admin/tipos-membresia` | `membresias:leer` | Tipos de membresía |
| `/admin/usuarios` | `usuarios:leer` | Gestión de usuarios staff |
| `/admin/roles` | `roles:leer` | Roles y permisos |
| `/admin/clientes/app` | — *(solo requiere token `staff`)* | Cuentas app móvil |
| `/admin/bitacora` | `usuarios:leer` | Bitácora de actividad |

**Resumen de permisos únicos del panel admin:**

| Permiso | Rutas que lo usan |
|---|---|
| `clientes:leer` | `/admin/clientes` |
| `membresias:leer` | `/admin/tipos-membresia` |
| `usuarios:leer` | `/admin/usuarios`, `/admin/bitacora` |
| `roles:leer` | `/admin/roles` |

### Panel Plataforma (token tipo `plataforma`)

Las rutas de plataforma no usan `PermissionGuard` individual. El acceso está controlado únicamente por el campo `rol_plataforma` del JWT y por el backend.

| Ruta | Token requerido |
|---|---|
| `/platform/dashboard` | `plataforma` |
| `/platform/usuarios` | `plataforma` |
| `/platform/roles` | `plataforma` |
| `/platform/planes` | `plataforma` |
| `/platform/caracteristicas` | `plataforma` |
| `/platform/companias` | `plataforma` |
| `/platform/companias/:id` | `plataforma` |

Los roles de plataforma disponibles son: `super_admin` \| `soporte` \| `viewer`. El control fino de qué acciones puede hacer cada rol se gestiona en el backend.
