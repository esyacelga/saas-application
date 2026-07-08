# IMPL_15 — Auto-Registro de Usuarios Staff

> **Estado:** Borrador v0.4 — revisado desde perspectiva de usuario real  
> **Módulo:** Pantalla pública de login (`/login`) → nueva ruta pública `/registro`  
> **Última actualización:** 2026-06-19

---

## Objetivo

Permitir que el dueño de un gimnasio pueda **registrarse de forma autónoma** en el sistema sin necesidad de que un operador de plataforma lo cree manualmente. El flujo parte desde un enlace "Crear cuenta" en la pantalla de login de staff (`LoginPage.tsx`).

---

## Contexto y motivación

Actualmente el único camino para registrar un gimnasio nuevo es que un operador de plataforma use el **Gym Wizard** desde `/platform/companias` (protegido por `PlatformGuard`, rol `super_admin`). El wizard llama a `POST /companias/wizard` en el auth-service y crea compañía + sucursal + usuario admin en una sola transacción.

El auto-registro replica ese mismo flujo pero iniciado por el propio cliente final, sin token de plataforma.

---

## Decisiones tomadas

| # | Decisión | Resolución |
|---|----------|------------|
| 1 | Activación de la cuenta | **Inmediata** — el usuario puede ingresar al panel al finalizar el registro |
| 2 | Verificación de correo | **No en MVP** — se agrega en una fase posterior |
| 3 | Selección de plan | **Sí, explícita** — se muestra la lista de planes incluyendo el plan gratuito configurable; el usuario elige |
| 4 | Usuarios adicionales | **No en MVP** — el admin los agrega desde el panel luego |
| 5 | Correo ya registrado | Backend devuelve 409 con campo `conflicto: 'correo' | 'ci' | 'ruc'` — ver tabla de errores |
| 6 | Planes de pago en el selector | **Solo planes activos, el backend valida que tenga precio 0** en MVP. Si `precioMensual > 0`, la tarjeta se muestra con badge "Próximamente" deshabilitada — no se puede seleccionar hasta implementar pasarela de pago |
| 7 | Navegación con Back button del browser | El wizard **no usa subrutas** — si el usuario sale con Back, pierde el progreso. Se muestra `beforeunload` warning nativo (solo aplica si hay datos llenados). No se bloquea la salida, solo se advierte |
| 8 | Usuario ya autenticado llega a `/registro` | Redirigir siempre a `/admin/dashboard` independientemente del tipo de token (`staff`, `plataforma`, `cliente`) |

---

## Impacto en backend

### ¿Dónde viven los planes?

Los planes están en el **platform-service (puerto 8081)**, gestionados por `PlatformHttpRepository`. El endpoint actual `GET /planes` requiere token de plataforma.

**Decisión MVP:** El wizard llama a `GET /api/v1/planes` usando una **instancia axios pública** (sin interceptor de auth). Para que esto funcione, **el backend debe permitir este endpoint sin autenticación** — o se crea el endpoint `/planes/publicos` que no requiera token.

> ⚠️ **Aclaración importante:** El `GET /planes` actual del platform-service valida el token de plataforma. Hay dos opciones:
> - **Opción A (recomendada MVP):** El backend agrega una excepción de seguridad para `GET /planes` sin token (o crea `GET /planes/publicos`).
> - **Opción B (alternativa):** El backend devuelve 200 pero filtra los campos sensibles cuando no hay token (mismo endpoint, respuesta reducida).
>
> **Decisión elegida: Opción A** — se crea `GET /api/v1/planes/publicos` en platform-service que devuelve solo planes activos sin requerir token. El frontend llama a este endpoint con la instancia pública.

```typescript
// src/infrastructure/http/platform/axios-platform-public.instance.ts
// Instancia sin interceptor de Authorization — solo para endpoints públicos
const platformPublicInstance = axios.create({
  baseURL: import.meta.env.VITE_PLATFORM_API_URL ?? 'http://localhost:8081/api/v1',
})
```

