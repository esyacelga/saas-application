# Prompt — Módulo Platform en el Frontend React

## Contexto del proyecto

Existe un proyecto React ya desarrollado ubicado en `auth-service-frond-end/` con la siguiente arquitectura y stack definidos. **Todo lo que se desarrolle debe respetar estrictamente esta arquitectura y convenciones — no introducir nada nuevo.**

### Stack confirmado
- **React 19 + TypeScript 5.7** con Vite 6
- **Tailwind CSS 4** + **shadcn/ui** (componentes en `src/components/ui/`)
- **Zustand 5** para estado global (patrón de store ya definido en `src/infrastructure/store/auth/auth.store.ts`)
- **Axios 1.16** con interceptor de token ya configurado en `src/infrastructure/http/axios.instance.ts`
- **React Router DOM 7** con guards ya definidos (`AuthGuard`, `PlatformGuard`, `PermissionGuard`)
- **React Hook Form 7 + Zod 4** para formularios (schemas co-ubicados en `schemas/` de cada feature)
- **TanStack React Table 8** para tablas de datos
- **Sonner** para toast notifications
- **i18next** para internacionalización (ES/EN), con traducciones en `src/i18n/locales/`
- **Lucide React** para iconos
- Alias de paths: `@/` → `src/`

### Arquitectura hexagonal (4 capas — respetar la dirección de dependencias)
```
src/
├── domain/           → Entidades e interfaces (ports) — sin dependencias externas
├── application/      → Casos de uso — solo importa domain
├── infrastructure/   → Implementaciones concretas (HTTP repos, stores Zustand)
└── ui/               → Componentes React, páginas, router, layouts
```

### Convenciones de nombres
- **PascalCase** para componentes React: `PlanesPage.tsx`, `CrearPlanModal.tsx`
- **camelCase** con sufijos para capas: `platform.store.ts`, `PlatformHttpRepository.ts`, `platform.dto.ts`
- **kebab-case** para schemas Zod: `crear-plan.schema.ts`
- **Sufijos obligatorios**: `.usecase.ts`, `.store.ts`, `.port.ts`, `.entity.ts`, `.dto.ts`
- Organización por feature bajo `src/ui/features/<feature>/`

### Layouts y guards ya existentes
- `PlatformLayout` — layout con sidebar para usuarios `tipo: 'plataforma'`
- `PlatformGuard` — protege rutas que requieren JWT con `tipo === 'plataforma'`
- Las rutas platform ya arrancan desde `/platform/...`

### Usuario platform y roles
El JWT de plataforma contiene:
```typescript
type JwtPayloadPlataforma = {
  sub: string
  tipo: 'plataforma'
  rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  nombre: string
  iat: number
  exp: number
}
```
- **super_admin**: acceso completo (CRUD planes, registrar gyms, suspender, gestionar suscripciones)
- **soporte**: puede ver todo y registrar/confirmar pagos, no puede crear/editar planes ni suspender
- **viewer**: solo lectura

---

## Tarea

Desarrolla el **módulo Platform** completo en el frontend. Este módulo expone al operador de la plataforma SaaS las pantallas para gestionar el catálogo de planes, los gimnasios clientes, sus suscripciones y pagos.

El módulo debe tener **excelente UX/UI**: flujo claro, feedback inmediato, estados de carga, manejo de errores visible, confirmaciones antes de acciones destructivas, y respeto total al sistema de diseño ya establecido (colores gym-950/gym-800/gym-orange, tipografía Inter, sombras shadow-card/shadow-sidebar).

---

## Base URL del backend

```
http://localhost:8081/api/v1
```
El axios instance ya agrega el header `Authorization: Bearer {token}` automáticamente.

---

## Archivos a crear por capa

### 1. Domain — Entidades y ports

