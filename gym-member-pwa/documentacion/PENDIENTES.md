# gym-member-pwa — Tareas Pendientes

Estado auditado: 2026-06-14  
Contexto: PWA React 19 / Vite 8 / Tailwind v4 / Zustand / React Router v7

---

## Resumen ejecutivo

El núcleo de la aplicación está completo: login manual, CheckIn QR, Historial 30 días con heatmap, Membresía y Perfil. Las tareas pendientes son mejoras de producto (OAuth, reset de contraseña, deep-link QR) e infraestructura de soporte (íconos PWA, i18n, documentación).

---

## Tarea 1 — OAuth Login UI

**Prioridad:** Alta  
**Estimado:** 1–2 h

### Qué falta

Solo la UI. El backend y el repositorio HTTP ya están listos:

| Endpoint backend | Método en `AuthHttpRepository.ts` |
|---|---|
| `POST /auth/app/oauth/google` | `loginGoogle(req)` |
| `POST /auth/app/oauth/facebook` | `loginFacebook(req)` |

### Contratos de request/response

```typescript
// request
interface OAuthLoginRequest {
  id_compania: number
  token: string        // ID token del proveedor (Google: credential; Facebook: accessToken)
}

// response — igual que loginManual
interface AuthResponse {
  access_token: string
  refresh_token: string
  token_type: string
  expires_in: number
}
```

### Requisitos de implementación

1. **Google Sign-In**: usar `@react-oauth/google` (`GoogleOAuthProvider` + `GoogleLogin` button) o `google.accounts.id.initialize` / `renderButton` directamente via script tag en `index.html`.
   - Variable de entorno nueva: `VITE_GOOGLE_CLIENT_ID`
   - El callback recibe `credential` (JWT ID token) → pasar como `token` al backend.

2. **Facebook Login**: usar el SDK de Facebook JS (`window.FB.init` + `FB.login`).
   - Variable de entorno nueva: `VITE_FACEBOOK_APP_ID`
   - El callback retorna `authResponse.accessToken` → pasar como `token` al backend.

3. **Flujo UX en `LoginPage.tsx`**:
   - Mantener el formulario manual existente arriba.
   - Agregar separador visual `— o continúa con —`.
   - Dos botones debajo: `Continuar con Google` y `Continuar con Facebook`.
   - El campo `id_compania` (ya en el form) debe estar completado ANTES de pulsar OAuth — mostrar error si está vacío.
   - Al éxito OAuth, llamar `useAuthStore().setTokens(...)` igual que el login manual.

4. **Archivos a modificar**:
   - `src/ui/pages/login/LoginPage.tsx` — agregar botones y handlers
   - `src/application/usecase/auth.types.ts` — verificar si `OAuthLoginRequest` ya existe
   - `.env.example` — agregar `VITE_GOOGLE_CLIENT_ID` y `VITE_FACEBOOK_APP_ID`
   - `index.html` — agregar script del SDK de Facebook si se usa esa vía

5. **Dependencias nuevas a instalar**:
   ```bash
   npm install @react-oauth/google   # para Google
   # Facebook no requiere paquete npm, usa script tag
   ```

---

## Tarea 2 — Íconos PWA

**Prioridad:** Alta (la app no instala correctamente sin ellos)  
**Estimado:** 30 min

### Qué falta

`vite.config.ts` declara los íconos pero los archivos no existen:

```
public/icons/pwa-192x192.png   ← FALTA
public/icons/pwa-512x512.png   ← FALTA
```

La carpeta `public/icons/` tampoco existe.

### Requisitos

- Formato: PNG, fondo opaco (los íconos con transparencia no se muestran bien en Android).
- Tamaños exactos: **192×192 px** y **512×512 px**.
- Colocarlos en `public/icons/`.

### Opción rápida si no hay assets de diseño

