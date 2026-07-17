# Estado de pago en ventas de membresía

> **ESTADO:** 📋 **Planeado — sin implementar.** HU revisada por `architect`, `product-owner` y `code-reviewer` (verificación contra código existente, dos rondas). Lista para pasar a implementación.
> **Fecha:** 2026-07-17
> **Historia asociada:** `GYM-XXX` — asignar número al crear la carpeta `db/scripts/YYYYMM_GYM-XXX/`.
> **Origen:** el flujo actual asume que toda venta de membresía se cobra al momento. Para habilitar la futura compra desde la PWA del socio (HU-B) y tener trazabilidad de cobros en proceso, se necesita separar el **momento de la venta** del **momento del pago**.

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
- Backend: `core-service` (modelo, endpoints, casos de uso), `attendance-service` (validación de acceso), `auth-service` (nuevo permiso `membresias:confirmar_pago`).
- Base de datos: **nueva story** `db/scripts/YYYYMM_GYM-XXX/` con columnas nuevas en `core.membresias`, migración con backfill, nuevo permiso en `seguridad`.
- Frontend: nueva sección "Ventas pendientes" en `auth-service-frond-end` con acciones marcar como pagada / rechazar.

### OUT — HUs posteriores
- **HU-B**: flujo de compra desde `gym-member-pwa` que crea membresías con `estado_pago = 'PENDIENTE'`.
- **HU-C**: integración con `billing-service` para emitir factura al pasar a `PAGADO`. En esta HU se creará la tabla `core.pagos` y `estado_pago` de `core.membresias` **pasará a derivarse** de ella (deuda técnica documentada aquí).
- **HU-D** (opcional): notificación al staff cuando entra una venta pendiente nueva.
- **HU-E** (opcional): métrica de conversión pendientes → pagadas usando `motivo_eliminacion`.
- Integración con pasarelas de pago (Stripe / PayPhone / Kushki).

---

## 3. Decisiones aprobadas

### 3.1 Negocio (validadas con `product-owner`)

| # | Pregunta | Decisión |
|---|----------|----------|
| N1 | Nombre y valores del campo | Campo separado `estado_pago`, valores `PENDIENTE` y `PAGADO`. Nada de `CANCELADO` ni `RECHAZADO` (el rechazo se representa con `eliminado = true`). |
| N2 | ¿PENDIENTE da acceso al gym? | **NO.** `validar-acceso` en attendance-service rechaza cualquier membresía en `PENDIENTE`. |
| N3 | ¿Desde cuándo cuenta la vigencia? | Desde la **fecha de confirmación de pago**. Al pasar a `PAGADO`, se calcula `fecha_inicio = hoy` y `fecha_fin = hoy + duración`. |
| N4 | Timeout de pendientes | **No hay auto-cancelación.** El staff las rechaza manualmente. |
| N5 | Cómo se confirma el pago | **Solo botón manual del staff** desde el panel admin. Sin pasarelas por ahora. |
| N6 | Relación con billing-service | Se **deja TODO documentado** (evento interno `MembresiaPagadaEvent`). Este código NO se toca en esta HU. |
| N7 | Alcance de la HU | **Solo backend + admin.** La PWA queda para HU-B. |
| N8 | Membresías simultáneas | Un cliente puede tener **1 PAGADA activa + máximo 1 PENDIENTE** en paralelo (caso: renovación anticipada). Solo se responde `409 Conflict` si ya existe **otra PENDIENTE viva** (`estado_pago='PENDIENTE' AND eliminado=false`). La validación actual "1 activa por cliente" de `MembresiaService.venderMembresia` **debe relajarse** para permitir esta coexistencia (ver §5). |
| N9 | Roles habilitados | El permiso `membresias:confirmar_pago` **típicamente** corresponde a los roles equivalentes a *propietario*, *administrador* y *recepción* (quien atiende el mostrador). **Pero los nombres reales de rol los define cada gimnasio** — no existe un catálogo global de roles en el sistema (ver §4.9). El dueño de cada gym asigna manualmente este permiso desde la UI de "Editar rol" a los roles que en su organización cumplen esa función. **NO** corresponde al equivalente de *instructor* ni *socio*. |
| N10 | Motivo de rechazo | **Obligatorio, catálogo cerrado** de 5 valores: `SOCIO_CAMBIO_OPINION`, `ERROR_DE_VENTA`, `DUPLICADA`, `DATOS_INCORRECTOS`, `OTRO`. |
| N11 | KPIs / reportería | **Solo PAGADAS cuentan** en KPI de "socios activos", MRR, ingresos y comisiones. Las PENDIENTES viven en un dashboard separado tipo "Pipeline de ventas". |