**`src/domain/platform/entities/Plan.entity.ts`**
```typescript
export interface Caracteristica {
  id: number
  codigo: string
  nombre: string
  modulo: string
  activo: boolean
}

export interface Plan {
  id: number
  nombre: string
  descripcion: string
  precioMensual: number
  activo: boolean
  caracteristicas: Caracteristica[]
}

export interface PlanActivo {
  nombre: string
  estado: 'ACTIVO' | 'EN_GRACIA' | 'VENCIDO' | 'PROGRAMADO' | 'CANCELADO' | 'SUSPENDIDO'
  fechaFin: string        // ISO date
  diasRestantes: number
}

export interface Compania {
  id: number
  nombre: string
  ruc: string
  telefono: string
  whatsapp: string
  correo: string
  activo: boolean
  planActivo: PlanActivo | null
}

export interface Sucursal {
  id: number
  idCompania: number
  nombre: string
  direccion: string
  esPrincipal: boolean
  activo: boolean
  qrToken: string
  qrTokenExpira: string | null
}

export interface CompaniaPlan {
  id: number
  idPlan: number
  estado: string
  fechaInicio: string
  fechaFin: string
  diasRestantes: number
  diasGracia: number
  tipoCambio: string
}

export interface Pago {
  id: number
  idCompaniaPlan: number
  monto: number
  fechaPago: string
  periodoDesde: string | null
  periodoHasta: string | null
  metodoPago: string
  tipoPago: string
  estado: 'PENDIENTE' | 'PAGADO' | 'FALLIDO'
  referencia: string | null
}

export interface NotifConfig {
  idCompania: number
  diasAntes: number
  canal: 'EMAIL' | 'WHATSAPP' | 'AMBOS'
  activo: boolean
}
```

**`src/domain/platform/ports/PlatformRepository.port.ts`**
```typescript
// Define las signaturas de todos los métodos HTTP del módulo platform.
// Las implementaciones van en infrastructure/http/platform/PlatformHttpRepository.ts
export interface PlatformRepository {
  // Planes
  getPlanes(): Promise<Plan[]>
  crearPlan(body: CrearPlanDto): Promise<Plan>
  actualizarPlan(id: number, body: ActualizarPlanDto): Promise<Plan>
  asignarCaracteristicas(id: number, body: AsignarCaracteristicasDto): Promise<Plan>
  desactivarPlan(id: number): Promise<void>

  // Características
  getCaracteristicas(): Promise<Caracteristica[]>
  crearCaracteristica(body: CrearCaracteristicaDto): Promise<Caracteristica>

  // Compañías
  getCompanias(): Promise<Compania[]>
  registrarGym(body: RegistrarGymDto): Promise<RegistrarGymResponse>
  actualizarCompania(id: number, body: ActualizarCompaniaDto): Promise<Compania>
  suspenderCompania(id: number, body: SuspenderDto): Promise<void>

  // Sucursales
  getSucursales(idCompania: number): Promise<Sucursal[]>
  crearSucursal(idCompania: number, body: CrearSucursalDto): Promise<Sucursal>
  actualizarSucursal(id: number, body: ActualizarSucursalDto): Promise<Sucursal>
  renovarQrToken(id: number, body: RenovarQrDto): Promise<QrRenovarResponse>

  // Suscripciones
  getSuscripcionActiva(idCompania: number): Promise<CompaniaPlan>
  getHistorialSuscripcion(idCompania: number): Promise<CompaniaPlan[]>
  renovarSuscripcion(idCompania: number, body: RenovarSuscripcionDto): Promise<CompaniaPlan>
  upgradePlan(idCompania: number, body: UpgradeDto): Promise<UpgradeResponse>
  downgradePlan(idCompania: number, body: DowngradeDto): Promise<DowngradeResponse>

  // Pagos
  getPagos(idCompania: number): Promise<Pago[]>
  registrarPago(body: RegistrarPagoDto): Promise<Pago>
  confirmarPago(id: number): Promise<Pago>

  // Notificaciones config
  getNotifConfig(idCompania: number): Promise<NotifConfig[]>
  updateNotifConfig(idCompania: number, body: NotifConfig[]): Promise<void>
}
```

