# Solicitudes de Membresía desde PWA Cliente

**ESTADO:** 🟡 **Feature aún no implementado** — Especificación de diseño del nuevo flujo de compra autoservicio. El código es la fuente de verdad una vez implementado; esta spec refleja las decisiones cerradas de producto.

**Servicio:** core-service  
**Esquemas BD:** `core.membresias` (columna nueva + índice)  
**Depende de:** auth-service (JWT `tipo=cliente`), platform-service (validación de módulos)  
**Estado:** Diseño completado, lista para implementar

---

## Contexto del Feature

Actualmente, solo el staff puede vender membresías vía `POST /api/v1/clientes/{id}/membresias`. Este feature agrega un **segundo origen de compra**: el cliente PWA puede ver el catálogo de tipos de membresía y solicitar una sin pasar por staff.

**Flujo nuevo:**
```
Cliente PWA (sin membresía activa)
    ├─ GET /api/v1/tipos-membresia    ← ya existe
    ├─ [ve catálogo]
    └─ POST /api/v1/clientes/me/membresias/solicitar
         │
         └─► Crea membresía con origen=cliente, estado_pago=PENDIENTE
             
Staff Dashboard (Ventas pendientes)
    ├─ GET /api/v1/companias/{id}/membresias/pendientes   ← devuelve ambos orígenes
    ├─ [ve nuevas solicitudes del cliente remarcadas]
    └─ POST /api/v1/membresias/{id}/confirmar-pago
         │
         └─► Completa la venta con datos de pago, fecha de inicio real
```

**Flujo antiguo (staff vendiendo directamente) permanece sin cambios.**

---

## Estado Actual en Código

### Tablas y Columnas
- `core.membresias.estado_pago` — columna existente, ENUM(`PENDIENTE`, `PAGADO`) desde linea 11 de `Membresia.java`
- `core.membresias.estado` — columna existente, CHECK IN (`activa`, `vencida`, `congelada`, `anulada`)
- `core.membresias.precio_pagado` — puede ser NULL hoy cuando `estado_pago=PENDIENTE` (confirmado en `estado-pago-membresias.md` §4.4)
- `core.membresias.fecha_inicio`, `fecha_fin` — NULL cuando `estado_pago=PENDIENTE` por CHECK `ck_membresias_fechas_por_estado_pago`
- Índice actual: búsqueda por compañía + estado sin discriminación de origen

