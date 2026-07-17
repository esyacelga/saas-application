# Estado de pago en ventas de membresía

> **ESTADO:** 📋 **Planeado — sin implementar.** Historia de usuario aprobada, lista para revisión de arquitectura.
> **Fecha:** 2026-07-17
> **Origen:** el flujo actual asume que toda venta de membresía se cobra al momento. Para habilitar la futura compra desde la PWA del socio y tener trazabilidad de cobros en proceso, se necesita separar el **momento de la venta** del **momento del pago**.

---

## 1. Contexto de negocio

Hoy `POST /api/v1/clientes/{id}/membresias` crea una membresía activa que asume pago recibido. Esto impide:

1. Que un socio inicie la compra desde la PWA (`gym-member-pwa`) y pague después en recepción.
2. Que el staff tenga una bandeja de "ventas por cobrar" con acciones claras.
3. Distinguir contablemente ingreso confirmado de ingreso pendiente.

La solución: agregar un nuevo campo `estado_pago` con dos valores (`PENDIENTE`, `PAGADO`), y habilitar los flujos de confirmación y rechazo desde el panel admin. **El flujo de compra desde la PWA queda para una HU posterior** — este documento solo prepara el terreno backend + admin.

---

## 2. Alcance

### IN — dentro de esta HU
- Backend: `core-service` (modelo, endpoints, casos de uso), `attendance-service` (validación de acceso), `auth-service` (nuevo permiso `membresias:cobrar`).
- Base de datos: nuevas columnas en `core.membresias`, migración con backfill, nuevo permiso en `seguridad`.
- Frontend: nueva sección "Ventas pendientes" en `auth-service-frond-end` con acciones marcar como pagada / rechazar.

### OUT — HUs posteriores
- **HU-B**: flujo de compra desde `gym-member-pwa` que crea membresías con `estado_pago = 'PENDIENTE'`.
- **HU-C**: integración con `billing-service` para emitir factura al pasar a `PAGADO`.
- **HU-D** (opcional): notificación al staff cuando entra una venta pendiente nueva.
- **HU-E** (opcional): métrica de conversión pendientes → pagadas.
- Integración con pasarelas de pago (Stripe / PayPhone / Kushki).

---

## 3. Decisiones de diseño (contestadas con el negocio)

| # | Pregunta | Decisión |
|---|----------|----------|
| 1 | Nombre y valores del campo | Campo separado `estado_pago`, valores `PENDIENTE` y `PAGADO`. Nada de `CANCELADO` o `RECHAZADO`. |
| 2 | ¿PENDIENTE da acceso al gym? | **NO.** `validar-acceso` en attendance-service rechaza cualquier membresía en `PENDIENTE`. |
| 3 | ¿Desde cuándo cuenta la vigencia? | Desde la **fecha de confirmación de pago**. Al pasar a `PAGADO`, se recalcula `fecha_inicio = hoy` y `fecha_fin = hoy + duración`. |
| 4 | Timeout de pendientes | **No hay auto-cancelación**. El staff las rechaza manualmente desde el dashboard admin. |
| 5 | Cómo se confirma el pago | **Solo botón manual del staff**. Sin integración con pasarelas por ahora. |
| 6 | Relación con billing-service | Se **deja TODO documentado**: cuando pase a `PAGADO`, disparar emisión de factura. Este código NO se toca en esta HU. |
| 7 | Rechazo/eliminación | **Soft-delete** con columnas `eliminada`, `fecha_eliminacion`, `eliminada_por` para trazabilidad. |
| 8 | Alcance de la HU | **Solo backend + admin.** La PWA queda para HU-B. |

---

## 4. Criterios de aceptación

### 4.1 Modelo de datos (`core.membresias`)

- Columna `estado_pago` VARCHAR, `CHECK IN ('PENDIENTE', 'PAGADO')`, NOT NULL, DEFAULT `'PAGADO'`.
- Columna `eliminada` BOOLEAN, NOT NULL, DEFAULT `false`.
- Columna `fecha_eliminacion` TIMESTAMP, NULL.
- Columna `eliminada_por` BIGINT, NULL (FK al usuario staff que rechazó).
- Migración Liquibase con **backfill**: todos los registros existentes → `estado_pago = 'PAGADO'`, `eliminada = false`.
- Índice `(id_compania, estado_pago, eliminada)` para acelerar el dashboard de pendientes.

### 4.2 Creación desde admin (comportamiento actual conservado)

- El staff crea desde el panel admin → `estado_pago = 'PAGADO'` por defecto.
- `fecha_inicio` y `fecha_fin` se calculan como hoy (comportamiento actual).

### 4.3 Endpoint de creación acepta PENDIENTE (preparación PWA)

- `POST /api/v1/clientes/{id}/membresias` recibe campo opcional `estado_pago` (default `'PAGADO'`).
- Cuando llega con `estado_pago = 'PENDIENTE'`:
  - `fecha_inicio` y `fecha_fin` quedan tentativos o NULL (**decidir con backend/arquitectura**).
  - No dispara ningún evento hacia billing-service ni notificaciones.
- Endpoint queda listo para que la PWA lo consuma en HU-B; no hay UI que lo dispare en esta HU.

### 4.4 `validar-acceso` bloquea membresías pendientes