---

### 2. Application — Casos de uso

Crear un caso de uso por agrupación lógica siguiendo el patrón de `src/application/auth/`:

- **`src/application/platform/GetPlanes.usecase.ts`**
- **`src/application/platform/GestionarPlan.usecase.ts`** (crear, actualizar, asignar características, desactivar)
- **`src/application/platform/GetCaracteristicas.usecase.ts`**
- **`src/application/platform/GestionarCompania.usecase.ts`** (listar, registrar, actualizar, suspender)
- **`src/application/platform/GestionarSucursal.usecase.ts`** (listar, crear, actualizar, renovar QR)
- **`src/application/platform/GestionarSuscripcion.usecase.ts`** (activa, historial, renovar, upgrade, downgrade)
- **`src/application/platform/GestionarPago.usecase.ts`** (listar, registrar, confirmar)
- **`src/application/platform/GestionarNotifConfig.usecase.ts`** (get, update)

Cada caso de uso recibe el repositorio por inyección (igual que `LoginStaff.usecase.ts`).

---

### 3. Infrastructure — HTTP Repository y DTOs

**`src/infrastructure/http/platform/platform.dto.ts`**
Define todos los tipos de request/response:
```typescript
// Planes
export interface CrearPlanDto { nombre: string; descripcion: string; precioMensual: number }
export interface ActualizarPlanDto { nombre?: string; descripcion?: string; precioMensual?: number }
export interface AsignarCaracteristicasDto { caracteristicaIds: number[] }

// Características
export interface CrearCaracteristicaDto { codigo: string; nombre: string; modulo: string }

// Compañías
export interface RegistrarGymDto {
  nombre: string; ruc: string; logoUrl?: string; telefono?: string
  whatsapp?: string; correo?: string; idPlan: number
  nombreSucursal?: string; direccionSucursal?: string
}
export interface RegistrarGymResponse { idCompania: number; idCompaniaPlan: number; idSucursal: number; qrToken: string }
export interface ActualizarCompaniaDto { nombre?: string; logoUrl?: string; telefono?: string; whatsapp?: string; correo?: string }
export interface SuspenderDto { motivo: string }

// Sucursales
export interface CrearSucursalDto { nombre: string; direccion?: string; esPrincipal?: boolean }
export interface ActualizarSucursalDto { nombre?: string; direccion?: string }
export interface RenovarQrDto { expiresInHours?: number }
export interface QrRenovarResponse { qrToken: string; qrTokenExpira: string | null }

// Suscripciones
export interface RenovarSuscripcionDto { idPlan?: number; meses?: number }
export interface UpgradeDto { idPlanNuevo: number }
export interface UpgradeResponse { idCompaniaPlanNuevo: number; creditoAplicado: number; montoAPagar: number; planAnteriorCancelado: boolean }
export interface DowngradeDto { idPlanNuevo: number }
export interface DowngradeResponse { idCompaniaPlanNuevo: number; estado: string; efectivoDe: string; creditoGenerado: number }

// Pagos
export interface RegistrarPagoDto {
  idCompaniaPlan: number; monto: number; metodoPago: string
  tipoPago: string; referencia?: string; periodoDesde?: string; periodoHasta?: string
}
```

**`src/infrastructure/http/platform/PlatformHttpRepository.ts`**
Implementa `PlatformRepository` usando el axios instance existente. Mapea las respuestas del backend (snake_case) a las entidades del dominio (camelCase).

**`src/infrastructure/store/platform/platform.store.ts`**
Store Zustand para estado global del módulo:
- `companias: Compania[]` — lista cacheada
- `selectedCompania: Compania | null` — compañía actualmente en detalle
- `planes: Plan[]` — catálogo cacheado
- `caracteristicas: Caracteristica[]` — catálogo cacheado
- Acciones: `setCompanias`, `setSelectedCompania`, `setPlanes`, `setCaracteristicas`, `reset`

---

### 4. UI — Schemas Zod

