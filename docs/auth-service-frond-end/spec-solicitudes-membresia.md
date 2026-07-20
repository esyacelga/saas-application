# Especificación UI — Solicitudes de Membresía en Dashboard

**Estado:** 📜 Pendiente — spec de UI, esperando implementación  
**Última actualización:** 2026-07-17  
**Componentes afectados:** `VentasPendientesPage.tsx`, `DashboardPage.tsx`

---

## 1. VentasPendientesPage.tsx — Cambios en gestión de ventas

### 1.1 Filtros por origen (tabs)

Al top de la página, agregar tab bar con 3 opciones:

```
┌─────────────────────────────────────────┐
│  [Todas] [Solicitudes de clientes] [Sin cobrar por staff]  │
│                                                             │
│  (Tabla con membresías pendientes...)                      │
└─────────────────────────────────────────┘
```

**Implementación:**
- Usar `Tabs` de shadcn/ui o componente personalizado.
- Active tab vinculado a state local + query string: `?origen=todas|cliente|staff`.
- Al cambiar tab, actualizar query string sin refrescar la página (usar `useNavigate` de React Router).

**Valores de query string:**
- `?origen=cliente` — Solicitudes de clientes (origen_venta='cliente')
- `?origen=staff` — Ventas iniciadas por staff pendiente de cobro (origen_venta='staff')
- Sin param o `?origen=todas` — Todas las pendientes

**Sincronización:** Al montar el componente, leer query string y seleccionar el tab correspondiente.

### 1.2 Badge de origen en cada fila

Cada fila de membresía en la tabla tendrá una columna adicional con un badge/pill de color:

```
ID │ Nombre Cliente  │ Tipo      │ Precio   │ Origen              │ Acción
───┼─────────────────┼───────────┼──────────┼─────────────────────┼───────
1  │ Juan Pérez      │ Premium   │ $50.00   │ [Solicitud cliente] │ ...
2  │ María García    │ Básica    │ $30.00   │ [Venta staff]       │ ...
```

**Estilos del badge:**
- `origen='cliente'` → Fondo **amber-100**, texto **amber-900**, icono (e.g., `lucide-react` → `AlertCircle` o `Clock`). Label: "Solicitud cliente"
- `origen='staff'` → Fondo **slate-100**, texto **slate-700**, icono (e.g., `User`). Label: "Venta staff"

**Comportamiento:**
- El badge es solo visual, no clickeable.
- Si `origen` no viene del backend, asumir `'staff'` por compatibilidad.

### 1.3 Modal "Completar pago" — dos flujos

**Flujo 1: `origen='cliente'` — Solicitud del cliente**

Cuando el usuario hace clic en "Completar venta" en una fila con `origen='cliente'`, se abre modal:

```
┌─────────────────────────────────────────────────────────┐
│  Completar pago de solicitud de membresía               │ ╳
├─────────────────────────────────────────────────────────┤
│  Cliente: Juan Pérez                                    │
│  Tipo:    Premium                                       │
│  Precio catálogo: $50.00                               │
│                                                         │
│  Método de pago *                                       │
│  [ dropdown: Efectivo | Tarjeta | Transferencia | ... ]│
│                                                         │
│  Descuento ($) (opcional)                              │
│  [ input: 0.00 ]                                        │
│                                                         │
│  Fecha de inicio *                                      │
│  [ datepicker: 2026-07-17 (default hoy) ]             │
│                                                         │
│  Precio pagado ($) *                                   │
│  [ input: 50.00 (prefill desde catálogo, editable) ]  │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Total a registrar: $50.00                       │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│                         [ Cancelar ] [ Confirmar ]     │
└─────────────────────────────────────────────────────────┘
```

**Campos:**

| Campo | Tipo | Requerido | Default | Validación | Notas |
|-------|------|-----------|---------|-----------|-------|
| Método de pago | Dropdown (enum) | Sí | — | No vacío | Fetch desde `GET /metodos-pago`. Mostrar nombre legible. |
| Descuento | Input numérico | No | 0.00 | ≥ 0, ≤ precio pagado | Opcional. Solo números. |
| Fecha inicio | Date picker | Sí | Hoy (Date.now()) | Valid date, no futura | Reutilizar `react-day-picker` (ya en el proyecto). |
| Precio pagado | Input numérico | Sí | Precio del tipo (catálogo) | > 0 | Editable. Puede diferir del precio catálogo si hay ajuste. |

