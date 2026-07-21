# Attendance Service

Microservicio reactivo de control de asistencia para la plataforma SaaS **GymAdmin**. Registra entradas de socios por código QR, ingreso manual de staff, y override del dueño.

- **Puerto:** `8084`
- **Stack:** Java 21 · Spring Boot 3.3.5 · WebFlux · Spring Data R2DBC · PostgreSQL

---

## Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_HOST` | Host de PostgreSQL | `postgres` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base de datos | `gym_administrator` |
| `DB_USER` | Usuario de la base de datos | `gym_user` |
| `DB_PASSWORD` | Contraseña de la base de datos | `gym_pass` |
| `JWT_SECRET` | Clave HMAC en Base64 | `cGxhdGZvcm1...` |
| `CORE_SERVICE_URL` | URL base del Core Service | `http://core-service:8082` |
| `MESSAGING_JOB_CRON` | Cron del job de mensajería | `0 15 0 * * *` |
| `CORS_ALLOW_ALL` | Permitir todos los orígenes CORS | `false` |
| `CORS_ORIGIN_1` | Origen CORS permitido 1 | `http://localhost:5173` |
| `CORS_ORIGIN_2` | Origen CORS permitido 2 | `http://localhost:3000` |

---

## Ejecutar con Docker

### 1. Construir la imagen

```bash
docker build -t attendance-service:latest .
```

### 2. Ejecutar el contenedor

```bash
docker run -d \
  --name attendance-service \
  -p 8084:8084 \
  -e DB_HOST=<host> \
  -e DB_PORT=5432 \
  -e DB_NAME=gym_administrator \
  -e DB_USER=gym_user \
  -e DB_PASSWORD=<password> \
  -e JWT_SECRET=<base64-secret> \
  -e CORE_SERVICE_URL=http://<core-host>:8082 \
  -e MESSAGING_JOB_CRON="0 15 0 * * *" \
  attendance-service:latest
```

### 3. Verificar que está corriendo

```bash
curl http://localhost:8084/actuator/health
# {"status":"UP"}
```

---

## Ejecutar con Docker Compose

Crea un archivo `docker-compose.yml` junto a este proyecto:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: gym_administrator
      POSTGRES_USER: gym_user
      POSTGRES_PASSWORD: gym_pass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  attendance-service:
    build: .
    ports:
      - "8084:8084"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: gym_administrator
      DB_USER: gym_user
      DB_PASSWORD: gym_pass
      JWT_SECRET: cGxhdGZvcm1TZWNyZXRLZXlGb3JHeW1BZG1pbmlzdHJhdG9yMjAyNg==
      CORE_SERVICE_URL: http://core-service:8082
      MESSAGING_JOB_CRON: "0 15 0 * * *"
    depends_on:
      - postgres

volumes:
  pgdata:
```

```bash
docker compose up -d
```

---

## Arquitectura

Arquitectura hexagonal (Ports & Adapters):

```
com.gymadmin.attendance/
├── domain/
│   ├── model/          # Asistencia, PlantillaMensaje, MensajeLog
│   └── port/
│       ├── in/         # Interfaces de casos de uso (puertos de entrada)
│       └── out/        # Interfaces de repositorio (puertos de salida)
├── application/
│   └── service/        # Implementa los casos de uso
└── infrastructure/
    ├── adapter/
    │   ├── in/web/     # Controladores REST (WebFlux RouterFunction)
    │   └── out/        # Adaptadores R2DBC, CoreServiceClient
    ├── config/         # SecurityConfig, JwtConfig, CorsConfig, AppProperties
    ├── exception/      # GlobalExceptionHandler + excepciones personalizadas
    └── scheduler/      # MensajeriaJob (cron diario 00:15 Guayaquil) — avisos WhatsApp de vencimiento
