# Auth Service

> Punto único de autenticación y autorización para la plataforma SaaS **Gym Administrator**.

**Stack:** Spring Boot 3.3.5 · Java 21 · WebFlux (reactivo) · R2DBC · PostgreSQL  
**Puerto:** `8080`  
**Esquemas BD:** `saas` · `identidad` · `seguridad` · `tenant`

---

## ¿Qué hace este servicio?

El Auth Service es la **única puerta de entrada para autenticación y autorización** en toda la plataforma. Ningún otro microservicio valida identidades — todos confían en el JWT emitido por este servicio.

Sus responsabilidades principales son:

- Autenticar tres tipos de usuario independientes: operadores de plataforma, empleados del gym (staff) y clientes de la app móvil.
- Emitir tokens JWT firmados con los permisos resueltos embebidos, para que los servicios downstream no necesiten consultar la BD.
- Gestionar roles y permisos RBAC por compañía (`modulo:accion`).
- Mantener la identidad centralizada mediante la entidad `Persona` — todos los tipos de usuario la referencian.
- Registrar en bitácora cada operación de escritura (POST / PUT / DELETE).
- Soportar login OAuth (Google, Facebook) para clientes de la app.
- Gestionar restablecimiento de contraseñas con tokens de un solo uso.

---

## Inicio rápido

### Opción A — Docker (recomendada)

No requiere Java ni Maven. Levanta PostgreSQL + auth-service en un solo comando.

```bash
# 1. Copiar el archivo de entorno y completar los valores
cp .env.example .env

# 2. Construir y levantar (primera vez o tras cambiar código)
docker compose up -d --build

# 3. Verificar que está corriendo
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Variables obligatorias en `.env`:

| Variable | Descripción |
|---|---|
| `DB_USER` / `DB_PASSWORD` / `DB_NAME` | Credenciales PostgreSQL |
| `JWT_SECRET` | Clave de firma HS256 (mín. 256 bits en base64) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Cuenta Gmail para envío de emails |
| `FRONTEND_URL` | URL del frontend (para links en emails de reset) |

Generar `JWT_SECRET`:
```bash
openssl rand -base64 32
```

### Opción B — Maven local

Requiere Java 21 y Maven 3.9+.

**Linux / macOS:**
```bash
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

**Windows (PowerShell):**
```powershell
Get-Content .env | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } |
  ForEach-Object {
    $p = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($p[0].Trim(), $p[1].Trim(), 'Process')
  }
mvn spring-boot:run
```

---

## Comandos

```bash
# Build
mvn clean package
mvn clean package -DskipTests

# Tests de integración (Testcontainers — requiere Docker)
mvn test
mvn test -Dtest=AuthIT       # clase específica

# Docker
docker compose up -d --build     # (re)construir y levantar
docker compose logs -f auth-service
docker compose down -v           # detener y borrar volúmenes
docker compose exec postgres psql -U ${DB_USER} -d ${DB_NAME}
```

---

## Arquitectura

El servicio sigue **Arquitectura Hexagonal (Puertos y Adaptadores)**:

```
HTTP Request
    │
    ▼
infrastructure/adapter/in/web/
  ApiRouter          ← define todas las rutas (RouterFunction, sin @Controller)
  *Handler           ← un handler por dominio
    │
    │  domain/port/in/  (interfaces de casos de uso)
    ▼
application/service/
  *ApplicationService  ← implementa los casos de uso
    │
    │  domain/port/out/  (interfaces de repositorio)
    ▼
infrastructure/adapter/out/persistence/
  *PersistenceAdapter  ← R2DBC + DatabaseClient
```

El dominio (`domain/`) no tiene dependencias de infraestructura. Al agregar una funcionalidad se tocan las cuatro capas: puerto de dominio → servicio de aplicación → adaptador de persistencia → handler + router.

### Pipeline de seguridad

```
Request → JwtAuthWebFilter (extrae Bearer, construye UserPrincipal)
        → SecurityConfig (stateless, reglas por ruta)
        → ApiRouter → Handler → ApplicationService
```

`UserPrincipal` lleva `tipo`, `idCompania` y `permisos[]` para que los servicios puedan aplicar lógica multi-tenant sin queries adicionales.

---

## Tres jerarquías de usuario

Cada jerarquía tiene su propia tabla, token JWT y acceso. Los tokens **nunca son intercambiables**.

| Nivel | Tabla | JWT `tipo` | Claims clave |
|---|---|---|---|
| Operador de plataforma | `saas.usuarios_plataforma` | `"plataforma"` | `rolPlataforma` — **sin** `idCompania` |
| Staff del gym | `seguridad.usuarios` | `"staff"` | `idCompania`, `idSucursal`, `idRol`, `permisos[]` |
| Cliente (app móvil) | `identidad.usuarios_app` | `"cliente"` | `idCompania`, `idPersona` |

La ausencia de `idCompania` en un token de plataforma indica acceso global (cross-company).

### Identidad centralizada

Todos los tipos de usuario referencian `identidad.personas` mediante `idPersona`. El `nombre` y `fotoUrl` nunca se almacenan en las tablas de usuario — siempre se obtienen mediante JOIN. Antes de crear cualquier usuario debe existir la `Persona`.

---

## Diseño del JWT

Todos los tokens se firman con HS256 usando `JWT_SECRET`.

**Token plataforma:**
```json
{
  "sub": "1",
  "tipo": "plataforma",
  "rolPlataforma": "super_admin",
  "nombre": "Santiago Yacelga",
  "iat": 1716000000,
  "exp": 1716086400
}
```

