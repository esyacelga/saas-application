# IMPL-12 — Portal del Miembro: Gaps de Backend

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../STATUS.md).

> **Módulo:** Portal del Miembro — Fase 1 (preparación de backends)
> **Complejidad:** ★★★★☆
> **Prerequisito:** `auth-service` + `attendance-service` + `gym-administrator` funcionales
> **Resultado:** Todos los backends listos para soportar el portal del miembro PWA

---

## Resumen de cambios por repositorio

| Repo | Cambios |
|---|---|
| `gym-administrator` | Migración: `logo_url` en `tenant.companias` |
| `auth-service` | Endpoint público de resolución de QR, extensión de JWT cliente, Google OAuth, Facebook OAuth |
| `attendance-service` | Verificar que `POST /asistencias/qr` acepta JWT `tipo: 'cliente'` |

---

## Sección A — gym-administrator (Liquibase)

### A.1 — Migración: columna `logo_url` en `tenant.companias`

Crear en `db/scripts/202605_GYM-001/ddl/` (o en el siguiente lote de scripts):

```sql
-- Archivo: YYYYMM_GYM-XXX/ddl/10_alter_companias_add_logo_url.sql
ALTER TABLE tenant.companias
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);
```

Registrar en `partial-changelog.yml`:

```yaml
- changeSet:
    id: add-logo-url-to-companias
    author: santiago
    changes:
      - sqlFile:
          path: ddl/10_alter_companias_add_logo_url.sql
          relativeToChangelogFile: true
```

---

## Sección B — auth-service

> **Arquitectura del auth-service:** Usa functional routing (`ApiRouter.java`), no `@RestController`.
> Patrón: `Router → Handler → UseCase → Port → Adapter`.

### B.1 — Endpoint público: resolver QR a datos del gimnasio

El miembro abre `https://app.tudominio.com/gym/<qrToken>`. La PWA necesita resolver ese
token a `{ id_compania, nombre_compania, logo_url }` **sin autenticación**.

#### B.1.1 — Nuevo endpoint

```
GET /api/v1/auth/gimnasio/by-qr/{qrToken}
Authorization: ninguna (público)

Response 200:
{
  "id_compania": 3,
  "nombre_compania": "FitZone Ecuador",
  "logo_url": "https://cdn.ejemplo.com/logos/fitzone.png"
}

Response 404:
{ "error": "QR no válido o expirado" }
```

#### B.1.2 — Archivos a crear/modificar

```
auth-service/src/main/java/com/gymadmin/auth/
├── domain/port/in/
│   └── ResolverQrUseCase.java           ← interfaz
├── application/usecase/
│   └── ResolverQrUseCaseImpl.java       ← implementación
├── infrastructure/adapter/
│   ├── in/web/
│   │   ├── router/ApiRouter.java        ← agregar ruta GET /gimnasio/by-qr/{qrToken}
│   │   └── handler/GimnasioHandler.java ← nuevo handler
│   └── out/persistence/
│       └── CompaniaR2dbcRepository.java ← agregar findByQrToken (o usar SucursalRepository)
└── infrastructure/dto/response/
    └── GimnasioPublicoResponse.java     ← { idCompania, nombreCompania, logoUrl }
```

#### B.1.3 — Lógica de resolución

El `qrToken` pertenece a `sucursales` (ya existe el campo). La consulta necesita
un JOIN `sucursales → companias`:

```sql
SELECT c.id, c.nombre, c.logo_url
FROM tenant.sucursales s
JOIN tenant.companias c ON c.id = s.id_compania
WHERE s.qr_token = :qrToken
  AND s.activo = true
  AND c.activo = true
```

#### B.1.4 — Seguridad (sin autenticación)

Agregar `/api/v1/auth/gimnasio/**` a la lista de rutas públicas en `SecurityConfig`:

```java
.pathMatchers("/api/v1/auth/gimnasio/**").permitAll()
```

---

### B.2 — Extensión del JWT de cliente (`tipo: 'cliente'`)

El JWT actual incluye `{ sub, tipo, id_compania, id_persona, nombre }`.
Agregar `nombre_compania` y `logo_url` para que la PWA no dependa del endpoint público
después del login (datos ya disponibles en el token).

#### B.2.1 — Clase `JwtPayloadCliente` actualizada

```java
// Campo adicional en el builder del JWT de cliente
claims.put("nombre_compania", compania.getNombre());
claims.put("logo_url",        compania.getLogoUrl());  // puede ser null
```

#### B.2.2 — `LoginAppResponse` actualizado

```java
public record LoginAppResponse(
    String accessToken,
    String refreshToken,
    long   expiresIn,
    PersonaInfo persona,
    CompaniaInfo compania   // ← nuevo
) {
    public record CompaniaInfo(
        Integer id,
        String  nombre,
        String  logoUrl
    ) {}
}
```

---

### B.3 — Google OAuth

> No existe implementación actual. Se debe agregar al auth-service.

#### B.3.1 — Flujo

```
PWA → [botón Google] → Google Sign-In SDK (frontend)
→ obtiene idToken de Google
→ POST /api/v1/auth/app/oauth/google { idToken, idCompania }
→ backend valida idToken con Google API
→ busca usuario por email en la compañía
→ si existe → devuelve JWT cliente
→ si NO existe → 404 con mensaje amigable
```

#### B.3.2 — Nuevo endpoint

