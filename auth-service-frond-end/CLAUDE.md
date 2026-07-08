# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Este frontend consume 4 backends — cuando el detalle de un endpoint importa, la verdad vive en la API del backend correspondiente:

| Área | Documento | Estado |
|------|-----------|--------|
| Contrato API auth-service | [../docs/auth-service/api/](../docs/auth-service/INDEX.md) | ✅ Verificado contra código backend |
| Contrato API core-service (clientes) | [../docs/core-service/api/clientes.md](../docs/core-service/api/clientes.md) | ✅ Verificado contra código backend |
| Convenciones de diseño y UI | [../docs/auth-service-frond-end/design-guidelines.md](../docs/auth-service-frond-end/design-guidelines.md) | 🟡 Referencia — verifica contra código si el detalle importa |
| Índice de docs de este frontend | [../docs/auth-service-frond-end/INDEX.md](../docs/auth-service-frond-end/INDEX.md) | 🟡 Índice — los `impl/*.md` son 📜 histórico de implementación ya completada |

Los archivos bajo `docs/auth-service-frond-end/impl/`, `backend-prompts/` y `preguntas/` son 📜 **histórico** — describen cómo se construyó, no cómo funciona hoy. Para el estado actual, mira el código y los contratos ✅ de los backends.

## Commands

```bash
npm run dev                  # Start Vite dev server at http://localhost:5173
npm run build                # tsc -b && vite build → outputs to dist/
npm run lint                 # ESLint
npm run preview              # Preview production build locally
npm run test:integration     # Run integration tests against live backend (vitest)
```

Environment: copy `.env.example` to `.env.local` and set:
- `VITE_API_AUTH_URL` — auth-service base URL (default: `http://127.0.0.1:8080/api/v1`)
- `VITE_API_PLATFORM_URL` — platform-service base URL (default: `http://127.0.0.1:8081/api/v1`)
- `VITE_API_CORE_URL` — core-service base URL (default: `http://127.0.0.1:8083/api/v1`)
- `VITE_API_ATTENDANCE_URL` — attendance-service base URL (default: `http://127.0.0.1:8084/api/v1`)
- `VITE_APP_NAME` — application display name
- `VITE_CLIENT_APP_URL` — URL for the gym member PWA (default: `http://localhost:5174`)
- `VITE_AVATAR_HOMBRE_URL` / `VITE_AVATAR_MUJER_URL` — default profile avatar images
- `VITE_AVATAR_LOGO_COMPANY` — default company logo for newly registered gyms
- `VITE_API_BASE_URL` — legacy alias for `VITE_API_AUTH_URL` (kept for backwards compat)

Integration tests read additional env vars (optional — tests skip when absent):
- `TEST_API_URL` — auth-service base (default: `http://localhost:8080/api/v1`)
- `TEST_PLATFORM_URL` — platform-service base (default: `http://localhost:8081/api/v1`)
- `TEST_CORE_URL` — core-service base (default: `http://localhost:8083/api/v1`)
- `TEST_STAFF_EMAIL` / `TEST_STAFF_PASSWORD` — real staff credentials
- `TEST_PLATFORM_EMAIL` / `TEST_PLATFORM_PASSWORD` — real platform credentials

## Architecture

This project uses **hexagonal architecture** with a strict dependency direction:

```
Domain ← Application ← Infrastructure ← UI
```

| Layer | Path | Responsibility |
|-------|------|----------------|
| Domain | `src/domain/` | Entities (`User`, `Token`, `Plan`) and port interfaces |
| Application | `src/application/` | Use cases per feature (auth, platform, core) |
| Infrastructure | `src/infrastructure/` | Axios HTTP repositories, Zustand stores |
| UI | `src/ui/` | React pages, layouts, guards, shared components |
| shadcn/ui | `src/components/ui/` | Pre-built UI primitives (do not modify these directly) |

### Four backend microservices

The frontend talks to four independent services, each with its own Axios instance:

| Service | Default port | Axios instance |
|---------|-------------|----------------|
| auth-service | 8080 | `src/infrastructure/http/auth/axios.instance.ts` (also `src/infrastructure/http/axios.instance.ts`) |
| platform-service | 8081 | `src/infrastructure/http/platform/axios-platform.instance.ts` |
| core-service | 8083 | `src/infrastructure/http/core/axios-core.instance.ts` |
| attendance-service | 8084 | `src/infrastructure/http/attendance/axios-attendance.instance.ts` |

