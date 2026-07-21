# Membresías API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `MembresiaController`).

Gestión del ciclo de vida de membresías: venta, validación de acceso, congelamiento, anulación y actualización de asistencias previas.

Base path: `/api/v1`  
Service: core-service (port 8083)

---

## Endpoints

### GET /api/v1/clientes/{id}/membresias
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Historial completo de membresías del cliente, enriquecido con nombre y modo del tipo, monto pagado / saldo pendiente y (para tipos de accesos) accesos usados/restantes. Filtrado por `id_compania` del JWT. **Incluye membresías con `eliminado = true`** (rechazadas) — la UI las muestra con badge y motivo. Ordenado por `creacion_fecha DESC`.

**Path param:**
- `id` — ID del cliente en `core.clientes`

**Response 200:**
```json
[
  {
    "id": 1,
    "id_cliente": 10,
    "id_tipo_membresia": 3,
    "tipo_nombre": "Plan Mensual",
    "modo_control": "calendario",
    "fecha_inicio": "2026-01-15",
    "fecha_fin": "2026-04-15",
    "dias_acceso_total": null,
    "dias_acceso_usados": null,
    "dias_acceso_restantes": null,
    "precio_pagado": "150.00",
    "descuento_aplicado": "10.00",
    "monto_pagado": "150.00",
    "saldo_pendiente": "0.00",
    "estado": "activa",
    "estado_pago": "PAGADO",
    "origen": "staff",
    "eliminado": false,
    "motivo_eliminacion": null
  }
]
```

**Response fields:**
- `id` — Membership ID
- `id_cliente` — Client ID
- `id_tipo_membresia` — Membership type ID
- `tipo_nombre` — Nombre del plan (join a `core.tipos_membresia.nombre`)
- `modo_control` — `calendario` o `accesos` (del tipo)
- `fecha_inicio` — Start date (null cuando `estado_pago = PENDIENTE`)
- `fecha_fin` — End date (null cuando `estado_pago = PENDIENTE`)
- `dias_acceso_total` — Total de accesos para tipos `accesos`; null para `calendario`
- `dias_acceso_usados` — Accesos consumidos (count sobre `asistencia.asistencias`); null cuando `modo_control = calendario`
- `dias_acceso_restantes` — `dias_acceso_total - dias_acceso_usados`; null cuando `modo_control = calendario`
- `precio_pagado` — Precio final después de descuento
- `descuento_aplicado` — Descuento aplicado
- `monto_pagado` — Monto efectivamente cobrado (derivado: igual a `precio_pagado` si `estado_pago = PAGADO`, `0` si `PENDIENTE`). Fuente de verdad: `estado_pago` (mientras no exista `core.pagos` — HU-C).
- `saldo_pendiente` — Saldo por cobrar (`precio_pagado - monto_pagado`)
- `estado` — `activa`, `vencida`, `anulada`, `congelada`
- `estado_pago` — `PAGADO` | `PENDIENTE`
- `origen` — `cliente` (solicitud autoservicio PWA) | `staff` (venta directa mostrador). Default histórico `staff` para migración retroactiva.
- `eliminado` — `true` cuando la membresía fue rechazada (soft-delete)
- `motivo_eliminacion` — Cuando `eliminado = true`: `SOCIO_CAMBIO_OPINION` | `ERROR_DE_VENTA` | `DUPLICADA` | `DATOS_INCORRECTOS` | `OTRO`

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions or wrong company
- `404` — client not found

---

### GET /api/v1/clientes/me/membresias
**Auth:** Bearer JWT (`tipo: cliente`)  
**Permission:** `requireCliente()` (resuelve el `id_cliente` desde `id_persona` + `id_compania` del JWT)  
**Description:** Alias del endpoint anterior pensado para la **PWA de socios** (GYM-003). Resuelve automáticamente el `id_cliente` del usuario autenticado consultando `core.clientes` por `id_persona` + `id_compania`. El shape del response es idéntico a `GET /clientes/{id}/membresias`. No hay path param — el ID del cliente se toma del token.

**Response 200:** Idéntico al endpoint `GET /clientes/{id}/membresias` (mismo array de objetos enriquecidos, incluye membresías `eliminado = true`).

