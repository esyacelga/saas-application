# Solicitudes de membresía desde la PWA del cliente

> **ESTADO:** Spec — pendiente de implementación (creada 2026-07-17)
> **Fecha:** 2026-07-17
> **Historia asociada:** `GYM-XXX` — asignar número al crear la carpeta `db/scripts/YYYYMM_GYM-XXX/`.
> **Origen:** el cliente con PWA no tiene forma de comprar membresía desde la app. Hoy solo ve "Sin membresía activa" con botón "Reintentar" — debe ir físicamente al gimnasio. Este feature permite al cliente ver el catálogo de tipos de membresía disponibles, solicitar compra, y al staff completar la venta desde el dashboard con datos precargados.

---

## 1. Contexto de negocio

Hoy:
- Cliente PWA entra a `/membresia` sin membresía activa → ve solo un estado "Sin membresía".
- No hay forma de comprar desde la app — debe hablar con recepción presencialmente.
- Staff puede crear membresía vía admin dashboard, pero sin inicativa del cliente.

Problema: experiencia de cliente pobre; oportunidad de venta perdida fuera de horas de atención física.

Solución: el cliente ve el catálogo de membresías, elige una, envía solicitud. Staff ve contador en dashboard, abre formulario precargado, elige método de pago, confirma. Venta directa del staff (flujo actual) sigue funcionando sin cambios — este feature solo agrega un segundo trigger.

**Scope: coexistencia de dos orígenes de membresía**
- `origen='staff'` — venta directa hoy (comportamiento actual, sin cambios).
- `origen='cliente'` — nueva: solicitud desde PWA. Requiere validación del staff antes de cobro.

---

## 2. Alcance

### IN — dentro de esta HU
- **Base de datos**: nueva columna `origen` en `core.membresias`, índice parcial.
- **Backend core-service**: 
  - Nuevo endpoint `POST /clientes/me/membresias/solicitar` (JWT cliente).
  - Nuevo endpoint `GET /companias/{id}/membresias/pendientes/contador` (JWT staff).
  - Extensión de `POST /membresias/{id}/confirmar-pago` con body opcional para precarga.
  - Campo `origen` en respuestas de membresía.
- **Frontend PWA gym-member-pwa**: página `/membresia` con 3 branches (activa / pendiente / catálogo).
- **Frontend dashboard auth-service-frond-end**: 
  - Sección "Ventas pendientes" con filtro por origen.
  - Widget en dashboard principal con contador de solicitudes pendientes.
  - Modal "Completar venta" con datos precargados.

### OUT — HUs posteriores
- Notificación WhatsApp al staff cuando entra una nueva solicitud (aprovechar `notif_buckets_globales`).
- Notificación push al cliente cuando su solicitud es confirmada.
- Job de limpieza de solicitudes viejas (auto-rechazo a los N días).
- Deep-link desde notificación directo al detalle de la solicitud.

---

## 3. Decisiones aprobadas

### 3.1 Negocio (validadas con `product-owner`)

| # | Pregunta | Decisión |
|---|----------|----------|
| N1 | ¿Hay costo de solicitud? | **No.** Solicitar es gratis. Se cobra cuando el staff confirma el pago. |
| N2 | ¿Se congela el precio? | **No.** Siempre se cobra el precio actual del catálogo en el momento de pago. Si el gym sube el precio entre solicitud y venta, se cobra el nuevo precio (editable por staff). |
| N3 | ¿Solicitud pendiente da acceso? | **NO.** `validar-acceso` rechaza con código `solicitud_membresia_pendiente`. Cliente debe esperar a que staff confirme. |
| N4 | ¿Puede el cliente cancelar? | **NO.** Solo staff puede rechazar/cancelar. Cliente ve su solicitud pendiente pero no tiene botón "cancelar" — debe hablar con staff. |
| N5 | ¿Método de pago elegido por cliente? | **NO.** Solo el staff elige el método al confirmar la venta. Cliente no ve ni elige. |
| N6 | Visibilidad en dashboard | **Contador widget destacado** en página principal de dashboard del staff. Tab separado "Solicitudes" en `/admin/ventas-pendientes?origen=cliente`. |
| N7 | Validación de acceso | **Sin cambios en `validar-acceso`** — solo se agrega un código de razón nuevo. La lógica de rechazo es la misma: si hay solicitud pendiente (sin membresía activa pagada), rechaza acceso. |
| N8 | Unidad de negocio | Staff que recibe solicitudes puede ser: propietario, administrador, recepcionista. Los roles exactos varían por gimnasio (no hay catálogo global de nombres) — asignación manual via "Editar rol". Permiso requerido: `membresias:solicitudes_confirmar` (nuevo). |
| N9 | Simultaneidad | Un cliente NO puede tener más de una solicitud pendiente viva simultáneamente (`estado_pago='PENDIENTE' AND origen='cliente' AND eliminado=false`). Si intenta 2 veces → `409 Conflict` `codigo=solicitud_ya_existe`. |
| N10 | Motivo de rechazo | **Obligatorio, catálogo cerrado** de 5 valores: `CLIENTE_CAMBIO_OPINION`, `ERROR_DE_VENTA`, `DUPLICADA`, `DATOS_INCORRECTOS`, `OTRO`. |
| N11 | Pipeline visible | **Dashboard staff**: contador en widget, filtro por origen en ventas, detalles de cliente precargados. **KPI**: "% solicitudes convertidas a pagadas" (métrica futura HU-X). |

