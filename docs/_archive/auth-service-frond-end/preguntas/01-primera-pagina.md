# ¿Cómo sabe la aplicación cuál es la primera página a mostrar?

> **ESTADO:** 📜 Histórico — nota personal de aprendizaje, no documentación técnica del sistema. Ver [../../STATUS.md](../../../STATUS.md).

## Resumen

La app combina tres mecanismos: **inicialización del token**, **estado en el store** y **route guards**, para decidir qué página renderizar primero.

---

## Paso 1 — App.tsx: intento de restaurar sesión

Al montar la aplicación, `App.tsx` llama al caso de uso `RefreshTokenUseCase.execute()`. Esto hace una petición al backend para renovar el token usando la cookie `refreshToken` guardada en el navegador.

```
App monta
  → RefreshTokenUseCase.execute()
      ↳ éxito  → guarda accessToken + user en el store
      ↳ falla  → no hay sesión activa (token expirado o inexistente)
  → setInitialized(true)   ← siempre se llama al final
```

La bandera `initialized` es clave: mientras sea `false`, los guards muestran un spinner en pantalla completa para evitar un parpadeo hacia la página de login.

---

## Paso 2 — Router: rutas y redirecciones por defecto

El router (`src/ui/router/index.tsx`) define tres grupos:

| Grupo | Guard | Rutas | Redirige a si falla |
|-------|-------|-------|---------------------|
| Públicas | ninguno | `/login`, `/platform/login`, `/reset-password/*` | — |
| Admin | `AuthGuard` | `/admin/*` | `/login` |
| Plataforma | `PlatformGuard` | `/platform/*` | `/platform/login` |

Además tiene dos rutas comodín:
- `/` → redirige a `/login`
- `*` (cualquier ruta desconocida) → redirige a `/login`

---

## Paso 3 — Guards: quién puede pasar

### AuthGuard
1. Si `initialized === false` → muestra `FullScreenLoader` (espera al refresh)
2. Si `accessToken === null` → redirige a `/login`
3. Si `user.tipo !== 'staff'` → redirige a `/login`
4. Si todo está bien → renderiza la ruta protegida

### PlatformGuard
1. Si `initialized === false` → muestra `FullScreenLoader`
2. Si `accessToken === null` → redirige a `/platform/login`
3. Si `user.tipo !== 'plataforma'` → redirige a `/platform/login`
4. Si todo está bien → renderiza la ruta protegida

---

## Flujo completo al abrir la app

```
Navegador abre la app (ej: http://localhost:5173/)
        │
        ▼
App.tsx → RefreshTokenUseCase.execute()
        │
  ┌─────┴──────┐
  │ éxito      │ falla
  │            │
  ▼            ▼
accessToken   accessToken = null
en el store   en el store
        │
        ▼
setInitialized(true)
        │
        ▼
Router evalúa la URL actual
        │
  ┌─────┴──────────────────────┐
  │ URL = "/"                  │ URL = "/admin/dashboard"
  │                            │
  ▼                            ▼
redirige a /login          AuthGuard verifica
                                │
                      ┌─────────┴──────────┐
                      │ token + staff       │ sin token / plataforma
                      ▼                    ▼
                  muestra dashboard    redirige a /login
```

---

## ¿Cómo sabe la app a qué servidor conectarse?

La URL base del backend **no está hardcodeada** en el código. Se lee desde la variable de entorno `VITE_API_BASE_URL` que Vite inyecta en tiempo de build/dev:

```ts
// src/infrastructure/http/axios.instance.ts
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,   // ← viene del .env
  withCredentials: true,
})
```

El valor concreto vive en `.env.local` (no se versiona):

```
VITE_API_BASE_URL=http://localhost:8080
```

> Si `VITE_API_BASE_URL` está vacío, Axios no tiene `baseURL` y las peticiones van relativas al origen del frontend (`http://localhost:5173`), lo que rompe todas las llamadas al backend.

---

## Archivos clave

| Archivo | Rol |
|---------|-----|
| `src/App.tsx` | Dispara el refresh del token y marca `initialized` |
| `src/infrastructure/store/auth/auth.store.ts` | Guarda `accessToken`, `user`, `initialized` |
| `src/infrastructure/http/axios.instance.ts` | Instancia de Axios; toma `baseURL` de `VITE_API_BASE_URL` |
| `.env.local` | Define `VITE_API_BASE_URL=http://localhost:8080` (no versionado) |
| `src/ui/router/index.tsx` | Define rutas, redirecciones por defecto y qué guard aplica a cada grupo |
| `src/ui/router/guards/AuthGuard.tsx` | Protege rutas de staff |
| `src/ui/router/guards/PlatformGuard.tsx` | Protege rutas de plataforma |