**Cálculo mostrado:**
- Mostrar línea "Total a registrar: $X.XX" debajo de los inputs, calculado como: `precio_pagado - descuento_aplicado` (si descuento < precio_pagado, si no mostrar aviso visual).

**Validaciones de cliente (UI):**
- Método pago: no vacío.
- Descuento: ≥ 0.
- Fecha inicio: no vacía, no futura.
- Precio pagado: > 0.
- Descuento no puede ser mayor que Precio pagado.

**Submit:**
```typescript
POST /api/v1/membresias/{id}/confirmar-pago
Body: {
  id_metodo_pago: number,
  descuento_aplicado: number (default 0 si omitido),
  fecha_inicio: "2026-07-17" (ISO 8601),
  precio_pagado: number
}
```

**Respuesta esperada:** 200 OK (details TBD con backend).

**Manejo de error:**
- `400` — Validación (descuento > precio_pagado, fecha futura, etc.): Mostrar toast error con el mensaje del backend.
- `404` — Membresía no existe: Toast error, cerrar modal.
- `409` — Conflicto (ya confirmada, etc.): Toast error.
- `5xx` — Server error: Toast error genérico.

**Éxito:**
1. Toast success: "Pago registrado correctamente"
2. Cerrar modal
3. Refrescar lista de membresías (re-fetch desde el servidor)
4. Opcional: animar salida de la fila de la tabla

**Flujo 2: `origen='staff'` — Venta iniciada por staff**

Para filas con `origen='staff'` que ya tienen precio registrado:

```
┌─────────────────────────────────────────────────────────┐
│  Completar pago                                         │ ╳
├─────────────────────────────────────────────────────────┤
│  Cliente: María García                                  │
│  Tipo:    Básica                                        │
│  Precio:  $30.00                                        │
│                                                         │
│  ¿Completar pago de esta membresía?                    │
│                                                         │
│                         [ Cancelar ] [ Confirmar ]     │
└─────────────────────────────────────────────────────────┘
```

**Comportamiento:**
- Modal simplificado solo con confirmación.
- Submit → `POST /api/v1/membresias/{id}/confirmar-pago` **sin body** (compatibilidad actual).
- Éxito: idem flujo 1.

### 1.4 Tabla — Cambios de renderizado

**Columnas existentes (sin cambios):**
- ID
- Nombre cliente
- Tipo membresía
- Precio
- Fecha (si la hay)

**Nuevo:**
- Columna "Origen" (badge)

**Botón de acción:**
- "Completar venta" → abre modal.
- Texto igual para ambos orígenes (el modal se adapta).

### 1.5 Sincronización con query string

```typescript
// Al montar:
const [searchParams] = useSearchParams()
const origen = searchParams.get('origen') || 'todas'
const [activeTab, setActiveTab] = useState(origen)

// Al cambiar tab:
const handleOrigenChange = (newOrigen: string) => {
  setActiveTab(newOrigen)
  navigate(`?origen=${newOrigen === 'todas' ? '' : newOrigen}`, { replace: true })
}

// Fetch con filtro:
const response = await coreRepository.getMembresiassPendientes({
  origen: activeTab === 'todas' ? undefined : activeTab
})
```

---

## 2. DashboardPage.tsx — Widget nuevo

### 2.1 Card "Solicitudes de membresía"

Al dashboard principal, agregar nueva KPI card junto a las existentes ("Sin suscripción", "Próximos a vencer", etc.):

```
┌─────────────────────────┐
│ Solicitudes de membresía│
│                         │
│        5 nuevas         │
│                         │
│   [Ver solicitudes ▶]   │
└─────────────────────────┘
```

**Posición:** Recomendado al lado del card "Sin suscripción" o debajo (revisar diseño actual del dashboard).

**Contenido:**
- Título: "Solicitudes de membresía" (i18n key: `dashboard.solicitudesMembresia.titulo`)
- Número grande: contador de solicitudes de clientes (solo `origen='cliente'`)
- Label bajo número: "nuevas" o "pendientes" (i18n key: `dashboard.solicitudesMembresia.label`)
- Botón/enlace: "Ver solicitudes →" o solo texto "click aquí"