### 3.2 Técnicas (validadas con `architect`)

| # | Punto | Decisión |
|---|-------|----------|
| A1 | Columna origen | **Reusar `core.membresias`** con nueva columna `origen VARCHAR(10) CHECK IN ('cliente','staff')` DEFAULT `'staff'`. NO se crea tabla nueva `solicitudes_membresia`. |
| A2 | Estado + origen | `estado_pago='PENDIENTE' + origen='cliente'` = solicitud del cliente. `estado_pago='PENDIENTE' + origen='staff'` = venta creada por staff sin pagar. Ambas usan el mismo flujo de confirmación/rechazo. |
| A3 | Índice | **Parcial**: `WHERE estado_pago='PENDIENTE' AND origen='cliente' AND eliminado=false`. Acelera el contador del widget. Nombre: `idx_membresias_solicitudes_pendientes`. |
| A4 | Endpoint solicitar | **`POST /clientes/me/membresias/solicitar`** (JWT cliente, no necesita `{id_cliente}` en URL — usa `me`). Request: `{ "id_tipo_membresia": 5 }`. Response: `{ "id": 42, "estado_pago": "PENDIENTE", "origen": "cliente", ...}`. |
| A5 | Contador | **`GET /companias/{id}/membresias/pendientes/contador`** (JWT staff + permiso `membresias:solicitudes_confirmar`). Response: `{ "total": 3, "por_origen": { "cliente": 2, "staff": 1 } }`. |
| A6 | Confirmar pago | **Extender `POST /membresias/{id}/confirmar-pago`** (existente desde HU anterior `estado-pago-membresias`). Body actual: `{}` (noop si ya está PAGADO). Extensión: body puede llevar campos adicionales opcionales `{ "id_metodo_pago": 7, "precio_pagado": 25.00, "fecha_inicio": "2026-07-20", "descuento_aplicado": 0 }` para precarga desde modal del staff. Si van vacíos, staff debe llenarlos manualmente como hoy. |
| A7 | Validar acceso | **Nuevo código de razón**: `solicitud_membresia_pendiente`. No se cambia lógica de `validar-acceso` — solo se añade este código a la respuesta cuando aplique. |
| A8 | Soft-delete | **Reusar `eliminado`** columna existente. Rechazar una solicitud = `eliminado=true`, `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`. Ver HU previa `estado-pago-membresias` para estructura completa. |
| A9 | Sin cache Redis | `core-service` NO tiene cache Redis. Cuando se introduzca en otra HU, esa HU invalidará las claves relevantes. |

---

## 4. Criterios de aceptación

### 4.1 Modelo de datos — una sola migración para ambas HUs

**Advertencia**: esta HU y la previa (`estado-pago-membresias`) comparten la migración de la tabla `core.membresias`. Se debe ejecutar en un **único changeSet** Liquibase que incluya:
- Columnas nuevas: `estado_pago`, `origen`, `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`.
- Drops de NOT NULL en `fecha_inicio`, `fecha_fin`.
- CHECKs de consistencia (`ck_membresias_fechas_por_estado_pago`, `ck_membresias_motivo_si_eliminado`).
- Índices: `idx_membresias_pendientes_por_compania` (HU previa), `idx_membresias_solicitudes_pendientes` (esta HU).
- Seed del permiso `membresias:confirmar_pago` (HU previa) y `membresias:solicitudes_confirmar` (esta HU) en `seguridad.permisos`.

**Columna `origen`**:
```sql
ALTER TABLE core.membresias
  ADD COLUMN origen VARCHAR(10) NOT NULL DEFAULT 'staff'
    CHECK (origen IN ('cliente', 'staff'));
```

**Backfill**: todos los registros existentes quedan con `origen='staff'` (por el DEFAULT).

**Índice parcial para widget**:
```sql
CREATE INDEX idx_membresias_solicitudes_pendientes
  ON core.membresias(id_compania, creacion_fecha DESC)
  WHERE estado_pago = 'PENDIENTE' AND origen = 'cliente' AND eliminado = FALSE;
```