All instances share the same interceptor pattern: attach `Authorization: Bearer {token}` and handle 401 with queued-request retry.

### Application layer use cases

Use cases are **thin orchestrators** — each is a class wrapping one repository, with methods that call repo methods directly with no added business logic. They live under `src/application/<feature>/`. Groups:
- **auth** (5): `LoginStaff`, `LoginPlatform`, `RefreshToken`, `Logout`, `AutoRegistro`
- **platform** (9): `GetPlanes`, `GestionarPlan`, `GetCaracteristicas`, `GestionarCompania`, `GestionarSucursal`, `GestionarSuscripcion`, `GestionarPago`, `GestionarNotifConfig`, `RegistrarGymWizard`
- **core**: no use case layer — pages call `coreRepository` directly. Methods group into: tipos de membresía (CRUD + deactivate), clientes (CRUD + search by CI + plataforma variants), membresías (sell, cancel, update prior attendances), congelamientos (freeze, reactivate, list).
- **attendance**: no use case layer — pages call `attendanceRepository` directly. Methods: `getAsistenciasHoy`, `getEstadisticas`, `getAsistenciasUltimos30`, `getHistorialCliente`, `getRachaPerfecta`, `registrarManual`.

### Domain entities

`src/domain/platform/entities/Plan.entity.ts` defines 8 types: `Plan`, `Caracteristica`, `PlanActivo`, `Compania`, `Sucursal`, `CompaniaPlan`, `Pago`, `NotifConfig`. The filename doesn't reflect its full scope.

`src/domain/platform/ports/PlatformRepository.port.ts` is a single large interface with 35+ methods spanning all platform entities — it's the single port for the entire platform domain.

## Key Patterns

**JWT token types** — Three token types exist, discriminated by the `tipo` field in the payload:
- `staff` — admin user; carries `id_compania`, `id_sucursal`, `id_rol`, `permisos[]`
- `plataforma` — SaaS operator; carries `rol_plataforma` (`super_admin` | `soporte` | `viewer`)
- `cliente` — gym member app user; carries `id_compania`, `id_persona`

**State management** — Zustand store at `src/infrastructure/store/auth/auth.store.ts` holds `accessToken`, `user`, and `initialized`. Prefer the computed hooks (`useIsAuthenticated`, `useCurrentUser`, `useHasPermission`, `useIsPlatformUser`) over reading store state directly. `useHasPermission(permiso)` is staff-only — it returns `false` for all other token types.

**Platform store** — `src/infrastructure/store/platform/platform.store.ts` holds `companias[]`, `selectedCompania`, `planes[]`, `caracteristicas[]`. It is pure client state with no auto-refresh; populate it explicitly in page effects and update it after mutations.

**Loader store** — `src/infrastructure/store/loader/loader.store.ts` tracks in-flight Axios requests (`start()` / `done()` / `isLoading()`). Wired into all four Axios interceptors automatically; drives the `TopLoader` progress bar in the layout headers. Do not call `start()`/`done()` manually from components.

**Refresh token** — Stored in `sessionStorage` under key `'auth_rt'` via helpers in `src/lib/refresh-token-storage.ts` (`getStoredRefreshToken`, `storeRefreshToken`, `clearStoredRefreshToken`). Retrieved by `RefreshTokenUseCase` on app mount.

**Authentication flow** — On app mount, `App.tsx` calls `RefreshTokenUseCase` to restore the session before rendering routes. The `initialized` flag gates route rendering to avoid flashing login pages.

**Route guards** — Three guards exist in `src/ui/router/guards/`:
- `AuthGuard` — protects staff routes; checks `initialized` then `tipo === 'staff'`
- `PlatformGuard` — protects platform routes; checks `tipo === 'plataforma'`
- `PermissionGuard` — per-route permission check; redirects to `/admin/sin-acceso` if missing
- `IfPermission` — **same file as `PermissionGuard`**; inline conditional render (returns `null`, no redirect). Use inside pages: `<IfPermission permiso="roles:editar"><Button /></IfPermission>`

All guards render `<FullScreenLoader />` while `initialized === false` to avoid flashing the login page on refresh.