```
POST /api/v1/auth/app/oauth/google
Body: { idToken: string, idCompania: number }

Response 200: igual que LoginAppResponse
Response 404: { "error": "No tienes una cuenta en este gimnasio. Contacta a recepción." }
```

#### B.3.3 — Dependencia Maven a agregar

```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.4.0</version>
</dependency>
```

#### B.3.4 — Archivos a crear

```
auth-service/src/main/java/com/gymadmin/auth/
├── domain/port/in/
│   └── OAuthGoogleUseCase.java
├── application/usecase/
│   └── OAuthGoogleUseCaseImpl.java      ← valida idToken + busca usuario
├── infrastructure/adapter/in/web/
│   ├── router/ApiRouter.java            ← POST /app/oauth/google
│   └── handler/OAuthHandler.java        ← nuevo handler
└── infrastructure/dto/request/
    └── OAuthGoogleRequest.java          ← { idToken, idCompania }
```

#### B.3.5 — application.yml

```yaml
google:
  oauth:
    client-id: ${GOOGLE_CLIENT_ID}
```

---

### B.4 — Facebook OAuth

#### B.4.1 — Flujo

```
PWA → [botón Facebook] → Facebook Login SDK (frontend)
→ obtiene accessToken de Facebook
→ POST /api/v1/auth/app/oauth/facebook { accessToken, idCompania }
→ backend llama a https://graph.facebook.com/me?fields=email,name,picture&access_token=...
→ extrae email
→ busca usuario por email en la compañía
→ si existe → devuelve JWT cliente
→ si NO existe → 404 con mensaje amigable
```

#### B.4.2 — Nuevo endpoint

```
POST /api/v1/auth/app/oauth/facebook
Body: { accessToken: string, idCompania: number }

Response 200: igual que LoginAppResponse
Response 404: { "error": "No tienes una cuenta en este gimnasio. Contacta a recepción." }
```

#### B.4.3 — Llamada a Graph API (WebClient reactivo)

```java
// OAuthFacebookUseCaseImpl
webClient.get()
    .uri("https://graph.facebook.com/me?fields=id,email,name,picture&access_token=" + accessToken)
    .retrieve()
    .bodyToMono(FacebookUserInfo.class)
    .flatMap(info -> buscarUsuarioPorEmail(info.email(), idCompania));
```

#### B.4.4 — Archivos a crear

```
├── domain/port/in/
│   └── OAuthFacebookUseCase.java
├── application/usecase/
│   └── OAuthFacebookUseCaseImpl.java
├── infrastructure/adapter/in/web/
│   └── handler/OAuthHandler.java        ← agregar método facebookLogin()
│   └── router/ApiRouter.java            ← POST /app/oauth/facebook
└── infrastructure/dto/request/
    └── OAuthFacebookRequest.java        ← { accessToken, idCompania }
```

---

### B.5 — Rutas públicas (resumen)

Agregar a `SecurityConfig`:

```java
.pathMatchers(
    "/api/v1/auth/gimnasio/**",     // B.1 — resolver QR
    "/api/v1/auth/app/login",       // ya existe
    "/api/v1/auth/app/oauth/**",    // B.3 + B.4 — OAuth
    "/api/v1/auth/refresh",         // ya existe
    "/api/v1/auth/password/**"      // ya existe
).permitAll()
```

---

## Sección C — attendance-service

### C.1 — Verificar autorización del endpoint `POST /asistencias/qr`

El endpoint ya existe. Verificar que el `SecurityConfig` permite tokens `tipo: 'cliente'`:

```java
// AsistenciaController o SecurityConfig
// Asegurarse de que NO haya restricción por rol/tipo que excluya 'cliente'
// El JWT del miembro lleva id_compania e id_persona — el endpoint los usa para registrar
```

Si existe un filtro que solo acepta `tipo: 'staff'`, agregar `'cliente'` a la lista permitida.

### C.2 — Verificar endpoint de descongelamiento accesible para miembros

El endpoint de reactivación de congelamiento en `core-service` (`PUT /congelamientos/{id}/reactivar`
o similar) debe aceptar JWT `tipo: 'cliente'` **solo si el congelamiento pertenece al propio miembro**
(`id_cliente == id_persona del JWT`).

Si actualmente requiere `tipo: 'staff'`, agregar validación alternativa:

```java
// Pseudocódigo
if (jwt.tipo == 'staff') → permitir siempre (para cualquier cliente)
if (jwt.tipo == 'cliente') → solo si congelamiento.idCliente == jwt.idPersona
```

---

## Orden de implementación recomendado

| Paso | Tarea | Repo |
|---|---|---|
| 1 | Migración `logo_url` | gym-administrator |
| 2 | Endpoint público by-qr | auth-service |
| 3 | Extensión JWT cliente | auth-service |
| 4 | Verificar POST /asistencias/qr | attendance-service |
| 5 | Verificar descongelamiento | core-service |
| 6 | Google OAuth | auth-service |
| 7 | Facebook OAuth | auth-service |

> Los pasos 1–5 son bloqueantes para la PWA. Los pasos 6–7 (OAuth) se pueden implementar
> en paralelo con el frontend una vez que el login manual funcione.

---

## Variables de entorno requeridas (nuevas)

| Variable | Servicio | Descripción |
|---|---|---|
| `GOOGLE_CLIENT_ID` | auth-service | Client ID de Google Cloud Console |
| `FACEBOOK_APP_ID` | PWA (frontend) | App ID del panel Meta for Developers |
| `FACEBOOK_APP_SECRET` | auth-service | App Secret (solo para validación server-side si se usa) |