**Permiso nuevo**:
```sql
INSERT INTO seguridad.permisos (id_compania, id_sucursal, nombre, descripcion, modulo)
SELECT
  c.id,
  COALESCE(
    (SELECT s.id FROM tenant.sucursales s
       WHERE s.id_compania = c.id AND s.es_principal = TRUE
       LIMIT 1),
    (SELECT s.id FROM tenant.sucursales s
       WHERE s.id_compania = c.id
       ORDER BY s.id
       LIMIT 1)
  ) AS id_sucursal,
  'membresias:solicitudes_confirmar',
  'Confirmar o rechazar solicitudes de membresía desde la PWA del cliente',
  'core'
FROM tenant.companias c
WHERE NOT EXISTS (
  SELECT 1 FROM seguridad.permisos p
  WHERE p.id_compania = c.id
    AND p.nombre = 'membresias:solicitudes_confirmar'
);
```

### 4.2 Backend core-service

#### 4.2.1 Endpoint solicitar membresía (cliente)

- **`POST /api/v1/clientes/me/membresias/solicitar`**
- **Auth**: Bearer JWT (`tipo: cliente`)
- **Permission**: ninguno (todos los clientes pueden solicitar)
- **Request body**:
  ```json
  {
    "id_tipo_membresia": 5
  }
  ```
- **Validaciones**:
  - `id_tipo_membresia` obligatorio, debe existir y estar activo en la compañía del cliente.
  - Si cliente ya tiene una solicitud pendiente viva (`estado_pago='PENDIENTE' AND origen='cliente' AND eliminado=false`) → `409 Conflict` con `codigo=solicitud_ya_existe`.
  - Si cliente ya tiene una membresía PAGADA activa → permitir (puede tener PAGADA + PENDIENTE simultáneas para renovación anticipada).
- **Response 201**:
  ```json
  {
    "id": 42,
    "id_cliente": 10,
    "id_tipo_membresia": 5,
    "nombre_tipo": "Mensual",
    "precio_catalogo": 25.00,
    "precio_pagado": null,
    "estado_pago": "PENDIENTE",
    "origen": "cliente",
    "estado": "activa",
    "fecha_inicio": null,
    "fecha_fin": null,
    "creacion_fecha": "2026-07-17T10:30:00Z",
    "eliminado": false
  }
  ```
- **Errors**:
  - `400` — tipo no existe, cliente no existe, otros errores de validación.
  - `409` — solicitud ya existe con código `solicitud_ya_existe`.
  - `401` — missing/invalid JWT.

#### 4.2.2 Endpoint contador de solicitudes (staff)

- **`GET /api/v1/companias/{id}/membresias/pendientes/contador`**
- **Auth**: Bearer JWT (`tipo: staff`)
- **Permission**: `membresias:solicitudes_confirmar`
- **URL params**: `id_compania` (forzado al contexto del usuario logueado).
- **Response 200**:
  ```json
  {
    "total": 3,
    "por_origen": {
      "cliente": 2,
      "staff": 1
    }
  }
  ```
- **Errors**:
  - `401` — missing/invalid JWT.
  - `403` — permiso insuficiente.

#### 4.2.3 Listado de solicitudes pendientes (staff)

- **Extender `GET /api/v1/companias/{id}/membresias?estado_pago=PENDIENTE&origen=cliente`** (endpoint existente con nuevos query params).
- **Auth**: Bearer JWT (`tipo: staff`)
- **Permission**: `membresias:solicitudes_confirmar`
- **Response 200**:
  ```json
  [
    {
      "id": 42,
      "id_cliente": 10,
      "nombre_cliente": "Juan Pérez",
      "id_tipo_membresia": 5,
      "nombre_tipo": "Mensual",
      "precio_catalogo": 25.00,
      "precio_pagado": null,
      "estado_pago": "PENDIENTE",
      "origen": "cliente",
      "estado": "activa",
      "fecha_inicio": null,
      "fecha_fin": null,
      "creacion_fecha": "2026-07-17T10:30:00Z",
      "eliminado": false
    }
  ]
  ```
- **Errors**: similar al contador.

#### 4.2.4 Confirmar pago (extensión de endpoint existente)

- **`POST /api/v1/membresias/{id}/confirmar-pago`** (ya existe desde HU previa; se extiende aquí).
- **Auth**: Bearer JWT (`tipo: staff`)
- **Permission**: `membresias:confirmar_pago` (permiso de HU previa, reutilizado; ver nota en N8).
- **Request body** (extensión — campos opcionales):
  ```json
  {
    "id_metodo_pago": 7,
    "precio_pagado": 25.00,
    "fecha_inicio": "2026-07-20",
    "descuento_aplicado": 0.00
  }
  ```
  - Todos los campos son **opcionales** (backward-compatible con calls anteriores que lleven `{}`).
  - Si se omiten, staff debe llenarlos manualmente en un paso anterior (como hoy).
  - Si se incluyen, se precarga el modal sin que staff tenga que escribir.
