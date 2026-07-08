# Platform Service

Microservicio de gestión de empresas (gimnasios), suscripciones, sucursales y módulos para la plataforma **GymAdmin SaaS**.

- **Framework:** Spring Boot 3.3.5 · WebFlux (reactivo)
- **Base de datos:** PostgreSQL vía R2DBC
- **Caché:** Redis
- **Puerto:** `8081`
- **Java:** 21

---

## Requisitos previos

| Herramienta | Versión mínima |
|---|---|
| Docker | 24+ |
| Docker Compose | 2.20+ (plugin integrado en Docker Desktop) |
| Java (solo ejecución local) | 21 |
| Maven (solo ejecución local) | 3.9+ |

---

## Variables de entorno

Copia `.env.example` a `.env` y ajusta los valores:

```bash
cp .env.example .env
```

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_HOST` | Host de PostgreSQL | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base de datos | `gym_administrator` |
| `DB_USER` | Usuario de PostgreSQL | `gym_user` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `gym_pass` |
| `REDIS_HOST` | Host de Redis | `localhost` |
| `REDIS_PORT` | Puerto de Redis | `6379` |
| `JWT_SECRET` | Clave JWT en Base64 (≥ 256 bits) | valor de ejemplo inseguro |
| `MODULE_CHECK_CACHE_TTL_SECONDS` | TTL del caché de módulos (segundos) | `300` |
| `QR_TOKEN_LENGTH` | Longitud del token QR | `32` |
| `SUBSCRIPTION_JOB_CRON` | Expresión cron del job de suscripciones | `0 5 0 * * *` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos por CORS | `http://localhost:5173` |
| `CORS_ALLOW_ALL` | Permitir todos los orígenes (desarrollo) | `true` |

> **Nota de seguridad:** Cambia `JWT_SECRET` en producción. Debe ser una cadena Base64 que al decodificar produzca al menos 32 bytes (256 bits).

---

## Opción 1 — Docker Compose (recomendado)

Levanta el servicio junto con PostgreSQL y Redis con un solo comando.

### Iniciar

```bash
docker compose up -d
```

El stack arranca en el siguiente orden: **PostgreSQL → Redis → platform-service**.
Espera los healthchecks antes de lanzar el servicio (~20 s en el primer arranque).

### Ver logs en tiempo real

```bash
docker compose logs -f platform-service
```

### Detener sin borrar datos

```bash
docker compose down
```

### Detener y borrar volúmenes (borra la base de datos)

```bash
docker compose down -v
```

### Reconstruir la imagen tras cambios en el código

```bash
docker compose build platform-service
docker compose up -d
```

---

## Opción 2 — Solo Docker (PostgreSQL y Redis externos)

Usa esta opción si ya tienes PostgreSQL y Redis corriendo en tu máquina o en otro servidor.

### Construir la imagen

```bash
docker build -t platform-service:latest .
```

### Ejecutar el contenedor

```bash
docker run -d \
  --name platform-service \
  -p 8081:8081 \
  --env-file .env \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  platform-service:latest
```

> `host.docker.internal` resuelve al host del Docker en Mac y Windows.
> En Linux usa `--network host` o la IP del host (`172.17.0.1` por defecto).

### Ver logs

```bash
docker logs -f platform-service
```

### Detener y eliminar el contenedor

```bash
docker stop platform-service && docker rm platform-service
```

---

## Opción 3 — Ejecución local (sin Docker)

Requiere tener PostgreSQL y Redis corriendo localmente.

### Compilar

```bash
mvn clean package -DskipTests
```

### Ejecutar

```bash
java -jar target/platform-service-new.jar
```

O con variables de entorno explícitas:

```bash
DB_HOST=localhost \
DB_PASSWORD=gym_pass \
JWT_SECRET=<tu-secret> \
java -jar target/platform-service-new.jar
```

### Ejecutar en modo desarrollo (recarga el contexto de Spring)

```bash
mvn spring-boot:run
```

---

## Verificar que el servicio está activo

El servicio no expone un endpoint `/health`, pero cualquier ruta protegida responde con `401` cuando está en pie:

```bash
curl -o /dev/null -s -w "%{http_code}\n" http://localhost:8081/api/v1/companias
# Respuesta esperada: 401
```

---

## Esquema de base de datos

El servicio **no ejecuta migraciones automáticas** (sin Flyway/Liquibase).
El esquema debe existir antes del primer arranque. Los esquemas requeridos son:

- `tenant` — companias, sucursales, suscripciones, pagos, notificaciones
- `saas` — planes, características
- `seguridad` — usuarios, roles, permisos, bitácora
- `identidad` — personas

Aplica las migraciones Liquibase de `gym-administrator/db/` (mismo monorepo) antes de iniciar el servicio.

---

## Tests de integración

Los tests de integración arrancan un contexto de Spring Boot completo y requieren
una base de datos PostgreSQL accesible. Las credenciales se leen del archivo `.env`
en la raíz del proyecto.

```bash
mvn test
```

> **Nota:** La primera ejecución puede tardar más de 30 s mientras el pool de
> conexiones R2DBC se calienta.
