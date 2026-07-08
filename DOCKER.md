# Docker — Gym Administrator

Guía para levantar y detener los microservicios usando el `docker-compose.yml` de la raíz del repositorio.

## Compilación sin tests

Los Dockerfiles de todos los microservicios usan `mvn package -DskipTests`, por lo que **`docker-compose up --build` nunca ejecuta los tests**. El build dentro del contenedor solo compila y empaqueta el JAR.

Si en algún momento necesitas correr los tests antes de construir la imagen, hazlo manualmente desde la carpeta del servicio:

```powershell
cd auth-service
mvn test
cd ..
```

---

## Requisitos previos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y corriendo
- Estar ubicado en la raíz del proyecto: `c:\Respos\own-aplications`

---

## Servicios disponibles

| Servicio           | Puerto | Descripción                              |
|--------------------|--------|------------------------------------------|
| `postgres`         | 5432   | Base de datos PostgreSQL 16              |
| `redis`            | 6379   | Cache Redis 7                            |
| `auth-service`     | 8080   | Autenticación y autorización             |
| `platform-service` | 8081   | Gestión de plataforma y suscripciones    |
| `core-service`     | 8083   | Clientes, membresías y acceso al gym     |
| `attendance-service` | 8084 | Registro de asistencias y mensajería     |
| `admin-frontend`   | 5173   | Panel admin/staff (React + nginx)        |
| `member-pwa`       | 5174   | App móvil miembros PWA (React + nginx)   |

### Dependencias entre servicios

```
postgres ──────────────────────────────────┐
redis ─────────────────────────────────────┤
                                           ▼
                              auth-service (8080)
                              platform-service (8081)
                                           │
                                           ▼
                              core-service (8083)
                                           │
                                           ▼
                              attendance-service (8084)
                                           │
                          ┌────────────────┴────────────────┐
                          ▼                                  ▼
                 admin-frontend (5173)             member-pwa (5174)
```

---

## Variables de entorno (opcional)

Por defecto todo funciona sin configuración adicional. Si necesitas sobreescribir algún valor crea un archivo `.env` en la raíz:

```env
# .env  (raíz del repositorio)
JWT_SECRET=tu-secret-base64-de-256-bits

# Correo para recuperación de contraseña (auth-service)
MAIL_USERNAME=tucuenta@gmail.com
MAIL_PASSWORD=tu-app-password-de-gmail

# URLs de los backends que los frontends llaman desde el NAVEGADOR
# Si expones el stack en un servidor real, cambia localhost por la IP/dominio público
VITE_API_AUTH_URL=http://localhost:8080/api/v1
VITE_API_PLATFORM_URL=http://localhost:8081/api/v1
VITE_API_CORE_URL=http://localhost:8083/api/v1
VITE_API_ATTENDANCE_URL=http://localhost:8084/api/v1
VITE_AUTH_API_URL=http://localhost:8080/api/v1
VITE_CORE_API_URL=http://localhost:8083/api/v1
VITE_ATTENDANCE_API_URL=http://localhost:8084/api/v1

# Opcionales — PWA miembros (oculta botones de login social si están vacíos)
VITE_GOOGLE_CLIENT_ID=
VITE_FACEBOOK_APP_ID=
```

> **Nota sobre URLs de frontend:** Las variables `VITE_*` se incrustan en el bundle JavaScript durante el `build`. El navegador es quien hace las llamadas HTTP, no el contenedor nginx. Por eso las URLs deben ser accesibles desde el navegador del usuario, no desde dentro de la red Docker.

---

## Iniciar todo el stack

```powershell
# Primera vez o si hubo cambios en el código (construye las imágenes)
docker-compose up -d --build

# Siguientes veces (usa las imágenes ya construidas)
docker-compose up -d
```

Verificar que todos los contenedores están corriendo:

```powershell
docker-compose ps
```

Verificar que auth-service está listo (health check):

```powershell
docker-compose ps auth-service
# El estado debe mostrar "(healthy)"
```

---

## Ver logs

```powershell
# Logs de todos los servicios en tiempo real
docker-compose logs -f

# Logs de un servicio específico
docker-compose logs -f auth-service
docker-compose logs -f platform-service
docker-compose logs -f core-service
docker-compose logs -f attendance-service

# Últimas 100 líneas de un servicio
docker-compose logs --tail=100 core-service
```

---

## Iniciar servicios uno por uno

Útil cuando se quiere levantar el stack de forma gradual o depurar un servicio en particular.
El orden correcto es el siguiente (respetar las dependencias):

```powershell
# 1. Base de datos y cache (siempre primero)
docker-compose up -d postgres redis

# 2. Esperar a que postgres esté healthy antes de continuar
docker-compose ps postgres
# Repetir hasta ver: "healthy"

# 3. Auth service (no depende de redis)
docker-compose up -d auth-service

# 4. Platform service (depende de postgres + redis)
docker-compose up -d platform-service

# 5. Core service (depende de platform-service)
docker-compose up -d core-service

# 6. Attendance service (depende de core-service)
docker-compose up -d attendance-service
```

---

## Reiniciar un servicio

```powershell
# Reiniciar sin reconstruir la imagen
docker-compose restart auth-service

# Reconstruir la imagen y reiniciar
docker-compose up -d --build auth-service
```

---

## Detener servicios

```powershell
# Detener todo el stack (conserva los datos de la BD)
docker-compose stop

# Detener un servicio específico
docker-compose stop attendance-service
docker-compose stop core-service
docker-compose stop platform-service
docker-compose stop auth-service

# Detener la infraestructura (último paso)
docker-compose stop redis
docker-compose stop postgres
```

---

## Eliminar contenedores

```powershell
# Eliminar contenedores pero conservar los datos de la BD (volumen postgres-data)
docker-compose down

# Eliminar contenedores Y los datos de la BD (reset completo)
docker-compose down -v
```

> **Advertencia:** `down -v` borra el volumen `postgres-data`. Todos los datos de la base de datos se perderán.

---

## Reconstruir solo un servicio

Si hiciste cambios en el código de un microservicio sin tocar los demás:

```powershell
docker-compose up -d --build auth-service
docker-compose up -d --build platform-service
docker-compose up -d --build core-service
docker-compose up -d --build attendance-service
```

---

## Endpoints de verificación

Una vez levantado el stack, puedes verificar que cada servicio responde:

```powershell
# Health check de auth-service
curl http://localhost:8080/actuator/health

# Prueba rápida de conectividad (debe retornar 401 o 403, no connection refused)
curl http://localhost:8081
curl http://localhost:8083
curl http://localhost:8084
```

---

## Solución de problemas frecuentes

**El contenedor sale con error inmediatamente**
```powershell
docker-compose logs auth-service
# Leer el stack trace para identificar la causa
```

**La base de datos no está lista y el servicio falla al arrancar**
```powershell
# Verificar el estado del healthcheck de postgres
docker-compose ps postgres
# Si no está "healthy", revisar los logs
docker-compose logs postgres
```

**Puerto ya en uso**
```powershell
# Ver qué proceso usa el puerto (ejemplo: 8080)
netstat -ano | findstr :8080
# Detener el proceso o cambiar el puerto en docker-compose.yml
```

**Limpiar todo y empezar desde cero**
```powershell
docker-compose down -v
docker-compose up -d --build
```