- **Efecto**:
  - `estado_pago` → `'PAGADO'`.
  - `precio_pagado` = valor recibido o NULL si omitido (staff lo ingresa después).
  - `fecha_inicio` = valor recibido o hoy (si omitido).
  - `fecha_fin` = fecha_inicio + duración tipo membresía.
  - `descuento_aplicado` = valor recibido o 0 (si omitido).
- **Idempotencia**: si ya está `PAGADO`, retornar `200 OK` con el recurso actual sin re-calcular fechas.
- **Response 200**:
  ```json
  {
    "id": 42,
    "estado_pago": "PAGADO",
    "precio_pagado": 25.00,
    "fecha_inicio": "2026-07-20",
    "fecha_fin": "2026-08-20",
    "origen": "cliente"
  }
  ```
- **Errors**:
  - `400` — validación de fechas, método de pago inválido, etc.
  - `409` — si `eliminado = true` (no se puede pagar una rechazada).
  - `401`, `403` — auth/permiso.

### 4.3 validar-acceso — código de razón nuevo

- **Endpoint existente**: `GET /api/v1/membresias/validar-acceso` (usado por attendance-service vía HTTP).
- **Cambio**: cuando la membresía está en `estado_pago='PENDIENTE' AND origen='cliente'`, devolver:
  ```json
  {
    "codigo_acceso": false,
    "razon": "solicitud_membresia_pendiente"
  }
  ```
- **Precedencia**: esta razón debe evaluarse **antes** que `sin_membresia` (si no hay membresía) porque es más específica — una solicitud pendiente es una "ausencia temporal de acceso", no una "sin membresía".
- **i18n**: el texto amigable `"Tu solicitud de membresía está en trámite. Acércate a caja para completar."` vive en el frontend (PWA y kiosko de asistencia), **NO en el backend**.

### 4.4 Modelo de dominio (core-service)

- **`domain/model/Membresia.java`**: 
  - Agregar campo `origen: Origen` (enum con `CLIENTE`, `STAFF`).
  - Hereda ya los campos de HU previa: `estadoPago`, `eliminado`, `fechaEliminacion`, `eliminadoPor`, `motivoEliminacion`.
- **Caso de uso `SolicitarMembresiaUseCase`**: 
  - Recibe `idCliente`, `idTipoMembresia` (desde JWT + request).
  - Valida: tipo existe, activo; cliente no tiene otra solicitud pendiente viva.
  - Crea `Membresia` con `estado_pago=PENDIENTE`, `origen=CLIENTE`, fechas NULL, `eliminado=false`.
  - Retorna DTO respuesta (201).
  - NO publica evento (es un borrador, no una compra confirmada).
- **Caso de uso `ContarSolicitudesPendientesUseCase`** (nuevo):
  - Recibe `idCompania` (contexto del staff).
  - Consulta repo por `estado_pago=PENDIENTE` y agrupa por `origen`.
  - Retorna objeto `{ total, porOrigen }`.

### 4.5 Persistencia (core-service)

- **`MembresiaEntity.java`**: mapear `origen` como `@Column`.
- **`MembresiaR2dbcRepository.java`**:
  - Añadir método `findPendientesByCompaniaAndOrigen(idCompania, origen)` para listar.
  - Añadir método `countPendientesByCompania(idCompania)` con groupBy `origen`.
  - Extender método `findPendienteVivaPorCliente(idCliente)` → nuevo método que busca `estado_pago='PENDIENTE' AND origen='cliente' AND eliminado=false AND idCliente=?`.
- **DTOs**:
  - `MembresiaResponse` extendido con campo `origen`.
  - `ContadorSolicitudesResponse` con estructura `{ total, porOrigen: { cliente, staff } }`.
  - `ConfirmarPagoRequest` extendido con campos opcionales `idMetodoPago`, `precioPagado`, `fechaInicio`, `descuentoAplicado`.

### 4.6 Web (core-service)

- **`infrastructure/adapter/in/web/MembresiaController.java`**:
  - Nuevo endpoint `POST /clientes/me/membresias/solicitar` que mapea a `SolicitarMembresiaUseCase`.
  - Nuevo endpoint `GET /companias/{id}/membresias/pendientes/contador` que mapea a `ContarSolicitudesPendientesUseCase`.
  - Nuevo query param `origen` en el GET lista existente (como filtro adicional opcional).
  - Extender body de `POST /membresias/{id}/confirmar-pago` (backward-compatible).
  - Actualizar `GET /membresias/validar-acceso` para devolver código `solicitud_membresia_pendiente`.
- **Auth decorators**: verificar que `POST /solicitar` solo acepta `tipo:cliente` y los otros dos aceptan `tipo:staff` con permiso `membresias:solicitudes_confirmar`.

---

## 5. Frontend PWA (gym-member-pwa)

**Documentación completa**: `docs/gym-member-pwa/spec-solicitud-membresia.md`

### 5.1 Comportamiento de página `/membresia`

Tres ramas excluyentes:

