# UI Spec — Ventas pendientes (auth-service-frond-end)

Spec de la nueva vista **"Ventas pendientes"** en el panel admin, correspondiente a §4.8 de la HU GYM-003 (`../requirements/estado-pago-membresias.md`).

Contract backend: `../../core-service/api/membresias.md` — endpoints `GET /companias/{idCompania}/membresias/pendientes`, `POST /membresias/{id}/confirmar-pago`, `POST /membresias/{id}/rechazar`.

---

## 1. Ubicación en la app

**Ruta:** `/admin/ventas-pendientes`

**Entrada en sidebar:** nuevo `NavItem` en `AdminLayout.tsx`, insertado entre `nav.tiposMembresia` y `nav.usuarios`:

```
{ to: '/admin/ventas-pendientes', labelKey: 'nav.ventasPendientes', icon: <Clock size={20} />, permiso: 'membresias:confirmar_pago' }
```

El ítem solo aparece cuando el usuario tiene el permiso — el filtro `ALL_NAV_ITEMS.filter(item => !item.permiso || permisos.includes(item.permiso))` ya lo maneja (mismo mecanismo que los demás ítems con `permiso`).

**Guard de ruta:** en `router/index.tsx`, bajo `AdminLayout`:

```
{ path: '/admin/ventas-pendientes', element: <VentasPendientesPage /> }
```

La ruta NO envuelve en `PermissionGuard` como wrapper (consistente con `ClientesPage`, `TiposMembresiaPage`). La página llama a `useHasPermission` internamente y renderiza estado "sin acceso" si el permiso se revoca en runtime (ver §8).

**Justificación de página separada (no tab en Clientes):** §4.8 y N11 lo exigen. Las ventas pendientes son una cola operacional independiente del estado de cada cliente; mezclarlas en Clientes rompe el flujo de recepción que necesita actuar sobre la cola completa sin navegar cliente por cliente.

---

## 2. Layout