**`src/ui/features/platform/schemas/crear-plan.schema.ts`**
```typescript
// nombre: string, min 2, max 100
// descripcion: string opcional
// precioMensual: number, positivo, min 0.01
```

**`src/ui/features/platform/schemas/registrar-gym.schema.ts`**
```typescript
// nombre: string requerido
// ruc: string requerido, min 10, max 20
// correo: email opcional
// telefono, whatsapp: string opcionales
// idPlan: number requerido, positivo
// nombreSucursal: string requerido (nombre de la sede principal)
// direccionSucursal: string opcional
```

**`src/ui/features/platform/schemas/crear-sucursal.schema.ts`**
**`src/ui/features/platform/schemas/registrar-pago.schema.ts`**
**`src/ui/features/platform/schemas/suspender.schema.ts`** (motivo: string requerido, min 10 caracteres)
**`src/ui/features/platform/schemas/notif-config.schema.ts`**

---

### 5. UI — Páginas y componentes

#### 5.1 `/platform/planes` — Gestión de planes

**`src/ui/features/platform/pages/PlanesPage.tsx`**

**Layout de la pantalla:**
- `PageHeader` con título "Planes de suscripción" y botón "Nuevo plan" (solo `super_admin`)
- **Tabla** con columnas: Nombre, Precio/mes, Características (badges), Estado (activo/inactivo), Acciones
- Acciones por fila: "Editar" (modal), "Asignar características" (modal), "Desactivar" (confirm dialog)
- Estado vacío con ilustración si no hay planes
- Skeleton loader mientras carga

**Componentes requeridos:**
- **`CrearPlanModal.tsx`** — Formulario React Hook Form + Zod (nombre, descripción, precioMensual). Submit llama a `GestionarPlan.usecase.crearPlan()`. Toast success/error.
- **`EditarPlanModal.tsx`** — Mismo formulario pre-rellenado. Submit llama a `actualizarPlan()`.
- **`AsignarCaracteristicasModal.tsx`** — Lista de checkboxes con todas las características disponibles. Las ya asignadas aparecen marcadas. Submit llama a `asignarCaracteristicas()`.
- **`DesactivarPlanDialog.tsx`** — `ConfirmDialog` reutilizable ya existente. Advierte si hay suscriptores activos (el backend devuelve 409 en ese caso — mostrarlo como error inline).

**Regla de negocio visible en UI:**
> "Cambiar el precio no afecta contratos vigentes, solo nuevas suscripciones." — mostrar como `<p className="text-xs text-muted-foreground">` debajo del campo precio en el modal de edición.

---

#### 5.2 `/platform/caracteristicas` — Catálogo de características

**`src/ui/features/platform/pages/CaracteristicasPage.tsx`**

**Layout:**
- `PageHeader` con título "Características del sistema" y botón "Nueva característica" (solo `super_admin`)
- **Tabla** con columnas: Código (badge monoespacio), Nombre, Módulo, Estado, Acciones
- Las características son el catálogo global — no se eliminan, solo se desactivan (si el backend lo permite)

**Componente requerido:**
- **`CrearCaracteristicaModal.tsx`** — Campos: código (texto sin espacios, lowercase), nombre, módulo (select con opciones: clientes, membresias, asistencia, finanzas, marketing, inventario). El código debe mostrarse en minúsculas automáticamente. Si el backend devuelve 409 (código duplicado), mostrar error en el campo `codigo`.

---

#### 5.3 `/platform/companias` — Listado de gimnasios

**`src/ui/features/platform/pages/CompaniasPage.tsx`**

**Layout:**
- `PageHeader` con título "Gimnasios registrados", contador de total, y botón "Registrar gym" (solo `super_admin`)
- **Tabla** con columnas:
  - Nombre del gym
  - RUC
  - Plan activo (badge con color según estado: verde=activo, amarillo=en_gracia, rojo=vencido/suspendido)
  - Días restantes (número con color: verde >10d, amarillo 1-10d, rojo 0d)
  - Estado de la compañía (activo/inactivo)
  - Acciones: "Ver detalle" (navega a `/platform/companias/:id`), "Suspender" (solo `super_admin`)