1. **Cliente con membresía PAGADA activa** (estado actual):
   - Muestra detalles: tipo, precio, vigencia, botón "Renovar" (si próximo a vencer).
   
2. **Cliente con solicitud pendiente viva** (`estado_pago=PENDIENTE + origen=cliente + eliminado=false`):
   - Componente `<SolicitudPendienteCard/>`:
     - Texto: *"Tu solicitud de Mensual está en trámite"*.
     - Fecha de creación: *"hace 2 días"* (relative).
     - Botón: *"Ver detalles"* (abre modal con ficha de la solicitud).
     - Texto instrucciones: *"El staff confirmará tu compra en la próxima visita. No hay acceso hasta confirmar."*
   
3. **Cliente sin membresía activa ni solicitud pendiente**:
   - Componente `<CatalogoMembresias/>`:
     - Lista de tipos activos del gym.
     - Por cada tipo: nombre, precio, duración, botón "Solicitar".
     - Click "Solicitar" → loading → `POST /clientes/me/membresias/solicitar`.
     - Éxito → toast *"Solicitud enviada"* → refresca UI a rama 2.
     - Error 409 `solicitud_ya_existe` → toast error (no debería ocurrir, race condition).
     - Error 400 → toast genérico *"Error al enviar solicitud"*.

### 5.2 Componentes nuevos

- **`<CatalogoMembresias/>`**: lista de tipos con cards, busca datos de `GET /api/v1/tipos-membresia`.
- **`<SolicitudPendienteCard/>`**: muestra estado de solicitud, fecha creación, botón detalles.
- **Modal detalle de solicitud** (optional): muestra id, tipo, precio, estado, feedback de staff (si aplica).

### 5.3 Internacionalización

**Nuevas claves en `es.json` y `en.json`**:
- `pages.membresia.solicitud.titulo` = "Solicitud de membresía"
- `pages.membresia.solicitud.pendiente` = "Tu solicitud de {tipo} está en trámite"
- `pages.membresia.solicitud.hace_x_dias` = "hace {dias} día(s)"
- `pages.membresia.solicitud.instrucciones` = "El staff confirmará tu compra en la próxima visita. No hay acceso hasta confirmar."
- `pages.membresia.catalogo.titulo` = "Elige tu membresía"
- `pages.membresia.catalogo.btn_solicitar` = "Solicitar"
- `pages.membresia.catalogo.btn_renovar` = "Renovar"
- `errors.solicitud_ya_existe` = "Ya tienes una solicitud pendiente"
- `success.solicitud_enviada` = "Solicitud enviada correctamente"

### 5.4 Estados de carga y errores

- Indicador spinner mientras se envía `POST /solicitar`.
- Toast error si respuesta != 201.
- Retry automático? **No** (YAGNI — user puede recargar página).

---

## 6. Frontend Dashboard (auth-service-frond-end)

**Documentación completa**: `docs/auth-service-frond-end/spec-solicitudes-membresia.md`

### 6.1 Widget en DashboardPage

- **Ubicación**: sección principal (abajo del contador de "socios activos" o en su propia fila).
- **Badge/Contador**: muestra número total de membresías pendientes.
  - Ej: *"3 membresías por confirmar"* con badge rojo si > 0.
- **Click**: navega a `/admin/ventas-pendientes?origen=cliente`.
- **Query** backend: `GET /companias/{id}/membresias/pendientes/contador`.

### 6.2 Página `/admin/ventas-pendientes` (nueva sección)

Refactor de la página existente de "Ventas pendientes" (si existe) para agregar filtro por origen.

- **Tabs**: dos tabs excluyentes:
  - Tab "Solicitudes cliente" (origen=cliente) — activo por defecto.
  - Tab "Ventas staff" (origen=staff) — muestra ventas creadas por staff sin confirmar pago.
- **Columnas de lista**:
  - Cliente (nombre completo).
  - Tipo de membresía.
  - Precio (editable en modal, pero se muestra aquí).
  - Origen (badge: "cliente" / "staff").
  - Fecha de creación (relativa, ej: "hace 2 días").
  - Acciones: botón "Completar venta", botón "Rechazar" (dropdown o modal).

### 6.3 Modal "Completar venta"

Precargación de datos desde la solicitud del cliente.

- **Trigger**: botón "Completar venta" en fila de solicitud.
- **Campos**:
  - Nombre cliente (read-only, extraído de solicitud).
  - Tipo membresía (read-only).
  - **Precio** (editable, precargado = precio actual del tipo de membresía del gym; staff puede ajustar si aplica descuento manual).
  - **Método de pago** (dropdown, obligatorio; opciones: Efectivo, Transferencia, Tarjeta, etc. — según el gym).
  - **Fecha de inicio** (date picker, precargado = hoy).
  - **Descuento aplicado** (número, optional, default 0).