### 3.2 Técnicas (validadas con `architect`)

| # | Punto | Decisión |
|---|-------|----------|
| A1 | Fechas cuando PENDIENTE | `fecha_inicio` y `fecha_fin` **NULL** mientras `estado_pago = 'PENDIENTE'`. Se agrega `CHECK` de consistencia. Comunica "aún no aplica" sin ambigüedad. |
| A2 | Idempotencia `confirmar-pago` | **200 no-op** si ya está `PAGADO` (no re-calcular fechas, no re-emitir efectos). **409** si `eliminado = true`. |
| A3 | Verbo del rechazo | **`POST /membresias/{id}/rechazar`** (no `DELETE`). Es transición de estado con auditoría; el recurso sigue vivo para reportería. |
| A4 | Columna de rechazo | **Reusar `eliminado` existente** (`BOOLEAN NOT NULL DEFAULT FALSE` — línea 17 del DDL actual). Añadir solo `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`. |
| A5 | Nombre del permiso | **`membresias:confirmar_pago`** (cubre confirmar y rechazar, ambas son "gestionar cobro"). Consistente con la convención `:leer/:crear/:editar`. |
| A6 | Evento interno | Publicar `MembresiaPagadaEvent` vía Spring `ApplicationEventPublisher` cuando pase a PAGADO — sin consumidor aún; permite que HU-C se enchufe sin refactor. **Ubicación**: `core-service/src/main/java/com/gymadmin/core/domain/event/MembresiaPagadaEvent.java` (nuevo paquete `domain/event/` — no hay precedentes en el servicio; se establece la convención aquí). Publicación desde el caso de uso en `application/service/`. |
| A7 | Cache Redis | **N/A en esta HU** — `core-service` **no tiene** cache Redis hoy (grep confirmado: no hay `RedisTemplate` ni `CacheManager`). Cuando se introduzca la capa de cache en otra HU, será su responsabilidad invalidar las claves relevantes. Aquí no se crea infra de cache (evita scope creep). |
| A8 | Índice | **Parcial**: `WHERE estado_pago='PENDIENTE' AND eliminado=false`. Reduce tamaño ~99% porque la mayoría son PAGADO. Prefijo `idx_` para consistencia con el baseline (los índices actuales de `core.membresias` son `idx_membresias_*`). |
| A9 | Códigos de razón en `validar-acceso` | El endpoint hoy devuelve códigos **cortos** (`sin_membresia`, `membresia_congelada`, `membresia_vencida`, `accesos_agotados`) que `attendance-service` propaga como `ForbiddenException` y la PWA/Kiosko traducen por clave i18n. **Se añaden dos códigos nuevos**: `pago_pendiente` y `membresia_rechazada`. El texto largo NO va al backend — vive en el i18n del frontend. |

---

## 4. Criterios de aceptación

### 4.1 Modelo de datos (`core.membresias`)

Se agrega **una story nueva** `db/scripts/YYYYMM_GYM-XXX/` (no se edita la baseline `202605_GYM-001`).

**Columnas nuevas**:
```sql
ALTER TABLE core.membresias
  ADD COLUMN estado_pago         VARCHAR(20) NOT NULL DEFAULT 'PAGADO'
    CHECK (estado_pago IN ('PENDIENTE','PAGADO')),
  ADD COLUMN fecha_eliminacion   TIMESTAMPTZ NULL,
  ADD COLUMN eliminado_por       INT NULL,
  ADD COLUMN motivo_eliminacion  VARCHAR(30) NULL
    CHECK (motivo_eliminacion IN (
      'SOCIO_CAMBIO_OPINION','ERROR_DE_VENTA','DUPLICADA','DATOS_INCORRECTOS','OTRO'
    ));
```