```
┌─ AdminLayout (sidebar izq) ─────────────────────────────────────────┐
│  ┌─ main overflow-y-auto ────────────────────────────────────────┐ │
│  │  PageHeader                                                    │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │ title: t('ventasPendientes.title')                       │ │ │
│  │  │ description: t('ventasPendientes.description')           │ │ │
│  │  │ action: [Actualizar ↺]                                   │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  │                                                                │ │
│  │  Barra de filtros  (px-6 py-3, border-bottom)                  │ │
│  │  ┌──────────────────────────────────────────────────┐         │ │
│  │  │ [🔍 Buscar por nombre de cliente...            ] │         │ │
│  │  └──────────────────────────────────────────────────┘         │ │
│  │                                                                │ │
│  │  DataTable  (flex-1 p-4)                                       │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │ # │ Cliente │ Tipo membresía │ Precio │ Desc.│ Pendiente │ │ │
│  │  │───┼─────────┼────────────────┼────────┼──────┼───────────│ │ │
│  │  │ 1 │ Juan P. │ Plan Mensual   │ $35.00 │  —   │ hace 2d   │ │ │
│  │  │   │         │                │        │      │ [✓] [✗]   │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

Sin panel lateral de detalle — todas las acciones se resuelven en fila o en el dialog de rechazo.

---

## 3. Columnas de la tabla

| # | Header i18n | Campo | Formato |
|---|-------------|-------|---------|
| 1 | `ventasPendientes.colCliente` | `nombre_cliente` (ver §11) | Texto + avatar inicial (patrón de ClientesPage) |
| 2 | `ventasPendientes.colTipo` | `tipo_nombre` (backend actual lo devuelve) | Texto |
| 3 | `ventasPendientes.colPrecio` | `precio_pagado` | `$35.00` (2 decimales, prefijo `$`) |
| 4 | `ventasPendientes.colDescuento` | `descuento_aplicado` | `$0.00`; si es `0.00` → `—` en `var(--page-muted)` |
| 5 | `ventasPendientes.colPendienteDesde` | `creacion_fecha` | Tiempo relativo `t('ventasPendientes.haceN', { count: n })`; tooltip con fecha absoluta ISO local |
| 6 | `ventasPendientes.colAcciones` | — | Botones (§4) |

Orden: `creacion_fecha DESC` (backend lo garantiza). Sin paginación en la primera versión — la cola será corta. Si crece a >50 filas, añadir `paginator rows={25}`.

Anchos sugeridos: `colPrecio`/`colDescuento` → `7rem`; `colPendienteDesde` → `10rem`; `colAcciones` → `8rem`; resto flexible.

---

## 4. Acciones por fila

**Confirmar pago**
- PrimeReact `Button` `severity="success"` `size="small"` `icon="pi pi-check"` `label={t('ventasPendientes.accionConfirmar')}`
- `pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}` (consistente con ClientesPage)
- `loading` mientras la petición está en vuelo → deshabilita ambos botones de esa fila
- Tooltip: `t('ventasPendientes.tooltipConfirmar')`
- **Sin dialog de confirmación** — la acción es idempotente (backend devuelve 200 aunque ya esté PAGADO). Toast success + refetch.

**Rechazar**
- PrimeReact `Button` `severity="danger"` `size="small"` `text` `icon="pi pi-times"` `label={t('ventasPendientes.accionRechazar')}`
- `pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}`
- Tooltip: `t('ventasPendientes.tooltipRechazar')`
- Abre `RechazarVentaPendienteModal` pasando `id` y `nombre_cliente` de la fila.

**Estado disabled**: durante el request activo de cualquier acción de la fila, ambos botones de esa fila se deshabilitan. Otras filas no se ven afectadas.

---

## 5. Dialog "Rechazar"

Componente nuevo `RechazarVentaPendienteModal` en `src/ui/features/core/components/`. Patrón: shadcn `Dialog` (`@/components/ui/dialog`), mismo estilo que `CongelarMembresiaModal` / `EditarClienteModal` / `VenderMembresiaModal` (TODOS los modales de `features/core` usan shadcn, no PrimeReact Dialog).

**Header:** `t('ventasPendientes.rechazarTitle', { nombre })`

**Cuerpo:**
- Aviso en `var(--page-muted)` `text-xs`: `t('ventasPendientes.rechazarAviso')`
- Label `t('ventasPendientes.rechazarMotivoLabel')` (`text-xs font-medium`)
- `<select>` nativo con las clases estándar (`var(--input-bg)`, `var(--input-border)`, `var(--input-text)`):
  - Placeholder disabled `""`: `t('ventasPendientes.rechazarMotivoPlaceholder')`
  - `SOCIO_CAMBIO_OPINION` → `t('ventasPendientes.motivo.socioCambioOpinion')`
  - `ERROR_DE_VENTA` → `t('ventasPendientes.motivo.errorDeVenta')`
  - `DUPLICADA` → `t('ventasPendientes.motivo.duplicada')`
  - `DATOS_INCORRECTOS` → `t('ventasPendientes.motivo.datosIncorrectos')`
  - `OTRO` → `t('ventasPendientes.motivo.otro')`

  (Valores confirmados contra el enum `Membresia.MotivoEliminacion` del backend.)

- Error inline en rojo `text-xs` si la petición falla.

**Footer:**
- Secundario `t('common.cancel')` — cierra sin acción
- Danger `t('ventasPendientes.rechazarSubmit')` — disabled si no hay motivo; `Loader2` mientras el request está en vuelo

**Flujo éxito:** cierra + toast `t('ventasPendientes.rechazadaSuccess')` + refetch.

**Manejo 409:** toast error `t('ventasPendientes.rechazarError409')` + cierra dialog + refetch (la lista queda consistente).

---

## 6. Empty state

Cuando el array de pendientes está vacío (respuesta `[]`):

```
Centro vertical (py-16 text-center):
  <CheckCircle size={40} /> de lucide-react, color var(--page-border)
  Título (text-sm font-medium, var(--page-muted)):
    t('ventasPendientes.emptyTitle')
  Subtítulo (text-xs mt-1, var(--page-muted)):
    t('ventasPendientes.emptySubtitle')
```

**Filtro activo con resultado vacío**: mismo layout pero con `t('ventasPendientes.emptyFiltered', { q })`.

---

## 7. Filtros y búsqueda

**Client-side con debounce de 300ms.**

Justificación:
- La cola de pendientes es por definición corta (§4.8 no impone límites pero N4 confirma que no hay timeout que las acumule).
- El endpoint no expone `?nombre=`.
- Al resolver `nombre_cliente` client-side (§11), el filtrado es trivial.

**Implementación:** input nativo con ícono `Search` de lucide (mismo estilo que ClientesPage). Estado local `[query, setQuery]`. `useMemo`:

```
items.filter(i => i.nombre_cliente.toLowerCase().includes(debouncedQuery.toLowerCase()))
```

El filtro no afecta las llamadas al backend — se carga la lista completa al montar y en cada refetch.

---

## 8. Guard de permiso

**Nivel de ruta:** sin `PermissionGuard` wrapper (consistente con el resto de páginas de `core`). El sidebar ya oculta el ítem.

**Nivel de página — permiso revocado en runtime:** `useHasPermission('membresias:confirmar_pago')` al inicio. Si es `false`, la página renderiza inline:

```
py-24 text-center
  <ShieldOff size={40} /> color var(--page-border)
  t('noAccess.title')
  t('noAccess.description')
