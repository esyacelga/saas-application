# gym-member-pwa — Tareas Pendientes

> **ESTADO:** 📜 Backlog — tareas pendientes, NO estado actual del código. Algunas ya pueden estar hechas; verificar contra el código. Ver [../STATUS.md](../STATUS.md).

Estado auditado: 2026-06-14  
Contexto: PWA React 19 / Vite 8 / Tailwind v4 / Zustand / React Router v7

---

## Resumen ejecutivo

El núcleo de la aplicación está completo: login manual, CheckIn QR, Historial 30 días con heatmap, Membresía y Perfil. Las tareas pendientes son mejoras de producto (OAuth, reset de contraseña, deep-link QR) e infraestructura de soporte (íconos PWA, i18n, documentación).

---

## Tarea 1 — OAuth Login UI ✅ Completada (2026-07-16)

Login con Google y Facebook + flujo "completar registro" cuando el usuario no existe en el gimnasio actual.

**Contratos actuales:** ver [`../auth-service/api/auth.md`](../auth-service/api/auth.md) (`/auth/app/oauth/google`, `/auth/app/oauth/facebook`, `/auth/app/oauth/completar-registro`).

- El response de `oauth/google` y `oauth/facebook` ahora es `OAuthLoginResponse` con `status = "logged_in" | "registro_pendiente"`.
- Cuando `status = "registro_pendiente"`, `LoginPage.tsx` cambia a la sub-vista `CompletarRegistroOAuth.tsx` (co-locada) que solicita documento de identidad (cédula, pasaporte, RUC o cualquier documento numérico — mínimo 3 caracteres), nombre editable y teléfono opcional; luego llama `POST /auth/app/oauth/completar-registro`.
- El servidor calcula automáticamente `ciValidada` (bandera de validez según algoritmo ecuatoriano) en la `Persona` creada — `true` solo si es cédula EC válida, `false` para documentos extranjeros o inválidos. El registro nunca se rechaza por este valor.
- El id_token/access_token vive solo en memoria del componente (no en router state ni localStorage) por su corta expiración.

**Cambios en 2026-07-17:** El campo de documento (antes limitado a 10/13 dígitos para "Cédula o RUC") ahora acepta cualquier documento numérico de cualquier longitud para soportar documentos de socios extranjeros. La validación del dígito verificador y la población de `ciValidada` ocurren en el servidor.

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

## Tarea 5 — Solicitud de Membresía ✅ Completada (2026-07-20)

**Prioridad:** Media  
**Estimado:** 4–5 h (backend + frontend)  
**Estado:** Completada — backend ya estaba desplegado; frontend implementado en esta sesión.  

### Descripción

Cuando un cliente entra a la sección `/membresia` sin membresía activa, en lugar de mostrar solo un estado vacío ("Sin membresía activa"), debe mostrar un catálogo de tipos de membresía disponibles en su gimnasio. El cliente puede seleccionar uno, generar una solicitud de membresía y el dueño la ve en su dashboard para completar el pago.

Nuevos sub-componentes:
- `<CatalogoMembresias/>` — lista de tipos disponibles con precio, modo (calendario/accesos), duración. Botón "Solicitar" genera solicitud.
- `<SolicitudPendienteCard/>` — muestra solicitud en proceso de pago. Sin botón cancelar (solo staff puede).

### Especificación detallada

Consultar: [`../gym-member-pwa/spec-solicitud-membresia.md`](./spec-solicitud-membresia.md) para UX, componentes, flujos de error e i18n keys.

### Dependencias backend

1. **Endpoint nuevo:** `GET /api/v1/tipos-membresia` — lista de tipos con `id`, `nombre`, `precio`, `modo_control` (enum: `calendario` | `accesos`), duración.

2. **Endpoint nuevo:** `POST /api/v1/clientes/me/membresias/solicitar` — crea solicitud con `{ id_tipo_membresia: number }`. Response: `{ id, id_tipo_membresia, tipo_nombre, precio_actual, creacion_fecha }`. Errores:
   - `409` con `codigo=solicitud_ya_existe` → cliente ya tiene solicitud pendiente.

3. **Campo en `MiPerfilResponse`:** extender para incluir `solicitud_pendiente: { id, id_tipo_membresia, tipo_nombre, precio_actual, creacion_fecha } | null`.

### Requisitos de implementación

1. **Logic en `MembresiaPage.tsx`** (3 branches):
   - `data?.membresia_activa` → tarjeta actual (sin cambios).
   - `!activa && data?.solicitud_pendiente` → `<SolicitudPendienteCard/>`.
   - `!activa && !solicitud_pendiente` → `<CatalogoMembresias/>`.

2. **`CatalogoMembresias` component**:
   - Fetch via `coreRepository.getTiposMembresia()` (nuevo).
   - Cards por tipo con nombre, precio, badge de modo, duración legible.
   - Modal de confirmación al hacer "Solicitar".
   - POST via `coreRepository.solicitarMembresia(id_tipo_membresia)`.
   - Éxito: toast + invalidar store + re-fetch.
   - Error 409 `solicitud_ya_existe`: toast "Ya tienes una solicitud pendiente" + re-fetch.

3. **`SolicitudPendienteCard` component**:
   - Muestra nombre tipo, precio, "Solicitud enviada — acércate a caja para completar el pago".
   - Tiempo relativo (ej. "Hace 5 minutos").
   - Icono reloj (`lucide-react`, ya instalado).
   - Sin botón cancelar.

4. **i18n keys nuevas** (detallar en spec):
   - `membresia.catalogo.titulo`, `membresia.catalogo.solicitar`, `membresia.catalogo.modo.*`, etc.
   - `membresia.solicitud.titulo`, `membresia.solicitud.mensaje`, `membresia.solicitud.hace`.

### Relacionado con

- Backend: `core-service` endpoints de tipos-membresia y solicitudes.
- Cross-cutting: Ver [`docs/gym-administrator/requirements/solicitudes-membresia.md`](../gym-administrator/requirements/solicitudes-membresia.md) para requisitos completos del feature.

---

## Tarea 6 — CLAUDE.md del proyecto

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

## Tarea 7 — Infraestructura i18n

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
| 1 | OAuth Login UI | ✅ Completada (2026-07-16) | — |
| 2 | Íconos PWA | Pendiente | Instalación en dispositivo |
| 3 | Flujo password reset | Pendiente | — |
| 4 | QR deep-link | Pendiente | — |
| 5 | Solicitud de Membresía | ✅ Completada (2026-07-20) | — |
| 6 | CLAUDE.md | Pendiente | — |
| 7 | i18n | Pendiente | — |

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