### Endpoints Vigentes
- `GET /api/v1/tipos-membresia` (MembresiaController:línea?) — cliente PWA puede llamarlo hoy (filtra por `id_compania` del JWT)
- `GET /api/v1/companias/{id}/membresias/pendientes` (MembresiaController:206) — lista membresías `PENDIENTE` + `eliminado=false` (venta vigente)
- `POST /api/v1/membresias/{id}/confirmar-pago` (MembresiaController:167) — hoy **NO acepta body**, solo confirma; cambiaremos esto
- `POST /api/v1/membresias/{id}/rechazar` (MembresiaController:186) — soft-delete con motivo
- `GET /api/v1/membresias/validar-acceso` — rechaza solicitudes `PENDIENTE` (decisión #2 del PO)
- `POST /api/v1/clientes/{id}/membresias` — venta staff, requiere `requireRecepcionOrAbove()`

### Contrato de Errores RFC 7807
- Piloto vivo en core-service desde 2026-07-18 (`arquitectura/error-contract.md`)
- Campo `codigo` STRING para errores de negocio (ej: `solicitud_ya_existe`)
- Mantener coherencia: 400/409 devuelven `codigo` siguiendo patrón existente

---

## Cambios de Diseño

### 1. Schema — Liquibase (Nuevo Changeset)

**Cambio 1a: Columna `origen`**
```sql
ALTER TABLE core.membresias
  ADD COLUMN origen VARCHAR(10) NOT NULL DEFAULT 'staff'
  CONSTRAINT chk_membresias_origen CHECK (origen IN ('cliente', 'staff'));
```

Migración idempotente: aplica DEFAULT a membresías existentes. Todas las ventas staff históricas o futuras dirían `origen='staff'`.

**Cambio 1b: Índice parcial**
```sql
CREATE INDEX idx_membresias_pendientes_cliente
  ON core.membresias (id_compania)
  WHERE estado_pago = 'PENDIENTE'
    AND origen = 'cliente'
    AND eliminado = false;
```

Usado por dashboard para filtrar rápido solicitudes cliente que requieren acción del staff.

### 2. DTOs / Responses

#### `MembresiaResponse` (modelo de respuesta existente) — agregar campo
```json
{
  "id": 1,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "fecha_inicio": "2026-01-15",
  "fecha_fin": "2026-04-15",
  "dias_acceso_total": null,
  "precio_pagado": "150.00",
  "descuento_aplicado": "10.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "eliminado": false,
  "motivo_eliminacion": null,
  "origen": "cliente"
}
```

**Cambio:** Nuevo campo `origen: "cliente" | "staff"` (snake_case). Permite UI distinguir quién originó la solicitud.

#### `MembresiaPendienteResponse` (para GET /pendientes) — agregar campo
```json
{
  "id": 42,
  "id_cliente": 10,
  "nombre_cliente": "Ana Pérez",
  "id_tipo_membresia": 3,
  "tipo_nombre": "Plan Mensual",
  "modo_control": "calendario",
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "creacion_fecha": "2026-07-15T09:12:33Z",
  "origen": "cliente"
}
```

**Cambio:** Nuevo campo `origen: "cliente" | "staff"`. UI usa para renderizar botón "Completar venta" diferente si es cliente.

---

### 3. Endpoints Nuevos

#### `POST /api/v1/clientes/me/membresias/solicitar`

Cliente PWA solicita membresía sin intermediario staff.

**Auth:** Bearer JWT (`tipo: cliente`)  
**Permission:** `requireCliente()` (resuelve `id_cliente` desde JWT)  
**Description:** Cliente crea una solicitud de membresía como `PENDIENTE`. El staff debe completarla luego con datos de pago.

**Request body:**
```json
{
  "id_tipo_membresia": 3
}
```

**Request fields:**
- `id_tipo_membresia` (required, integer) — Membership type ID from `tipos-membresia`. Debe estar activo y pertenecer a la misma `id_compania` del JWT.

**Response 201:**
```json
{
  "id": 101,
  "id_cliente": 10,
  "id_tipo_membresia": 3,
  "tipo_nombre": "Plan Mensual",
  "modo_control": "calendario",
  "fecha_inicio": null,
  "fecha_fin": null,
  "dias_acceso_total": null,
  "precio_pagado": "35.00",
  "descuento_aplicado": "0.00",
  "estado": "activa",
  "estado_pago": "PENDIENTE",
  "eliminado": false,
  "motivo_eliminacion": null,
  "origen": "cliente"
}
```

**Response fields:** Idénticos a `MembresiaResponse` con `origen=cliente`, `estado_pago=PENDIENTE`, `fecha_inicio=null`, `fecha_fin=null`.

**Reglas de negocio:**
1. Cliente no debe tener ya una membresía con `estado_pago=PENDIENTE` y `eliminado=false` viva → `409 Conflict` con:
   ```json
   {
     "tipo": "https://core-service/errors/solicitud-ya-existe",
     "titulo": "Solicitud en espera",
     "detalle": "Ya tienes una solicitud de membresía en revisión. Espera a que sea confirmada o rechazada.",
     "codigo": "solicitud_ya_existe",
     "status": 409
   }
   ```

2. `id_tipo_membresia` debe estar activo y pertenecer a `id_compania` del JWT → `404` con:
   ```json
   {
     "tipo": "https://core-service/errors/tipo-membresia-no-disponible",
     "titulo": "Tipo de membresía no disponible",
     "detalle": "El tipo que solicitaste no está disponible o ya no es ofrecido.",
     "codigo": "tipo_membresia_no_disponible",
     "status": 404
   }
   ```

3. Cliente no debe tener membresía activa vigente (redundante — la UI no muestra botón, pero backend valida):
   - Si existe una PAGADA + activa + no vencida → `409 Conflict` con:
   ```json
   {
     "tipo": "https://core-service/errors/membresia-activa-vigente",
     "titulo": "Membresía activa",
     "detalle": "No puedes solicitar una nueva membresía mientras tengas una activa. Espera a que venza.",
     "codigo": "membresia_activa_vigente",
     "status": 409
   }
   ```

4. Crear la membresía con:
   - `origen='cliente'`
   - `estado_pago='PENDIENTE'`
   - `precio_pagado=null` (no se cobra nada aún)
   - `descuento_aplicado=null` o `0.00` (el staff decide después)
   - `id_metodo_pago=null` (el staff elige)
   - `fecha_inicio=null`, `fecha_fin=null` (CHECK `ck_membresias_fechas_por_estado_pago` lo exige)
   - `id_cliente` derivado de `id_persona` + `id_compania` del JWT via `ClienteService.obtenerClienteDesdePersona()`
   - `estado='activa'` (invariante: estado describe la membresía lógica, no el estado de pago)
   - `eliminado=false` (nueva, no rechazada)

**Errores:**
- `400` — `id_tipo_membresia` ausente o tipo inválido
- `401` — missing or invalid JWT
- `403` — JWT no es tipo `cliente` o pertenece a otra compañía
- `404` — tipo de membresía no encontrado o no disponible
- `404` — cliente no registrado para esa persona en esa compañía
- `409` — cliente ya tiene una solicitud PENDIENTE viva
- `409` — cliente tiene membresía activa vigente (redundante pero por seguridad)

**Ver:** `docs/core-service/api/membresias.md` (actualizar tras implementar).

---

#### `GET /api/v1/companias/{id}/membresias/pendientes/contador`

Dashboard muestra badge con el total de ventas pendientes, desglosado por origen.

**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago`  
**Description:** Retorna conteo de membresías `PENDIENTE` no rechazadas, desglosado por origen (cliente vs. staff). Usado para el badge del dashboard principal.

**Path param:**
- `id` — Company ID (debe coincidir con la compañía del JWT)

**Query params:** ninguno

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
- `total` — Suma total de membresías PENDIENTE + eliminado=false
- `por_origen` — Objeto con conteos:
  - `cliente` — Solicitudes originadas por cliente
  - `staff` — Ventas directas del staff pendientes de confirmación

**Errores:**
- `401` — missing or invalid JWT
- `403` — cross-tenant (`id` no es la del JWT) o sin permiso granular `membresias:confirmar_pago`

**Ver:** `docs/core-service/api/membresias.md` (añadir nuevo endpoint).

---

### 4. Endpoints Modificados

#### `POST /api/v1/membresias/{id}/confirmar-pago` — Cambio de contrato

Hoy **NO acepta body**. Cambio: acepta body **opcional**, con comportamiento condicional.

**Auth:** Bearer JWT (`tipo: staff`)  
**Permission:** `requireRecepcionOrAbove()` + permiso granular `membresias:confirmar_pago`  
**Description:** Marca una membresía `PENDIENTE` como `PAGADO`. Si la membresía fue originada por cliente (`origen=cliente`, `precio_pagado=null`), los datos de venta **son obligatorios**. Si fue venta staff directo, el body se ignora. Calcula `fecha_inicio` y `fecha_fin` solo si aún están NULL. Idempotente sobre membresías ya `PAGADO`.

**Path param:**
- `id` — Membership ID

**Request body (opcional):**
```json
{
  "id_metodo_pago": 1,
  "descuento_aplicado": "0.00",
  "fecha_inicio": "2026-07-17",
  "precio_pagado": "35.00"
}
```

**Body fields:**
- `id_metodo_pago` (required si `origen=cliente` y `precio_pagado=null`, optional si `origen=staff`) — Payment method ID
- `descuento_aplicado` (required si `origen=cliente`, optional si `origen=staff`, default `0.00`) — Discount amount (DECIMAL)
- `fecha_inicio` (required si `origen=cliente` y `precio_pagado=null`, optional si `origen=staff`, format `YYYY-MM-DD`) — Start date. **Este es el día real que comienza la membresía.** Típicamente `CURRENT_DATE` (el día que el staff completa la venta), pero staff puede backdatear si necesita.
- `precio_pagado` (required si `origen=cliente`, optional si `origen=staff`, DECIMAL >= 0) — Amount actually charged

**Lógica de validación:**

```
Si membresía.precio_pagado = NULL (originada por cliente, origen='cliente'):
  ├─ id_metodo_pago: REQUERIDO, sino 400 con codigo=datos_venta_incompletos
  ├─ descuento_aplicado: REQUERIDO (puede ser 0), sino 400 con codigo=datos_venta_incompletos
  ├─ fecha_inicio: REQUERIDO, validar formato DATE, sino 400 con codigo=datos_venta_incompletos
  └─ precio_pagado: REQUERIDO (>= 0), sino 400 con codigo=datos_venta_incompletos

Si membresía.precio_pagado != NULL (originada por staff, origen='staff'):
  └─ Body (si presente) SE IGNORA COMPLETAMENTE
     El endpoint funciona igual que hoy: confirma y usa fecha_inicio=HOY

Si membresía.estado_pago = PAGADO (ya confirmada):
  └─ Idempotente 200 OK, sin recalcular, sin re-emitir evento
```

**Response 200 (originada por cliente, data de venta completada):**
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
  "eliminado": false,
  "motivo_eliminacion": null,
  "origen": "cliente"
}
```

**Response 200 (originada por staff, confirmación rápida):**
```json
{
  "id": 42,
  "id_cliente": 10,
  "id_tipo_membresia": 2,
  "fecha_inicio": "2026-07-17",
  "fecha_fin": "2026-07-24",
  "dias_acceso_total": 22,
  "precio_pagado": "40.00",
  "descuento_aplicado": "5.00",
  "estado": "activa",
  "estado_pago": "PAGADO",
  "eliminado": false,
  "motivo_eliminacion": null,
  "origen": "staff"
}
```

**Reglas:**
- Si `estado_pago = PAGADO` (ya confirmada) → 200 OK idempotente, no recalcula.
- Si `eliminado = true` (rechazada) → `409 Conflict` con `"La membresía fue rechazada y no puede confirmarse"`.
- Efecto lateral: cambia estado del cliente a `activo` (o `proximo_vencer` si ≤3 días para vencer).
- Publica `MembresiaPagadaEvent` **solo en transición PENDIENTE → PAGADO**, no en idempotencia.

**Errores:**
- `400` — campos incompletos / formato inválido. Si `origen=cliente` y falta alguno: `codigo=datos_venta_incompletos`
- `401` — missing or invalid JWT
- `403` — sin permiso `membresias:confirmar_pago`, o compañía distinta
- `404` — membership o tipo de membresía no encontrados
- `409` — membresía rechazada (`eliminado = true`)

**Cambios en doc:** `docs/core-service/api/membresias.md` (§POST /confirmar-pago).

**Ver:** RFC 7807 con `codigo` en `architecture/error-contract.md`.

---

## Comportamiento Invariante

Estos comportamientos NO cambian con este feature:

### Validación de Acceso
- `GET /api/v1/membresias/validar-acceso` — Solicitudes PENDIENTE rechazadas (no permiten acceso). Decisión #2 del PO: cliente espera confirmación antes de poder entrar.
  - Razón: `pago_pendiente` si existe PENDIENTE viva (decisión #2 GYM-003 §4.5)

### Cancelación de Solicitud
- Cliente NO puede cancelar su propia solicitud (decisión #4 del PO).
- Solo staff vía `POST /membresias/{id}/rechazar` (soft-delete).
- Motivo: asimetría deliberada — staff tiene poder sobre las solicitudes.

### Precio No Se Congela
- Decisión #1 del PO: **precio NO se congela** en el catálogo al momento de solicitar.
- Al `confirmar-pago`, el staff ingresa el `precio_pagado` que será cobrado (típicamente = `tipos_membresia.precio` hoy, pero staff puede ofrecer descuento).
- Beneficio: flexibilidad si el catálogo cambia entre solicitud y venta (ej: promo del día).

### Método de Pago
- Cliente NO elige método de pago (decisión #5 del PO).
- Staff elige al confirmar vía `id_metodo_pago` en el body de `confirmar-pago`.

---

## State Machine de Membresía

Actualizar `core-service/CLAUDE.md` con el estado actual expandido:

```
Cliente solicita desde PWA
    │
    ├─► POST /clientes/me/membresias/solicitar
    │       │
    │       └─ Crea con: origen=cliente, estado_pago=PENDIENTE,
    │                     precio_pagado=NULL, fecha_inicio=NULL, fecha_fin=NULL,
    │                     estado=activa, eliminado=false
    │
    ├─ [Estado: SOLICITADA POR CLIENTE]
    │   Estado lógico: activa (la membresía "existe" contractualmente)
    │   Estado de pago: PENDIENTE (no confirmada aún)
    │   Acceso: DENEGADO (razon=pago_pendiente)
    │   Staff: Ves en dashboard "Ventas pendientes" → puedes "Completar venta"
    │
    └─ POST /membresias/{id}/confirmar-pago + datos de venta
         │
         ├─ Staff ingresa: id_metodo_pago, descuento, fecha_inicio real, precio_pagado
         │
         └─ Transition: estado_pago=PAGADO, calcula fecha_fin, UPDATE cliente.estado=activo
    
              [Estado: ACTIVA PAGADA]
                Estado lógico: activa
                Estado de pago: PAGADO
                Acceso: PERMITIDO
                Historial: aparece en GET /clientes/me/membresias con origen=cliente

O bien (si staff rechaza):

    ├─ POST /membresias/{id}/rechazar + motivo
    │       │
    │       └─ UPDATE: eliminado=true, motivo_eliminacion=ENUM(...)
    │           Precios y fechas se preservan NULL para auditoría
    │
    └─ [Estado: RECHAZADA]
        eliminado=true, estado_pago sigue PENDIENTE
        Acceso: DENEGADO (razon=membresia_rechazada)
        Historial: aparece en GET /clientes/me/membresias con eliminado=true
        UI: muestra badge "Rechazada" con motivo
```

---

## Decisiones de Producto Cerradas (PO)

Se documentan aquí para evitar regresiones:

| # | Decisión | Justificación | Riesgo si cambia |
|---|---|---|---|
| 1 | Precio NO se congela al solicitar; se ingresa al confirmar | Flexibilidad si el catálogo cambia. Evita complejidad. | Clientes frustrados si el precio sube entre solicitud y confirmación. |
| 2 | Solicitudes PENDIENTE NO permiten acceso al gym | Cliente debe esperar confirmación del staff para entrar. Parte del contrato: "todavía no es cliente hasta que pague". | Exposición si cliente accede gratis mientras debería pagar. |
| 3 | Staff puede cambiar precio, descuento, fecha_inicio al confirmar | Manejo manual de excepciones (ej: cliente paga menos porque trae amigo, promo especial). | Inconsistencia si no hay auditoría clara (pero se registra en base). |
| 4 | Cliente NO puede cancelar su propia solicitud; solo staff vía rechazar | Evita spam, decisión final = staff. Simplifica flujo. | Fricción si cliente cambia de idea después de solicitar. |
| 5 | Cliente NO elige método de pago; solo staff ingresa id_metodo_pago | Evita UX de métodos de pago (card, cash, etc.). PWA solo "solicita", staff "finaliza". | Si en futuro PWA quiere cobrar directamente, esto requiere refactor. |

---

## Riesgos Abiertos

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|---|---|---|
| **Solicitud spam** — Cliente envía 100 solicitudes rápido | Alta | Bajo | RateLimit en endpoint (guardia futura). Hoy: validación "no dos PENDIENTE simultáneas". |
| **Precio cambia entre solicitud y confirmación** — Catálogo actualizado | Media | Media | Decisión #1: staff ingresa precio. UI muestra precio actual al confirmar. |
| **Cliente olvida solicitud, intenta de nuevo** — Confusión UX | Media | Bajo | GET /clientes/me/membresias muestra solicitudes PENDIENTE. UI avisa si ya hay una. |
| **Congelamiento en estado PENDIENTE** — ¿Permitir congelar solicitud? | Baja | Bajo | No permitir congelar mientras `estado_pago=PENDIENTE`. Validar en `POST /congelar`. |
| **Auditoría incompleta** — ¿Quién confirmó la solicitud?** | Media | Media | Agregar `id_usuario_confirmacion` y `fecha_confirmacion` a futuro. Hoy: se registra en `updated_at`. |
| **Flujo de cancelación indefinida** — ¿Expiración automática de solicitudes?** | Baja | Bajo | No implementar expiración en fase 1. Futuro: job que rechace automáticamente solicitudes >7 días. |

---

## Checklist Post-Implementación

Estos archivos de `docs/core-service/api/` deben sincronizarse **después de que el código esté escrito**:

- [ ] `docs/core-service/api/membresias.md` — Actualizar:
  - Nuevo endpoint: `POST /api/v1/clientes/me/membresias/solicitar`
  - Nuevo endpoint: `GET /api/v1/companias/{id}/membresias/pendientes/contador`
  - Modificación: `POST /api/v1/membresias/{id}/confirmar-pago` ahora acepta body opcional con campos condicionales
  - Modificación: `MembresiaPendienteResponse` agrega campo `origen`
  - Modificación: `MembresiaResponse` agrega campo `origen`
  - Errores de negocio: nuevos códigos `solicitud_ya_existe`, `tipo_membresia_no_disponible`, `membresia_activa_vigente`, `datos_venta_incompletos`
  - State machine actualizado en sección de Reglas

- [ ] `docs/core-service/INDEX.md` — Actualizar sección "Specs pendientes" → "Specs implementadas" con enlace a este documento en archivo final

- [ ] `core-service/CLAUDE.md` — Actualizar:
  - State machine de Membresía en sección de arquitectura
  - Variables de entorno requeridas (si aplica)
  - Descripción de permiso granular `membresias:confirmar_pago` (ya existe, confirmar en código)

- [ ] `core-service/README.md` — Actualizar sección de Docker/variables si hay nuevas requeridas

---

## Links de Referencia

- Actual estado-pago flow: `docs/gym-administrator/requirements/estado-pago-membresias.md`
- Contrato de errores RFC 7807: `docs/gym-administrator/architecture/error-contract.md`
- Core Service arquitectura: `core-service/CLAUDE.md`
- Spec anterior (GYM-003): `auth-service-frond-end/impl/13-member-portal-pwa.md`

---

**Spec v0.1 · Solicitudes de Membresía · 2026-07-17**  
**Autor:** Product Owner & Tech Lead  
**Estado:** Listo para dev  
**Próximo paso:** Crear changeset Liquibase + implementar endpoints + tests