- Requiere **un endpoint nuevo mínimo en el backend**: `GET /planes/publicos` (sin auth, devuelve solo `activo: true`).
- El frontend filtra además por `precioMensual === 0` en MVP para determinar cuáles son seleccionables (ver Decisión #6).
- Si en el futuro se quitan restricciones de pago, solo se cambia la lógica de filtrado en el frontend.

### Nuevo endpoint público de planes

En el **platform-service (puerto 8081)**, crear:

```
GET /api/v1/planes/publicos
```

- Sin autenticación.
- Devuelve solo planes con `activo = true`.
- No expone campos internos (ej. configuraciones de comisión).
- Rate limiting recomendado.

---

### Nuevo endpoint principal de registro

En el **auth-service (puerto 8080)**, crear:

```
POST /api/v1/auto-registro
```

- Sin autenticación.
- Recibe el payload completo (empresa + sucursal + plan + usuario admin).
- Internamente ejecuta la misma lógica que `/companias/wizard` pero sin validar token de plataforma.
- Rate limiting obligatorio para evitar spam.

### Payload del nuevo endpoint

```typescript
// Request — POST /api/v1/auto-registro
{
  // Paso 1 — Empresa
  nombre: string           // nombre del gimnasio
  ruc: string              // RUC/documento fiscal (único en el sistema)
  correo?: string          // correo corporativo (distinto al del admin)
  telefono?: string
  whatsapp?: string

  // Paso 2 — Sucursal
  nombreSucursal: string
  direccionSucursal?: string

  // Paso 3 — Plan
  idPlan: number           // debe ser un plan activo con precioMensual = 0 en MVP

  // Paso 4 — Usuario admin (siempre nuevo — no se busca persona existente)
  usuarioPrincipal: {
    ci: string             // único en el sistema (tabla personas)
    nombre: string
    correo: string         // será el login — único en la tabla usuarios
    telefono?: string
    password: string
  }
}

// Response (igual que /companias/wizard)
{
  idCompania: number
  idCompaniaPlan: number
  idSucursal: number
  qrToken: string          // string alfanumérico (NO imagen) — se muestra copiable en frontend
  usuarioPrincipal: { id: number; nombre: string; correo: string }
  usuariosCreados: number
}
```

**Campos con unicidad validada por el backend (devuelve 409 con `campo` específico):**

| Campo | Unicidad |
|-------|----------|
| `ruc` | Global — no puede existir otro empresa con el mismo RUC |
| `usuarioPrincipal.correo` | Global — no puede existir otro usuario con ese correo de login |
| `usuarioPrincipal.ci` | En el contexto de la nueva compañía — un CI puede existir como persona en otras compañías; el error 409 por CI solo ocurre si ya existe en la nueva compañía creada (edge case: solo aplica si el backend hace una búsqueda global) |

---

## Flujo de usuario — 4 pasos

```
[Login Page]
"¿No tienes cuenta? Créala aquí" (enlace debajo del form)
        │
        ▼
┌─────────────────────────────────────────────────────┐
│  PASO 1 — Tu gimnasio                               │
│  Nombre del gimnasio · RUC · Correo · Tel · WA      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  PASO 2 — Sede principal                            │
│  Nombre de la sede · Dirección                      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  PASO 3 — Elige tu plan                             │
│  Tarjetas de planes (incluye plan gratuito)         │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  PASO 4 — Tus datos                                 │
│  Nombre · CI · Correo · Contraseña · Confirmar      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  ¡Listo! Tu gimnasio está registrado                │
│  Resumen · QR Token · Ir a iniciar sesión           │
└─────────────────────────────────────────────────────┘
```

---

## Especificación UX/UI

### Principios de diseño aplicados al wizard

El wizard de auto-registro es la **primera experiencia** que tiene el dueño de un gimnasio con el sistema. Debe transmitir confianza, claridad y velocidad. Se aplican los siguientes principios:

1. **Progresión visible** — el usuario siempre sabe en qué paso está y cuántos faltan. La barra de progreso es el ancla cognitiva principal.
2. **Un solo foco por paso** — cada paso reúne solo los campos temáticamente relacionados. No se mezclan datos de la empresa con datos personales.
3. **Validación inmediata** — los errores aparecen al salir de cada campo (`onBlur`), no solo al intentar avanzar.
4. **Feedback de estado** — estados de loading, éxito y error son siempre visibles y descriptivos.
5. **Reversibilidad** — el usuario puede volver a pasos anteriores sin perder lo que escribió (estado acumulado en el orquestador).
6. **Plan visible antes de datos personales** — el usuario ve el valor que obtiene (Paso 3) antes de comprometer sus datos de acceso (Paso 4). Orden psicológicamente correcto.

---

### Layout general del wizard

La pantalla de auto-registro usa el mismo `PublicLayout` que el login. El wizard ocupa el mismo card blanco centrado, pero más ancho para acomodar los pasos con más contenido.

```
┌─────────────────────────────────────────────┐  ← bg: var(--page-bg) del PublicLayout
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  [Logo]                             │   │  ← Mismo header que LoginPage
│  │                                     │   │
│  │  ┌─────────────────────────────┐   │   │
│  │  │  Barra de progreso (4 pasos)│   │   │  ← Persistente en todos los pasos
│  │  ├─────────────────────────────┤   │   │
│  │  │                             │   │   │
│  │  │   Contenido del paso actual │   │   │  ← Cambia por paso
│  │  │                             │   │   │
│  │  ├─────────────────────────────┤   │   │
│  │  │  [← Atrás]    [Siguiente →] │   │   │  ← Footer fijo, botones contextuales
│  │  └─────────────────────────────┘   │   │
│  └─────────────────────────────────────┘   │
│                                             │
└─────────────────────────────────────────────┘
```

**Dimensiones del card:** `max-w-lg` (mismo que LoginPage es `max-w-sm`, el wizard necesita un poco más de espacio — `max-w-md`).

---

### Barra de progreso (Stepper)

Ubicada en la parte superior del card, visible en todos los pasos.

**Diseño:**

```
  ①──────────②──────────③──────────④
  Gimnasio   Sede       Plan       Tus datos
```

- Cada nodo es un círculo (`w-8 h-8`, `rounded-full`).
- **Paso completado:** fondo naranja sólido `#f97316`, ícono de check blanco `size={14}`.
- **Paso actual:** borde naranja `2px solid #f97316`, número en naranja, fondo blanco/surface.
- **Paso pendiente:** borde `var(--page-border)`, número en `var(--page-muted)`, fondo `var(--page-surface)`.
- La línea conectora entre nodos: `h-0.5`, color `var(--page-border)` para pendientes, `#f97316` para completados.
- Label debajo de cada nodo: `text-[0.65rem]`, `var(--page-muted)` para pendientes, `#f97316` para el actual.

**Comportamiento:** el stepper es visual únicamente (no se hace clic en él para navegar). La navegación ocurre con los botones de Atrás/Siguiente.

---

### PASO 1 — Tu gimnasio

**Título del paso:** "Tu gimnasio"  
**Subtítulo:** "Empieza con los datos básicos de tu negocio."

**Campos:**

| Campo | Tipo | Required | Notas |
|-------|------|----------|-------|
| Nombre del gimnasio | text | Sí | Placeholder: "CrossFit Central, Sport Zone..." |
| RUC | text | Sí | Placeholder: "1234567890001" · Min 10, Max 20 |
| Correo corporativo | email | No | Placeholder: "contacto@migimnasio.com" |
| Teléfono | text | No | |
| WhatsApp | text | No | |

**Validación Zod (reusa `wizardStep1Schema`):**
```typescript
z.object({
  nombre: z.string().min(2).max(150),
  ruc: z.string().min(10).max(20),
  correo: z.string().email().optional().or(z.literal('')),
  telefono: z.string().optional().or(z.literal('')),
  whatsapp: z.string().optional().or(z.literal('')),
})
```

**UX notes:**
- Los campos opcionales se muestran todos desde el inicio (no hay "mostrar más"). El formulario no es tan largo como para ocultarlos.
- El label "RUC" puede variar según el país — si hay internacionalización futura se puede generalizar a "Documento fiscal".

---

### PASO 2 — Sede principal

**Título del paso:** "Sede principal"  
**Subtítulo:** "¿Dónde está ubicado tu gimnasio?"

**Campos:**

| Campo | Tipo | Required | Notas |
|-------|------|----------|-------|
| Nombre de la sede | text | Sí | Placeholder: "Sede Central, Sucursal Norte..." |
| Dirección | text | No | Placeholder: "Av. Amazonas N23-45..." |

**Validación Zod (reusa `wizardStep2Schema`):**
```typescript
z.object({
  nombreSucursal: z.string().min(2).max(150),
  direccionSucursal: z.string().optional().or(z.literal('')),
})
```

**UX notes:**
- Solo 2 campos. El paso se completa rápido — refuerza la sensación de progreso.
- Hint debajo del nombre: "Puedes agregar más sedes desde el panel una vez registrado."

---

### PASO 3 — Elige tu plan

**Título del paso:** "Elige tu plan"  
**Subtítulo:** "Selecciona el plan que mejor se adapte a tu gimnasio. Puedes cambiarlo cuando quieras."

**Comportamiento:**
- Al entrar al paso, hace `GET /api/v1/planes/publicos` al **platform-service** (sin token, nueva instancia pública).
- Muestra skeleton loaders mientras carga (3 tarjetas placeholder).
- Si falla la carga: banner de error con botón "Reintentar" (no `.catch(() => {}`) silencioso — el usuario debe poder recuperarse).
- El botón "Siguiente" se mantiene deshabilitado mientras los planes están cargando o si falla la carga.