- Buscador por nombre o RUC (filtrado client-side)
- Skeleton loader mientras carga

**Componente requerido:**
- **`RegistrarGymModal.tsx`** — Formulario multi-sección:
  - **Sección 1: Datos del gym** — nombre, RUC, correo, teléfono, whatsapp
  - **Sección 2: Plan inicial** — select de planes disponibles (carga desde `/planes`), muestra precio y características del plan seleccionado como preview
  - **Sección 3: Sede principal** — nombre de la sucursal, dirección
  - Submit llama a `GestionarCompania.usecase.registrarGym()`. Response muestra un dialog de confirmación con el QR token generado y un botón "Copiar token".
- **`SuspenderCompaniaDialog.tsx`** — `ConfirmDialog` con campo de texto para el motivo (requerido, mínimo 10 caracteres). Acción destructiva — botón en rojo.

**Estado del badge del plan:**
```
activo      → bg verde   "Activo"
en_gracia   → bg amarillo "En gracia"
vencido     → bg rojo    "Vencido"
suspendido  → bg rojo    "Suspendido"
programado  → bg gris    "Programado"
cancelado   → bg gris    "Cancelado"
```

---

#### 5.4 `/platform/companias/:id` — Detalle del gimnasio

**`src/ui/features/platform/pages/CompaniaDetallePage.tsx`**

Esta es la pantalla central de operación. Usa un layout de **tabs** (usar `Tabs` de shadcn/ui):

**Header de la página:**
- Nombre del gym + RUC
- Badge del estado del plan (mismo color-coding que en la lista)
- Botón "← Volver" que navega a `/platform/companias`
- Botón "Suspender" (solo `super_admin`, solo si la compañía está activa)

**Tab 1: Suscripción**

Componente: **`SuscripcionTab.tsx`**
- **Card de suscripción activa**:
  - Plan (nombre + precio)
  - Estado (badge)
  - Fecha inicio / Fecha fin
  - Días restantes con barra de progreso visual (full = total días del período)
  - Días de gracia
  - Tipo de cambio (badge: NUEVO, RENOVACION, UPGRADE, DOWNGRADE)
- **Acciones disponibles** (según rol y estado):
  - "Renovar" → modal `RenovarSuscripcionModal.tsx` (solo `super_admin`)
  - "Upgrade" → modal `UpgradePlanModal.tsx` (solo `super_admin`)
  - "Downgrade" → modal `DowngradePlanModal.tsx` (solo `super_admin`)
- **Historial de suscripción**: tabla con todas las filas de `compania_planes` ordenadas por fecha descendente (id, plan, estado, fechaInicio, fechaFin, tipoCambio)

**`RenovarSuscripcionModal.tsx`**:
- Select del plan (pre-seleccionado el actual), campo meses (número, mínimo 1)
- Muestra precio total calculado en tiempo real: `precio_plan × meses`

**`UpgradePlanModal.tsx`**:
- Select de planes con precio mayor al actual (filtrado en UI)
- Muestra: "Crédito proporcional estimado" (calculado en UI como días_restantes/30 × precio_actual)
- Warning: "El plan actual será cancelado inmediatamente al confirmar"
- Respuesta del backend muestra: crédito aplicado real, monto a pagar

**`DowngradePlanModal.tsx`**:
- Select de planes con precio menor al actual
- Selector de fecha de inicio (debe ser futura, mínimo mañana)
- Información: "El plan actual sigue activo hasta su fecha de vencimiento. El nuevo plan entrará en vigor en la fecha seleccionada."

---

**Tab 2: Pagos**