**Columnas que se aflojan** (para permitir PENDIENTE sin fechas):
```sql
ALTER TABLE core.membresias
  ALTER COLUMN fecha_inicio DROP NOT NULL,
  ALTER COLUMN fecha_fin    DROP NOT NULL;
```

**Constraint de consistencia**:
```sql
ALTER TABLE core.membresias
  ADD CONSTRAINT ck_membresias_fechas_por_estado_pago CHECK (
    (estado_pago = 'PENDIENTE' AND fecha_inicio IS NULL AND fecha_fin IS NULL)
    OR
    (estado_pago = 'PAGADO' AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL)
  );
```

**Constraint de rechazo** (motivo obligatorio cuando `eliminado = true`):
```sql
ALTER TABLE core.membresias
  ADD CONSTRAINT ck_membresias_motivo_si_eliminado CHECK (
    (eliminado = FALSE AND motivo_eliminacion IS NULL AND fecha_eliminacion IS NULL AND eliminado_por IS NULL)
    OR
    (eliminado = TRUE AND motivo_eliminacion IS NOT NULL AND fecha_eliminacion IS NOT NULL AND eliminado_por IS NOT NULL)
  );
```

**Backfill**: los registros existentes ya tienen `eliminado = false` y sus fechas OK; solo hay que asegurar que `estado_pago = 'PAGADO'` quede fijado antes de agregar el CHECK (el `DEFAULT 'PAGADO'` en la sentencia de `ADD COLUMN` lo hace en el mismo movimiento).

**Índice parcial** para el dashboard:
```sql
CREATE INDEX idx_membresias_pendientes_por_compania
  ON core.membresias(id_compania, creacion_fecha DESC)
  WHERE estado_pago = 'PENDIENTE' AND eliminado = FALSE;
```

### 4.2 Interacción con la columna `estado` existente ⚠️

La tabla ya tiene `estado IN ('activa','vencida','congelada','anulada')`. Ambas columnas coexisten con reglas claras:

| Situación | `estado` | `estado_pago` | `eliminado` |
|-----------|----------|---------------|-------------|
| Venta pagada al momento (admin, hoy) | `activa` | `PAGADO` | `false` |
| Venta pendiente desde PWA (futuro) | `activa` | `PENDIENTE` | `false` |
| Pendiente rechazada por staff | `activa` | `PENDIENTE` | `true` |
| Membresía vencida naturalmente | `vencida` | `PAGADO` | `false` |
| Membresía anulada por dueño | `anulada` | `PAGADO` | `false` |

**Consecuencias**:
- El **job de vencimiento** (`cron 00:10`) debe **ignorar** membresías con `estado_pago = 'PENDIENTE'` — no cambia a `vencida` una que nunca fue pagada.
- `validar-acceso` rechaza aunque `estado = 'activa'` si `estado_pago = 'PENDIENTE'` o `eliminado = true`.
- Reportería de "socios activos" cuenta: `estado = 'activa' AND estado_pago = 'PAGADO' AND eliminado = false`.

### 4.3 Creación desde admin (comportamiento actual conservado)

- El staff crea desde el panel admin → `estado_pago = 'PAGADO'` por defecto.
- `fecha_inicio` = hoy (comportamiento actual).
- `fecha_fin` = hoy + duración (comportamiento actual).

### 4.4 Endpoint de creación acepta PENDIENTE (preparación HU-B)

- `POST /api/v1/clientes/{id}/membresias` recibe campo opcional `estado_pago` (default `'PAGADO'`).
- Cuando llega `estado_pago = 'PENDIENTE'`:
  - `fecha_inicio` y `fecha_fin` se guardan **NULL** en BD (validado por el CHECK).
  - No se dispara evento hacia billing ni notificaciones.
  - Se valida: **si el cliente ya tiene otra membresía con `estado_pago='PENDIENTE'` y `eliminado=false`, responder `409 Conflict`** (N8: máximo 1 pendiente simultánea).