**Diseño de tarjetas de plan:**

```
┌─────────────────────────────────────────────────────┐
│  ● Nombre del plan                    $XX.XX / mes  │  ← borde naranja si seleccionado
│    [Gratis] ← badge verde si precio = 0             │
│                                                     │
│  Descripción breve del plan                         │
│                                                     │
│  ✓ Característica 1   ✓ Característica 2           │
│  ✓ Característica 3                                 │
└─────────────────────────────────────────────────────┘

Planes de pago (MVP — deshabilitados):
┌─────────────────────────────────────────────────────┐
│  ● Nombre del plan                    $29.99 / mes  │  ← opacity-50, cursor-not-allowed
│    [Próximamente] ← badge gris                      │
│                                                     │
│  Descripción...                                     │
└─────────────────────────────────────────────────────┘
```

- Tarjeta seleccionada: `border: 2px solid #f97316`, fondo `var(--color-warning-subtle)`, badge "Seleccionado" naranja.
- Tarjeta no seleccionada y gratuita: `border: 2px solid var(--page-border)`, fondo `var(--page-surface)`.
- **Tarjeta de pago (MVP):** `opacity-50`, no clickeable, badge "Próximamente" gris. La nota al pie del paso explica: "Los planes de pago estarán disponibles próximamente."
- El plan gratuito (`precioMensual === 0`) muestra badge "Gratis" en `text-green-600` / `bg-green-50`.
- Las características se muestran como píldoras (igual al Step3Plan existente del wizard de plataforma).