```

Cubre el caso del §4.9: el dueño revoca el permiso al rol durante la sesión activa.

**Nivel de acción:** los botones "Confirmar" y "Rechazar" se envuelven en `<IfPermission permiso="membresias:confirmar_pago">`. Si el permiso no está, la columna de acciones queda vacía (patrón consistente con ClientesPage).

**Backend 403**: si el GET devuelve 403, tratar como estado "sin acceso" de la página completa.

---

## 9. Estados de carga y error

**Loading inicial**: skeleton de 5 filas (`motion-safe:animate-pulse`, rectángulos en `var(--page-surface)`, alto `h-10`). Sin librería externa.

**Error de GET** (≠ 403): bloque inline centrado con `AlertCircle` + `t('ventasPendientes.loadError')` + botón `t('common.refresh')` que reintenta.

**GET 403**: bloque "sin acceso" (§8).

**Toasts**:
- Confirmar OK → `toast.success(t('ventasPendientes.confirmadaSuccess'))`
- Rechazar OK → `toast.success(t('ventasPendientes.rechazadaSuccess'))`
- Confirmar 409 → `toast.error(t('ventasPendientes.confirmarError409'))`
- Error genérico → `toast.error(t('ventasPendientes.accionError'))`

En todos los errores de acción se refresca la lista automáticamente para mantener consistencia UI ↔ backend.

---

## 10. Claves i18n

**`es.json`:**

```json
"ventasPendientes": {
  "title": "Ventas pendientes",
  "description": "Cola de membresías vendidas pendientes de cobro",
  "colCliente": "Cliente",
  "colTipo": "Tipo de membresía",
  "colPrecio": "Precio",
  "colDescuento": "Descuento",
  "colPendienteDesde": "Pendiente desde",
  "colAcciones": "Acciones",
  "accionConfirmar": "Confirmar",
  "accionRechazar": "Rechazar",
  "tooltipConfirmar": "Marcar como pagada",
  "tooltipRechazar": "Rechazar esta venta",
  "haceN_one": "hace {{count}} día",
  "haceN_other": "hace {{count}} días",
  "emptyTitle": "No hay ventas pendientes de cobro",
  "emptySubtitle": "Todas las ventas han sido procesadas.",
  "emptyFiltered": "Sin resultados para \"{{q}}\"",
  "loadError": "No se pudo cargar la lista.",
  "confirmadaSuccess": "Pago confirmado. Membresía activada.",
  "rechazadaSuccess": "Venta rechazada correctamente.",
  "confirmarError409": "Esta membresía fue rechazada. Recarga la lista.",
  "accionError": "Ocurrió un error. Intenta de nuevo.",
  "rechazarTitle": "Rechazar venta de {{nombre}}",
  "rechazarAviso": "La membresía quedará anulada. Esta acción no se puede deshacer.",
  "rechazarMotivoLabel": "Motivo de rechazo",
  "rechazarMotivoPlaceholder": "Seleccionar motivo...",
  "rechazarSubmit": "Rechazar venta",
  "rechazarError409": "No se puede rechazar: membresía ya pagada o ya rechazada.",
  "motivo": {
    "socioCambioOpinion": "El socio cambió de opinión",
    "errorDeVenta": "Error en la venta",
    "duplicada": "Venta duplicada",
    "datosIncorrectos": "Datos incorrectos",
    "otro": "Otro"
  }
}
```

**`en.json`:**

```json
"ventasPendientes": {
  "title": "Pending Sales",
  "description": "Queue of sold memberships awaiting payment",
  "colCliente": "Client",
  "colTipo": "Membership type",
  "colPrecio": "Price",
  "colDescuento": "Discount",
  "colPendienteDesde": "Pending since",
  "colAcciones": "Actions",
  "accionConfirmar": "Confirm",
  "accionRechazar": "Reject",
  "tooltipConfirmar": "Mark as paid",
  "tooltipRechazar": "Reject this sale",
  "haceN_one": "{{count}} day ago",
  "haceN_other": "{{count}} days ago",
  "emptyTitle": "No pending sales",
  "emptySubtitle": "All sales have been processed.",
  "emptyFiltered": "No results for \"{{q}}\"",
  "loadError": "Failed to load the list.",
  "confirmadaSuccess": "Payment confirmed. Membership activated.",
  "rechazadaSuccess": "Sale rejected.",
  "confirmarError409": "This membership was already rejected. Reload the list.",
  "accionError": "An error occurred. Try again.",
  "rechazarTitle": "Reject sale for {{nombre}}",
  "rechazarAviso": "The membership will be cancelled. This action cannot be undone.",
  "rechazarMotivoLabel": "Rejection reason",
  "rechazarMotivoPlaceholder": "Select reason...",
  "rechazarSubmit": "Reject sale",
  "rechazarError409": "Cannot reject: membership already paid or already rejected.",
  "motivo": {
    "socioCambioOpinion": "Member changed their mind",
    "errorDeVenta": "Sale entry error",
    "duplicada": "Duplicate sale",
    "datosIncorrectos": "Incorrect data",
    "otro": "Other"
  }
}
```

**Nav labels:**
- `es`: `"nav.ventasPendientes": "Ventas pendientes"`
- `en`: `"nav.ventasPendientes": "Pending Sales"`

---

## 11. Trade-off: resolución de `nombre_cliente`

**Estado actual del backend (verificado contra `MembresiaPendienteResponse.java`):** devuelve `id`, `id_cliente`, `id_tipo_membresia`, `tipo_nombre`, `modo_control`, `precio_pagado`, `descuento_aplicado`, `creacion_fecha`. **No devuelve `nombre_cliente`.**

**Opciones:**

- **A — JOIN en backend (recomendada).** Añadir `nombre_cliente` al response haciendo `JOIN core.clientes c ON c.id = m.id_cliente JOIN identidad.personas p ON p.id = c.id_persona`. Mismo patrón que ya se usa para `tipo_nombre`. Coste: una query, un round-trip, un cambio en el DTO. Habilita filtrado client-side trivial.
- **B — N+1 en frontend (descartada).** Un `GET /clientes/{id}` por fila. Con 50 pendientes, 51 requests. Inaceptable.
- **C — Batch lookup en frontend (descartada).** `GET /clientes?ids=1,2,3` no existe (revisado en `docs/core-service/api/clientes.md`).

**Recomendación: A.** Es una dependencia **bloqueante** para el `frontend-developer`. Antes de arrancar el frontend, extender `MembresiaPendienteResponse` con `nombreCliente` y ajustar el query de `findPendientesByCompania` en el persistence adapter.

---

## 12. Componentes a usar / crear

**Existentes (reutilizar):**
- `PageHeader` (con slot `action`)
- `DataTable` + `Column` de PrimeReact
- `Dialog` + `Button` de PrimeReact
- `IfPermission`
- `useHasPermission`
- `useTranslation`
- `useAuthStore` (para `id_compania` del JWT)
- `toast` de Sonner

**Nuevos:**
- `VentasPendientesPage` en `src/ui/features/core/pages/`
- `RechazarVentaPendienteModal` en `src/ui/features/core/components/`
- Métodos en `coreRepository`:
  - `getPendientes(idCompania: number): Promise<VentaPendiente[]>`
  - `confirmarPago(idMembresia: number): Promise<MembresiaResponse>`
  - `rechazarMembresia(idMembresia: number, motivo: string): Promise<MembresiaResponse>`
- Tipo `VentaPendiente`: `{ id, idCliente, nombreCliente, idTipoMembresia, tipoNombre, modoControl, precioPagado, descuentoAplicado, creacionFecha }`

---

## 13. Notas para el frontend-developer

1. **Dependencia bloqueante** — el backend debe añadir `nombre_cliente` al response del GET pendientes (§11). Coordinar con backend-developer antes de arrancar.
2. **`id_compania`** viene del JWT: `(user as JwtPayloadStaff).id_compania`. No pedirle al usuario.
3. **Tiempo relativo**: `Date.now() - new Date(creacion_fecha).getTime()` en un util local. NO instalar `date-fns` solo para esto — no está en el proyecto.
4. **Confirmar pago idempotente**: el backend devuelve 200 aunque ya estuviera PAGADO. Tratar cualquier 200 como éxito, mismo toast en ambos casos.
5. **Rechazar via Dialog declarativo**, NO `confirmDialog()` imperativo — el select complejo no encaja en el patrón imperativo de PrimeReact.
6. **Preexisting broken ITs** — antes de correr integration tests, ver la memoria `project_core_java25_and_broken_tests.md`. Correr unit tests `*Test` primero.

---

## 14. Decisiones controversiales (revisar antes de implementar)

1. **"Confirmar pago" sin dialog de confirmación** — el flujo va directo al POST. Argumento: la acción es idempotente y el socio ya está frente al recepcionista; añadir un paso extra ralentiza el flujo. Alternativa si prefieres extra fricción: `confirmDialog()` con "¿Confirmar que se recibió el pago?".
2. **`PermissionGuard` como wrapper de ruta** — no se añade, siguiendo el patrón de `core`. Si prefieres consistencia total con `auth`, el cambio a `element: <PermissionGuard permiso="membresias:confirmar_pago"><VentasPendientesPage /></PermissionGuard>` es trivial y sin impacto visual.