- **Ajuste a la validación existente**: hoy `MembresiaService.venderMembresia` invoca `findActivaByIdClienteAndIdCompania` y lanza `ConflictException` si encuentra cualquier membresía activa. Esta validación debe **relajarse** para permitir el caso "1 PAGADA vigente + 1 PENDIENTE de renovación anticipada" (N8):
  - Si se está creando una **PAGADA**: bloquear si ya hay otra `PAGADA` activa (comportamiento actual, sin cambio).
  - Si se está creando una **PENDIENTE**: bloquear solo si ya hay otra `PENDIENTE` viva; ignorar la existencia de una `PAGADA` activa.
- Endpoint queda listo para que la PWA lo consuma en HU-B; no hay UI que lo dispare en esta HU.

### 4.5 `validar-acceso` bloquea membresías pendientes

- **Verificado contra código**: `attendance-service` **no** consulta `core.membresias` directamente — llama vía HTTP a `core-service`'s `GET /api/v1/membresias/validar-acceso` y propaga el campo `razon` como `ForbiddenException` (ver `AsistenciaService.registrarPorQr/registrarPorApp/registrarManual`). Toda la lógica de bloqueo por `estado_pago`/`eliminado` **vive en core-service**.
- El endpoint de core-service `GET /api/v1/membresias/validar-acceso` debe rechazar si la membresía activa está en `estado_pago = 'PENDIENTE'` o `eliminado = true`.
- **Contrato del endpoint** (crítico — respeta el patrón existente): la razón se devuelve como **código corto** en el campo `razon`, NO como texto largo. Los códigos actuales son `sin_membresia`, `membresia_congelada`, `membresia_vencida`, `accesos_agotados`. Se añaden:
  - `pago_pendiente` — cuando `estado_pago = 'PENDIENTE'`.
  - `membresia_rechazada` — cuando `eliminado = true`.
- El texto amigable *("Membresía pendiente de pago, acércate a recepción")* vive en el i18n del frontend (PWA de socio y kiosko de asistencia), **NO en el backend**.
- **Prerequisito bloqueante**: la query `MembresiaR2dbcRepository.findActivaByIdClienteAndIdCompania` hoy **NO filtra por `eliminado = false`** (verificado en código actual). Sin este filtro, una membresía rechazada seguirá apareciendo como "activa" y `validar-acceso` no la bloqueará. El filtro es tarea de esta HU (ver §5).
- Tests de integración: caso PENDIENTE, caso ELIMINADA, caso PAGADO (regresión positiva).

### 4.6 Confirmar pago (nuevo endpoint)

- `POST /api/v1/membresias/{id}/confirmar-pago`.
- Permiso requerido: `membresias:confirmar_pago`.
- Efecto:
  - `estado_pago` → `'PAGADO'`.
  - `fecha_inicio` = hoy.
  - `fecha_fin` = hoy + duración de la membresía.
- **Idempotencia**: si ya está `PAGADO`, retornar `200 OK` con el recurso actual sin re-calcular fechas (retry-safe).
- **Conflicto**: si `eliminado = true`, retornar `409 Conflict`.
- Publica `MembresiaPagadaEvent` en el bus interno de Spring (`ApplicationEventPublisher`). No hay consumidor todavía. Paquete `domain/event/` (ver A6).
- **Cache Redis**: no aplica en esta HU — `core-service` no tiene cache. Cuando se introduzca cache en otra HU, la invalidación será su responsabilidad.

**TODO FACTURACION**: cuando se retome billing-service, un `@EventListener` en billing consumirá `MembresiaPagadaEvent` para emitir factura electrónica.

### 4.7 Rechazar pendiente (soft-delete)

- `POST /api/v1/membresias/{id}/rechazar`.
- Permiso requerido: `membresias:confirmar_pago`.
- Solo aplica si `estado_pago = 'PENDIENTE'` (si es `PAGADO`, responder `409 Conflict` — anular una pagada requiere flujo distinto).
- Body:
  ```json
  { "motivo_eliminacion": "SOCIO_CAMBIO_OPINION" }
  ```
