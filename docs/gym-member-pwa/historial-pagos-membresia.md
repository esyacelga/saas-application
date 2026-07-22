# Historial de Pagos de Membresía — PWA Miembros

> **ESTADO:** ✅ **Implementado** (2026-07-21)

Página de consulta para que el socio visualice todas sus membresías (activas, vencidas, rechazadas) con desglose de montos pagados, saldo pendiente, y estado de cobro.

---

## Ubicación

**Ruta:** `/membresia/historial`  
**Componente:** `HistorialPagosMembresiaPage.tsx` (`src/ui/pages/membresia/HistorialPagosMembresiaPage.tsx`)  
**Punto de entrada:** Botón "Ver historial de pagos" en `MembresiaPage` (vía sección de membresía activa o modal).

---

## Contrato de datos

Invoca `coreRepository.misMembresias()` que consume `GET /api/v1/clientes/me/membresias` (core-service):

```typescript
interface MembresiaHistorialItem {
  id: number
  id_cliente: number
  id_tipo_membresia: number
  tipo_nombre: string                         // Plan Mensual, etc.
  modo_control: 'calendario' | 'accesos'
  fecha_inicio: string                        // ISO date
  fecha_fin: string                           // ISO date
  dias_acceso_total: number | null            // Solo cuando modo_control='accesos'
  dias_acceso_usados: number | null           // Solo cuando modo_control='accesos'
  dias_acceso_restantes: number | null        // Solo cuando modo_control='accesos'
  precio_pagado: number                       // Precio final (post-descuento)
  descuento_aplicado: number                  // Monto de descuento
  monto_pagado: number                        // Derivado: estado_pago='PAGADO' ? precio_pagado : 0
  saldo_pendiente: number                     // Derivado: precio_pagado - monto_pagado
  estado: 'activa' | 'vencida' | 'congelada' | 'anulada'
  estado_pago: 'PAGADO' | 'PENDIENTE'
  eliminado: boolean                          // true si fue rechazada
  motivo_eliminacion: 'SOCIO_CAMBIO_OPINION' | 'ERROR_DE_VENTA' | 'DUPLICADA' | 'DATOS_INCORRECTOS' | 'OTRO' | null
}
```

---

## Flujo de la página

1. **Carga**: al montar, llama `coreRepository.misMembresias()` con estado `loading=true`.
2. **Ordenamiento**: ordena el array por `fecha_inicio` descendente (más reciente primero).
3. **Estados**:
   - `loading=true` → renderiza skeleton (3 tarjetas vacías, efecto pulse).
   - `error != null` → renderiza `ErrorState` con botón "Reintentar".
   - `membresias.length === 0` → renderiza `EmptyState` (ícono Receipt + texto).
   - Caso feliz → lista de tarjetas `VentaCard` con accordion expandible.

---

## Componentes

### `VentaCard`

Cada membresía se renderiza como una tarjeta (`bg-slate-800`, border `slate-700`):

**Encabezado (siempre visible):**
- Nombre del plan: `tipo_nombre` o fallback `Plan #id_tipo_membresia` si null.
- Fecha de inicio: formateada con `toLocaleDateString('es', { day: '2-digit', month: 'short', year: 'numeric' })`.
- Badge de estado de pago:
  - `PAGADO` → verde (emerald) + ícono CheckCircle.
  - `PENDIENTE` → ámbar (amber) + ícono Clock.

**Fila secundaria:**
- Badge mini con estado (`activa`, `vencida`, `congelada`, `anulada`) y clases de color respectivas (emerald, red, sky, slate). Aplica `opacity-60` si `eliminado=true`.
- Span con rango de fechas: `fecha_inicio → fecha_fin`.

**Botón toggle:**
- "Mostrar detalles" (ChevronDown) / "Ocultar detalles" (ChevronUp).
- Accesible: `aria-expanded={expanded}`.

**Sección expandida (accordion):**
- Aparece solo cuando `expanded=true`, separada por `border-t border-slate-700`.
- Filas de detalle condicionales (ver tabla abajo).

### Filas de detalle (expandidas)

| Campo | Condición | Formato |
|-------|-----------|---------|
| `precio_pagado` | Siempre | `DetailRow` — "Precio pagado: $X,XXX.00" |
| `monto_pagado` | Si `monto_pagado > 0` | `DetailRow` — "Monto pagado: $X,XXX.00" |
| `saldo_pendiente` | Si `saldo_pendiente > 0` | `DetailRow` con `highlight=true` (amber-300) — "Saldo pendiente: $X,XXX.00" |
| `descuento_aplicado` | Si `descuento_aplicado > 0` | `DetailRow` — "Descuento: $X,XXX.00" |
| Accesos | Si `modo_control==='accesos' && dias_acceso_total != null` | `DetailRow` — "Días de acceso: {diasAccesoUsados} / {diasAccesoTotal}" |
| Motivo rechazo | Si `motivo_eliminacion != null` | Párrafo `text-xs italic` — "Motivo: {traducción de motivo_eliminacion}" |

**`DetailRow` component:**
```tsx
interface DetailRowProps {
  label: string                        // Clave i18n resuelta
  value: string                        // Valor formateado
  highlight?: boolean                  // Si true, aplica amber-300; sino slate-300
}
```