**Click:**
- Navega a `/admin/ventas-pendientes?origen=cliente`

### 2.2 Refetch y polling

**Endpoint:**
```typescript
GET /api/v1/companias/{id}/membresias/pendientes/contador
Response: {
  total: number,
  por_origen: {
    cliente: number,
    staff: number
  }
}
```

**Lógica:**
- Fetch al montar el componente.
- Refetch cada 5 minutos (configurable) automáticamente.
- Refetch al enfocar la ventana (`onfocus` event o Visibility API).
- Integrar con patrón actual de refetch del dashboard (revisar cómo lo hace `SinSuscripcionPanel` o `ProximosVencerPanel`).

**Error handling:**
- Si falla el fetch: mostrar "–" en lugar del número o mantener el anterior.
- No mostrar toast de error (es un widget, no una acción del usuario).

### 2.3 Styling y animación

- Reutilizar el componente `AlertKpiCard` o `KpiCard` existente del dashboard.
- Color/icono: Ámbar (como "Sin suscripción") o color propio si la marca lo requiere.
- Icono sugerido: `Clock`, `AlertCircle`, o `FileText` (lucide-react).

---

## 3. Componentes y archivos

### Nuevos archivos:
- `src/ui/features/core/components/CompletarPagoMembresiasModal.tsx` — Modal reutilizable (similar a `VenderMembresiaModal`).

### Archivos modificados:
- `src/ui/features/core/pages/VentasPendientesPage.tsx` — tabs, badge, modal openings, query string sync.
- `src/ui/features/admin/pages/DashboardPage.tsx` — nuevo widget.
- `src/infrastructure/http/core/CoreRepository.ts` — métodos nuevos.
- `src/i18n/locales/es.json` — i18n keys.
- `src/i18n/locales/en.json` — i18n keys (English).

### Sin cambios:
- Otros componentes del dashboard.
- Rutas (ya existe `/admin/ventas-pendientes`).

---

## 4. Diccionario i18n

### Nuevas claves (español — es.json)

```json
{
  "dashboard": {
    "solicitudesMembresia": {
      "titulo": "Solicitudes de membresía",
      "label": "pendientes",
      "verSolicitudes": "Ver solicitudes"
    }
  },
  "ventasPendientes": {
    "titulo": "Ventas pendientes",
    "tabs": {
      "todas": "Todas",
      "clientes": "Solicitudes de clientes",
      "staff": "Sin cobrar por staff"
    },
    "origen": {
      "cliente": "Solicitud cliente",
      "staff": "Venta staff"
    },
    "modal": {
      "titulo": "Completar pago",
      "cliente": "Cliente",
      "tipo": "Tipo",
      "precioCatalogo": "Precio catálogo",
      "metodoPago": "Método de pago",
      "metodoPagoRequerido": "Selecciona un método de pago",
      "descuento": "Descuento ($) (opcional)",
      "descuentoError": "El descuento no puede ser mayor que el precio pagado",
      "fechaInicio": "Fecha de inicio",
      "fechaInicioRequerida": "Selecciona una fecha de inicio",
      "fechaInicioFutura": "No puedes seleccionar una fecha futura",
      "precioPagado": "Precio pagado ($)",
      "precioPagadoRequerido": "El precio pagado es requerido",
      "precioPagadoPositivo": "El precio pagado debe ser mayor a 0",
      "totalARegistrar": "Total a registrar",
      "cancelar": "Cancelar",
      "confirmar": "Confirmar",
      "successMessage": "Pago registrado correctamente"
    }
  }
}
```

### Nuevas claves (inglés — en.json)

```json
{
  "dashboard": {
    "solicitudesMembresia": {
      "titulo": "Membership Requests",
      "label": "pending",
      "verSolicitudes": "View requests"
    }
  },
  "ventasPendientes": {
    "titulo": "Pending Sales",
    "tabs": {
      "todas": "All",
      "clientes": "Client Requests",
      "staff": "Unpaid by Staff"
    },
    "origen": {
      "cliente": "Client request",
      "staff": "Staff sale"
    },
    "modal": {
      "titulo": "Complete Payment",
      "cliente": "Client",
      "tipo": "Type",
      "precioCatalogo": "Catalog Price",
      "metodoPago": "Payment Method",
      "metodoPagoRequerido": "Please select a payment method",
      "descuento": "Discount ($) (optional)",
      "descuentoError": "Discount cannot exceed the price paid",
      "fechaInicio": "Start Date",
      "fechaInicioRequerida": "Please select a start date",
      "fechaInicioFutura": "You cannot select a future date",
      "precioPagado": "Amount Paid ($)",
      "precioPagadoRequerido": "Amount paid is required",
      "precioPagadoPositivo": "Amount paid must be greater than 0",
      "totalARegistrar": "Total to Register",
      "cancelar": "Cancel",
      "confirmar": "Confirm",
      "successMessage": "Payment recorded successfully"
    }
  }
}
```

