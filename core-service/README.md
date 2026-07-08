# core-service

Microservicio reactivo de gestión de clientes, membresías y control de acceso para la plataforma Gym Administrator SaaS.

- **Puerto:** 8083
- **Stack:** Java 21 · Spring Boot 3.3.5 · WebFlux · R2DBC · PostgreSQL · Redis

---

## Requisitos previos

| Herramienta | Versión mínima |
|---|---|
| Docker + Docker Compose | Docker 24 / Compose v2 |
| Java (solo para desarrollo local) | 21 |
| Maven (solo para desarrollo local) | 3.9 |

---

## Levantar con Docker (recomendado)

Levanta el microservicio junto con PostgreSQL y Redis en contenedores:

```bash
docker compose up --build
```

Para correr en segundo plano:

```bash
docker compose up --build -d
```

Para detener y eliminar los contenedores:

```bash
docker compose down
```

Para detener y también borrar los datos de la base de datos:

```bash
docker compose down -v
```

> La primera vez que se levanta, Docker compila el JAR dentro del contenedor de build. Las siguientes veces usa la caché de capas y es más rápido.

---

## Levantar en local (sin Docker)

Requiere PostgreSQL en `localhost:5432` y Redis en `localhost:6379` ya corriendo.

```bash
# 1. Copiar y ajustar variables de entorno
cp .env.example .env   # si existe, o editar .env directamente

# 2. Compilar
mvn clean package -DskipTests

# 3. Correr
mvn spring-boot:run
```

---

## Variables de entorno

El servicio se configura mediante variables de entorno. Con Docker Compose estas ya vienen configuradas. Para local, se leen del archivo `.env` en la raíz del proyecto.

| Variable | Default | Descripción |
|---|---|---|
| `DB_HOST` | `localhost` | Host de PostgreSQL |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `gym-app-saas` | Nombre de la base de datos |
| `DB_USER` | `administrador` | Usuario de PostgreSQL |
| `DB_PASSWORD` | — | Contraseña de PostgreSQL |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `JWT_SECRET` | — | Clave secreta JWT en Base64 |
| `PLATFORM_SERVICE_URL` | `http://localhost:8081` | URL del servicio de plataforma |
| `CLIENT_STATUS_JOB_CRON` | `0 10 0 * * *` | Cron del job de actualización de estados |
| `CARNET_PREFIX` | `GYM` | Prefijo para códigos de carnet |

---

## Comandos de desarrollo

```bash
# Correr todos los tests de integración
mvn test

# Correr una clase de test específica
mvn test -Dtest=MembresiaIntegrationTest

# Correr un test específico
mvn test -Dtest=MembresiaIntegrationTest#venderMembresiaCalendario

# Compilar sin tests
mvn clean package -DskipTests
```

> Los tests de integración requieren PostgreSQL y Redis corriendo (usan el perfil `test` con las variables del `.env`).

---

## Endpoints principales

Todos los endpoints (excepto `validar-acceso`) requieren header `Authorization: Bearer <token>`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/clientes` | Listar clientes (paginado, filtrable) |
| `POST` | `/api/v1/clientes` | Registrar cliente |
| `GET` | `/api/v1/clientes/{id}` | Detalle de cliente |
| `PUT` | `/api/v1/clientes/{id}` | Actualizar datos físicos |
| `GET` | `/api/v1/clientes/ci/{ci}` | Buscar persona por cédula |
| `GET` | `/api/v1/clientes/{id}/membresias` | Historial de membresías |
| `POST` | `/api/v1/clientes/{id}/membresias` | Vender membresía |
| `GET` | `/api/v1/membresias/{id}` | Detalle de membresía |
| `PUT` | `/api/v1/membresias/{id}/anular` | Anular membresía |
| `GET` | `/api/v1/membresias/validar-acceso` | Validar acceso al gym (público) |
| `POST` | `/api/v1/membresias/{id}/congelar` | Congelar membresía |
| `PUT` | `/api/v1/congelamientos/{id}/reactivar` | Reactivar congelamiento |
| `GET` | `/api/v1/membresias/{id}/congelamientos` | Historial de congelamientos |
| `GET` | `/api/v1/tipos-membresia` | Listar tipos de membresía |
| `POST` | `/api/v1/tipos-membresia` | Crear tipo de membresía |
| `PUT` | `/api/v1/tipos-membresia/{id}` | Actualizar tipo |
| `PUT` | `/api/v1/tipos-membresia/{id}/desactivar` | Desactivar tipo |