- **Acciones del modal**:
  - Botón "Confirmar" → `POST /api/v1/membresias/{id}/confirmar-pago` con body:
    ```json
    {
      "id_metodo_pago": 7,
      "precio_pagado": 25.00,
      "fecha_inicio": "2026-07-20",
      "descuento_aplicado": 0
    }
    ```
  - Botón "Cancelar" → cierra sin hacer nada.
- **Feedback post-envío**:
  - Éxito (201) → toast *"Membresía confirmada"* → modal cierra → tabla se refresca (elimina la fila).
  - Error 409 (rechazada) → toast *"Esta membresía ya fue rechazada"*.
  - Error 400 → toast genérico *"Error al confirmar"*.

### 6.4 Acción "Rechazar" (por fila)

- **Trigger**: botón "Rechazar" en fila (o dropdown con opción).
- **Dialog de confirmación**:
  - Título: *"Rechazar solicitud"*.
  - Pregunta: *"¿Rechazas esta solicitud? Elige el motivo."*
  - Select (obligatorio) con 5 opciones:
    - `CLIENTE_CAMBIO_OPINION` = "El cliente cambió de opinión"
    - `ERROR_DE_VENTA` = "Error al crear la solicitud"
    - `DUPLICADA` = "Solicitud duplicada"
    - `DATOS_INCORRECTOS` = "Datos incorrectos del cliente"
    - `OTRO` = "Otro motivo"
  - Botón "Rechazar" → `POST /api/v1/membresias/{id}/rechazar` con body:
    ```json
    { "motivo_eliminacion": "CLIENTE_CAMBIO_OPINION" }
    ```
  - Botón "Cancelar" → cierra dialog.
- **Feedback**:
  - Éxito → toast *"Solicitud rechazada"* → tabla se refresca.
  - Error → toast error.

### 6.5 Filtro y search

- **Search**: input de búsqueda por nombre del cliente.
- **Opcional**: date picker para filtrar por rango de creación.

### 6.6 Internacionalización

**Nuevas claves en `es.json` y `en.json`**:
- `pages.ventas_pendientes.titulo` = "Ventas pendientes"
- `pages.ventas_pendientes.tab_cliente` = "Solicitudes de cliente"
- `pages.ventas_pendientes.tab_staff` = "Ventas de staff"
- `pages.ventas_pendientes.columnas.cliente` = "Cliente"
- `pages.ventas_pendientes.columnas.tipo` = "Tipo de membresía"
- `pages.ventas_pendientes.columnas.precio` = "Precio"
- `pages.ventas_pendientes.columnas.origen` = "Origen"
- `pages.ventas_pendientes.columnas.creacion` = "Creado"
- `pages.ventas_pendientes.btn_confirmar` = "Completar venta"
- `pages.ventas_pendientes.btn_rechazar` = "Rechazar"
- `dialogs.rechazar_solicitud.titulo` = "Rechazar solicitud"
- `dialogs.rechazar_solicitud.pregunta` = "¿Rechazas esta solicitud? Elige el motivo."
- `dialogs.rechazar_solicitud.motivos.cliente_cambio_opinion` = "El cliente cambió de opinión"
- `dialogs.rechazar_solicitud.motivos.error_de_venta` = "Error al crear la solicitud"
- `dialogs.rechazar_solicitud.motivos.duplicada` = "Solicitud duplicada"
- `dialogs.rechazar_solicitud.motivos.datos_incorrectos` = "Datos incorrectos del cliente"
- `dialogs.rechazar_solicitud.motivos.otro` = "Otro motivo"
- `modals.completar_venta.titulo` = "Confirmar venta"
- `modals.completar_venta.campo_cliente` = "Cliente"
- `modals.completar_venta.campo_tipo` = "Tipo de membresía"
- `modals.completar_venta.campo_precio` = "Precio"
- `modals.completar_venta.campo_metodo_pago` = "Método de pago"
- `modals.completar_venta.campo_fecha_inicio` = "Fecha de inicio"
- `modals.completar_venta.campo_descuento` = "Descuento aplicado"
- `modals.completar_venta.btn_confirmar` = "Confirmar"
- `modals.completar_venta.btn_cancelar` = "Cancelar"
- `success.membresia_confirmada` = "Membresía confirmada correctamente"
- `error.membresia_rechazada` = "Esta membresía ya fue rechazada"

### 6.7 Permisos

Guard: `usePermission('membresias:solicitudes_confirmar')`. Solo staff con este permiso ve la página y los botones de acción.

---

## 7. Integración Attendance-service

**Sin cambios de código en esta HU.**

- Cuando `validar-acceso` de core-service devuelve código `solicitud_membresia_pendiente`, attendance-service lo propaga como `ForbiddenException` con ese código.
- El kiosko de asistencia y PWA reciben el código y muestran el texto i18n correspondiente.
- Tests de integración (opcional): verificar que ambos códigos nuevos (este + `membresia_rechazada` de HU previa) se propagan.