Usar [PWA Asset Generator](https://github.com/elegantapp/pwa-asset-generator) pasando el logo del gimnasio:

```bash
npx pwa-asset-generator logo.png public/icons --background "#1e293b" --padding "10%"
```

Seleccionar solo los dos tamaños requeridos.

### Verificación post-tarea

```bash
npm run build
# Lighthouse > PWA audit debe pasar el check de íconos
```

---

## Tarea 3 — Flujo "Olvidé mi contraseña"

**Prioridad:** Media  
**Estimado:** 2–3 h

### Qué falta

Enlace y páginas de recuperación de contraseña. El backend de auth-service ya tiene los endpoints.

### Endpoints backend (auth-service, puerto 8080)

> Verificar paths exactos en `auth-service` antes de implementar — los paths debajo son los convencionales del proyecto.

| Acción | Método | Path |
|---|---|---|
| Solicitar reset | `POST` | `/auth/password/forgot` |
| Confirmar token + nueva clave | `POST` | `/auth/password/reset` |

### Contratos esperados

```typescript
// POST /auth/password/forgot
{ email: string; id_compania: number }
// response: 200 OK (vacío o mensaje genérico)

// POST /auth/password/reset
{ token: string; new_password: string; id_compania: number }
// response: 200 OK
```

### Requisitos de implementación

1. **LoginPage.tsx**: agregar enlace `¿Olvidaste tu contraseña?` debajo del botón de login.

2. **Nueva página `ForgotPasswordPage.tsx`** en `src/ui/pages/forgot-password/`:
   - Campos: `id_compania` + `email`.
   - Botón "Enviar instrucciones".
   - Mensaje de confirmación post-envío (no redirigir, para no exponer si el email existe).

3. **Nueva página `ResetPasswordPage.tsx`** en `src/ui/pages/reset-password/`:
   - Leer `?token=...` de la URL query string.
   - Campos: nueva contraseña + confirmar contraseña (Zod refinement).
   - Redirigir a `/login` al éxito.

4. **Nuevas rutas en `App.tsx`**:
   ```tsx
   <Route path="/forgot-password" element={<ForgotPasswordPage />} />
   <Route path="/reset-password" element={<ResetPasswordPage />} />
   ```
   Ambas rutas son públicas (sin `ClienteGuard`).

5. **`AuthHttpRepository.ts`**: agregar métodos `forgotPassword` y `resetPassword`.

6. **`AuthRepository.ts` (puerto)**: declarar las firmas nuevas.

---

## Tarea 4 — QR Deep-link (auto-populate id_compania)

**Prioridad:** Media  
**Estimado:** 1 h

### Contexto

El módulo "Mi Empresa" del panel admin genera un QR que apunta a una URL del tipo:

```
https://member.gymadmin.app/login?qr=<token>
```

Cuando un miembro escanea ese QR, la app debería:
1. Detectar el param `?qr=` en la URL.
2. Llamar `GET /auth/gimnasio/by-qr/{token}` para resolver el `id_compania`.
3. Pre-rellenar el campo `id_compania` en `LoginPage` (campo readonly/disabled).

### Endpoint backend

```
GET /auth/gimnasio/by-qr/{token}
Response: { id_compania: number; nombre: string }
```

### Requisitos de implementación

1. En `LoginPage.tsx`, al montar el componente:
   ```typescript
   const [searchParams] = useSearchParams()
   const qrToken = searchParams.get('qr')
   ```

2. Si `qrToken` existe, hacer el fetch y pre-llenar `id_compania` con `setValue('id_compania', data.id_compania)`. Mostrar el nombre del gimnasio como hint visual.

3. El campo `id_compania` pasa a ser `readOnly` mientras venga de QR (para evitar confusión).

4. **`AuthHttpRepository.ts`**: agregar método `getGymByQr(token: string)`.

5. **`AuthRepository.ts` (puerto)**: declarar la firma.

6. Manejo de error: si el token QR es inválido o expirado, mostrar toast de error y dejar `id_compania` editable.

---

## Tarea 5 — CLAUDE.md del proyecto

**Prioridad:** Baja  
**Estimado:** 30 min

### Qué falta

No existe `CLAUDE.md` en la raíz de `gym-member-pwa`. Claude Code necesita este archivo para entender el proyecto en futuras sesiones.

### Contenido mínimo requerido

```markdown
# CLAUDE.md — gym-member-pwa

## Comandos
npm run dev      # Vite dev server en http://localhost:5173
npm run build    # tsc + vite build → dist/
npm run preview  # Preview del build de producción

## Variables de entorno (.env.local)
VITE_AUTH_API_URL=http://localhost:8080/api/v1
VITE_CORE_API_URL=http://localhost:8083/api/v1
VITE_ATTENDANCE_API_URL=http://localhost:8082/api/v1   # (8084 si usa el mismo puerto que en admin)
VITE_GOOGLE_CLIENT_ID=...     # Para OAuth Google (Tarea 1)
VITE_FACEBOOK_APP_ID=...      # Para OAuth Facebook (Tarea 1)

## Arquitectura
Hexagonal: Domain → Application → Infrastructure → UI

## Auth
- JWT tipo 'cliente': campos id_compania, id_persona, sub
- Zustand store con persistencia localStorage (clave: gym-member-auth)
- Guard: ClienteGuard verifica tipo === 'cliente'

## Rutas
/login         — LoginPage (pública)
/home          — HomePage (protegida)
/check-in      — CheckInPage (protegida)
/membresia     — MembresiaPage (protegida)
/historial     — HistorialPage (protegida)
/profile       — ProfilePage (protegida)
```

---

## Tarea 6 — Infraestructura i18n

**Prioridad:** Baja (deuda técnica)  
**Estimado:** 3–4 h para setup + migración de strings existentes

### Estado actual

Todos los strings están hardcodeados en español en los componentes. No hay ninguna librería de i18n instalada.

### Requisitos

1. **Instalar `i18next` + `react-i18next`**:
   ```bash
   npm install i18next react-i18next
   ```

2. **Crear `src/i18n/index.ts`** con configuración análoga a `auth-service-frond-end`:
   - Idioma por defecto: `es`
   - Fallback: `en`
   - Namespaces: `common`, `login`, `home`, `checkin`, `membresia`, `historial`, `profile`

3. **Crear archivos**:
   - `src/i18n/locales/es.json`
   - `src/i18n/locales/en.json`

4. **Migrar strings** de cada página a las claves correspondientes.

5. **Inicializar i18n** en `src/main.tsx` antes del render.

> Nota: Esta tarea no bloquea ninguna otra. Se puede hacer en paralelo o al final.

---

## Checklist de estado

| # | Tarea | Estado | Bloquea |
|---|---|---|---|
| 1 | OAuth Login UI | Pendiente | — |
| 2 | Íconos PWA | Pendiente | Instalación en dispositivo |
| 3 | Flujo password reset | Pendiente | — |
| 4 | QR deep-link | Pendiente | — |
| 5 | CLAUDE.md | Pendiente | — |
| 6 | i18n | Pendiente | — |

---

## Dependencias externas necesarias

| Paquete | Para | Estado |
|---|---|---|
| `@react-oauth/google` | Tarea 1 Google OAuth | No instalado |
| Facebook JS SDK (CDN) | Tarea 1 Facebook OAuth | No instalado |
| `i18next` + `react-i18next` | Tarea 6 | No instalado |

## Variables de entorno a agregar a `.env.example`

```env
VITE_GOOGLE_CLIENT_ID=tu_google_client_id_aqui
VITE_FACEBOOK_APP_ID=tu_facebook_app_id_aqui
```