**Token staff:**
```json
{
  "sub": "42",
  "tipo": "staff",
  "idCompania": 1,
  "idSucursal": 2,
  "idRol": 3,
  "nombre": "Juan Pérez",
  "permisos": ["clientes:leer", "clientes:crear", "membresias:leer"],
  "iat": 1716000000,
  "exp": 1716086400
}
```

**Token cliente:**
```json
{
  "sub": "15",
  "tipo": "cliente",
  "idCompania": 1,
  "idPersona": 10,
  "nombre": "María López",
  "iat": 1716000000,
  "exp": 1716432000
}
```

| Token | Duración por defecto |
|---|---|
| Access token (plataforma / staff) | 8 horas |
| Access token (cliente) | 7 días |
| Refresh token | 30 días (un solo uso) |
| Token reset de contraseña | 1 hora (un solo uso) |

---

## RBAC — Permisos

Los permisos siguen el patrón `modulo:accion` (ej. `socios:leer`, `pagos:escribir`). El JWT de staff embebe la lista completa de permisos resueltos al momento del login.

Los servicios de aplicación usan helpers de `SecurityUtils`:
- `requirePlataforma(principal)` — solo operadores de plataforma
- `requireStaff(principal)` — solo staff
- `requirePermiso(principal, "modulo:accion")` — permiso granular

---

## Prefijos de ruta

| Prefijo | Acceso |
|---|---|
| `/api/v1/auth/*` | Público |
| `/api/v1/personas/*` | Público (algunas rutas requieren token) |
| `/api/v1/platform/*` | `tipo=plataforma` |
| `/api/v1/app-usuarios/*` | `tipo=staff` |
| `/api/v1/usuarios`, `/roles`, `/permisos`, `/bitacora` | `tipo=staff` + permisos RBAC |
| `/actuator/health` | Público |

---

## Documentación de endpoints

| Módulo | Archivo |
|---|---|
| Autenticación (login, refresh, OAuth, registro, reset pwd, QR) | [docs/AUTH_API.md](docs/AUTH_API.md) |
| Personas | [docs/PERSONAS_API.md](docs/PERSONAS_API.md) |
| Usuarios Staff, Roles y Permisos | [docs/USUARIOS_STAFF_API.md](docs/USUARIOS_STAFF_API.md) |
| Usuarios App (clientes) | [docs/APP_USUARIOS_API.md](docs/APP_USUARIOS_API.md) |
| Plataforma (usuarios, roles, permisos, compañías) | [docs/PLATFORM_API.md](docs/PLATFORM_API.md) |
| Bitácora | [docs/BITACORA_API.md](docs/BITACORA_API.md) |

---

## Reglas de negocio críticas

| # | Regla |
|---|---|
| RN-01 | Los tres tipos de JWT (`plataforma`, `staff`, `cliente`) nunca son intercambiables |
| RN-02 | Token plataforma sin `idCompania` → acceso global; staff y cliente deben tenerlo y debe coincidir con el recurso |
| RN-03 | Contraseñas en bcrypt con factor mínimo 12 |
| RN-04 | Login fallido devuelve 401 genérico — no se revela si el email existe |
| RN-05 | No se puede eliminar ni desactivar al último `super_admin` activo |
| RN-06 | No se puede desactivar al último usuario con rol Dueño en una compañía |
| RN-07 | La `CI` de `Persona` es inmutable después de la creación |
| RN-08 | Una persona puede tener como máximo una cuenta app por compañía (`UNIQUE (idPersona, idCompania)`) |
| RN-09 | Una persona puede tener como máximo una cuenta staff por compañía (`UNIQUE (idPersona, idCompania)`) |
| RN-10 | Los permisos asignados a un rol deben pertenecer a la misma compañía que el rol |
| RN-11 | No se puede eliminar un rol si tiene usuarios asignados |
| RN-12 | Toda operación de escritura genera un registro en `seguridad.bitacora_accesos` |
| RN-13 | Los refresh tokens son de un solo uso — el anterior se invalida en cada renovación |

---

## Variables de entorno

```env
# Base de datos
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gym-app-saas
DB_USER=administrador
DB_PASSWORD=***

# JWT
JWT_SECRET=clave-base64-minimo-256-bits
JWT_EXPIRY_STAFF=28800       # 8 horas en segundos
JWT_EXPIRY_CLIENTE=604800    # 7 días en segundos
JWT_EXPIRY_REFRESH=2592000   # 30 días en segundos

# Seguridad
BCRYPT_ROUNDS=12
MAX_LOGIN_ATTEMPTS=5
LOCKOUT_DURATION_MINUTES=15
PASSWORD_RESET_EXPIRY_MINUTES=60

# Email
MAIL_USERNAME=app@gmail.com
MAIL_PASSWORD=xxxx xxxx xxxx xxxx
FRONTEND_URL=http://localhost:5173

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:5173
CORS_ALLOW_ALL=false
```

---

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Connection refused` en el healthcheck | El servicio tarda ~40 s en arrancar | Esperar y revisar `docker compose logs -f auth-service` |
| `password authentication failed` | `DB_USER` / `DB_PASSWORD` incorrectos | Verificar `.env` y hacer `docker compose down -v && docker compose up -d --build` |
| `Could not resolve placeholder 'DB_USER'` | `.env` no existe | Ejecutar `cp .env.example .env` y completar los valores |
| Puerto 8080 ocupado | Otro proceso usa el puerto | Cambiar `PORT=8081` en `.env` |
