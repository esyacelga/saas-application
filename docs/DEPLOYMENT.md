# Guía de Despliegue — Cloud Run + Neon

**Proyecto GCP:** `project-e628007e-c8f0-4e2d-a5f`  
**Cuenta GCP:** `mi.gimnasio.001@gmail.com`  
**Artifact Registry:** `us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images/`  
**Base de datos:** Neon PostgreSQL — región us-east-1  
**Fecha inicio:** 2026-06-19

---

## Estado actual

| # | Tarea | Estado |
|---|-------|--------|
| 1 | Modificaciones de código | ✅ Hecho |
| 2 | Migraciones BD → Neon | ✅ Hecho |
| 3 | Proyecto GCP + Artifact Registry | ✅ Hecho |
| 4 | Build y push de 4 imágenes backend | ✅ Hecho |
| 5 | Deploy backends en Cloud Run | ✅ Hecho |
| 6 | Build y push de 2 imágenes frontend | ✅ Hecho |
| 7 | Deploy frontends en Cloud Run | ✅ Hecho |
| 8 | Actualizar FRONTEND_URL en auth-service | ✅ Hecho |
| 9 | Ajustar CORS con URLs reales | ✅ Hecho |
| 10 | Borrar archivos docs/env-*.yaml (credenciales) | ✅ Hecho |

---

## Modificaciones de código realizadas

### 1. Redis eliminado de platform-service

**Motivo:** simplificar despliegue inicial sin dependencias externas de caché.

| Archivo | Cambio |
|---------|--------|
| `platform-service/pom.xml` | Eliminadas deps `spring-boot-starter-data-redis-reactive` y `spring-boot-starter-cache` |
| `platform-service/src/main/resources/application.yml` | Eliminada sección `spring.data.redis` y prop `module-check.cache-ttl-seconds` |
| `platform-service/src/test/resources/application-test.yml` | Ídem |
| `platform-service/.../config/RedisConfig.java` | Vaciada — clase sin `@Configuration` ni imports Redis |
| `platform-service/.../cache/RedisModuloCheckCache.java` | Reemplazada por no-op: `get` → `Mono.empty()`, `put` → `Mono.empty()`, `evict` → `Mono.empty()` |
| `platform-service/.../service/ModuloCheckService.java` | Eliminado campo `cacheTtl` y `@Value`. TTL hardcodeado en `Duration.ofSeconds(300)` listo para restaurar |

### 2. Redis eliminado de core-service

| Archivo | Cambio |
|---------|--------|
| `core-service/pom.xml` | Eliminadas las mismas 2 deps |
| `core-service/src/main/resources/application.yml` | Eliminada sección `spring.data.redis` |
| `core-service/src/test/resources/application-test.yml` | Ídem |

> Ver `docs/REDIS_REMOVAL.md` para instrucciones completas de reintegración.

### 3. Soporte SSL para Neon (los 4 servicios)

**Motivo:** Neon exige SSL obligatorio. Se hizo configurable para no romper el entorno local.

Archivos modificados:
- `auth-service/src/main/resources/application.yml`
- `platform-service/src/main/resources/application.yml`
- `core-service/src/main/resources/application.yml`
- `attendance-service/src/main/resources/application.yml`

Cambio en cada uno:

```yaml
# Antes
url: r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}

# Después
url: r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}${DB_SSL_OPTIONS:}
```

- **Local:** `DB_SSL_OPTIONS` no se setea → URL sin SSL, funciona con PostgreSQL local
- **Cloud Run:** `DB_SSL_OPTIONS=?sslMode=REQUIRE` → URL con SSL obligatorio para Neon

---

## Base de datos — Neon

### Datos de conexión

| Campo | Valor |
|-------|-------|
| Host (pooler) | `ep-curly-butterfly-at7jkqkq-pooler.c-9.us-east-1.aws.neon.tech` |
| Puerto | `5432` |
| Base de datos | `neondb` |
| Usuario | `neondb_owner` |
| SSL | `sslmode=require&channel_binding=require` |

### Migraciones ejecutadas

```powershell
cd gym-administrator
# gradle.properties actualizado con credenciales Neon
./gradlew update
# Resultado: 69 tablas en 12 schemas creadas en Neon
```

---

## Infraestructura GCP

### Comandos de configuración inicial (ya ejecutados)