Componente: **`PagosTab.tsx`**
- Tabla de pagos con columnas: ID, Monto, Fecha pago, Periodo (desde-hasta), Método, Tipo, Estado (badge: PENDIENTE=amarillo, PAGADO=verde, FALLIDO=rojo), Referencia, Acciones
- Acción "Confirmar" (solo en pagos `PENDIENTE`, visible para `super_admin` y `soporte`)
- Botón "Registrar pago" en el header del tab (solo `super_admin` y `soporte`)

**`RegistrarPagoModal.tsx`**:
- idCompaniaPlan (pre-rellenado con el plan activo, no editable — mostrar como texto)
- monto (número, positivo)
- metodoPago (select: efectivo, transferencia, tarjeta)
- tipoPago (select: pago_completo, diferencia_upgrade, credito_downgrade, renovacion)
- referencia (texto opcional)
- periodoDesde / periodoHasta (date pickers opcionales)

---

**Tab 3: Sucursales**

Componente: **`SucursalesTab.tsx`**
- Tabla de sucursales con columnas: Nombre, Dirección, Principal (badge "Principal" o vacío), Estado, QR Token (truncado a 16 chars + copy button), Expira, Acciones
- Acciones por fila: "Editar" (modal), "Renovar QR" (modal)
- Botón "Nueva sucursal" en el header del tab

**`CrearSucursalModal.tsx`**: nombre, dirección, esPrincipal (checkbox)
**`EditarSucursalModal.tsx`**: nombre, dirección pre-rellenados
**`RenovarQrModal.tsx`**:
- Campo "Expira en horas" (número opcional — vacío = sin expiración)
- Warning: "El token anterior quedará inválido inmediatamente"
- Al completar: muestra el nuevo token en un cuadro con botón "Copiar"

---

**Tab 4: Notificaciones**

Componente: **`NotifConfigTab.tsx`**
- Lista editable en forma de tabla: Días antes, Canal, Activo (toggle switch)
- Botón "Agregar alerta" — agrega una fila nueva con campos editables (días antes: número, canal: select EMAIL/WHATSAPP/AMBOS)
- Botón "Guardar cambios" — llama a `PUT /companias/{id}/notif-config` con la configuración completa (replace all)
- Warning informativo: "Las alertas se envían automáticamente cada día a las 00:05 UTC"

---

#### 5.5 Actualización de datos del gym

En el header del detalle (o dentro del Tab de información general si se agrega), botón "Editar datos":
- **`EditarCompaniaModal.tsx`** — Campos editables: nombre, teléfono, whatsapp, correo, logoUrl (URL de imagen). El RUC no es editable.

---

### 6. UI — Router: nuevas rutas

Agregar dentro de `[PlatformGuard]` → `[PlatformLayout]` en `src/ui/router/index.tsx`:

```typescript
// Módulo Platform
{ path: '/platform/planes',         element: <PlanesPage /> }
{ path: '/platform/caracteristicas', element: <CaracteristicasPage /> }
{ path: '/platform/companias',       element: <CompaniasPage /> }
{ path: '/platform/companias/:id',   element: <CompaniaDetallePage /> }
```

Agregar los items al sidebar de `PlatformLayout`:

```
📦 Planes           → /platform/planes        (super_admin)
⚙️ Características  → /platform/caracteristicas (super_admin)
🏢 Gimnasios        → /platform/companias      (todos los roles)
```
Usar `useCurrentUser()` del store de auth para condicionar la visibilidad de items según `rol_plataforma`.

---

### 7. UI — Componentes compartidos del módulo

**`src/ui/features/platform/components/EstadoPlanBadge.tsx`**
Badge reutilizable que recibe `estado: string` y devuelve el badge con el color correcto. Centraliza la lógica de color para no repetirla en cada tabla.

**`src/ui/features/platform/components/PlanSelector.tsx`**
Select de planes reutilizable que carga desde el store (o hace fetch si el store está vacío). Muestra nombre + precio. Usado en RegistrarGymModal, UpgradePlanModal, DowngradePlanModal, RenovarSuscripcionModal.