- Validación: `motivo_eliminacion` debe estar en el catálogo cerrado (§4.1).
- Efecto: `eliminado = true`, `fecha_eliminacion = now()`, `eliminado_por = usuario_actual`, `motivo_eliminacion = <recibido>`.
- Todas las consultas por defecto excluyen `eliminado = true` (ver §5 para el listado exhaustivo de queries a modificar).
- **Cache Redis**: no aplica (misma justificación que §4.6).

### 4.8 Dashboard admin — sección "Ventas pendientes"

- Nueva vista (ubicación exacta a definir con `ui-ux-designer`) tipo **"Pipeline de ventas"**, separada del dashboard principal de "socios activos".
- Lista membresías `estado_pago = 'PENDIENTE'` y `eliminado = false` de la compañía del usuario logueado.
- Columnas: cliente, tipo de membresía, precio, fecha de creación, tiempo pendiente (relativo tipo *"hace 2 días"*).
- Acciones por fila:
  - **Marcar como pagada** → `POST /confirmar-pago`, toast success, refresca lista.
  - **Rechazar** → dialog con select de `motivo_eliminacion` (5 opciones), `POST /rechazar`, toast, refresca.
- Filtros: search por nombre de cliente.
- Empty state: *"No hay ventas pendientes de cobro"*.
- i18n en `es` y `en`.

### 4.9 Permisos

Nuevo permiso `membresias:confirmar_pago` en `seguridad.permisos`, que cubre las dos acciones (confirmar y rechazar). El frontend lo consume vía `usePermission('membresias:confirmar_pago')` (§5.5).

#### 4.9.1 Realidad multi-tenant verificada contra código

- **Estructura real de la tabla** (`db/scripts/202605_GYM-001/ddl/25_create_table_seguridad_permisos.sql`):
  ```
  seguridad.permisos (id, id_compania, id_sucursal, nombre, descripcion, modulo)
  UNIQUE (id_compania, nombre)
  ```
  El nombre completo del permiso vive en `nombre` (ejemplo del baseline: `'membresias:leer'`, `modulo = 'core'`). **No hay columnas `recurso` ni `accion`** — todo va en `nombre`.
- **Permisos por compañía**: la unicidad es `(id_compania, nombre)`. Cada gimnasio tiene su propia fila del permiso. **No existe hoy un catálogo global**.
- **Roles NO estandarizados por nombre**: `seguridad.roles` es también por-tenant (`UNIQUE (id_compania, nombre)`) y se crea vía `RolApplicationService` cuando el gimnasio se onboardea. Los nombres `PROPIETARIO`/`ADMINISTRADOR`/`RECEPCION` **no están garantizados** — cada gym decide cómo llamar a sus roles. Grep confirmado: cero coincidencias en `.sql` y `auth-service`.
- **Único seed baseline**: `61_insert_seed_usuario_root.sql` crea el rol `SUPER_ADMIN` **solo** para la compañía Sistema (RUC `0000000000001`) y le asigna todos los permisos con `INSERT INTO seguridad.rol_permisos SELECT v_id_rol, id FROM seguridad.permisos WHERE id_compania = v_id_compania`.

#### 4.9.2 Estrategia elegida — pragmática

**Fuera de scope**: convertir `seguridad.permisos` en catálogo global. Ese refactor implica migrar la tabla, actualizar todos los adapters de auth-service y romper la unicidad actual — se difiere a la HU-G (§7).

**En scope**: seed backfill del permiso **por cada compañía existente** en `tenant.companias`. NO se vincula automáticamente a roles — el dueño de cada gym lo asigna manualmente desde la UI "Editar rol" a los roles equivalentes a *propietario*/*administrador*/*recepción* (los nombres que use en su organización).

#### 4.9.3 SQL del seed (backfill de compañías existentes)

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
  'membresias:confirmar_pago',
  'Confirmar o rechazar el pago de una membresía',
  'core'
