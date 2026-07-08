# IMPL_16 — Sistema de Carga Global (Top Loader + Puntos Pulsantes)

> **Estado:** Borrador v0.1 — listo para implementar  
> **Última actualización:** 2026-06-19

---

## Objetivo

Reemplazar los indicadores de carga dispersos por dos componentes unificados que cubren todos los contextos de espera de la aplicación, respetan el sistema de temas y dan retroalimentación visual coherente sin bloquear la UI innecesariamente.

---

## Los dos componentes

### 1. `TopLoader` — barra de progreso superior

Barra delgada fija en el borde superior del viewport. Se activa automáticamente en cada request HTTP y para navegaciones lentas.

**Comportamiento de la animación:**
1. Al iniciar: salta a `15%` instantáneamente (el usuario sabe que algo pasó).
2. Avanza con easing hasta `70%` en ~400ms (simula progreso real).
3. Frena y avanza muy lento hacia `85%` mientras espera respuesta (evita llegar al 100% antes de tiempo).
4. Cuando el backend responde: salta a `100%` en 150ms.
5. Fade out en 200ms y se resetea a `0%`.

**Especificación visual:**

```
┌──────────────────────────────────────────────────────────────────┐
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
└──────────────────────────────────────────────────────────────────┘
  ↑ position: fixed, top: 0, left: 0, right: 0
  ↑ height: 3px
  ↑ background: #f97316 (naranja, constante en todos los temas)
  ↑ z-index: 9999
  ↑ transition: width 150ms ease-out
```

Tiene un brillo sutil en el extremo derecho (`box-shadow: 0 0 8px #f97316, 0 0 4px #f97316`) para dar sensación de movimiento incluso cuando la barra avanza despacio.

**Cuándo se activa:**
- Automáticamente en cada request de los 4 interceptores axios (auth, platform, core, attendance).
- No se activa en requests silenciosos de refresh token (`/auth/refresh`) para no interrumpir al usuario sin causa aparente.

---

### 2. `PulsingDots` — puntos pulsantes inline

Tres puntos que se animan en secuencia de ola. Reemplaza al `Loader2` actual en todos los contextos inline.

**Especificación visual:**

```
  ● ● ●
  ↑ Cada punto: w-1.5 h-1.5 rounded-full
  ↑ Color activo: #f97316
  ↑ Color inactivo: var(--page-border)
  ↑ Animación: cada punto hace scale 1 → 1.4 → 1 con delay escalonado
               punto 1: delay 0ms
               punto 2: delay 150ms
               punto 3: delay 300ms
  ↑ Duración del ciclo: 900ms, infinite
```

**Variantes por tamaño:**

| Prop `size` | Puntos | Gap | Uso |
|-------------|--------|-----|-----|
| `sm` | `w-1 h-1` | `gap-1` | Dentro de botones, labels |
| `md` | `w-1.5 h-1.5` | `gap-1.5` | Cards, modales, steps del wizard |
| `lg` | `w-2 h-2` | `gap-2` | `FullScreenLoader` centrado en pantalla |

**Uso en botones:**
```tsx
<button disabled={isSubmitting}>
  {isSubmitting
    ? <span className="flex items-center gap-2">
        <PulsingDots size="sm" />
        Guardando...
      </span>
    : 'Guardar'}
</button>
```

---

## Arquitectura — store de carga

Un store Zustand mínimo controla el estado del `TopLoader`. Es el único estado global necesario — `PulsingDots` es stateless (solo recibe props).

```typescript
// src/infrastructure/store/loader/loader.store.ts

interface LoaderStore {
  activeRequests: number   // contador de requests en vuelo
  start: () => void        // incrementa contador
  done: () => void         // decrementa contador
  isLoading: () => boolean // activeRequests > 0
}
```

El uso de un **contador** (en lugar de un booleano) resuelve el problema de requests paralelos: si hay 3 requests en vuelo, `done()` debe llamarse 3 veces antes de que la barra desaparezca. Con un booleano, el primero en terminar apagaría la barra aunque los otros dos sigan.

---

## Integración con los interceptores axios

Los 4 interceptores actuales reciben las llamadas `start()` / `done()`. El estado actual de cada instancia difiere — adaptar según corresponde:

| Instancia | Request interceptor | Response interceptor |
|-----------|--------------------|--------------------|
| `axios.instance.ts` (auth) | ✅ Existe (agrega Bearer token) | ✅ Existe (manejo 401 + refresh queue) — **solo agregar `done()`** |
| `axios-platform.instance.ts` | ✅ Existe (agrega Bearer token) | ✅ Existe mínimo (reject) — **solo agregar `done()`** |
| `axios-core.instance.ts` | ✅ Existe (agrega Bearer token) | ❌ No existe — **crear desde cero** |
| `axios-attendance.instance.ts` | ✅ Existe (agrega Bearer token) | ✅ Existe mínimo (reject) — **solo agregar `done()`** |

### Patrón para instancias con response interceptor existente (auth, platform, attendance)