**`src/ui/features/platform/components/QrTokenDisplay.tsx`**
Muestra un token en un cuadro monoespaciado con botón "Copiar" (usa `navigator.clipboard.writeText`). Toast de confirmación "Token copiado" al copiar.

---

### 8. i18n — Claves de traducción

Agregar en `src/i18n/locales/es.json` y `en.json` una sección `"platform"`:

```json
// es.json (extracto)
{
  "platform": {
    "planes": {
      "title": "Planes de suscripción",
      "nuevo": "Nuevo plan",
      "nombre": "Nombre",
      "precio": "Precio / mes",
      "caracteristicas": "Características",
      "precioNota": "Cambiar el precio no afecta contratos vigentes",
      "desactivar": "Desactivar plan",
      "desactivarConfirm": "¿Desactivar este plan? Solo es posible si no tiene suscriptores activos."
    },
    "companias": {
      "title": "Gimnasios registrados",
      "nuevo": "Registrar gym",
      "suspender": "Suspender compañía",
      "suspenderMotivo": "Motivo de suspensión",
      "detalle": "Ver detalle",
      "tabs": {
        "suscripcion": "Suscripción",
        "pagos": "Pagos",
        "sucursales": "Sucursales",
        "notificaciones": "Notificaciones"
      }
    },
    "suscripcion": {
      "activa": "Suscripción activa",
      "renovar": "Renovar",
      "upgrade": "Upgrade de plan",
      "downgrade": "Downgrade de plan",
      "historial": "Historial",
      "upgradeWarning": "El plan actual será cancelado inmediatamente al confirmar",
      "downgradeInfo": "El plan actual sigue activo hasta su fecha de vencimiento"
    },
    "pagos": {
      "registrar": "Registrar pago",
      "confirmar": "Confirmar pago",
      "confirmarWarning": "¿Confirmar este pago? El estado cambiará a PAGADO."
    },
    "sucursales": {
      "nueva": "Nueva sucursal",
      "renovarQr": "Renovar QR",
      "qrWarning": "El token anterior quedará inválido inmediatamente",
      "qrCopiado": "Token copiado al portapapeles"
    },
    "notif": {
      "title": "Alertas de vencimiento",
      "agregar": "Agregar alerta",
      "guardar": "Guardar cambios",
      "info": "Las alertas se envían automáticamente cada día a las 00:05 UTC"
    }
  }
}
```

---

## Restricciones de acceso por rol (resumen para implementar en UI)

| Acción en UI | super_admin | soporte | viewer |
|---|:---:|:---:|:---:|
| Ver planes y características | ✅ | ✅ | ✅ |
| Crear/editar/desactivar planes | ✅ | ❌ | ❌ |
| Ver lista de gimnasios | ✅ | ✅ | ✅ |
| Registrar nuevo gym | ✅ | ❌ | ❌ |
| Ver/editar datos de compañía | ✅ | ✅ | ✅ |
| Suspender compañía | ✅ | ❌ | ❌ |
| Ver/crear/editar sucursales | ✅ | ✅ (solo ver) | ✅ (solo ver) |
| Renovar QR token | ✅ | ❌ | ❌ |
| Ver suscripción e historial | ✅ | ✅ | ✅ |
| Renovar/upgrade/downgrade | ✅ | ❌ | ❌ |
| Ver historial de pagos | ✅ | ✅ | ✅ |
| Registrar y confirmar pagos | ✅ | ✅ | ❌ |
| Ver/editar notif config | ✅ | ❌ | ❌ |

Implementar ocultando/deshabilitando botones y acciones según `user.rol_plataforma`. Usar el hook `useCurrentUser()` del store de auth.

---

## Checklist de entregables