FROM tenant.companias c
WHERE NOT EXISTS (
  SELECT 1 FROM seguridad.permisos p
  WHERE p.id_compania = c.id
    AND p.nombre = 'membresias:confirmar_pago'
);
```

Notas:
- Idempotente vía `NOT EXISTS` (Liquibase re-corre el mismo changeSet? no, pero blindaje barato).
- `id_sucursal` sigue la convención del baseline (sucursal principal, con fallback a "primera por id" si algún tenant no tiene principal marcada).
- **Sí se seedea también para la compañía Sistema** (RUC `0000000000001`), lo que hace que `SUPER_ADMIN` lo herede automáticamente en un flujo posterior o vía re-ejecución del bloque `INSERT INTO rol_permisos SELECT v_id_rol, id FROM permisos ...` — pero **este seed no toca `rol_permisos` de ningún rol de compañías reales** (ver §4.9.4).

#### 4.9.4 Compañías nuevas (post-migración)

Esta HU **no** modifica el flujo de onboarding de compañías nuevas. Consecuencia: cuando se cree una compañía nueva después de aplicar esta migración, el permiso **no** existirá para ella hasta que un flujo posterior lo cree.

- Riesgo bajo: la creación de compañías en producción es infrecuente y hoy no está automatizada al 100% (verificar en `auth-service` el flujo de creación).
- Mitigación aceptada: incluir tarea en §7 (HU-G) para (a) convertir a catálogo global o (b) añadir hook en `RolApplicationService`/`CompaniaOnboardingService` que auto-cree este permiso al crear la compañía.

#### 4.9.5 Vinculación rol ↔ permiso

- **NO** se crean filas automáticas en `seguridad.rol_permisos` para roles de compañías reales (los nombres no están garantizados).
- El dueño de cada gym asigna manualmente el permiso desde la UI "Editar rol" (auth-service-frond-end).
- Verificación post-seed: `GET /me/permissions` debe devolver el permiso una vez que el usuario tiene un rol vinculado en `rol_permisos` (comportamiento existente, sin cambios).

---

## 5. Servicios afectados

### 5.1 `gym-administrator/db`
- Nueva story `YYYYMM_GYM-003_estado_pago_membresias/` (siguiente número disponible tras `202607_GYM-002-fix-ci-validada-comment`) con:
  - ALTERs a `core.membresias` (columnas `estado_pago`, `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`; drop NOT NULL de fechas; CHECKs de consistencia).
  - Índice parcial `idx_membresias_pendientes_por_compania`.
  - Seed del permiso `membresias:confirmar_pago` en `seguridad.permisos` **por cada compañía existente** (backfill según §4.9.3). **NO se toca `rol_permisos`** — el dueño asigna manualmente desde la UI.
- Append en `db/scripts/main-changelog.yml` con el include del `partial-changelog.yml` de la nueva story.
- Actualizar `docs/gym-administrator/architecture/database-schema.md`.

### 5.2 `core-service` (backend principal — mayor superficie de cambios)

**Persistencia** (verificado en código actual — pendientes reales):
- `infrastructure/adapter/out/persistence/entity/MembresiaEntity.java`: mapear la columna existente `eliminado` (hoy no está mapeada aunque exista en BD) y las cuatro nuevas (`estado_pago`, `fecha_eliminacion`, `eliminado_por`, `motivo_eliminacion`).
- `infrastructure/adapter/out/persistence/repository/MembresiaR2dbcRepository.java`: agregar `AND eliminado = false` a **todas** las queries que hoy no lo tienen. Como mínimo:
  - `findByIdCliente`
  - `findActivaByIdClienteAndIdCompania`
  - `findActivasParaJob` (usada por el job de vencimiento)
- Nuevas queries: listado de PENDIENTES por compañía (para el dashboard), búsqueda de PENDIENTE viva por cliente (para la validación de N8).

**Dominio y aplicación**:
- `domain/model/Membresia.java`: agregar campos `estadoPago`, `eliminado`, `fechaEliminacion`, `eliminadoPor`, `motivoEliminacion`.
- `domain/event/MembresiaPagadaEvent.java` (nuevo paquete — no existía): record inmutable con `idMembresia`, `idCliente`, `idCompania`, `montoPagado`, `fechaConfirmacion`.
- `application/service/MembresiaService.venderMembresia`: relajar la validación de "1 activa por cliente" según §4.4 (permitir PAGADA + PENDIENTE simultáneas; solo bloquear una segunda PENDIENTE viva).
- Nuevo caso de uso `ConfirmarPagoMembresiaUseCase` (idempotente, publica `MembresiaPagadaEvent`).
- Nuevo caso de uso `RechazarMembresiaUseCase` (soft-delete con auditoría).
- `application/service/ClienteStatusJobService.procesarCliente`: **modificar** para ignorar membresías con `estado_pago = 'PENDIENTE'` — hoy re-evalúa con `findActivaByIdClienteAndIdCompania` y podría marcar al cliente como `vencido` si la única "activa" es una PENDIENTE sin fechas. Filtrar por `estado_pago = 'PAGADO'` explícitamente.

**Web**:
- `infrastructure/adapter/in/web/MembresiaController.java`: agregar `POST /membresias/{id}/confirmar-pago` y `POST /membresias/{id}/rechazar`. Verificar en el grep de rutas actuales que no colisionan.
- **Modificar** el endpoint existente `GET /api/v1/membresias/validar-acceso` (que attendance-service consume vía HTTP): agregar la lógica que devuelve `razon = 'pago_pendiente'` cuando `estado_pago = 'PENDIENTE'` y `razon = 'membresia_rechazada'` cuando `eliminado = true`. Esta rama debe evaluarse **antes** de las razones existentes (`sin_membresia`, `membresia_congelada`, `membresia_vencida`, `accesos_agotados`) porque una PENDIENTE eliminada no debe reportarse como "sin membresía".
- DTOs: `ConfirmarPagoResponse`, `RechazarMembresiaRequest` (con `motivoEliminacion` obligatorio y validado contra el enum).

**Tests**: unitarios de los tres casos de uso; ITs de los dos endpoints nuevos, del `venderMembresia` con `estado_pago=PENDIENTE`, del job ignorando PENDIENTES, y de la query filtrada por `eliminado=false`.

### 5.3 `attendance-service`
- **Cero cambios de lógica**: attendance no consulta `core.membresias`. Solo llama vía HTTP a `core-service`'s `/validar-acceso` (`CoreServiceClient.validarAcceso`) y propaga el `razon` recibido como `ForbiddenException` (`AsistenciaService.registrarPorQr/registrarPorApp/registrarManual`). Los nuevos códigos `pago_pendiente` y `membresia_rechazada` se propagan automáticamente sin cambio de código.
- **Opcional**: agregar tests de integración con `WebClient` mockeado que verifiquen que ambos códigos nuevos se propagan como `ForbiddenException` con el `razon` correcto. Fuera de scope si no aporta señal (los tests existentes ya cubren la propagación de `razon`).
- **No hay cambios de DDL, ni de repository, ni de service, ni de controller** en este servicio.

### 5.4 `auth-service` (schema `seguridad`)
- Sin cambios de código si el seed vive en la story de BD (§5.1). Verificar únicamente que el endpoint `GET /me/permissions` devuelve el permiso nuevo para los 3 roles.

### 5.5 `auth-service-frond-end`
- Sección "Ventas pendientes" (§4.8), botones de acción, dialog de motivo con select de 5 opciones, i18n `es`/`en`, guard por `usePermission('membresias:confirmar_pago')`.
- Nuevas claves i18n para códigos de razón `pago_pendiente` y `membresia_rechazada` (si el kiosko de asistencia vive aquí; si no, en `gym-member-pwa`).

---

## 6. Riesgos y consideraciones

- **Backfill trivial pero verificar**: el `DEFAULT 'PAGADO'` en el `ADD COLUMN` popula las filas existentes; validar en pre-prod que ningún registro queda en NULL antes de aplicar los CHECKs.
- **ITs preexistentes rotos**: `attendance-service` y `core` tienen ITs preexistentes rotos en master (Mockito strict / cleanDatabase FK / validar-acceso 400). Al ejecutar tests hay que separar regresiones reales del ruido existente. Correr `*Test` unitarios primero y reproducir el estado de master antes de culpar cambios de esta HU.
- **Endpoint sin consumidor**: crear con `PENDIENTE` queda expuesto pero sin UI que lo dispare en esta HU. No se protege con feature flag (YAGNI); se documenta como "reservado para HU-B".
- **Job de vencimiento**: `ClienteStatusJobService.procesarCliente` debe filtrar por `estado_pago = 'PAGADO'` — de lo contrario marcará al cliente como `vencido` cuando su única "activa" sea una PENDIENTE sin fechas. Riesgo real, cambio pequeño y fácil de olvidar (ya listado en §5.2).
- **`eliminado` reusado**: la columna existe en el DDL pero **no está mapeada** en `MembresiaEntity` y **no se filtra** en las queries actuales. Riesgo cero de colisión semántica (nadie la lee hoy) pero riesgo alto de olvidar el filtro en alguna query nueva — auditar todas las queries del repositorio como parte de esta HU (§5.2).
- **Sin cache Redis**: `core-service` no tiene cache. La invalidación queda diferida a la HU que introduzca esa capa. No se agrega infraestructura de cache en esta HU (evita scope creep).
- **Deuda técnica documentada para HU-C**: cuando se cree `core.pagos`, `estado_pago` de `core.membresias` pasará a **derivarse** (`SELECT CASE WHEN EXISTS(pago) THEN 'PAGADO' ELSE 'PENDIENTE' END`) o se marcará como legacy. No se arrastra esta duplicación en silencio.

---

## 7. Pendientes / follow-ups

- **HU-B — Compra desde PWA del socio** (`gym-member-pwa`): pantalla de tipos disponibles, confirmación de compra que crea `estado_pago = 'PENDIENTE'`, vista "mis compras pendientes" con instrucciones para pagar en recepción. Autorización de la creación desde JWT tipo `ClienteToken` — definir en HU-B.
- **HU-C — Integración billing-service**: `@EventListener` que consume `MembresiaPagadaEvent`, emisión de factura electrónica SRI, creación de tabla `core.pagos` y migración de `estado_pago` a valor derivado.
- **HU-D — Notificación al staff** (opcional): WhatsApp o in-app cuando entra una venta pendiente nueva desde la PWA.
- **HU-E — Métrica de conversión** (opcional): dashboard con "% pendientes que se convirtieron a PAGADAS" por mes, con desglose por `motivo_eliminacion` para las rechazadas.
- **HU-F — Pasarelas de pago** (opcional, largo plazo): Stripe / PayPhone / Kushki para pago automático desde PWA con webhook que confirma.
- **HU-G — Catálogo global de permisos + auto-seed en onboarding de compañías** (deuda arquitectónica descubierta en esta HU): evaluar cambiar `seguridad.permisos` a catálogo global (quitar `id_compania`/`id_sucursal`, mover multi-tenancy solo a `rol_permisos`). Alternativa menos invasiva: añadir un hook en `CompaniaOnboardingService` (auth-service) que auto-cree todos los permisos vigentes para cada compañía nueva. Sin resolver esto, cada permiso nuevo que agreguemos en el futuro repite el mismo patrón de backfill manual + gap para compañías creadas después de la migración.

---

## 8. Referencias

- DDL actual de la tabla: [`gym-administrator/db/scripts/202605_GYM-001/ddl/31_create_table_core_membresias.sql`](../../../gym-administrator/db/scripts/202605_GYM-001/ddl/31_create_table_core_membresias.sql)
- Endpoint actual de venta de membresía: [core-service/api/membresias.md](../../core-service/api/membresias.md)
- Modelo actual de `core.membresias`: [architecture/database-schema.md](../architecture/database-schema.md)
- Convenciones de migración: [`gym-administrator/CLAUDE.md`](../../../gym-administrator/CLAUDE.md)
- Componente admin actual de venta: `auth-service-frond-end/src/ui/features/core/components/VenderMembresiaModal.tsx`