**Estado de carga fallida:**
```
┌────────────────────────────────────────────────┐
│  ⚠ No pudimos cargar los planes disponibles.  │
│     [Reintentar]                               │
└────────────────────────────────────────────────┘
```

**Validación Zod (reusa `wizardStep3Schema`):**
```typescript
z.object({
  idPlan: z.coerce.number().positive('Selecciona un plan'),
})
```

**UX notes:**
- Si solo hay un plan gratuito disponible, se preselecciona automáticamente y se muestra: "Este es el único plan disponible en este momento."
- Si todos los planes tienen costo (edge case raro), mostrar un mensaje: "En este momento no hay planes gratuitos disponibles. Contáctanos." con el enlace de WhatsApp.
- El plan gratuito debe estar visualmente diferenciado (badge verde) para que el usuario lo identifique en segundos — es su motivación para completar el registro.

---

### PASO 4 — Tus datos

**Título del paso:** "Tus datos"  
**Subtítulo:** "Serás el administrador principal del gimnasio."

**Campos:**

| Campo | Tipo | Required | Notas |
|-------|------|----------|-------|
| Nombre completo | text | Sí | Min 2, Max 150 |
| CI / Cédula | text | Sí | Identificador único |
| Correo electrónico | email | Sí | Será tu usuario de acceso |
| Contraseña | password | Sí | Min 8 caracteres · con indicador de fortaleza |
| Confirmar contraseña | password | Sí | Debe coincidir |

**Componente `PasswordStrength`:** reusar `src/ui/features/auth/components/PasswordStrength.tsx` tal cual. Se muestra debajo del campo de contraseña.

**Validación Zod:**
```typescript
z.object({
  ci: z.string().min(1, 'CI requerido').max(20),
  nombre: z.string().min(2, 'Nombre requerido').max(150),
  correo: z.string().email('Correo no válido'),
  password: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarPassword: z.string().min(8, 'Mínimo 8 caracteres'),
}).refine(d => d.password === d.confirmarPassword, {
  message: 'Las contraseñas no coinciden',
  path: ['confirmarPassword'],
})
```