```

### Dominios principales

- **Asistencia** — Tres métodos de entrada: QR (auto-check-in del cliente), manual (staff), override (dueño, sin validación de membresía).
- **PlantillaMensaje** — CRUD de plantillas de mensajes automatizados.
- **MensajeLog** — Historial de mensajes enviados por el job de mensajería.

### Dependencia externa

`CoreServiceClient` llama al **Core Service** (`CORE_SERVICE_URL`) para validar membresías y códigos QR. Los flujos QR/app usan `validarAcceso(idPersona, idCompania, token)` → `GET /api/v1/membresias/validar-acceso` (con `id_persona` del JWT). El flujo **manual** usa `validarAccesoPorCliente(idCliente, idCompania)` → `GET /api/v1/membresias/validar-acceso-cliente` (con `id_cliente`, ya que el heatmap admin solo conoce ese PK; ambos endpoints de core son públicos). El endpoint de override (`POST /api/v1/asistencias/manual/override`) omite esta validación. Además (REQ-SAAS-001 Fase 5) consume el endpoint interno `GET /internal/v1/companias/{id}/clientes-por-vencer` (header `X-Internal-Call: {INTERNAL_SECRET}`) para alimentar el `MensajeriaJob`.

### MensajeriaJob — avisos de vencimiento por WhatsApp (REQ-SAAS-001 Fase 5)

El job diario (cron `${MESSAGING_JOB_CRON:0 15 0 * * *}`, hora Guayaquil) avisa por WhatsApp a los socios cuya **membresía** está por vencer, con buckets **{3, 0}** (aviso previo a 3 días + día del vencimiento):

- Consume `clientes-por-vencer` de core (no duplica la detección de vencimiento).
- Envía **plantillas HSM pre-aprobadas** vía `WhatsAppSender`/`MetaWhatsAppAdapter` (Meta Cloud API): `recordatorio_vencimiento_membresia` (calendario; antes `venc_membresia_previo`) y `recordatorio_vencimiento_accesos` (accesos; antes `venc_accesos_previo`) — las de día 0 (`venc_membresia_hoy`/`venc_accesos_final`) están retiradas. Sin credenciales Meta el adaptador loguea WARN y no envía (dev/CI).
- **Opt-in (R4):** solo envía si `identidad.personas.acepta_whatsapp = TRUE` y el teléfono es normalizable a E.164; si no, omite (attendance no manda emails → sin fallback).
- **Idempotencia (C2):** `existsEnviadoHoy(idCliente, tipo, 'whatsapp')` evita duplicar el mismo aviso el mismo día.
- Registra cada envío en `asistencia.mensajes_log` con estado `enviado`/`fallido`.

Variables de entorno WhatsApp: `META_PHONE_NUMBER_ID`, `META_ACCESS_TOKEN`, `META_API_VERSION` (default `v21.0`), `META_API_BASE_URL`; `INTERNAL_SECRET` para el header interno hacia core.

---

## Endpoints principales

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/api/v1/asistencias/qr` | Registro por QR (escaneo del cliente) | Cliente (JWT) |
| `POST` | `/api/v1/asistencias/app` | Registro desde la app sin QR | Cliente (JWT) |
| `POST` | `/api/v1/asistencias/manual` | Registro manual por staff | Staff (no entrenador) |
| `POST` | `/api/v1/asistencias/manual/override` | Override del dueño (sin validar membresía) | Dueño |
| `GET` | `/api/v1/asistencias/me` | Mis asistencias (cliente autenticado) | Cliente (JWT) |
| `GET` | `/api/v1/asistencias/me/ultimos-30` | Mis últimas asistencias de 30 días | Cliente (JWT) |
| `GET` | `/api/v1/asistencias/hoy` | Asistencias de hoy | Staff |
| `GET` | `/api/v1/asistencias/estadisticas` | Estadísticas de asistencia | Staff |
| `GET` | `/api/v1/clientes/{id}/asistencias` | Asistencias de un cliente | Staff |
| `GET` | `/api/v1/clientes/{id}/asistencias/ultimos-30` | Últimas 30 días de un cliente | Staff |
| `GET` | `/api/v1/clientes/{id}/asistencias/racha-perfecta` | Racha de asistencia (consumido por marketing) | Staff |
| `GET` | `/api/v1/plantillas` | Listar plantillas de mensajes | Staff |
| `POST` | `/api/v1/plantillas` | Crear plantilla | Dueño |
| `GET` | `/api/v1/mensajes` | Listar logs de mensajes | Staff |
| `POST` | `/api/v1/mensajes/enviar` | Enviar mensaje | Staff |
| `POST` | `/api/v1/mensajes/reenviar/{id}` | Reenviar un mensaje | Staff |

> **Nota:** el auto-check-in por QR (`/asistencias/qr`) **requiere un JWT de cliente** — no es un endpoint público. El flujo real de `gym-member-pwa` hace login antes del check-in. Ver también la nota en STATUS.md sobre una regla muerta en `SecurityConfig` (`/asistencias/check`, que no existe como endpoint).

---

## Desarrollo local

Requiere un archivo `.env` en la raíz del proyecto con las variables listadas arriba.

```bash
# Compilar
mvn clean package

# Ejecutar tests (requiere .env y PostgreSQL activo)
mvn test

# Ejecutar un test específico
mvn test -Dtest=AsistenciaManualIntegrationTest

# Servidor de desarrollo
mvn spring-boot:run
```
