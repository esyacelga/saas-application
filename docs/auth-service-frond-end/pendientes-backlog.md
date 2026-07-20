# auth-service-frond-end — Tareas Pendientes

> **ESTADO:** 📜 Backlog — tareas pendientes, NO estado actual del código. Algunas ya pueden estar hechas; verificar contra el código. Ver [../STATUS.md](../STATUS.md).

Estado auditado: 2026-07-17  
Contexto: React 19 / TypeScript / Tailwind CSS / shadcn/ui / React Router v7

---

## Resumen ejecutivo

El dashboard admin y los módulos transversales (auth, usuarios, roles) están completos. Las tareas pendientes son features de producto (solicitud de membresía desde PWA + gestión en dashboard) y mejoras de UX (estadísticas adicionales, filtros avanzados).

---

## Tarea 1 — Solicitudes de membresía en Dashboard

**Prioridad:** Alta  
**Estimado:** 5–7 h (frontend + backend)  
**Estado:** Pendiente — spec definida, esperando implementación backend  

### Descripción

Cuando un cliente solicita una membresía desde su PWA (porque no tiene una activa), esa solicitud aparece en el dashboard del staff con origen distintivo (`origen='cliente'`). El staff debe poder completar el pago de esa solicitud sin tener que crear la membresía manualmente.

Cambios en dos páginas del dashboard:

1. **`VentasPendientesPage.tsx`** — filtrado y gestión de solicitudes pendientes de pago.
2. **`DashboardPage.tsx`** — widget nuevo con contador de solicitudes de clientes.

### Especificación detallada

Consultar: [`spec-solicitudes-membresia.md`](spec-solicitudes-membresia.md) para UI, componentes, flujos de error, campos del modal, e integración con endpoint `confirmar-pago` extendido.

### Cambios de VentasPendientesPage.tsx

- **Badge/pill de color por `origen`** en cada fila:
  - `origen='cliente'` → amber (más urgente — solicitud del cliente esperando venta)
  - `origen='staff'` → slate (venta ya iniciada pendiente de cobro)

- **Filtro tabs** en el top: `Todas | Solicitudes de clientes | Sin cobrar por staff`.
  - Query string: `?origen=cliente` o `?origen=staff` (para deep-link desde el widget del dashboard).

- **Botón "Completar venta"** en cada fila con `origen=cliente` abre modal:
  - Campos: `id_metodo_pago` (obligatorio, dropdown desde `GET /metodos-pago`), `descuento_aplicado` (opcional, default 0), `fecha_inicio` (default hoy), `precio_pagado` (prefill desde catálogo actual de tipos_membresia, editable).
  - Submit → `POST /api/v1/membresias/{id}/confirmar-pago` con esos 4 campos en el body.
  - Éxito: toast + refresh de la lista.

- Para filas `origen=staff` con precio ya cargado, el botón "Completar venta" mantiene comportamiento actual (solo confirma, body vacío).

### Cambios de DashboardPage.tsx

Card nueva en el dashboard principal:
- Título: "Solicitudes de membresía"
- Contador de solicitudes de clientes (`por_origen.cliente`) desde `GET /api/v1/companias/{id}/membresias/pendientes/contador`
- Click → navega a `/admin/ventas-pendientes?origen=cliente`
- Refetch cada X minutos o al enfocar la ventana (patrón actual del dashboard).

### Dependencias backend

**Endpoints nuevos o extendidos:**
1. `GET /api/v1/membresias/pendientes` — filtro `?origen=cliente|staff|todos` (ya existe, agregar filtro).
2. `POST /api/v1/membresias/{id}/confirmar-pago` — extender para aceptar `id_metodo_pago`, `descuento_aplicado`, `fecha_inicio`, `precio_pagado` (hoy el body es vacío).
3. `GET /api/v1/companias/{id}/membresias/pendientes/contador` — retorna `{ total: number, por_origen: { cliente: number, staff: number } }`.
4. `GET /metodos-pago` — lista de métodos de pago (verificar endpoint exacto con backend).

Ver: [`../gym-administrator/requirements/solicitudes-membresia.md`](../gym-administrator/requirements/solicitudes-membresia.md) para especificación backend completa.

### Requisitos de implementación

1. **`VentasPendientesPage.tsx`**:
   - Agregar state de tabs (Todas | Clientes | Staff).
   - Sinc tabs con query string (`?origen=...`).
   - Renderizar badge de origen en cada fila.
   - Modal nuevo "Completar pago" con lógica diferente según origen.

2. **Modal "Completar pago"** (`CompletarPagoMembresiasModal` nueva):
   - Ver spec para campos exactos y validaciones.

3. **`DashboardPage.tsx`**:
   - Card nuevo "Solicitudes de membresía" con contador y click a `/admin/ventas-pendientes?origen=cliente`.
   - Refetch via `useEffect` + polling (similar a otras KPI cards).

4. **`CoreRepository.ts`**:
   - Extender `getMembresiassPendientes(filtro?: { origen?: string })`.
   - Agregar `getMetodosPago()`.
   - Agregar `getMembresiasContador(idCompania: number)`.
   - Agregar `confirmarPagoMembresia(idMembresia: number, payload: { id_metodo_pago, descuento_aplicado?, fecha_inicio, precio_pagado })`.

5. **i18n keys nuevas**:
   - `dashboard.solicitudesMembresia.titulo`
   - `dashboard.solicitudesMembresia.contador`
   - `ventasPendientes.tabs.todas`
   - `ventasPendientes.tabs.clientes`
   - `ventasPendientes.tabs.staff`
   - `ventasPendientes.origen.cliente`
   - `ventasPendientes.origen.staff`
   - `ventasPendientes.modal.titulo`
   - `ventasPendientes.modal.metodoPago`
   - `ventasPendientes.modal.descuento`
   - `ventasPendientes.modal.fechaInicio`
   - `ventasPendientes.modal.precioPagado`
   - `ventasPendientes.modal.confirmar`

### Relacionado con

- Backend: `core-service` endpoints de filtro membresias, `confirmar-pago` extendido, y contador.
- PWA: `gym-member-pwa` feature "Solicitud de Membresía" (Tarea 5 en [`../gym-member-pwa/pendientes-backlog.md`](../gym-member-pwa/pendientes-backlog.md)).
- Cross-cutting: [`../gym-administrator/requirements/solicitudes-membresia.md`](../gym-administrator/requirements/solicitudes-membresia.md).

---

## Checklist de estado

| # | Tarea | Estado | Bloquea |
|---|---|---|---|
| 1 | Solicitudes de membresía en Dashboard | Pendiente — spec definida | Gestión de pagos sin flujo manual |

---

## Dependencias externas necesarias

| Paquete | Para | Estado |
|---|---|---|
| Existentes | N/A | Todo está instalado |

---