**Errors:**
- `401` — missing or invalid JWT
- `403` — el JWT no es de tipo `cliente` o pertenece a otra compañía
- `404` — la persona del JWT no está registrada como cliente en la compañía

---

### POST /api/v1/clientes/{id}/membresias
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()`  
**Description:** Vender (crear) una membresía a un cliente. Calcula `fecha_fin` según el tipo. Soporta ventas cobradas al momento (`estado_pago = PAGADO`) y ventas pendientes de pago (`estado_pago = PENDIENTE`, reservado para el flujo de compra desde la PWA — HU-B).

**Path param:**
- `id` — ID del cliente

**Request body:**
```json
{
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "id_metodo_pago": 1,
  "descuento_aplicado": "10.00",
  "estado_pago": "PAGADO"
}
```

**Body fields:**
- `id_tipo_membresia` (required, integer) — Membership type ID from `tipos-membresia`
- `fecha_inicio` (required, date `YYYY-MM-DD`) — Start date (ignorado cuando `estado_pago = PENDIENTE`)
- `id_metodo_pago` (optional, integer) — Payment method ID
- `descuento_aplicado` (optional, decimal >= 0) — Discount amount
- `estado_pago` (optional, enum: `PAGADO` \| `PENDIENTE`, default `PAGADO`) — Estado de cobro de la venta. Con `PENDIENTE`, `fecha_inicio`/`fecha_fin` se persisten como `null` (respetando el CHECK `ck_membresias_fechas_por_estado_pago`) y no se dispara efecto sobre el estado del cliente ni evento hacia billing.

**Validaciones de negocio:**
- Si `estado_pago = PAGADO` y el cliente ya tiene otra `PAGADA` activa → `409 Conflict`.
- Si `estado_pago = PENDIENTE` y el cliente ya tiene otra `PENDIENTE` viva (`estado_pago = PENDIENTE` y `eliminado = false`) → `409 Conflict`. Se permite coexistencia `PAGADA activa + PENDIENTE nueva` (renovación anticipada).

**Response 201:**
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "fecha_fin": "2026-04-15",
  "dias_acceso_total": 90,
  "precio_pagado": "150.00",
  "descuento_aplicado": "10.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "origen": "staff",
  "eliminado": false,
  "motivo_eliminacion": null
}
```

**Errors:**
- `400` — invalid request (missing required fields, invalid dates, valor de `estado_pago` fuera del catálogo)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — client or membership type not found
- `409` — business rule conflict (`El cliente ya tiene una membresía activa` o `El cliente ya tiene una membresía pendiente de pago`)