---

## 8. Flujo end-to-end

```
Cliente:
1. PWA: usuario abre /membresia sin membresía activa
2. Ve catalogo con "Mensual $25" y botón "Solicitar"
3. Click → POST /clientes/me/membresias/solicitar { id_tipo_membresia: 5 }
4. Backend: crea membresia id=42, estado_pago=PENDIENTE, origen=cliente, precio_pagado=null
5. PWA: muestra "Solicitud enviada — el staff la confirmará pronto"
6. PWA muestra <SolicitudPendienteCard/> con fecha creación

Staff en dashboard:
7. Recarga dashboard → ve widget "3 membresías por confirmar" con badge rojo
8. Click en widget → navega a /admin/ventas-pendientes?origen=cliente
9. Ve tab "Solicitudes cliente" activo, lista con fila: "Juan Pérez | Mensual | $25.00 | cliente | hace 2 min"
10. Click "Completar venta" en fila → modal precargado:
    - Cliente: "Juan Pérez" (read-only)
    - Tipo: "Mensual" (read-only)
    - Precio: 25.00 (editable, si gym cambió precio o aplica descuento)
    - Método: (empty, staff elige de dropdown)
    - Fecha inicio: hoy (pre-filled, editable)
    - Descuento: 0 (pre-filled)
11. Staff elige "Efectivo" en método, deja resto por defecto, click "Confirmar"
12. POST /membresias/42/confirmar-pago { id_metodo_pago: 7, precio_pagado: 25.00, fecha_inicio: "2026-07-20", descuento_aplicado: 0 }
13. Backend: membresia id=42 → estado_pago=PAGADO, fecha_inicio=hoy, fecha_fin=hoy+30d, precio_pagado=25.00
14. Toast "Membresía confirmada" → modal cierra → tabla refresca (fila desaparece)
15. Dashboard widget se actualiza: "2 membresías por confirmar"

Cliente intenta acceso:
16. Cliente entra al gym → kiosko QR intenta validar acceso
17. HTTP a attendance-service → `CoreServiceClient.validarAcceso`
18. core-service: GET /membresias/validar-acceso → encuentra membresia id=42, estado_pago=PAGADO ✓
19. Devuelve codigo_acceso=true
20. Kiosko: ACCESO CONCEDIDO

(Si staff hubiera rechazado en paso 10:)
10b. Click "Rechazar" → dialog motivo
11b. Staff elige "CLIENTE_CAMBIO_OPINION", click "Rechazar"
12b. POST /membresias/42/rechazar { motivo_eliminacion: "CLIENTE_CAMBIO_OPINION" }
13b. Backend: membresia → eliminado=true, fecha_eliminacion=now(), motivo=CLIENTE_CAMBIO_OPINION
14b. Toast "Solicitud rechazada" → fila desaparece
15b. Widget se actualiza: "2 membresías por confirmar"
16b. Cliente en PWA ve <CatalogoMembresias/> de nuevo (sin solicitud pendiente)
```

---

## 9. Riesgos y edge cases

- **Solicitud duplicada**: cliente rápidamente hace click 2 veces en "Solicitar" → primera POST =201, segunda POST =409 `solicitud_ya_existe`. Frontend debe deshabilitar botón mientras se envía.
- **Tipo desactivado**: gym desactiva un tipo de membresía entre solicitud y venta → la fila permanece en pendientes, staff puede confirmar o rechazarla (tipo aún es consultable por `id`).
- **Precio cambió**: gym sube precio entre solicitud y venta → modal muestra el precio actual (editable por staff). Decisión: **siempre cobrar precio actual, no congelado**. Si staff quiere aplicar descuento, suma en el campo "Descuento aplicado".
- **Cliente sin membresía rechazada intenta acceso**: intenta entrar al gym con solicitud rechazada → `validar-acceso` devuelve `codigo=membresia_rechazada` (de HU previa) → acceso denegado. Cliente no ve la solicitud rechazada en la PWA (solo solicitudes PENDIENTES vivas).
- **Membresía simultáneas**: cliente tiene PAGADA activa + intenta solicitar otra → permitido (renovación anticipada). Si intenta solicitar 2 pendientes → `409 solicitud_ya_existe`.
- **Sincronización widget**: widget en dashboard se actualiza manualmente (user recarga página) o vía polling (si se añade después). **Sin websockets en MVP** — refresco manual.
- **Solicitudes huérfanas**: si staff no rechaza y cliente nunca paga, la solicitud permanece PENDIENTE indefinidamente. **Futuro**: auto-rechazo a los N días (job de limpieza, HU-Y).

---

## 10. Dependencias técnicas entre HUs

