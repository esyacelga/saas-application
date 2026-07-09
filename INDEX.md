# Gym Administrator — Índice de Microservicios

Plataforma SaaS multi-tenant para administración de gimnasios. Arquitectura de microservicios con backends reactivos en Java/Spring Boot y frontends en React.

---

## Mapa de servicios

```
PostgreSQL 16 (5432)
Redis 7 (6379)
        │
        ├─▶ auth-service (8080)       ← autenticación, JWT, usuarios, roles
        ├─▶ platform-service (8081)   ← empresas, suscripciones, planes
        │         │
        │         ▼
        └─▶ core-service (8083)       ← clientes, membresías, acceso
                  │
                  ▼
            attendance-service (8084) ← asistencias, mensajería
                  │
        ┌─────────┴──────────┐
        ▼                    ▼
  admin-frontend (5173)  member-pwa (5174)
  Panel admin/staff      App móvil PWA miembros
  nginx sirve React      nginx sirve React PWA
```

---

## Índice de documentación

### Infraestructura y despliegue

| Documento | Descripción |
|-----------|-------------|
| [DOCKER.md](DOCKER.md) | Guía completa para levantar/detener el stack con Docker Compose |
| [docker-compose.yml](docker-compose.yml) | Configuración de todos los servicios, puertos y variables de entorno |
| [docs/gym-administrator/architecture/overview.md](docs/gym-administrator/architecture/overview.md) | Arquitectura general, modelo de negocio y decisiones de diseño |
| [docs/gym-administrator/architecture/database-schema.md](docs/gym-administrator/architecture/database-schema.md) | Esquema completo de la base de datos (42 tablas, 10 schemas) |
| [docs/gym-administrator/architecture/roadmap.md](docs/gym-administrator/architecture/roadmap.md) | Orden de construcción de microservicios y roadmap |

---

### Requerimientos activos

| ID | Documento | Estado |
|----|-----------|--------|
| REQ-SAAS-001 | [Nuevo esquema de planes SaaS Freemium (Free / Trial / Premium)](docs/gym-administrator/requirements/planes-saas-freemium.md) | Aprobado v1.0 — pendiente de descomponer en tickets |

---

### Backend — Microservicios Java (Spring Boot 3.3.5 / WebFlux / Java 21)

#### auth-service — Puerto 8080
Punto único de autenticación y autorización para toda la plataforma.

| Documento | Descripción |
|-----------|-------------|
| [auth-service/README.md](auth-service/README.md) | Documentación completa: arquitectura, endpoints, JWT, seguridad, testing |
| [auth-service/CLAUDE.md](auth-service/CLAUDE.md) | Guía de desarrollo y convenciones del servicio |
| [docs/auth-service/INDEX.md](docs/auth-service/INDEX.md) | Índice de documentación de API (auth, personas, usuarios-staff, app-usuarios, platform, bitácora) |

**Responsabilidades clave:**
- 3 flujos de login independientes: operadores de plataforma, staff de gym, clientes app
- Generación y validación de JWT (access + refresh tokens)
- RBAC: roles y permisos por compañía
- Recuperación de contraseña (tokens de 1 hora)
- Rate limiting en login (5 intentos, bloqueo 15 min)
- Registro de auditoría (`bitacora_accesos`)

**Schemas de BD:** `saas`, `identidad`, `seguridad`, `tenant`

---

#### platform-service — Puerto 8081
Gestión del SaaS: empresas, sucursales, planes y suscripciones.

| Documento | Descripción |
|-----------|-------------|
| [platform-service/README.md](platform-service/README.md) | Documentación completa: arquitectura, endpoints, seguridad |
| [platform-service/CLAUDE.md](platform-service/CLAUDE.md) | Guía de desarrollo, patrones y convenciones |
| [docs/platform-service/INDEX.md](docs/platform-service/INDEX.md) | Índice de documentación centralizada |

**Responsabilidades clave:**
- CRUD de empresas (tenants) y sucursales
- Ciclo de vida de suscripciones (planes Básico / Premium / Enterprise)
- Registro de pagos
- Verificación de módulos habilitados por plan (con caché Redis, TTL 300s)
- Generación de tokens QR para acceso al gym (32 caracteres, por sucursal)
- Job diario de renovación de suscripciones (cron 00:05 UTC)
- Subida de logos a Cloudinary

**Schemas de BD:** `tenant`, `saas`

---

#### core-service — Puerto 8083
Gestión de clientes y membresías; validación de acceso al gym.

| Documento | Descripción |
|-----------|-------------|
| [core-service/README.md](core-service/README.md) | Documentación completa: arquitectura, endpoints, modos de membresía |
| [core-service/CLAUDE.md](core-service/CLAUDE.md) | Guía de desarrollo y convenciones del servicio |
| [docs/core-service/INDEX.md](docs/core-service/INDEX.md) | Índice de documentación de API (clientes) |

**Responsabilidades clave:**
- Registro y gestión de clientes (miembros del gym)
- Tipos de membresía y ventas
- Dos modos de membresía:
  - **Calendario**: duración fija desde fecha de inicio
  - **Accesos**: visitas diarias limitadas con fecha de expiración