```powershell
$sdkBin = "C:\Users\EdwinSantiagoYacelga\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
$env:PATH = "$sdkBin;$env:PATH"
$gcloud = "$sdkBin\gcloud.cmd"

& $gcloud config set project project-e628007e-c8f0-4e2d-a5f
& $gcloud services enable run.googleapis.com artifactregistry.googleapis.com
& $gcloud artifacts repositories create gym-images --repository-format=docker --location=us-east1
& $gcloud auth configure-docker us-east1-docker.pkg.dev --quiet
```

---

## Docker — Backends (✅ completado)

### Imágenes construidas y subidas

| Servicio | Imagen |
|----------|--------|
| auth-service | `us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images/auth-service:latest` |
| platform-service | `us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images/platform-service:latest` |
| core-service | `us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images/core-service:latest` |
| attendance-service | `us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images/attendance-service:latest` |

### Script para rebuild (cuando haya cambios en el código)

```powershell
$sdkBin = "C:\Users\EdwinSantiagoYacelga\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
$env:PATH = "$sdkBin;$env:PATH"
$PROJECT = "project-e628007e-c8f0-4e2d-a5f"
$REGISTRY = "us-east1-docker.pkg.dev/$PROJECT/gym-images"
$BASE = "C:\Respos\own-aplications"

$services = @(
  @{ name="auth-service";       path="$BASE\auth-service"       },
  @{ name="platform-service";   path="$BASE\platform-service"   },
  @{ name="core-service";       path="$BASE\core-service"       },
  @{ name="attendance-service"; path="$BASE\attendance-service" }
)

foreach ($svc in $services) {
  $tag = "$REGISTRY/$($svc.name):latest"
  docker build -t $tag $svc.path
  docker push $tag
}
```

---

## Deploy backends en Cloud Run (✅ completado)

Los deploys usaron archivos YAML en `docs/env-*.yaml` para pasar las variables de entorno.

**Nota:** esos archivos contienen credenciales en texto plano — borrarlos después de terminar con los frontends.

### URLs desplegadas y verificadas (health: UP)

| Servicio | URL | Puerto |
|----------|-----|--------|
| auth-service | `https://auth-service-504178179681.us-east1.run.app` | 8080 |
| platform-service | `https://platform-service-504178179681.us-east1.run.app` | 8081 |
| core-service | `https://core-service-504178179681.us-east1.run.app` | 8083 |
| attendance-service | `https://attendance-service-504178179681.us-east1.run.app` | 8084 |

### Comunicación entre servicios configurada

| Variable | Valor configurado |
|----------|-------------------|
| `core-service → PLATFORM_SERVICE_URL` | `https://platform-service-504178179681.us-east1.run.app` |
| `attendance-service → CORE_SERVICE_URL` | `https://core-service-504178179681.us-east1.run.app` |
| `attendance-service → AUTH_SERVICE_URL` | `https://auth-service-504178179681.us-east1.run.app` |

---

## Deploy frontends en Cloud Run (⏳ pendiente)

### Punto crítico — VITE_* se hornean en build time

Las variables de entorno de Vite **se incrustan en el bundle JS durante el `docker build`**, no en tiempo de ejecución. Por eso se pasan como `--build-arg`, no como env vars del contenedor.

Ambos frontends usan **nginx en puerto 80** (no 5173/5174).

### Paso 1 — Build y deploy de gym-member-pwa (primero)

```powershell
$sdkBin = "C:\Users\EdwinSantiagoYacelga\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
$env:PATH = "$sdkBin;$env:PATH"
$REGISTRY = "us-east1-docker.pkg.dev/project-e628007e-c8f0-4e2d-a5f/gym-images"

docker build `
  --build-arg VITE_AUTH_API_URL=https://auth-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_CORE_API_URL=https://core-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_ATTENDANCE_API_URL=https://attendance-service-504178179681.us-east1.run.app/api/v1 `
  -t "$REGISTRY/gym-member-pwa:latest" `
  C:\Respos\own-aplications\gym-member-pwa

docker push "$REGISTRY/gym-member-pwa:latest"

& "$sdkBin\gcloud.cmd" run deploy gym-member-pwa `
  --image="$REGISTRY/gym-member-pwa:latest" `
  --region=us-east1 --platform=managed --allow-unauthenticated --port=80 `
  --project="project-e628007e-c8f0-4e2d-a5f"