**Este feature depende de la HU previa `estado-pago-membresias`**:
- Usa columnas: `estado_pago`, `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`.
- Usa endpoint: `POST /membresias/{id}/confirmar-pago` (aquí se extiende, no se crea).
- Usa permiso: `membresias:confirmar_pago` (aquí se reutiliza; se crea el nuevo `membresias:solicitudes_confirmar`).
- Usa código de razón: `validar-acceso` ya ha sido actualizado para devolver códigos nuevos.

**Orden de implementación**:
1. Crear/aplicar migrations de ambas HUs en un **único changeSet** (`db/scripts/YYYYMM_GYM-003/`).
2. Implementar backend de HU-A (`estado-pago-membresias`): modelos, casos de uso, endpoints de confirmar/rechazar.
3. Implementar backend de HU-B (esta): solicitar, contador, extensión de confirmar, nuevo código.
4. Implementar frontend dashboard (HU-A y HU-B en paralelo): sección "Ventas pendientes", modal confirmación, dialog rechazo.
5. Implementar frontend PWA (esta HU): página `/membresia` con 3 branches, componentes catálogo/solicitud.

---

## 11. Documentación a crear/actualizar post-implementación

### Documentos a crear
- **`docs/core-service/spec/solicitudes-membresia.md`** — diseño técnico detallado de modelo, queries, casos de uso, eventos (si aplica).
- **`docs/gym-member-pwa/spec-solicitud-membresia.md`** — especificación de frontend: componentes, rutas, i18n, flujo UX.
- **`docs/auth-service-frond-end/spec-solicitudes-membresia.md`** — especificación de dashboard: página, modal, dialog, permisos, i18n.

### Documentos a actualizar
- **`docs/core-service/api/membresias.md`** — agregar endpoints nuevos, documentar campo `origen` en todas las respuestas.
- **`docs/core-service/api/tipos-membresia.md`** — mencionar que el listado de tipos lo puede consumir la PWA del cliente (público o requiere JWT cliente).
- **`docs/gym-administrator/requirements/estado-pago-membresias.md`** — si existe, aclarar que esta HU (solicitudes) es el segundo trigger de creación de membresías PENDIENTES (además de staff).
- **`docs/gym-administrator/architecture/database-schema.md`** — documentar `origen`, `estado_pago`, y relaciones de soft-delete.
- **`docs/gym-administrator/INDEX.md`** — si existe, enlazar a este spec y a los sub-docs de cada servicio.

### Documentos auxiliares (ya existentes, verificar)
- **`docs/gym-administrator/architecture/scheduled-jobs.md`** — verificar que job de vencimiento de membresías ignora PENDIENTES.
- **`docs/gym-administrator/ui-specs/ventas-pendientes.md`** — si existe, actualizar con nuevo tab de origen y modal precargada.

---

## 12. Pendiente para futuras iteraciones

- **HU-W — Notificación WhatsApp al staff** (opcional): cuando entra una nueva solicitud, enviar notificación a staff con permiso `membresias:solicitudes_confirmar`, link directo a `/admin/ventas-pendientes?origen=cliente&id_solicitud=42`.
- **HU-X — Notificación push al cliente** (opcional): cuando staff confirma la solicitud, enviar push in-app o notificación en PWA.
- **HU-Y — Job de limpieza de solicitudes viejas** (opcional): auto-rechazo a las N días si no se confirmaron (ej: 7 días).
- **HU-Z — Deep-link desde notificación** (opcional): cuando staff recibe notificación (HU-W), click abre `/admin/ventas-pendientes?origen=cliente&highlight=id_solicitud`.
- **HU-AA — Integración con billing-service** (deuda técnica): cuando se cree `core.pagos`, esta HU puede publicar evento `SolicitudMembresiaConfirmadaEvent` con detalles de facturación.

---

## 13. References

- **HU previa** (misma migración de BD): [`docs/gym-administrator/requirements/estado-pago-membresias.md`](./estado-pago-membresias.md)
- **API core-service**: [`docs/core-service/api/membresias.md`](../../core-service/api/membresias.md) (a actualizar)
- **Estructura DB**: [`docs/gym-administrator/architecture/database-schema.md`](../architecture/database-schema.md)
- **Permisos**: [`docs/gym-administrator/architecture/database-schema.md#seguridad`](../architecture/database-schema.md#seguridad)
- **Convenciones de migración**: [`gym-administrator/CLAUDE.md`](../../../gym-administrator/CLAUDE.md)
- **PWA estructura**: [`docs/gym-member-pwa/CLAUDE.md`](../../gym-member-pwa/CLAUDE.md)
- **Dashboard estructura**: [`docs/auth-service-frond-end/CLAUDE.md`](../../auth-service-frond-end/CLAUDE.md)

---

## 14. Contacto y preguntas abiertas

- **¿Quién define catálogo de métodos de pago?** Ver `gym-administrator/requirements/` si existe spec de pasarelas.
- **¿Deep-link desde notificación?** Pendiente para HU-W (notificaciones).
- **¿Auto-rechazo de solicitudes viejas?** Pendiente para HU-Y (job).