```
domain/platform/entities/Plan.entity.ts
domain/platform/ports/PlatformRepository.port.ts

application/platform/GetPlanes.usecase.ts
application/platform/GestionarPlan.usecase.ts
application/platform/GetCaracteristicas.usecase.ts
application/platform/GestionarCompania.usecase.ts
application/platform/GestionarSucursal.usecase.ts
application/platform/GestionarSuscripcion.usecase.ts
application/platform/GestionarPago.usecase.ts
application/platform/GestionarNotifConfig.usecase.ts

infrastructure/http/platform/platform.dto.ts
infrastructure/http/platform/PlatformHttpRepository.ts
infrastructure/store/platform/platform.store.ts

ui/features/platform/schemas/crear-plan.schema.ts
ui/features/platform/schemas/registrar-gym.schema.ts
ui/features/platform/schemas/crear-sucursal.schema.ts
ui/features/platform/schemas/registrar-pago.schema.ts
ui/features/platform/schemas/suspender.schema.ts
ui/features/platform/schemas/notif-config.schema.ts

ui/features/platform/components/EstadoPlanBadge.tsx
ui/features/platform/components/PlanSelector.tsx
ui/features/platform/components/QrTokenDisplay.tsx

ui/features/platform/pages/PlanesPage.tsx
ui/features/platform/pages/PlanesPage/CrearPlanModal.tsx
ui/features/platform/pages/PlanesPage/EditarPlanModal.tsx
ui/features/platform/pages/PlanesPage/AsignarCaracteristicasModal.tsx

ui/features/platform/pages/CaracteristicasPage.tsx
ui/features/platform/pages/CaracteristicasPage/CrearCaracteristicaModal.tsx

ui/features/platform/pages/CompaniasPage.tsx
ui/features/platform/pages/CompaniasPage/RegistrarGymModal.tsx
ui/features/platform/pages/CompaniasPage/SuspenderCompaniaDialog.tsx

ui/features/platform/pages/CompaniaDetallePage.tsx
ui/features/platform/pages/CompaniaDetallePage/SuscripcionTab.tsx
ui/features/platform/pages/CompaniaDetallePage/SuscripcionTab/RenovarSuscripcionModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SuscripcionTab/UpgradePlanModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SuscripcionTab/DowngradePlanModal.tsx
ui/features/platform/pages/CompaniaDetallePage/PagosTab.tsx
ui/features/platform/pages/CompaniaDetallePage/PagosTab/RegistrarPagoModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SucursalesTab.tsx
ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/CrearSucursalModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/EditarSucursalModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/RenovarQrModal.tsx
ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/EditarCompaniaModal.tsx
ui/features/platform/pages/CompaniaDetallePage/NotifConfigTab.tsx

i18n/locales/es.json  (agregar sección "platform")
i18n/locales/en.json  (agregar sección "platform")

ui/router/index.tsx   (agregar 4 nuevas rutas)
```

---

## Notas finales de UX/UI

1. **Feedback inmediato**: Todos los botones de submit deben mostrar estado de carga (`isSubmitting` de React Hook Form) con spinner Lucide (`<Loader2 className="animate-spin" />`).
2. **Toasts**: Usar `toast.success()` / `toast.error()` de Sonner en todos los casos.
3. **Confirmaciones destructivas**: "Desactivar plan", "Suspender compañía", "Confirmar pago" deben usar `ConfirmDialog` ya existente.
4. **Estados vacíos**: Cada tabla debe tener un estado vacío descriptivo cuando no haya datos.
5. **Skeletons**: Usar `Skeleton` de shadcn/ui durante la carga inicial de cada página/tab.
6. **Errores 409** (conflictos de negocio): Mostrar como error inline en el formulario o en un `Alert` destructive de shadcn/ui, no solo como toast.
7. **Colores del tema**: Respetar `gym-950`, `gym-800`, `gym-orange`. No introducir colores nuevos.
8. **Responsive**: El layout de tabs en `CompaniaDetallePage` debe colapsar correctamente en mobile (tabs scrollables horizontalmente).
9. **Buscador en CompaniasPage**: Filtrado client-side sobre el array ya cargado, sin llamadas adicionales al backend.
10. **QR token**: Nunca mostrar el token completo en la tabla — truncar y ofrecer botón copiar con `QrTokenDisplay`.