### `PagoBadge`

Muestra el estado de cobro con color e ícono:
- `estado_pago='PAGADO'` → verde + CheckCircle.
- `estado_pago='PENDIENTE'` → ámbar + Clock.
- Texto localizado: `t('pagoHistorial.estadoPago.PAGADO')` / `t('pagoHistorial.estadoPago.PENDIENTE')`.

### `EstadoBadgeMini`

Badge pequeño (11px font, uppercase) con el estado de la membresía. Aplica mapa de colores (`clsMap`). Si `eliminado=true`, aplica `opacity-60`.

### `LoadingSkeleton`, `ErrorState`, `EmptyState`

Sub-componentes estándar de carga, error y vacío. Véase el componente para detalles.

---

## Traducción (i18n)

**Namespace:** `pagoHistorial.*`

**Claves esperadas:**
- `pagoHistorial.title` — Encabezado de página (ej: "Historial de pagos").
- `pagoHistorial.back` — Aria-label del botón atrás.
- `pagoHistorial.estadoPago.PAGADO` — Etiqueta badge.
- `pagoHistorial.estadoPago.PENDIENTE` — Etiqueta badge.
- `pagoHistorial.estadoMembresia.{estado}` — `activa`, `vencida`, `congelada`, `anulada`.
- `pagoHistorial.card.labels.precioPagado` — "Precio pagado".
- `pagoHistorial.card.labels.montoPagado` — "Monto pagado".
- `pagoHistorial.card.labels.saldoPendiente` — "Saldo pendiente".
- `pagoHistorial.card.labels.descuento` — "Descuento".
- `pagoHistorial.card.labels.diasAcceso` — "Días de acceso".
- `pagoHistorial.card.labels.motivo` — "Motivo".
- `pagoHistorial.motivoEliminacion.{motivo}` — `SOCIO_CAMBIO_OPINION`, `ERROR_DE_VENTA`, `DUPLICADA`, `DATOS_INCORRECTOS`, `OTRO`.
- `pagoHistorial.card.expand` — "Mostrar detalles".
- `pagoHistorial.card.collapse` — "Ocultar detalles".
- `pagoHistorial.card.labels.ariaLabel` — Aria-label para el badge (ej: "Estado de pago: PAGADO").
- `pagoHistorial.empty.title` — "Sin membresías" (o similar).
- `pagoHistorial.empty.description` — Texto ayuda para empty state.
- `pagoHistorial.error.title` — "Error al cargar".
- `pagoHistorial.error.retry` — "Reintentar".
- `pagoHistorial.loading.aria` — Aria-label para skeleton de carga.

Todos los idiomas (es.json, en.json) deben tener estas claves definidas.

---

## Manejo de errores

Invoca `getApiErrorMessage(err, t('pagoHistorial.error.title'))` que captura:
- `axios` errors con `response.data.detail` o `response.statusText`.
- Fallback al título genérico si no hay detail.

Renderiza `ErrorState` con mensaje y botón "Reintentar" que repite el fetch.

---

## Estilos

- **Tema adaptativo**: usa tokens `accent-*` para líneas de separación y botones (se adaptan automáticamente al tema activo). Los fondos/bordes usan `slate-*` (oscuros).
- **Responsivo**: `px-4` lateral, `pb-24` y `pt-4` vertical (safe-area aware). Las tarjetas son `rounded-2xl`.
- **Accesibilidad**: botones toggle tienen `aria-expanded`, ícono de carga tiene `role="status"` + aria-label.

---

## Casos de uso

1. **Membresía PAGADA activa**: socio ve su plan actual con montos y fechas, accesos disponibles si es por accesos.
2. **Membresía PENDIENTE**: muestra estado ámbar "PENDIENTE" y saldo total como pendiente (monto_pagado=0).
3. **Membresía rechazada** (`eliminado=true`): muestra `eliminado=true`, motivo en sección expandida, badge mini con opacity-60.
4. **Sin membresías**: empty state con ícono.
5. **Error de red**: error state con retry.

---

## Limitaciones conocidas

- **Ordenamiento local**: ordena por `fecha_inicio` DESC (no hay opción de filtro/ordenamiento).
- **Sin paginación**: carga todas las membresías en memoria. Para clientes con +100 membresías histórico, la UI puede resentir.
- **Timezone**: el formateo de fechas usa `toLocaleDateString('es', ...)` que respeta la zona local del navegador. Si hay discrepancia servidor (Ecuador) vs. cliente, habrá desfase de 1 día en extremos (00:00-06:00 UTC-5).

---

## Referencias

- Implementación: `src/ui/pages/membresia/HistorialPagosMembresiaPage.tsx`
- Interfaz: `src/infrastructure/http/CoreHttpRepository.ts` (`misMembresias()`)
- Tipo de dato: `MembresiaHistorialItem` en `CoreHttpRepository.ts`
- API backend: [core-service/api/membresias.md](../../docs/core-service/api/membresias.md) (§`GET /clientes/me/membresias`)
- HU: [gym-administrator/requirements/estado-pago-membresias.md](../../docs/gym-administrator/requirements/estado-pago-membresias.md) (parte A implementada, GYM-003)