---

## 5. Integración con backend

### Endpoints requeridos

**GET /api/v1/metodos-pago**
```json
Response (200):
[
  { "id": 1, "nombre": "Efectivo" },
  { "id": 2, "nombre": "Tarjeta de crédito" },
  { "id": 3, "nombre": "Transferencia bancaria" },
  { "id": 4, "nombre": "Otro" }
]
```

**GET /api/v1/membresias/pendientes?origen=cliente|staff|todos**
```json
Response (200):
[
  {
    "id": 1,
    "id_cliente": 101,
    "nombre_cliente": "Juan Pérez",
    "id_tipo_membresia": 5,
    "nombre_tipo": "Premium",
    "precio_actual": 50.00,
    "origen": "cliente",
    "fecha_solicitud": "2026-07-17T10:30:00Z"
  }
]
```

**POST /api/v1/membresias/{id}/confirmar-pago**
```json
Body:
{
  "id_metodo_pago": 1,
  "descuento_aplicado": 0,
  "fecha_inicio": "2026-07-17",
  "precio_pagado": 50.00
}
Response (200):
{
  "id": 1,
  "id_cliente": 101,
  "id_tipo_membresia": 5,
  "estado": "activo",
  "fecha_inicio": "2026-07-17",
  "fecha_vencimiento": "2026-08-17"
}
```

**GET /api/v1/companias/{id}/membresias/pendientes/contador**
```json
Response (200):
{
  "total": 3,
  "por_origen": {
    "cliente": 2,
    "staff": 1
  }
}
```

---

## 6. Flujos de error y edge cases

### Error 400 — Validación del servidor

**Caso:** Descuento > precio_pagado

```json
Response:
{
  "error": "El descuento no puede ser mayor que el precio pagado",
  "codigo": "descuento_invalido"
}
```

**Manejo:** Toast error rojo con el mensaje.

### Error 404 — Membresía no existe

```json
Response:
{
  "error": "Membresía no encontrada",
  "codigo": "membresia_no_existe"
}
```

**Manejo:** Toast error, cerrar modal, refrescar tabla.

### Error 409 — Membresía ya completada

```json
Response:
{
  "error": "Esta membresía ya fue completada",
  "codigo": "membresia_ya_completada"
}
```

**Manejo:** Toast error, cerrar modal, refrescar tabla (la fila desaparecerá).

### Edge case — Descuento = Precio pagado

**Comportamiento:** Total a registrar = $0.00. Mostrar aviso visual (naranja) pero permitir envío (staff puede crear membresía sin costo, e.g., regalo).

### Edge case — Campo vacío después de cambio

Si el usuario edita `precio_pagado` y lo deja vacío, antes de hacer submit: mensaje de validación "Este campo es requerido", botón "Confirmar" deshabilitado.

---

## 7. Criterios de aceptación

- [ ] Tabs de filtro sincronizados con query string y tabla.
- [ ] Badge de origen visible en cada fila.
- [ ] Modal "Completar pago" abre con datos pre-llenados (cliente, tipo, precio catálogo).
- [ ] Validaciones de cliente funcionan antes de submit.
- [ ] Submit envía body correcto según origin.
- [ ] Éxito muestra toast y refrescar tabla.
- [ ] Error maneja toast y no cierra modal.
- [ ] Widget "Solicitudes de membresía" fetches contador correctamente.
- [ ] Widget refetch cada 5 min y al enfocar ventana.
- [ ] Click en widget navega a `/admin/ventas-pendientes?origen=cliente`.
- [ ] Todos los i18n keys están en es.json y en.json.
- [ ] No hay errores de TypeScript, lint.

---