- Congelamiento/descongelamiento retroactivo de membresías
- Endpoint público de validación de acceso QR (`GET /membresias/validar-acceso`)
- Job diario de actualización de estado de clientes (cron 00:10 UTC)
- Caché Redis para consultas frecuentes

**Schemas de BD:** `core` (clientes, membresías, congelamientos)

---

#### attendance-service — Puerto 8084
Registro de asistencias y mensajería automatizada.

| Documento | Descripción |
|-----------|-------------|
| [attendance-service/README.md](attendance-service/README.md) | Documentación completa: arquitectura, endpoints, plantillas, mensajería |
| [attendance-service/CLAUDE.md](attendance-service/CLAUDE.md) | Guía de desarrollo y convenciones del servicio |
| [docs/attendance-service/INDEX.md](docs/attendance-service/INDEX.md) | Índice de documentación centralizada |

**Responsabilidades clave:**
- 3 métodos de registro de asistencia:
  - **QR auto** (`POST /api/v1/asistencias/check`): check-in público desde app
  - **Manual staff** (`POST /api/v1/asistencias/manual`): registro por personal
  - **Override owner** (`POST /api/v1/asistencias/manual/override`): sin validaciones
- CRUD de plantillas de mensajes (ausencia, recuperación, expiración)
- Job diario de envío de mensajes (cron 00:15 UTC)
- Zona horaria: America/Guayaquil (UTC-5)

**Schemas de BD:** `asistencia` (asistencias, plantillas, mensajes)  
**Dependencias runtime:** core-service (valida membresías), auth-service (resuelve tokens QR)

---

### Frontend — Aplicaciones React (React 19 / TypeScript / Vite / Tailwind CSS 4)

#### auth-service-frond-end — Puerto 5173
Panel de administración y staff (web).

| Documento | Descripción |
|-----------|-------------|
| [auth-service-frond-end/README.md](auth-service-frond-end/README.md) | Stack, catálogo de permisos, inicio rápido |
| [auth-service-frond-end/CLAUDE.md](auth-service-frond-end/CLAUDE.md) | Arquitectura, patrones de código, JWT, guards |
| [docs/auth-service-frond-end/INDEX.md](docs/auth-service-frond-end/INDEX.md) | Índice completo: 17 docs de implementación por módulo, prompts de backend, design guidelines |
| [docs/gym-administrator/frontend/auth-frontend-spec.md](docs/gym-administrator/frontend/auth-frontend-spec.md) | Especificación funcional del frontend |
| [docs/gym-administrator/frontend/auth-frontend-impl.md](docs/gym-administrator/frontend/auth-frontend-impl.md) | Guía de implementación (incluye sistema de diseño y tokens) |

**Stack:** React 19, TypeScript 5.7, Vite 6, Tailwind CSS 4, React Router 7, Zustand 5, React Hook Form + Zod, PrimeReact, shadcn/ui, Axios, i18next

**Arquitectura:** Hexagonal estricta (Domain → Application → Infrastructure → UI)

**Grupos de rutas:**
- `PublicLayout`: `/`, `/login`, `/reset-password`
- `AdminLayout` (`/admin/*`): panel staff, 11+ rutas con guards de permisos
- `PlatformLayout` (`/platform/*`): operadores de plataforma, 8+ rutas

**Guards:** `AuthGuard` (staff), `PlatformGuard` (operadores), `PermissionGuard` (por ruta), `IfPermission` (condicional inline)

**Backends consumidos:** auth-service (8080), platform-service (8081), core-service (8083), attendance-service (8084)

---

#### gym-member-pwa — Puerto 5174
App móvil Progressive Web App para miembros del gym.

| Documento | Descripción |
|-----------|-------------|
| [gym-member-pwa/CLAUDE.md](gym-member-pwa/CLAUDE.md) | Guía completa: arquitectura, flujos, patrones, convenciones |
| [docs/gym-member-pwa/INDEX.md](docs/gym-member-pwa/INDEX.md) | Índice de documentación centralizada y backlog de pendientes |

**Stack:** React 19, TypeScript 6.0, Vite, Tailwind CSS 4, Zustand 5, React Hook Form + Zod, html5-qrcode, vite-plugin-pwa

**Flujos principales:**
- **Login vía QR**: `?qr=<token>` → obtiene info del gym → login → check-in automático
- **Check-in QR**: escanea código → registra asistencia instantánea
- **Check-in App**: botón en home cuando hay `gymInfo` activo
- **Historial**: heatmap de 5 semanas + lista de últimas 20 asistencias

**Estado (Zustand + localStorage):**
- `auth.store.ts`: accessToken, refreshToken, usuario (`ClienteToken`), gymInfo
- `theme.store.ts`: 6 temas (acero, volcan, bosque, coral, violeta, aurora)

**JWT `ClienteToken`:** `sub` (cuenta auth), `id_persona`, `id_compania`, `sexo`, `nombre`, `nombre_compania`, `logo_url`, `foto_url`

**Backends consumidos:** auth-service (8080), core-service (8083), attendance-service (8084)