> Revisar contra código: `MembresiaController#vender` + `MembresiaService#vender` (§4.4 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

Nota: toda venta creada por este endpoint queda con `origen='staff'`. Para solicitudes autoservicio desde la PWA usar `POST /clientes/me/membresias/solicitar` (más abajo).

---

### POST /api/v1/clientes/me/membresias/solicitar
**Auth:** Bearer JWT (`tipo: cliente`)
**Permission:** `requireCliente()` (resuelve `id_cliente` desde `id_persona` + `id_compania` del JWT).
**Description:** Cliente PWA envía una solicitud de membresía autoservicio. Crea la fila como `PENDIENTE` con `origen='cliente'` y placeholders (precio 0, sin método de pago, fechas NULL). El staff completa la venta luego vía `POST /membresias/{id}/confirmar-pago`. El cliente NO puede cancelar su propia solicitud (decisión PO #4); solo el staff vía `POST /membresias/{id}/rechazar`.

**Request body:**
```json
{
  "id_tipo_membresia": 3
}
```

**Body fields:**
- `id_tipo_membresia` (required, integer) — Debe estar `activo=true` y pertenecer a la `id_compania` del JWT.

**Response 201:**
```json
{
  "id": 101,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "tipo_nombre": null,
  "modo_control": null,
  "fecha_inicio": null,
  "fecha_fin": null,
  "dias_acceso_total": null,
  "dias_acceso_usados": null,
  "dias_acceso_restantes": null,
  "precio_pagado": "0.00",
  "descuento_aplicado": "0.00",
  "monto_pagado": "0.00",
  "saldo_pendiente": "0.00",
  "estado": "activa",
  "estado_pago": "PENDIENTE",
  "origen": "cliente",
  "eliminado": false,
  "motivo_eliminacion": null
}
```

**Reglas de negocio:**
1. Si el cliente ya tiene una membresía viva con `estado_pago='PENDIENTE' AND eliminado=false` (sin importar el `origen`) → `409` con `codigo=solicitud_ya_existe` y mensaje "Ya tienes una compra en trámite. Espera a que el staff la confirme o cancele antes de solicitar una nueva." El chequeo se hace en el service (check-then-act) y **además** existe el índice `UNIQUE uq_membresias_pendiente_por_cliente_vivo` que cierra la race condition — el `DataIntegrityMapper` traduce la violación al mismo `codigo` si la carrera lo evade.
2. Si el cliente tiene una membresía PAGADA activa aún vigente (`fecha_fin >= hoy`) → `409` con `codigo=membresia_activa_vigente`.
3. Si el `id_tipo_membresia` no existe, está inactivo o pertenece a otra compañía → `404` con `codigo=tipo_membresia_no_disponible`.
4. La fila se inserta con: `origen='cliente'`, `estado_pago='PENDIENTE'`, `estado='activa'`, `fecha_inicio=NULL`, `fecha_fin=NULL` (CHECK `ck_membresias_fechas_por_estado_pago`), `precio_pagado=0` (placeholder — la columna es NOT NULL sin default; se sobrescribe al confirmar), `descuento_aplicado=0`, `id_metodo_pago=NULL`, `eliminado=false`, `id_sucursal=cliente.id_sucursal` (heredada del registro del cliente).
5. `precio_pagado=0` en la solicitud NO se congela — el staff ingresa el precio real al confirmar (decisión PO #1). `dias_acceso_total` tampoco se congela; se rellena al confirmar desde el catálogo actual (decisión B1 — riesgo mitigado por widget de Home en el PWA).

**Errors:**
- `400` — `id_tipo_membresia` ausente o body inválido (`codigo=validacion`)
- `401` — missing or invalid JWT (`codigo=no_autenticado`)
- `403` — el JWT no es de tipo `cliente` o pertenece a otra compañía (`codigo=acceso_denegado`)
- `404` — la persona del JWT no está registrada como cliente en la compañía (`codigo=recurso_no_encontrado`)
- `404` — tipo de membresía no encontrado / inactivo / de otra compañía (`codigo=tipo_membresia_no_disponible`)
- `409` — solicitud viva ya existe (`codigo=solicitud_ya_existe`)
- `409` — membresía PAGADA activa vigente (`codigo=membresia_activa_vigente`)

> Revisar contra código: `MembresiaController#solicitarMembresia` + `MembresiaService#solicitarMembresia`.

---

### GET /api/v1/membresias/{id}
**Auth:** Bearer JWT (`tipo: staff | cliente`)  
**Permission:** `requireGymStaff()` OR `requireCliente()`  
**Description:** Detalle de una membresía con información computada (días usados, restantes, etc.).

**Path param:**
- `id` — Membership ID

**Response 200:**
```json
{
  "id": 1,
  "tipo": "Plan Mensual",
  "modo_control": "calendario",
  "fecha_inicio": "2026-01-15",
  "fecha_fin": "2026-04-15",
  "dias_acceso_total": 90,
  "dias_acceso_usados": 15,
  "dias_acceso_restantes": 75,
  "asistencias_previas": 0,
  "precio_pagado": "150.00",
  "estado": "ACTIVA"
}
```

**Response fields:**
- `id` — Membership ID
- `tipo` — Membership type name
- `modo_control` — `calendario` (calendar-based) or `accesos` (access-based)
- `fecha_inicio` — Start date
- `fecha_fin` — End date
- `dias_acceso_total` — Total days (calendar mode) or total accesses (access mode)
- `dias_acceso_usados` — Used days (from `asistencia.asistencias`)
- `dias_acceso_restantes` — Remaining days
- `asistencias_previas` — Prepaid visits before gym access tracking started
- `precio_pagado` — Amount paid
- `estado` — `ACTIVA`, `VENCIDA`, `ANULADA`, `CONGELADA`

**Errors:**
- `401` — missing or invalid JWT
- `403` — insufficient permissions or wrong company
- `404` — membership not found

---

### PATCH /api/v1/membresias/{id}/asistencias-previas
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()`  
**Description:** Actualizar el contador de asistencias previas (before migrating to attendance tracking system). Ajusta el recount de visitas pendientes.

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "cantidad": 5
}
```

**Body fields:**
- `cantidad` (required, integer >= 0) — New value for `asistencias_previas`

**Response 200:**
```json
{
  "id": 1,
  "asistencias_previas": 5
}
```

**Errors:**
- `400` — invalid quantity (negative, non-integer)
- `401` — missing or invalid JWT
- `403` — insufficient permissions
- `404` — membership not found

---

### PUT /api/v1/membresias/{id}/anular
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireAdminOrDueno()`  
**Description:** Anular una membresía. Requiere motivo. Cambiar estado a `ANULADA`.

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "motivo": "Solicitud del cliente"
}
```

**Body fields:**
- `motivo` (required, string) — Reason for cancellation

**Response 200:** (no body)

**Errors:**
- `400` — missing motivo
- `401` — missing or invalid JWT
- `403` — insufficient permissions (admin/owner only)
- `404` — membership not found

---

### GET /api/v1/membresias/validar-acceso
**Auth:** None (PUBLIC endpoint)  
**Description:** Validar si una persona puede acceder al gym. Usada por sistemas de acceso (turnstiles, QR readers) y consumida vía HTTP por `attendance-service` (`CoreServiceClient.validarAcceso`), que propaga el `razon` como `ForbiddenException` sin traducirlo. El texto amigable vive en el i18n de la PWA/kiosko.

**Query parameters:**
- `id_persona` (required, integer) — Person ID from `identidad.personas`
- `id_compania` (required, integer) — Company ID

**Response 200 (Acceso permitido):**
```json
{
  "permitido": true,
  "id_cliente": 10,
  "id_membresia": 1,
  "modo_control": "calendario",
  "tipo_membresia": "Plan Mensual",
  "dias_acceso_restantes": 75,
  "fecha_fin": "2026-04-15",
  "accesos_usados": 5
}
```

**Response 403 (Acceso denegado):**
```json
{
  "permitido": false,
  "razon": "sin_membresia",
  "tipo_membresia": "Plan Mensual",
  "ultima_membresia_fin": "2026-04-15",
  "accesos_usados": 5,
  "accesos_total": 90
}
```

**Response fields (acceso permitido):**
- `permitido` — Always `true` for 200
- `id_cliente` — Client ID
- `id_membresia` — Active membership ID
- `modo_control` — `calendario` or `accesos`
- `tipo_membresia` — Membership type name
- `dias_acceso_restantes` — Remaining visits/days
- `fecha_fin` — Membership expiry date
- `accesos_usados` — (Optional) Used accesses if access-based

**Response fields (acceso denegado):**
- `permitido` — Always `false` for 403
- `razon` — Código corto (snake_case) explicando el bloqueo. Ver catálogo abajo.
- `tipo_membresia` — (Optional) Last membership type
- `ultima_membresia_fin` — (Optional) Last membership end date
- `accesos_usados` — (Optional) If the last membership was access-based
- `accesos_total` — (Optional) Total accesses of last membership

**Catálogo de `razon` (códigos cortos, snake_case):**

| Código | Situación | Origen |
|--------|-----------|--------|
| `pago_pendiente` | Existe una membresía con `estado_pago = PENDIENTE` viva | §4.5 GYM-003 |
| `membresia_rechazada` | La membresía fue soft-deleted (`eliminado = true`) | §4.5 GYM-003 |
| `sin_membresia` | No existe membresía activa ni cliente registrado | histórico |
| `membresia_congelada` | Estado `congelada` en curso | histórico |
| `membresia_vencida` | Estado `vencida` o `fecha_fin < hoy` | histórico |
| `accesos_agotados` | Membresía por accesos con `usados >= total` | histórico |

**Orden de evaluación (importante):** las razones `pago_pendiente` y `membresia_rechazada` se evalúan **antes** que `sin_membresia`, `membresia_congelada`, `membresia_vencida` y `accesos_agotados`. Consecuencia: un cliente cuya única "activa" es una PENDIENTE + tiene una PAGADA vencida reporta `pago_pendiente` (no `membresia_vencida`).

**Errors:**
- `400` — missing or invalid query parameters
- `403` — access denied (see response body for `razon`)

> Revisar contra código: `MembresiaController#validarAcceso` + `MembresiaService#resolverAcceso/evaluarMembresia`.

---

### GET /api/v1/membresias/validar-acceso-cliente
**Auth:** None (PUBLIC endpoint)  
**Description:** Variante de `validar-acceso` que resuelve el cliente por su **propio `id_cliente`** (`core.clientes.id`) en lugar de por `id_persona`. Usada por el flujo de **asistencia manual** del heatmap admin (`attendance-service` → `CoreServiceClient.validarAccesoPorCliente`), que solo conoce el `id_cliente`. Reutiliza exactamente la misma lógica de resolución de acceso (`MembresiaService#resolverAcceso`), por lo que el body de respuesta, el catálogo de `razon` y el orden de evaluación son idénticos a los de `validar-acceso`. Los flujos QR/app siguen usando `validar-acceso` con `id_persona`.

**Query parameters:**
- `id_cliente` (required, integer) — Client ID from `core.clientes`
- `id_compania` (required, integer) — Company ID

**Response 200 / 403:** Idénticos a `validar-acceso` (mismos campos, mismo catálogo de `razon`).

**Errors:**
- `400` — missing or invalid query parameters
- `403` — access denied (see response body for `razon`)

> Revisar contra código: `MembresiaController#validarAccesoCliente` + `MembresiaService#validarAccesoPorCliente/resolverAcceso`.

---

### POST /api/v1/membresias/{id}/confirmar-pago
**Auth:** Bearer JWT (`tipo: staff`)
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago` (se exige solo si el token trae la lista `permisos` explícita; los tokens legacy sin `permisos` y `super_admin` la bypass-ean — el gate real vive en `MembresiaController#requireConfirmarPagoPermiso`).
**Description:** Marca una membresía `PENDIENTE` como `PAGADO`. El comportamiento del body es **condicional según `origen`** de la membresía:
- `origen='staff'` (venta directa mostrador): body opcional; si viene se **ignora** completamente. `fecha_inicio=hoy` y se preserva el `precio_pagado` fijado en la venta.
- `origen='cliente'` (solicitud autoservicio): body **obligatorio** con `id_metodo_pago`, `precio_pagado` y `fecha_inicio` (el `descuento_aplicado` es opcional, default `0.00`).

Calcula `fecha_fin = fecha_inicio + duración`. Publica `MembresiaPagadaEvent` en el bus interno solo en la transición real PENDIENTE → PAGADO. Idempotente sobre membresías ya `PAGADO` (incluso si viene body).

**Path param:**
- `id` — Membership ID

**Request body (opcional, requerido si `origen='cliente'`):**
```json
{
  "id_metodo_pago": 1,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "fecha_inicio": "2026-07-17"
}
```

**Body fields:**
- `id_metodo_pago` (required si `origen='cliente'`, ignored si `origen='staff'`, integer) — ID del método de pago con que se cobró.
- `precio_pagado` (required si `origen='cliente'`, ignored si `origen='staff'`, decimal >= 0) — Monto efectivamente cobrado. El staff puede ajustarlo (decisión PO #1: el precio del catálogo NO se congela al solicitar).
- `descuento_aplicado` (optional, decimal >= 0, default `0.00`) — Descuento aplicado.
- `fecha_inicio` (required si `origen='cliente'`, ignored si `origen='staff'`, `YYYY-MM-DD`) — Día real que empieza la membresía; el staff puede backdatear.

**Response 200 (venta autoservicio completada):**
```json
{
  "id": 101,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-07-17",
  "fecha_fin": "2026-08-17",
  "dias_acceso_total": null,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "origen": "cliente",
  "eliminado": false,
  "motivo_eliminacion": null
}
```

**Reglas:**
- Idempotente: si la membresía ya está `PAGADO`, devuelve `200 OK` con el recurso actual (no recalcula fechas, no re-emite evento, no aplica el body).
- Si `eliminado = true` → `409 Conflict` con mensaje `"La membresía fue rechazada y no puede confirmarse"` (`codigo=conflicto`).
- Si `origen='cliente'` y falta algún campo obligatorio del body → `400` con `codigo=datos_venta_incompletos` y detalle por-campo:
  ```json
  {
    "codigo": "datos_venta_incompletos",
    "detail": "Faltan datos de venta para confirmar la solicitud del cliente",
    "status": 400,
    "errores": [
      { "campo": "id_metodo_pago", "mensaje": "es obligatorio para completar la venta" },
      { "campo": "fecha_inicio",   "mensaje": "es obligatoria para iniciar la membresía" }
    ]
  }
  ```
- Efecto lateral: cambia el estado del cliente a `activo`.
- Cuando la membresía es `origen='cliente'` de tipo `accesos`, `dias_acceso_total` (que quedó NULL en la solicitud) se completa con `tipos_membresia.dias_acceso` al confirmar.

**Errors:**
- `400` — datos de venta incompletos (`codigo=datos_venta_incompletos`, solo para `origen='cliente'`)
- `401` — missing or invalid JWT
- `403` — sin permiso `membresias:confirmar_pago`, o compañía distinta
- `404` — membership o tipo de membresía no encontrados
- `409` — membresía rechazada (`eliminado = true`)

> Revisar contra código: `MembresiaController#confirmarPago` + `MembresiaService#confirmarPago` (§4.6 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### POST /api/v1/membresias/{id}/rechazar
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago` (cubre confirmar y rechazar).  
**Description:** Soft-delete de una membresía `PENDIENTE`. Marca `eliminado = true`, setea auditoría (`fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`). La membresía sigue viva en BD para reportería (métrica de conversión, §HU-E). NO aplica a membresías ya `PAGADAS` (esas se anulan con `PUT /membresias/{id}/anular`).

**Path param:**
- `id` — Membership ID

**Request body:**
```json
{
  "motivo_eliminacion": "SOCIO_CAMBIO_OPINION"
}
```

**Body fields:**
- `motivo_eliminacion` (required, enum) — Catálogo cerrado: `SOCIO_CAMBIO_OPINION` \| `ERROR_DE_VENTA` \| `DUPLICADA` \| `DATOS_INCORRECTOS` \| `OTRO`.

**Response 200:**
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": null,
  "fecha_fin": null,
  "dias_acceso_total": null,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "estado": "activa",
  "estado_pago": "PENDIENTE",
  "eliminado": true,
  "motivo_eliminacion": "SOCIO_CAMBIO_OPINION"
}
```

**Reglas:**
- Si `estado_pago = PAGADO` → `409 Conflict` con mensaje `"No se puede rechazar una membresía pagada; usar anulación"`.
- Si `eliminado = true` (ya rechazada) → `409 Conflict` con mensaje `"La membresía ya fue rechazada"`.

**Errors:**
- `400` — body ausente, `motivo_eliminacion` null o fuera del catálogo
- `401` — missing or invalid JWT
- `403` — sin permiso `membresias:confirmar_pago`, o compañía distinta
- `404` — membership no encontrada
- `409` — membresía ya pagada o ya rechazada

> Revisar contra código: `MembresiaController#rechazar` + `MembresiaService#rechazar` (§4.7 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### GET /api/v1/companias/{idCompania}/membresias/pendientes
**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago`.  
**Description:** Dashboard "Ventas pendientes" (§4.8). Lista membresías con `estado_pago = PENDIENTE` y `eliminado = false` de la compañía indicada, ordenadas por `creacion_fecha` DESC (última primero). El frontend calcula el "hace X días" a partir de `creacion_fecha`.

**Path param:**
- `idCompania` — Company ID (debe coincidir con la compañía del JWT).

**Response 200:**
```json
[
  {
    "id": 42,
    "id_cliente": 10,
    "nombre_cliente": "Ana Pérez",
    "id_tipo_membresia": 3,
    "tipo_nombre": "Plan Mensual",
    "modo_control": "calendario",
    "precio_pagado": "35.00",
    "descuento_aplicado": "0.00",
    "origen": "cliente",
    "creacion_fecha": "2026-07-15T09:12:33Z"
  }
]
```

**Response fields por fila:**
- `id` — Membership ID
- `id_cliente` — Client ID
- `nombre_cliente` — Nombre completo del cliente (join a `identidad.personas.nombre`). `null` si la persona fue borrada.
- `id_tipo_membresia` — Membership type ID
- `tipo_nombre` — Nombre del tipo (join a `core.tipos_membresia`)
- `modo_control` — `calendario` o `accesos`
- `precio_pagado` — Amount to be paid (placeholder `0.00` cuando `origen='cliente'`; el staff lo ingresa al confirmar)
- `descuento_aplicado` — Discount applied
- `origen` — `cliente` (solicitud autoservicio PWA, requiere que staff complete datos de venta al confirmar) | `staff` (venta directa PENDIENTE, solo requiere confirmación)
- `creacion_fecha` — Timestamp de creación (ISO-8601 con TZ)

**Errors:**
- `401` — missing or invalid JWT
- `403` — cross-tenant (`idCompania` no es la del JWT) o sin permiso granular

> Revisar contra código: `MembresiaController#listarPendientes` + `MembresiaService#listarPendientesPorCompania` (§4.8 de `docs/gym-administrator/requirements/estado-pago-membresias.md`).

---

### GET /api/v1/companias/{idCompania}/membresias/pendientes/contador
**Auth:** Bearer JWT (`tipo: staff`)
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago`.
**Description:** Contador para el badge del dashboard staff. Retorna el total de membresías PENDIENTE + vivas de la compañía con desglose por `origen` (cliente vs. staff). La UI usa `por_origen.cliente` para llamar la atención sobre solicitudes autoservicio pendientes de completar. Query única con `GROUP BY origen` (usa el índice parcial `idx_membresias_pendientes_cliente`).

**Path param:**
- `idCompania` — Company ID (debe coincidir con la compañía del JWT).

**Response 200:**
```json
{
  "total": 5,
  "por_origen": {
    "cliente": 3,
    "staff": 2
  }
}
```

**Response fields:**
- `total` — Suma total de membresías `PENDIENTE` + `eliminado=false` de la compañía.
- `por_origen.cliente` — Solicitudes autoservicio pendientes de completar (staff debe ingresar precio, método, fecha).
- `por_origen.staff` — Ventas mostrador PENDIENTES (staff solo debe confirmar pago).

Si algún origen no tiene filas la clave sigue apareciendo con valor `0`.

**Errors:**
- `401` — missing or invalid JWT
- `403` — cross-tenant (`idCompania` no es la del JWT) o sin permiso granular

> Revisar contra código: `MembresiaController#contadorPendientes` + `MembresiaService#contarPendientesPorCompania`.

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/clientes/{id}/membresias` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/clientes/me/membresias` | GET | `requireCliente()` (resuelve id_cliente del JWT) |
| `/clientes/me/membresias/solicitar` | POST | `requireCliente()` (autoservicio PWA) |
| `/clientes/{id}/membresias` | POST | `requireRecepcionOrAbove()` |
| `/membresias/{id}` | GET | `requireGymStaff()` \| `requireCliente()` |
| `/membresias/{id}/asistencias-previas` | PATCH | `requireRecepcionOrAbove()` |
| `/membresias/{id}/anular` | PUT | `requireAdminOrDueno()` |
| `/membresias/{id}/confirmar-pago` | POST | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/membresias/{id}/rechazar` | POST | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/companias/{idCompania}/membresias/pendientes` | GET | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/companias/{idCompania}/membresias/pendientes/contador` | GET | `requireRecepcionOrAbove()` + `membresias:confirmar_pago` |
| `/membresias/validar-acceso` | GET | PUBLIC (sin auth) |
| `/membresias/validar-acceso-cliente` | GET | PUBLIC (sin auth) |

---

## Códigos de error comunes

| Código HTTP | `codigo` | Significado |
|-------------|----------|-------------|
| 400 | `validacion` | Datos de request inválidos |
| 400 | `datos_venta_incompletos` | Solicitud origen=cliente sin `id_metodo_pago` / `precio_pagado` / `fecha_inicio` al confirmar |
| 401 | `no_autenticado` | Token ausente o inválido |
| 403 | `acceso_denegado` | Sin permisos o cross-tenant |
| 404 | `recurso_no_encontrado` | Membresía / cliente / tipo no encontrado |
| 404 | `tipo_membresia_no_disponible` | En solicitar: tipo inactivo, inexistente o de otra compañía |
| 409 | `conflicto` | Membresía rechazada, ya pagada, etc. |
| 409 | `solicitud_ya_existe` | Cliente ya tiene cualquier compra viva en trámite (PENDIENTE + no eliminada, sin importar origen) |
| 409 | `membresia_activa_vigente` | Cliente tiene una membresía PAGADA activa aún vigente al solicitar |
