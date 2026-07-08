# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See also [../docs/gym-member-pwa/INDEX.md](../docs/gym-member-pwa/INDEX.md) for the backlog and centralized docs.

**When you complete or add a pending task, update [../docs/gym-member-pwa/pendientes-backlog.md](../docs/gym-member-pwa/pendientes-backlog.md) (or pendientes-checkin-qr.md if it's about the QR check-in flow) in the same task. When you add or change a flow, store, or convention described below, update this file too.**

## Commands

```bash
npm run dev        # Start dev server (Vite HMR)
npm run build      # tsc -b && vite build (type-check + bundle)
npm run lint       # ESLint static analysis
npm run preview    # Preview production build
```

No test runner is configured.

## Environment

Copy `.env.example` to `.env.local` and set all variables:

```
VITE_AUTH_API_URL=http://localhost:8080/api/v1
VITE_CORE_API_URL=http://localhost:8083/api/v1
VITE_ATTENDANCE_API_URL=http://localhost:8082/api/v1
VITE_GOOGLE_CLIENT_ID=     # opcional — botón de Google se oculta si está vacío
VITE_FACEBOOK_APP_ID=      # opcional — botón de Facebook se oculta si está vacío
VITE_PORT=5173
```

Three backend microservices: **auth-service** (8080), **core-service** (8083), **attendance-service** (8082). Each has its own Axios instance in `src/infrastructure/http/`.

`VITE_GOOGLE_CLIENT_ID` and `VITE_FACEBOOK_APP_ID` are optional. When empty, `App.tsx` skips mounting `GoogleOAuthProvider` entirely, and `LoginPage.tsx` hides the social buttons via `GOOGLE_ENABLED`/`FB_ENABLED` module-level constants evaluated at build time.

## Key dependency versions

React 19, TypeScript 6, Tailwind CSS v4, Zod v4, Zustand v5, React Hook Form v7. Use APIs from these versions — older patterns (e.g., Zustand v4 `devtools`, Zod v3 `z.string().email()` unchanged but note any v4 breaking changes) don't apply.

## Architecture

The app follows a layered/clean architecture split across four layers:

- **`src/domain/`** — Interfaces and business types (`ClienteToken`, `AuthState`, `AuthRepository` port).
- **`src/application/`** — DTOs / use-case request-response types.
- **`src/infrastructure/`** — Concrete implementations: three Axios instances, repository classes, and three Zustand stores.
- **`src/ui/`** — React pages, guards, layouts, and custom hooks.

### Routing & navigation

`App.tsx` sets up `BrowserRouter`. All authenticated routes are wrapped by `ClienteGuard` (`src/ui/guards/`), which checks the Zustand store and redirects to `/login` when unauthenticated. Authenticated pages render inside `AppLayout`, which provides the bottom navigation bar.

Public routes (no `ClienteGuard`): `/login`, `/forgot-password`, `/reset-password`.

All other paths fall through to `<Navigate to="/check-in" />`.

### State management

Three Zustand stores, all persisted to `localStorage`:

- **`auth.store.ts`** (`gym-member-auth`) — `accessToken`, `refreshToken`, `user: ClienteToken | null`, `gymInfo: GymInfo | null`, `initialized`. Use `useCurrentUser()` and `useIsAuthenticated()` selectors. `gymInfo` holds `{ id_compania, id_sucursal, nombre_sucursal, logo_url, nombre_compania }` — populated when the user scans a gym QR on login; required for the app-based check-in flow.
- **`theme.store.ts`** (`gym-member-theme`) — `theme: ThemeId`, `userCustomized: boolean`. `initTheme(sexo)` sets a gender-based default only if the user hasn't chosen manually.
- **`perfil.store.ts`** (`gym-member-perfil`) — `data: MiPerfilResponse | null`, `fetchedAt`. Caches the full membership profile with a 5-minute stale window. Use `isPerfilStale(fetchedAt)` to decide whether to re-fetch. Call `invalidate()` after any mutation that changes membership state.

### JWT payload structure (`ClienteToken`)

The decoded access token contains:
- `sub` — auth account ID (integer as string); **not** the persona/client ID
- `id_persona` — persona record ID in core-service
- `id_compania` — company the user belongs to
- `sexo` — `'M'` | `'F'` | `null`, used to pick the default theme
- `nombre`, `nombre_compania`, `logo_url`, `foto_url`

`sub` and `id_persona` are different IDs. Backend services use `id_persona` for membership/attendance queries; never pass `sub` where `id_persona` is expected.

### HTTP clients & token refresh

Three Axios instances with **different interceptor setups**:

- **`axios.instance.ts`** (auth-service) — has both request interceptor (injects Bearer token) **and** response interceptor: on 401, queues pending requests, calls `/auth/refresh` once, replays the queue; if refresh fails, clears the store and redirects to `/login`.
- **`core.instance.ts`** and **`attendance.instance.ts`** — have **only the request interceptor** (Bearer token injection). No 401 refresh logic on these instances; they rely on the access token being valid.

### Theme system

Six color themes (`acero`, `volcan`, `bosque`, `coral`, `violeta`, `aurora`) implemented as CSS custom property overrides on the `[data-theme]` attribute of `<html>`. The set of `--accent-300` through `--accent-900` variables (and some `--color-slate-*`) are redefined per theme in `src/index.css`.

**Always use `accent-*` Tailwind classes** (e.g., `text-accent-400`, `bg-accent-600`) for interactive and brand elements — never hardcode `blue-`, `orange-`, or other color scales. Components using `slate-*` darken automatically per theme. `App.tsx` applies the theme via `document.documentElement.setAttribute('data-theme', theme)` on mount and on change.

Shared UI components in `src/ui/components/`:
- **`LangToggle.tsx`** — language switcher used in LoginPage and ProfilePage.
- **`PulseBackground.tsx`** — animated background (blurred anatomical heart rings + scrolling ECG line) rendered on all authenticated pages. Uses `@keyframes heartPulseRing` and `@keyframes ecgScroll` defined in `src/index.css`. Uses `accent-*` tokens so it adapts to the active theme automatically. Do not remove those keyframes — they are referenced by inline `style.animation` in the component.
- **`InstallBanner.tsx`** — PWA install prompt banner; driven by `src/ui/hooks/useInstallPrompt.ts` which captures the `beforeinstallprompt` event and tracks dismissal in `localStorage`.

`ThemeSelector.tsx` is co-located with `ProfilePage` at `src/ui/pages/profile/ThemeSelector.tsx` — it is not in `src/ui/components/` because it is not reused elsewhere.

All other page sub-components are defined at the bottom of their page file (see sub-component pattern below).

### Login & self-registration flow

`LoginPage` has two modes toggled by a tab switcher: **"Iniciar sesión"** and **"Crear cuenta"**. The tabs only appear when `gymInfo` is available (from QR scan or localStorage cache). The `id_compania` field is never shown to the user when `gymInfo` is set — it is resolved silently from the store.

**QR-to-login flow**: when the app opens with `?qr=<token>` in the URL, `LoginPage` calls `authRepository.getGymByQr(token)` to verify and store `gymInfo` before the user logs in. After successful auth, the QR token is passed via `location.state.autoQrToken` to `CheckInPage`, which fires an immediate check-in.

**Self-registration** (`POST /auth/app/registro`, public endpoint on auth-service):
- Request: `{ nombre, correo, password, id_compania, telefono? }`
- Backend finds-or-creates a `Persona` by `correo`, then creates `UsuarioApp` with `login=correo`
- Returns the same `LoginAppResponse` as login → user is logged in immediately after registering
- Frontend: `authRepository.registrar(req)` in `AuthHttpRepository`, type `RegistroAppRequest` in `auth.types.ts`

**`id_compania` resolution order** in all auth actions (login, register, Google, Facebook):
1. `gymInfo.id_compania` from Zustand store (preferred — never shown in UI)
2. `loginForm.getValues('id_compania')` — only visible when gymInfo is absent

The login background uses `@keyframes floatFitness` defined in `src/index.css` to animate floating fitness SVG icons. Do not remove that keyframe — it is referenced by inline `style.animation` in `LoginPage.tsx`.

### Check-in flow (two paths)

1. **QR path**: Login page sets `autoQrToken` on `location.state` when a gym QR is scanned. `CheckInPage` reads this from `useLocation()`, fires `checkInQr(autoQrToken)` immediately, and clears the state.
2. **App path**: Button on `CheckInPage` calls `checkInApp(gymInfo.id_sucursal, gymInfo.nombre_sucursal)`. The button is shown only when `gymInfo.id_sucursal` is set (requires scanning a gym QR at least once).

`CheckInPage` uses a `'idle' | 'loading' | 'success' | 'error'` discriminated-union state. The error branch runs a `detectErrorKind()` function that matches `codigo` and `mensaje` fields to five categories (`ya_registrado_hoy`, `sin_membresia`, `membresia_expirada`, `accesos_agotados`, `congelado`, `generic`), each rendering a distinct UI. `ya_registrado_hoy` is treated as a success variant, not an error.

Use `getApiErrorCode()` from `src/lib/api-error.ts` to branch on error codes; `getApiErrorMessage()` for the human-readable string. When the backend returns an HTTP response (any 4xx/5xx), the page UI handles the state — only show a toast for network failures (no response object on the error).

### Historial page

`HistorialPage` (`/historial`) fetches stats and attendance records **in parallel** via `Promise.all()`. It renders a 5-week heatmap grid by computing a Monday-offset (`(firstDay.getDay() + 6) % 7`) for the first day of the period, then color-coding each cell: emerald (attended), slate-600 (absent), slate-700 (no data), accent-800 (today without attendance). The list below the heatmap is capped at the first 20 records (`slice(0, 20)`). `metodo_registro` values `qr`, `manual`, `override` are mapped to display labels inline in the component.

### Forms

All forms use **React Hook Form** with **Zod** resolvers (`@hookform/resolvers/zod`). Define the Zod schema first, infer the TypeScript type with `z.infer<typeof schema>`, then pass the schema as the resolver.

### Styling

**Tailwind CSS v4** (via `@tailwindcss/vite` plugin — no separate config file needed). Import path alias `@/` maps to `src/`. CSS custom properties in `src/index.css` handle the bottom and top safe-area for notched devices (`--safe-bottom`, `--safe-top`) via `env(safe-area-inset-*)`.

Skeleton loading states use inline `div` elements with `motion-safe:animate-pulse` — no external skeleton library.

### i18n

`i18next` + `react-i18next`. Config in `src/i18n/index.ts`. Translations in `src/i18n/locales/es.json` and `en.json`. Default language: `es`. Hook: `const { t } = useTranslation()`.

Language selection is persisted via `localStorage('gym-lang')`. The `LangToggle` component (`src/ui/components/LangToggle.tsx`) calls `i18n.changeLanguage(lang)` + `localStorage.setItem('gym-lang', lang)` on click. It appears in two places:
- **Login page** — `fixed top-4 right-4 z-20`, floating above the card.
- **Profile page** — section card below `ThemeSelector`, labelled with `profile.language.title`.

When adding new UI text, always add the key to **both** `es.json` and `en.json`. Some keys use `defaultValue` fallback for dynamic lookups (e.g., `t('membresia.status.${estado}', { defaultValue: estado })`).

### PWA icons

Generated with `@vite-pwa/assets-generator` from `public/logo.svg`:
```bash
npx pwa-assets-generator --preset minimal-2023 public/logo.svg
```
Expected output: `public/icons/pwa-192x192.png` and `pwa-512x512.png`.

The Vite PWA plugin is configured with `registerType: 'autoUpdate'` and network-first caching for `/api/v1/` endpoints (10-second timeout).

### Key patterns to follow

- **Repository pattern**: data-fetching logic lives in `src/infrastructure/http/*Repository.ts`, not in components.
- **Sub-components**: define page-specific sub-components at the bottom of the same page file (e.g., `StatusBanner`, `MembresiaCard`, `EmptyState` all live inside `MembresiaPage.tsx`). Extract to `src/ui/components/` only when the component is reused across pages.
- **Custom hooks**: feature-specific logic (e.g., `useQrScanner`) is extracted into hooks alongside the page that uses them. `useQrScanner` uses `html5-qrcode` and manages scanner lifecycle and scan deduplication via `useRef`.
- **Error handling**: use `src/lib/api-error.ts` (`getApiErrorMessage`, `getApiErrorCode`) to extract human-readable message and business error code from Axios responses. Show `sonner` toasts only for unexpected errors; let the page UI handle known business error states.
- **Date formatting**: use `toLocaleDateString('es', { day: '2-digit', month: 'short', year: 'numeric' })` inline per page — there is no shared date utility.