**UX notes:**
- Hint debajo del correo: "Este correo será tu usuario de acceso al panel."
- Hint debajo del CI: "Tu número de cédula de identidad."
- El ícono de ojo (mostrar/ocultar) en ambos campos de contraseña, igual que en `LoginPage`.
- A diferencia del Step4 del wizard de plataforma, **no hay búsqueda de persona existente** — el registrante siempre ingresa todos sus datos desde cero.
- Si el correo o CI ya existen, el backend devuelve 409 al hacer submit del paso 4 (no se puede validar antes sin exponer un endpoint de "verificar disponibilidad"). El error se muestra en un banner rojo en la parte **superior del Paso 4**, no en el campo específico (ya que viene del backend, no de Zod). El usuario puede corregir el campo y reintentar sin perder el resto del formulario.
- **El Paso 4 guarda su estado local** — si el usuario vuelve al Paso 3 y regresa al 4, los campos mantienen lo que había escrito (React Hook Form con `defaultValues` del estado acumulado en el orquestador).

---

### Botones de navegación (footer del wizard)

```
┌─────────────────────────────────────────────────────┐
│  [← Volver]                          [Siguiente →]  │
└─────────────────────────────────────────────────────┘
```

- **Paso 1:** solo "Siguiente" (no hay Atrás). También: enlace "← Volver al login" en esquina superior izquierda del card.
- **Pasos 2-3:** "Volver" + "Siguiente".
- **Paso 4:** "Volver" + "Crear mi cuenta" (texto final, no "Siguiente").
- **Botón Siguiente / Crear mi cuenta:**
  - Estilo: `bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 px-5 rounded-lg text-sm`.
  - Estado loading: `<PulsingDots size="sm" />` + texto "Creando cuenta…" (usa el componente de IMPL_16 si ya está; si no, `Loader2` provisorio).
  - Deshabilitado mientras se valida o envía.
- **Botón Volver:**
  - Estilo: ghost, `text-slate-500 hover:text-slate-700 text-sm`.
  - No deshabilitado durante el envío final (permite corregir sin recargar).
- **Warning de salida (Back button del browser):**
  - `useEffect` en el orquestador registra `window.beforeunload` cuando `currentStep > 1 && !registroCompletado`.
  - El mensaje nativo del browser aparece si el usuario intenta cerrar o navegar fuera (no podemos personalizar el texto — restricción del browser).
  - Se limpia el listener en el `return` del `useEffect` y cuando `registroCompletado = true` (no bloquear después del éxito).

---

### Pantalla de éxito

Una vez que el backend responde con 200:

```
┌──────────────────────────────────────┐
│                                      │
│          ✅  (CheckCircle2, 56px)    │  ← text-green-500
│                                      │
│   ¡Tu gimnasio está listo!           │  ← text-xl font-bold, var(--page-text)
│                                      │
│   CrossFit Central ha sido           │  ← nombre del gym del response
│   registrado exitosamente.           │  ← text-sm, var(--page-muted)
│                                      │
│   ┌──────────────────────────────┐  │
│   │  Tu token QR de asistencia   │  │  ← card con borde var(--page-border)
│   │                              │  │
│   │  abc123XYZ789...  [📋 Copiar]│  │  ← string alfanumérico, font-mono text-xs
│   │                              │  │
│   │  ⓘ Úsalo para que tus       │  │
│   │  clientes registren su       │  │
│   │  asistencia con el QR.       │  │
│   └──────────────────────────────┘  │
│                                      │
│   [Ir a iniciar sesión →]            │  ← botón naranja full-width
│                                      │
│   Usa el correo y contraseña         │  ← text-xs, var(--page-muted)
│   que acabas de crear.               │
│                                      │
└──────────────────────────────────────┘
```

**Detalles técnicos:**
- `qrToken` del response es un **string alfanumérico** — se muestra en una caja `font-mono text-xs` con botón de copiar usando `navigator.clipboard.writeText()`.
- No se renderiza como imagen QR (eso es la app móvil del cliente, no el panel web).
- Al copiar, el botón cambia a "¡Copiado!" por 2 segundos (igual al comportamiento existente en el wizard de plataforma).
- El botón "Ir a iniciar sesión" navega a `/login` con `replace: true` — el Back del navegador no regresa al wizard.
- El listener `beforeunload` se limpia al llegar a esta pantalla (`registroCompletado = true`).