```

→ Guardar la URL que devuelve (ej: `https://gym-member-pwa-504178179681.us-east1.run.app`)

### Paso 2 — Build y deploy de auth-service-frond-end (con URL del PWA)

Reemplazar `<URL_GYM_MEMBER_PWA>` con la URL obtenida en el paso anterior.

```powershell
docker build `
  --build-arg VITE_API_AUTH_URL=https://auth-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_API_PLATFORM_URL=https://platform-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_API_CORE_URL=https://core-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_API_ATTENDANCE_URL=https://attendance-service-504178179681.us-east1.run.app/api/v1 `
  --build-arg VITE_APP_NAME=GymAdmin `
  --build-arg VITE_CLIENT_APP_URL=<URL_GYM_MEMBER_PWA> `
  -t "$REGISTRY/frontend-admin:latest" `
  C:\Respos\own-aplications\auth-service-frond-end

docker push "$REGISTRY/frontend-admin:latest"

& "$sdkBin\gcloud.cmd" run deploy frontend-admin `
  --image="$REGISTRY/frontend-admin:latest" `
  --region=us-east1 --platform=managed --allow-unauthenticated --port=80 `
  --project="project-e628007e-c8f0-4e2d-a5f"
```

→ Guardar la URL que devuelve (ej: `https://frontend-admin-504178179681.us-east1.run.app`)

### Paso 3 — Actualizar FRONTEND_URL en auth-service

Reemplazar `<URL_FRONTEND_ADMIN>` con la URL obtenida en el paso anterior.

```powershell
& "$sdkBin\gcloud.cmd" run services update auth-service `
  --region=us-east1 `
  --project="project-e628007e-c8f0-4e2d-a5f" `
  --update-env-vars="FRONTEND_URL=<URL_FRONTEND_ADMIN>"
```

### Paso 4 — Ajustar CORS en todos los servicios

Una vez que tienes las URLs reales de los frontends, cambiar `CORS_ALLOW_ALL=false` y setear los orígenes permitidos en cada servicio:

```powershell
# Ejemplo para auth-service
& "$sdkBin\gcloud.cmd" run services update auth-service `
  --region=us-east1 --project="project-e628007e-c8f0-4e2d-a5f" `
  --update-env-vars="CORS_ALLOW_ALL=false,CORS_ALLOWED_ORIGINS=<URL_FRONTEND_ADMIN>"
```

Repetir para platform-service, core-service y attendance-service.

### Paso 5 — Limpiar archivos con credenciales

```powershell
Remove-Item C:\Respos\own-aplications\docs\env-auth-service.txt
Remove-Item C:\Respos\own-aplications\docs\env-auth-service.yaml
Remove-Item C:\Respos\own-aplications\docs\env-platform-service.yaml
Remove-Item C:\Respos\own-aplications\docs\env-core-service.yaml
Remove-Item C:\Respos\own-aplications\docs\env-attendance-service.yaml
```

---

## URLs finales (completar al desplegar)

| Servicio | URL Cloud Run |
|----------|---------------|
| auth-service | `https://auth-service-504178179681.us-east1.run.app` |
| platform-service | `https://platform-service-504178179681.us-east1.run.app` |
| core-service | `https://core-service-504178179681.us-east1.run.app` |
| attendance-service | `https://attendance-service-504178179681.us-east1.run.app` |
| gym-member-pwa | `https://gym-member-pwa-504178179681.us-east1.run.app` |
| frontend-admin | `https://frontend-admin-504178179681.us-east1.run.app` |

---

## Notas para el futuro

- **JWT_SECRET** debe ser idéntico en los 4 backends. Si se cambia, hay que actualizar todos simultáneamente.
- **Neon free tier:** límite de 10 conexiones. Si aparecen errores de conexión, reducir `pool.max-size` en los servicios que lo configuran (`auth-service`, `attendance-service`).
- **Redis:** eliminado temporalmente. Ver `docs/REDIS_REMOVAL.md` cuando la app crezca y necesite caché.
- **Redesplegar un servicio tras cambios:** rebuild imagen → push → `gcloud run deploy` con el mismo comando. Cloud Run hace rolling update automático sin downtime.
- **Credenciales Neon:** las expuestas en esta sesión deben rotarse en neon.tech → Settings → Reset password.