```typescript
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

// Request interceptor — ya existe, solo agregar start()
instance.interceptors.request.use((config) => {
  if (!config.url?.includes('/auth/refresh')) {
    useLoaderStore.getState().start()
  }
  // ... lógica existente de auth header sin cambios
  return config
})

// Response interceptor — ya existe, agregar done() en ambas ramas
instance.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    // ... lógica existente de manejo de errores sin cambios
    return Promise.reject(error)
  }
)
```

### Patrón para `axios-core.instance.ts` (crear response interceptor desde cero)

```typescript
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

// Request interceptor — ya existe, solo agregar start()
instance.interceptors.request.use((config) => {
  useLoaderStore.getState().start()
  // ... lógica existente de auth header sin cambios
  return config
})

// Response interceptor — NUEVO, no existía antes
instance.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    return Promise.reject(error)
  }
)
```

**Importante:** `done()` debe llamarse en **ambas** ramas del interceptor de respuesta (éxito y error) para que el contador no quede desfasado si una request falla.

---

## Integración en App.tsx

`TopLoader` se monta una sola vez en el root, fuera de cualquier layout, para garantizar que esté siempre disponible independientemente de la ruta activa:

```tsx
// src/App.tsx
import { TopLoader } from '@/ui/components/TopLoader'

function App() {
  return (
    <>
      <TopLoader />           {/* ← fixed top-0, siempre montado */}
      <RouterProvider ... />
    </>
  )
}
```

---

## Reemplazo del `FullScreenLoader` actual

El `FullScreenLoader` existente (`src/ui/components/FullScreenLoader.tsx`) usa un ícono `Dumbbell` con `animate-pulse` sobre fondo `bg-gym-950`. Se actualiza para usar `PulsingDots size="lg"` manteniendo el mismo ícono y estructura:

```
┌──────────────────────────────────────┐
│                                      │
│        ┌─────────────────┐           │
│        │   🏋️ (naranja)  │  ← w-14 h-14 rounded-2xl, bg-orange-500
│        └─────────────────┘           │
│                                      │
│              ● ● ●                   │  ← PulsingDots size="lg"
│                                      │
│          Cargando...                 │  ← text-slate-500 text-sm
│                                      │
└──────────────────────────────────────┘
```

El `animate-pulse` del ícono se elimina — los puntos ya comunican el estado de carga. El ícono queda estático como logo.

---

## Archivos a crear

| Archivo | Descripción |
|---------|-------------|
| `src/infrastructure/store/loader/loader.store.ts` | Store Zustand con contador de requests activos |
| `src/ui/components/TopLoader.tsx` | Barra de progreso fixed top-0 |
| `src/ui/components/PulsingDots.tsx` | Tres puntos animados, prop `size` |

## Archivos a modificar

| Archivo | Cambio |
|---------|--------|
| `src/infrastructure/http/axios.instance.ts` | Agregar `start()`/`done()` en interceptores |
| `src/infrastructure/http/platform/axios-platform.instance.ts` | Ídem |
| `src/infrastructure/http/core/axios-core.instance.ts` | Ídem |
| `src/infrastructure/http/attendance/axios-attendance.instance.ts` | Ídem |
| `src/App.tsx` | Montar `<TopLoader />` en el root |
| `src/ui/components/FullScreenLoader.tsx` | Reemplazar `animate-pulse` por `PulsingDots size="lg"` |

## Usos de `Loader2` a reemplazar por `PulsingDots`

Una vez creado `PulsingDots`, reemplazar los usos actuales de `Loader2` de lucide-react en botones y estados de carga inline a lo largo de toda la app:

| Contexto | Size recomendado |
|----------|-----------------|
| Botones de submit (`LoginPage`, wizard steps, modales) | `sm` |
| Cards cargando data (Step3Plan, tablas) | `md` |
| `FullScreenLoader` | `lg` |

---

## Animación CSS

Los puntos pulsantes requieren un keyframe personalizado en `src/index.css`:

```css
@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 0.4; }
  50%       { transform: scale(1.4); opacity: 1; }
}

.pulse-dot-1 { animation: pulse-dot 900ms ease-in-out infinite; }
.pulse-dot-2 { animation: pulse-dot 900ms ease-in-out 150ms infinite; }
.pulse-dot-3 { animation: pulse-dot 900ms ease-in-out 300ms infinite; }
```

La transición del `TopLoader` se maneja con `transition-[width]` de Tailwind + `duration-150` para el avance rápido, y una clase CSS inline para el avance lento durante la espera.

---

## Consideraciones

- **No hay dependencia de librería externa** (no NProgress, no react-top-loading-bar). Todo es CSS + Zustand que ya está en el proyecto.
- **El `TopLoader` es puramente visual** — no afecta el comportamiento de ningún request ni bloquea la interacción del usuario.
- **Compatibilidad con temas:** el naranja `#f97316` es el acento constante en todos los temas según `DESIGN_GUIDELINES.md` — no requiere variables CSS adicionales.
- **El contador de requests** previene el parpadeo cuando hay requests paralelos (típico en páginas que cargan datos de múltiples endpoints al mismo tiempo).