---

### Manejo de errores globales del wizard

Cuando el endpoint `POST /auto-registro` devuelve error, se lee el campo `conflicto` del body de respuesta para mostrar el mensaje correcto:

| Status | `conflicto` | Mensaje al usuario | Dónde aparece |
|--------|------------|-------------------|---------------|
| 409 | `correo` | "Este correo ya está registrado. ¿Ya tienes una cuenta? [Inicia sesión →]" | Banner rojo al tope del Paso 4 |
| 409 | `ci` | "Esta cédula ya está registrada en el sistema." | Banner rojo al tope del Paso 4 |
| 409 | `ruc` | "Ya existe una empresa registrada con ese RUC." | Banner rojo al tope del Paso 1 + el wizard retrocede al Paso 1 automáticamente |
| 400 | `idPlan` | "El plan seleccionado no está disponible." | Banner rojo al tope del Paso 3 + retrocede al Paso 3 |
| 429 | — | "Demasiados intentos. Espera unos minutos e intenta de nuevo." | Banner al tope del Paso 4, botón deshabilitado 60s |
| 5xx | — | "Ocurrió un error inesperado. Por favor intenta de nuevo." | Banner al tope del Paso 4 |

**Comportamiento del banner de error:**
- Componente `div` con ícono `AlertCircle` (igual a `LoginPage`).
- Si el error indica un paso anterior (ej. RUC duplicado), el wizard retrocede al paso afectado y muestra el banner ahí — el usuario no tiene que adivinar qué cambiar.
- El link "Inicia sesión →" del error de correo duplicado navega a `/login` usando `<Link>` de react-router.

**Ubicación del banner en el layout del card:**
```
┌─────────────────────────────┐
│  StepperBar                 │
├─────────────────────────────┤
│  🔴 Banner de error         │  ← aparece aquí, entre stepper y contenido del paso
├─────────────────────────────┤
│  Contenido del paso actual  │
├─────────────────────────────┤
│  [Volver]    [Siguiente]    │
└─────────────────────────────┘
```

---

### Enlace en LoginPage

Debajo del bloque actual de enlaces (¿Olvidaste tu contraseña? / Acceso plataforma):

```tsx
// En LoginPage.tsx — dentro del div.text-center.space-y-2
<Link
  to="/registro"
  className="block text-orange-600 hover:text-orange-700 font-medium transition-colors text-sm"
>
  ¿No tienes cuenta? Regístrate gratis
</Link>
```

---

## Archivos a crear / modificar (frontend)

### Nuevos archivos

| Archivo | Descripción |
|---------|-------------|
| `src/ui/features/auth/pages/AutoRegistroPage.tsx` | Orquestador del wizard: estado acumulado, navegación entre pasos, submit final |
| `src/ui/features/auth/pages/AutoRegistro/steps/Step1Empresa.tsx` | Campos empresa (reusar lógica de `RegistrarGymWizard/steps/Step1Empresa.tsx`) |
| `src/ui/features/auth/pages/AutoRegistro/steps/Step2Sucursal.tsx` | Campos sede (reusar lógica de `RegistrarGymWizard/steps/Step2Sucursal.tsx`) |
| `src/ui/features/auth/pages/AutoRegistro/steps/Step3Plan.tsx` | Cards de plan — nueva instancia que llama a `/planes/publicos` sin token |
| `src/ui/features/auth/pages/AutoRegistro/steps/Step4DatosPropios.tsx` | Datos del admin: nombre, CI, correo, contraseña |
| `src/ui/features/auth/pages/AutoRegistro/ResumenExito.tsx` | Pantalla final de éxito con QR |
| `src/ui/features/auth/pages/AutoRegistro/StepperBar.tsx` | Barra de progreso visual reutilizable |
| `src/ui/features/auth/schemas/auto-registro-wizard.schema.ts` | Schemas Zod por paso |
| `src/application/auth/AutoRegistro.usecase.ts` | Use case que llama al repositorio |

### Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `src/ui/router/index.tsx` | Agregar ruta pública `/registro` en `PublicLayout` |
| `src/ui/features/auth/pages/LoginPage.tsx` | Agregar enlace "¿No tienes cuenta? Regístrate gratis" |
| `src/infrastructure/http/auth/AuthHttpRepository.ts` | Agregar método `autoRegistro(dto)` → `POST /auto-registro` usando la instancia axios base sin token (el usuario no tiene sesión al registrarse) |
| `src/infrastructure/http/platform/axios-platform-public.instance.ts` | **Nuevo** — instancia axios sin interceptor de auth para `GET /planes/publicos` |
| `src/domain/auth/ports/AuthRepository.port.ts` | Declarar método `autoRegistro` en el port |
| `src/i18n/locales/es.json` | Claves `autoRegistro.*` |
| `src/i18n/locales/en.json` | Mismas claves en inglés |

### Archivos reutilizados sin cambios

| Archivo | Qué se reutiliza |
|---------|-----------------|
| `src/ui/features/auth/components/PasswordStrength.tsx` | Indicador de fortaleza de contraseña |
| `src/ui/features/platform/schemas/registrar-gym-wizard.schema.ts` | `wizardStep1Schema`, `wizardStep2Schema`, `wizardStep3Schema` — **importar directamente, no duplicar** |
| `src/lib/api-error.ts` | `getApiErrorMessage()`, `getApiErrorStatus()` |
| `src/lib/utils.ts` | `cn()` |
| `src/components/ui/` | Primitivos shadcn |

---

## Consideraciones de seguridad

- `POST /auto-registro` y `GET /planes/publicos` son endpoints sin autenticación — **rate limiting obligatorio** en el backend (sugerido: máx 5 registros/IP/hora, máx 20 consultas de planes/IP/minuto).
- Validar unicidad de RUC, correo del admin y CI en el backend antes de crear — devolver 409 con campo `conflicto` específico (`'ruc' | 'correo' | 'ci'`).
- El campo `idPlan` debe validarse en el backend contra planes activos — no confiar en que el frontend filtra correctamente. En MVP, adicionalmente validar que `precioMensual = 0`.
- El correo del administrador (campo `usuarioPrincipal.correo`) se convierte en el `login` del usuario — debe ser único en la tabla de usuarios, no solo en personas.
- No exponer en `GET /planes/publicos` información de configuración interna (porcentajes de comisión, parámetros de infraestructura, etc.).

---

## Ruta frontend

```
/registro
```

- Dentro de `PublicLayout` (no requiere auth).
- Si el usuario ya está autenticado → redirigir a `/admin/dashboard`.

---

## Fases de implementación

### Fase 1 — MVP
- [ ] **Backend (platform-service):** `GET /api/v1/planes/publicos` — sin token, solo planes activos, rate limit
- [ ] **Backend (auth-service):** `POST /api/v1/auto-registro` — sin token, valida unicidad RUC/correo/CI, devuelve 409 con campo `conflicto`, rate limit
- [ ] **Frontend:** instancia `axios-platform-public.instance.ts`
- [ ] **Frontend:** wizard completo de 4 pasos + pantalla de éxito + `beforeunload` warning
- [ ] **Frontend:** enlace "¿No tienes cuenta? Regístrate gratis" en `LoginPage`
- [ ] **Frontend:** planes de pago deshabilitados visualmente (badge "Próximamente")

### Fase 2 — Verificación de correo
- [ ] Email de confirmación al registrarse
- [ ] Pantalla "Verifica tu correo" entre el éxito y el login

### Fase 3 — Pago online (futuro)
- [ ] Integración con pasarela de pago para planes de pago
- [ ] Activación condicionada al pago

---

## Referencias

- Wizard de plataforma: `src/ui/features/platform/pages/RegistrarGymWizard/`
- Schemas Zod del wizard: `src/ui/features/platform/schemas/registrar-gym-wizard.schema.ts`
- Endpoint backend actual: `POST /companias/wizard` en auth-service :8080
- Instancia axios platform: `src/infrastructure/http/platform/axios-platform.instance.ts`
- Instancia axios auth: `src/infrastructure/http/auth/axios.instance.ts`
- Punto de entrada: `src/ui/features/auth/pages/LoginPage.tsx`
- Guías visuales: `documentacion/DESIGN_GUIDELINES.md`