- En `attendance-service`, el endpoint que valida acceso al gym debe rechazar si la única membresía activa está en `PENDIENTE` o `eliminada = true`.
- Mensaje al usuario: *"Membresía pendiente de pago, acércate a recepción"*.
- Tests de integración: caso PENDIENTE, caso ELIMINADA, caso PAGADO (regresión positiva).

### 4.5 Confirmar pago (nuevo endpoint)

- `POST /api/v1/membresias/{id}/confirmar-pago`.
- Permiso requerido: `membresias:cobrar` (nuevo).
- Efecto:
  - `estado_pago` → `'PAGADO'`.
  - `fecha_inicio` = hoy.
  - `fecha_fin` = hoy + duración de la membresía.
- Idempotencia: si ya está `PAGADO`, responder `409 Conflict` o `200 no-op` (**decidir con arquitectura**).
- **TODO FACTURACION**: aquí, en el futuro, disparar evento hacia billing-service.

### 4.6 Rechazar pendiente (soft-delete)

- `DELETE /api/v1/membresias/{id}` (o `POST /api/v1/membresias/{id}/rechazar` — decidir con arquitectura).
- Permiso requerido: `membresias:cobrar`.
- Solo aplica si `estado_pago = 'PENDIENTE'` (rechazar una PAGADA no es válido; usar flujo de anulación distinto).
- Efecto: `eliminada = true`, `fecha_eliminacion = now()`, `eliminada_por = usuario_actual`.
- Todas las consultas por defecto excluyen `eliminada = true`.

### 4.7 Dashboard admin — sección "Ventas pendientes"

- Nueva vista o tab (ubicación exacta a definir con `ui-ux-designer`).
- Lista membresías `estado_pago = 'PENDIENTE'` y `eliminada = false` de la compañía del usuario logueado.
- Columnas: cliente, tipo de membresía, precio, fecha de creación, tiempo pendiente (relativo tipo "hace 2 días").
- Acciones por fila:
  - **Marcar como pagada** → `POST /confirmar-pago`, toast success, refresca lista.
  - **Rechazar** → confirm dialog, `DELETE`, toast, refresca.
- Filtros: search por nombre de cliente.
- Empty state: *"No hay ventas pendientes de cobro"*.
- i18n en `es` y `en`.

### 4.8 Permisos

- Nuevo permiso `membresias:cobrar` en el schema `seguridad`.
- Asignado por defecto a los roles `PROPIETARIO` y `ADMINISTRADOR`.
- NO asignado a `SOCIO` ni a `INSTRUCTOR`.

---

## 5. Servicios afectados

| Servicio | Cambios |
|----------|---------|
| `core-service` | Modelo, repositorio R2DBC, casos de uso (crear con estado / confirmar pago / rechazar), endpoints, tests unitarios e ITs. |
| `attendance-service` | `validar-acceso` verifica `estado_pago` y `eliminada`. Tests de integración. |
| `auth-service` (seguridad) | Nuevo permiso `membresias:cobrar` en Liquibase + asignación a roles. |
| `auth-service-frond-end` | Sección "Ventas pendientes", componentes, botones de acción, i18n, permisos. |
| Base de datos | 4 columnas nuevas en `core.membresias` + backfill + índice + fila nueva en `seguridad.permisos`. |

---

## 6. Riesgos y consideraciones

- **Backfill obligatorio**: si algún registro existente queda con `estado_pago = NULL`, el CHECK constraint falla al desplegar. La migración debe hacer `UPDATE ... SET estado_pago = 'PAGADO'` antes de agregar el NOT NULL.
- **ITs preexistentes rotos**: `attendance-service` y `core` tienen ITs preexistentes rotos en master (ver memoria del proyecto). Al ejecutar los tests hay que separar regresiones reales del ruido existente. Correr `*Test` unitarios primero y reproducir el estado de master antes de culpar cambios de esta HU.
- **Endpoint sin consumidor**: crear con `PENDIENTE` queda expuesto pero sin UI que lo dispare en esta HU. Evaluar con arquitectura si se protege con feature flag o solo se documenta como "reservado para PWA".
- **Idempotencia de confirmar-pago**: pendiente de definir con arquitectura si es 409 o 200 no-op.

---

## 7. Pendientes / follow-ups

- **HU-B — Compra desde PWA del socio** (`gym-member-pwa`): pantalla de tipos disponibles, confirmación de compra que crea `estado_pago = 'PENDIENTE'`, vista "mis compras pendientes" con instrucciones para pagar en recepción.
- **HU-C — Integración billing-service**: cuando pase a `PAGADO`, emitir factura electrónica. Requiere que billing-service esté activo para la compañía.
- **HU-D — Notificación al staff** (opcional): WhatsApp o in-app cuando entra una venta pendiente nueva desde la PWA.
- **HU-E — Métrica de conversión** (opcional): dashboard con "% pendientes que se convirtieron a pagadas" por mes.
- **HU-F — Pasarelas de pago** (opcional, largo plazo): Stripe / PayPhone / Kushki para pago automático desde PWA con webhook que confirma.

---

## 8. Referencias

- Endpoint actual de venta de membresía: [core-service/api/membresias.md](../../core-service/api/membresias.md)
- Modelo actual de `core.membresias`: [architecture/database-schema.md](../architecture/database-schema.md)
- Componente admin actual de venta: `auth-service-frond-end/src/ui/features/core/components/VenderMembresiaModal.tsx`
