# Solicitudes de Membresía desde PWA Cliente

> **Sub-documento (backend/core) de la HU canónica** [`../../gym-administrator/requirements/solicitudes-membresia.md`](../../gym-administrator/requirements/solicitudes-membresia.md). Ahí está el requerimiento cross-cutting completo (negocio + decisiones de PO + alcance en los 3 componentes). Este archivo detalla solo la parte de **core-service**. El frontend PWA está en [`../../gym-member-pwa/spec-solicitud-membresia.md`](../../gym-member-pwa/spec-solicitud-membresia.md).

**ESTADO:** ✅ **Implementado** (2026-07-20) — código es la fuente de verdad. Esta spec ahora describe el flujo tal como quedó; las decisiones de PO se preservan como registro histórico. Cambios respecto al diseño original marcados con "**Δ**" en cada sección.

**Servicio:** core-service  
**Esquemas BD:** `core.membresias` (columna nueva + índice UNIQUE parcial)  
**Depende de:** auth-service (JWT `tipo=cliente`), platform-service (validación de módulos)  
**Estado:** Verificado contra el código (`MembresiaController`, `MembresiaService`).

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

### 1. Schema — Liquibase (Δ consolidado en baseline)

**Δ Respecto al diseño original:** en vez de un ALTER en release nuevo, la columna se consolidó directamente en el `CREATE TABLE` del baseline `GYM-001` (Opción A acordada con el usuario, coherente con GYM-002/003). Y el índice pasó a ser **UNIQUE parcial** para cerrar la race condition detectada en el code review.

**Cambio 1a: Columna `origen`** — en `db/scripts/202605_GYM-001/ddl/31_create_table_core_membresias.sql`
```sql
origen              VARCHAR(10)   NOT NULL DEFAULT 'staff'
                      CHECK (origen IN ('cliente','staff')),
```
Ubicada entre `estado_pago` y `dias_acceso_total` en el CREATE TABLE. El default `'staff'` asegura que cualquier fila insertada sin especificar origen (path de venta staff) reciba el valor retro-compatible.

**Cambio 1b: Índice UNIQUE parcial** — en `db/scripts/202605_GYM-001/ddl/56_create_indexes_core.sql`
```sql
CREATE UNIQUE INDEX uq_membresias_pendiente_por_cliente_vivo
  ON core.membresias(id_cliente)
  WHERE estado_pago = 'PENDIENTE' AND eliminado = FALSE;
```
**Doble propósito:**
1. Garantiza el invariante "un cliente = una compra viva en trámite" a nivel BD (sin importar el origen). Cierra la race condition de check-then-act en `solicitarMembresia`.
2. Sirve como índice de lookup para `findPendienteVivaByIdCliente(idCliente, idCompania)`.

Cuando PostgreSQL rechaza el segundo INSERT concurrente, `DataIntegrityMapper` detecta el nombre del constraint y traduce a `codigo=solicitud_ya_existe` (mismo sobre RFC 7807 que la validación check-then-act).

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
1. Cliente no debe tener ninguna membresía viva con `estado_pago=PENDIENTE` (sin importar el `origen`) → `409 Conflict`. **Δ** El diseño original filtraba por `origen='cliente'`; el reviewer detectó que eso abría una asimetría (cliente podía abrir una solicitud PWA mientras staff tenía una venta sin cobrar en paralelo, resultando en membresías huérfanas). La regla ahora bloquea cualquier PENDIENTE viva:
   ```json
   {
     "type": "https://core-service/errors/solicitud-ya-existe",
     "title": "Solicitud en espera",
     "detail": "Ya tienes una compra en trámite. Espera a que el staff la confirme o cancele antes de solicitar una nueva.",
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

4. Crear la membresía con (**Δ** placeholders para columnas NOT NULL sin default):
   - `origen='cliente'`
   - `estado_pago='PENDIENTE'`
   - `precio_pagado=0` (placeholder — la columna es NOT NULL sin default; se sobrescribe al `confirmar-pago`)
   - `descuento_aplicado=0` (default de la columna)
   - `dias_acceso_total=null` (se rellena al confirmar desde `tipos_membresia.dias_acceso` si el tipo es `accesos`; ver "Comportamiento Invariante" para el trade-off B1)
   - `id_metodo_pago=null` (el staff elige)
   - `fecha_inicio=null`, `fecha_fin=null` (CHECK `ck_membresias_fechas_por_estado_pago` lo exige)
   - `id_cliente` derivado de `id_persona` + `id_compania` del JWT vía `ClienteRepository.findByIdPersonaAndIdCompania`
   - `id_sucursal` derivado de `cliente.id_sucursal` (**Δ** el cliente está registrado en una sucursal; la solicitud hereda esa. Antes se usaba `id_compania` como valor incorrecto, corregido en review)
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
- `id_metodo_pago` (**required** si `origen=cliente` y `precio_pagado=0`, optional si `origen=staff`) — Payment method ID
- `descuento_aplicado` (**opcional, default `0.00`** — **Δ** Decision C: el diseño inicial lo marcaba REQUERIDO para origen cliente, pero el default es más ergonómico y coincide con la realidad de que la mayoría de ventas no llevan descuento; si viene ausente, se asume 0) — Discount amount (DECIMAL)
- `fecha_inicio` (**required** si `origen=cliente` y `precio_pagado=0`, optional si `origen=staff`, format `YYYY-MM-DD`) — Start date. **Este es el día real que comienza la membresía.** Típicamente `CURRENT_DATE` (el día que el staff completa la venta), pero staff puede backdatear si necesita.
- `precio_pagado` (**required** si `origen=cliente`, optional si `origen=staff`, DECIMAL >= 0) — Amount actually charged

**Lógica de validación (Δ `precio_pagado=0` en vez de `null` — la columna es NOT NULL):**

```
Si membresía.precio_pagado = 0 (originada por cliente, placeholder):
  ├─ id_metodo_pago: REQUERIDO, sino 400 con codigo=datos_venta_incompletos
  ├─ descuento_aplicado: OPCIONAL (default 0), pasa sin más
  ├─ fecha_inicio: REQUERIDO, validar formato DATE, sino 400 con codigo=datos_venta_incompletos
  └─ precio_pagado: REQUERIDO (>= 0), sino 400 con codigo=datos_venta_incompletos