---

### Base de datos y migraciones

#### gym-administrator
Migraciones Liquibase (`gym-administrator/db/`); su documentación de arquitectura, specs y frontend se centralizó en `docs/gym-administrator/`.

**Ver [docs/gym-administrator/INDEX.md](docs/gym-administrator/INDEX.md)** para el índice completo (arquitectura, specs por servicio, frontend, infra). Documentos clave:

| Documento | Descripción |
|-----------|-------------|
| [docs/gym-administrator/architecture/overview.md](docs/gym-administrator/architecture/overview.md) | Arquitectura general y modelo de negocio |
| [docs/gym-administrator/architecture/database-schema.md](docs/gym-administrator/architecture/database-schema.md) | Esquema completo de BD con diagramas ASCII |
| [docs/gym-administrator/architecture/roadmap.md](docs/gym-administrator/architecture/roadmap.md) | Roadmap y orden de desarrollo |
| [docs/gym-administrator/specs/](docs/gym-administrator/specs/) | Especificaciones por microservicio (auth, platform, core, attendance, finance, marketing, inventory, billing) |
| [docs/gym-administrator/frontend/](docs/gym-administrator/frontend/) | Especificaciones de los módulos frontend (auth, platform) |
| [docs/gym-administrator/infra/docker.md](docs/gym-administrator/infra/docker.md) | Guía Docker específica de gym-administrator |

**Schemas de PostgreSQL (42 tablas totales):**

| Schema | Alcance | Contenido |
|--------|---------|-----------|
| `saas` | Global | Planes, features, operadores de plataforma |
| `identidad` | Global | Personas, usuarios app, refresh tokens |
| `tenant` | Global | Empresas, sucursales, suscripciones, pagos |
| `seguridad` | Por empresa | Staff, roles, permisos, auditoría |
| `core` | Por empresa | Clientes, membresías, congelamientos |
| `asistencia` | Por empresa | Asistencias, plantillas, mensajes |
| `config` | Por empresa | Configuración clave-valor del gym |
| `finanzas` | Premium | Ingresos, gastos (futuro) |
| `marketing` | Premium | Promociones, fidelización (futuro) |
| `inventario` | Premium | Productos, stock, ventas (futuro) |

**Convenciones de migración:**
- Carpeta por historia: `db/scripts/YYYYMM_GYM-XXX/`
- ID de changeset: `GYM-XXX-1`, `GYM-XXX-2`, ... (únicos globalmente)
- Scripts DDL numerados: `01_`, `02_`, ...
- Changelog principal: `db/scripts/main-changelog.yml`

---

## Variables de entorno compartidas

| Variable | Servicios | Descripción |
|----------|-----------|-------------|
| `JWT_SECRET` | Todos los backends | Clave Base64 ≥256 bits para firmar JWT |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Todos los backends | Conexión a PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT` | platform-service, core-service | Conexión a Redis |
| `CORE_SERVICE_URL` | attendance-service | URL interna del core-service |
| `AUTH_SERVICE_URL` | attendance-service | URL interna del auth-service |
| `PLATFORM_SERVICE_URL` | core-service | URL interna del platform-service |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | auth-service, platform-service | Almacenamiento de imágenes |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | auth-service | SMTP Gmail para recuperación de contraseña |

---

## Planes de suscripción

### Esquema aprobado (REQ-SAAS-001 — en implementación)

| Plan | Precio | Duración | Módulos | Notas |
|------|--------|----------|---------|-------|
| **Free** | $0 | Permanente | Clientes, membresías, asistencia, mensajería, seguridad, config | Hard limits: 1 sucursal / 50 clientes activos / 2 staff |
| **Trial** | $0 | 2 meses | Todos (equivalente a Premium) | Único por tenant, irrevocable, degrada a Free al vencer |
| **Premium** | $29.99/mes | 1 mes (prepago) | Free + Finanzas + Marketing + Inventario | Sin renovación automática; degrada a Free al vencer |

Ver [REQ-SAAS-001](docs/gym-administrator/requirements/planes-saas-freemium.md) para reglas de negocio completas.

### Esquema legacy (en migración)

| Plan | Módulos |
|------|---------|
| **Básico** | Clientes, membresías, asistencias, acceso QR |
| **Premium (legacy)** | Básico + Finanzas + Marketing |
| **Enterprise** | Premium + Inventario + configuración avanzada |

> Tenants existentes en planes legacy reciben Premium nuevo hasta que venza su ciclo actual. Ver sección 11.3 del requerimiento.

---

## Comandos rápidos de despliegue

```powershell
# Levantar todo el stack (primera vez o con cambios)
docker-compose up -d --build

# Levantar sin rebuild
docker-compose up -d

# Ver logs en tiempo real
docker-compose logs -f

# Verificar salud de un servicio
docker-compose ps auth-service

# Reconstruir solo un servicio
docker-compose up -d --build core-service

# Resetear todo (borra datos de BD)
docker-compose down -v && docker-compose up -d --build
```

Health checks disponibles en:
- `http://localhost:8080/actuator/health`
- `http://localhost:8081/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8084/actuator/health`