**Route structure** — Three layout groups in `src/ui/router/index.tsx`:
- `PublicLayout`: login, reset-password routes (no auth required)
- `AdminLayout`: 11+ staff routes under `/admin/*`; each route has a `permiso` field checked by `PermissionGuard`
- `PlatformLayout`: 8+ platform operator routes under `/platform/*`; some routes are restricted to `super_admin` via a `roles` field on `NavItem`

**Forms** — React Hook Form + Zod schemas (in `src/ui/features/<feature>/schemas/`). Always co-locate the Zod schema with its form feature.

**Path alias** — `@/` resolves to `src/`. Use it for all imports outside the current file's directory.

**UI components** — Use shadcn/ui primitives from `src/components/ui/`. Add new shadcn components with `npx shadcn@latest add <component>`. For data tables and complex UI, PrimeReact components are also in use (wrapped inside `PrimeReactProvider` in `App.tsx`). Style with Tailwind CSS; merge classes with `cn()` from `src/lib/utils.ts`.

Shared page-level components in `src/ui/components/`:
- `PageHeader` — title + optional description + optional action slot; use at the top of every admin page
- `ConfirmDialog` — reusable confirmation modal with `destructive` flag; prefer this over PrimeReact's `confirmDialog()` imperative API for consistency
- `PrintQrModal` — QR code print dialog (reusable)
- `VenderMembresiaModal` (`src/ui/features/core/components/`) — sells a membership to an existing client; accepts `idCliente`, `nombreCliente`, `open`, `onClose`, `onVendida`; usable from any page

**PrimeReact DataTable global styles** — `src/index.css` contains a generic rule targeting `.p-datatable .p-datatable-tbody > tr > td` that sets `font-size`, `padding`, `line-height`, and `vertical-align` for all tables. Do not add per-column `fontSize` styles; the global rule handles it. Use `pt` (passthrough) on action icon buttons to add `!py-0.5 !px-1` — otherwise PrimeReact's default button height inflates row height.

**HTTP repositories** — Each service has a singleton repository exported from its module:
- `authRepository` from `src/infrastructure/http/auth/AuthHttpRepository.ts`
- `coreRepository` from `src/infrastructure/http/core/CoreRepository.ts` (exported functions, not a class)
- `platformRepository` from `src/infrastructure/http/platform/PlatformHttpRepository.ts`
- `attendanceRepository` from `src/infrastructure/http/attendance/AttendanceHttpRepository.ts`

Import the singleton, not the class.

**Utility libraries** (`src/lib/`):
- `api-error.ts` — `getApiErrorMessage()`, `getApiErrorStatus()` for Axios error handling
- `jwt.ts` — `decodeJwt(token)`, `isTokenExpired(token)` (client-side decode only, no signature verification)
- `refresh-token-storage.ts` — sessionStorage helpers for refresh token persistence
- `utils.ts` — `cn()` Tailwind class merge

**Error handling** — Use `getApiErrorMessage()` and `getApiErrorStatus()` from `src/lib/api-error.ts` when catching Axios errors. Show user feedback via Sonner (`toast.error`, `toast.success`).

**i18n** — Configured in `src/i18n/index.ts` with `es` as default and `en` as fallback. Use the `useTranslation` hook for all user-visible strings. Locale files live at `src/i18n/locales/`.

## Theme system

Themes are managed by `useThemeStore` (`src/infrastructure/store/theme/theme.store.ts`) and applied as `data-layout` on `document.body`. Available themes: `light`, `dark`, `dark-blue`, `ocean-blue`, `slate-carbon`, `mint-pastel`.

**Never hardcode Tailwind color classes in page components.** Use CSS variables instead:

```css
var(--page-bg)      /* main content background */
var(--page-border)  /* dividers */
var(--page-text)    /* primary text */
var(--page-muted)   /* secondary text / labels */
var(--page-surface) /* cards, tab headers */
var(--input-bg)     /* input/select backgrounds */
```

To add a new theme: add it to `AppTheme` in `theme.store.ts`, define its 17 CSS variables in `src/index.css`, add PrimeReact overrides, and register it in `THEMES` in all three layout files (`PlatformLayout`, `AdminLayout`, `PublicLayout`).

The store calls `syncUser(sub)` automatically from `auth.store.ts` on login/logout — do not call it manually from components.

## Integration tests