Si membresía.precio_pagado > 0 (originada por staff, ya trae precio):
  └─ Body (si presente) SE IGNORA COMPLETAMENTE
     El endpoint funciona igual que hoy: confirma y usa fecha_inicio=HOY

Si membresía.estado_pago = PAGADO (ya confirmada):
  └─ Idempotente 200 OK, sin recalcular, sin re-emitir evento

Si el tipo de membresía es 'accesos':
  └─ Antes de guardar, se copia tipos_membresia.dias_acceso → membresia.dias_acceso_total
     (Δ decisión B1: dias_acceso NO se congela al solicitar; se lee del catálogo actual)
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

### Días de Acceso No Se Congelan (Δ Decisión B1 — 2026-07-20)
- Coherente con "precio no se congela": `dias_acceso_total` para tipos `accesos` **se lee del catálogo al `confirmar-pago`**, no se copia al momento de solicitar.
- Riesgo aceptado: si el admin edita `tipos_membresia.dias_acceso` entre la solicitud y la confirmación (ej. "Bono 20 accesos" → "Bono 15 accesos"), el cliente recibe la cantidad nueva.
- **Mitigación**: el PWA muestra un widget persistente en el Home (`MembresiaStatusWidget`) que refleja el tipo solicitado y el precio del catálogo al momento de la solicitud. Si al pagar el cliente nota discrepancia, puede reclamar con evidencia visible. Ver spec en `docs/gym-member-pwa/spec-home-membresia-widget.md` (o equivalente producido por `ui-ux-designer`).

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
| **Solicitud spam** — Cliente envía 100 solicitudes rápido | Alta | Bajo | ✅ **Mitigado** con UNIQUE partial index `uq_membresias_pendiente_por_cliente_vivo` (2026-07-20). Un cliente no puede tener más de una PENDIENTE viva en BD, independiente de concurrencia. Rate limit adicional queda como guardia futura si se detecta abuso a nivel de red. |
| **Precio cambia entre solicitud y confirmación** — Catálogo actualizado | Media | Media | Decisión #1: staff ingresa precio. UI muestra precio actual al confirmar. |
| **Cliente olvida solicitud, intenta de nuevo** — Confusión UX | Media | Bajo | GET /clientes/me/membresias muestra solicitudes PENDIENTE. UI avisa si ya hay una. |
| **Congelamiento en estado PENDIENTE** — ¿Permitir congelar solicitud? | Baja | Bajo | No permitir congelar mientras `estado_pago=PENDIENTE`. Validar en `POST /congelar`. |
| **Auditoría incompleta** — ¿Quién confirmó la solicitud?** | Media | Media | Agregar `id_usuario_confirmacion` y `fecha_confirmacion` a futuro. Hoy: se registra en `updated_at`. |
| **Flujo de cancelación indefinida** — ¿Expiración automática de solicitudes?** | Baja | Bajo | No implementar expiración en fase 1. Futuro: job que rechace automáticamente solicitudes >7 días. |

---

## Checklist Post-Implementación

Estos archivos de `docs/core-service/api/` deben sincronizarse **después de que el código esté escrito**:

- [x] `docs/core-service/api/membresias.md` — actualizado por `backend-developer` en el mismo commit del feature.
- [x] `docs/core-service/INDEX.md` — spec marcada como implementada.
- [ ] `core-service/CLAUDE.md` — pendiente: state machine expandido si se considera relevante (el flujo actual ya es coherente con el diagrama de la sección "State Machines").
- [ ] `core-service/README.md` — sin cambios necesarios (no hay variables de entorno nuevas).
- [ ] Spec del PWA Home Widget (producida por `ui-ux-designer` en PR3) — commit pendiente.

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