Tests in `src/__tests__/integration/` run against live backends. The global setup (`helpers/setup.ts`) waits for all four microservices (8080, 8081, 8083, 8084) with a 2s retry loop and 60s timeout before any test runs. If a service is unavailable, tests for that service are skipped, not failed.

Test HTTP clients (`helpers/client.ts`) never throw on HTTP errors (`validateStatus: () => true`) — tests assert on `response.status` directly.

The JWT helper (`helpers/jwt.ts`) generates signed test tokens for all role types without hitting the auth service.

## Monorepo ecosystem

This frontend is one folder in the `gym-administrator` monorepo (`C:\Respos\own-aplications\`). See [../INDEX.md](../INDEX.md) for the full service map.

| Folder | Role |
|---|---|
| **auth-service-frond-end** | This React frontend |
| **auth-service** | Spring WebFlux, port **8080** |
| **platform-service** | Spring WebFlux, port **8081** |
| **core-service** | Spring WebFlux (R2DBC + PostgreSQL), port **8083** |
| **gym-administrator** | Liquibase migrations for the full DB (`gym_administrator`) — docs centralized in `../docs/gym-administrator/` |

### core-service (puerto 8083)

- Framework: Spring WebFlux (reactivo), Java 21
- ORM: Spring Data R2DBC sobre PostgreSQL
- Auth: JWT — lee el mismo secreto que auth-service
- Jackson: configurado con `property-naming-strategy: SNAKE_CASE` → **todas las respuestas y requests usan snake_case**
- Controladores relevantes para este frontend:
  - `TipoMembresiaController` → `/api/v1/tipos-membresia`
  - `ClienteController` → `/api/v1/clientes` — soporta `?sin_membresia=true` para filtrar clientes sin membresía activa (LEFT JOIN `m.id IS NULL`)
  - `MembresiaController` → `/api/v1/membresias`
  - `CongelamientoController` → `/api/v1/congelamientos`
- Config en: `core-service/src/main/resources/application.yml`

### gym-administrator (Liquibase)

- Contiene **dos** sistemas de changelog:
  - `db/changelog/db.changelog-master.yaml` — scripts legacy v1.0 (algunos incompletos, p.ej. `007-core-tables.sql` no tenía `modo_control` ni `dias_acceso`)
  - `db/scripts/main-changelog.yml` → `202605_GYM-001/partial-changelog.yml` — **sistema activo**, scripts correctos y completos
- La tabla `core.tipos_membresia` correcta está en `db/scripts/202605_GYM-001/ddl/30_create_table_core_tipos_membresia.sql`
- Esquemas relevantes para el panel admin: `core`, `identidad`, `tenant`, `seguridad`, `config`

### auth-service (puerto 8080) — endpoints de usuario app

- `GET /api/v1/app-usuarios/por-ci/{ci}` — busca la cuenta app de un cliente por CI; requiere token `staff`; devuelve `{ id, login, activo, ultimo_acceso }`
- `PATCH /api/v1/app-usuarios/{id}` — actualiza `login` y/o `password`; valida conflicto de login antes de guardar

### Convención JSON entre frontend ↔ backend

**Todos los DTOs del frontend usan snake_case** (ej. `modo_control`, `duracion_tipo`).
El `core-service` tiene `spring.jackson.property-naming-strategy: SNAKE_CASE` para mantener esta consistencia.
Si agregas un nuevo endpoint en el backend, verifica que el `TipoMembresiaResponse` (o el nuevo DTO) use nombres de campo Java estándar camelCase — Jackson los convertirá automáticamente a snake_case al serializar.

## Implementation Roadmap

See [../docs/auth-service-frond-end/INDEX.md](../docs/auth-service-frond-end/INDEX.md) for the full documentation index. Detailed specs for each module live in [../docs/auth-service-frond-end/impl/](../docs/auth-service-frond-end/impl/) (02 through 18). Before implementing any new module, read the corresponding `impl/NN-*.md` file and [../docs/gym-administrator/frontend/auth-frontend-spec.md](../docs/gym-administrator/frontend/auth-frontend-spec.md) for the backend API contract. [../docs/auth-service-frond-end/design-guidelines.md](../docs/auth-service-frond-end/design-guidelines.md) is the canonical reference for visual/UX conventions.

**When you add or change a route, permission, use case, or pattern described above, update this file in the same task. When you complete an implementation step, update the matching file in `../docs/auth-service-frond-end/impl/`.**
