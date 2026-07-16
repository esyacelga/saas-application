# Esquema de Base de Datos — Sistema de Gestión de Gimnasio

> **ESTADO:** 🟡 Arquitectura y modelo de negocio. Mezcla lo implementado con lo planeado; para detalle de implementación verificar contra el código. Ver [../../STATUS.md](../../STATUS.md).
## Arquitectura Multicompañía / Multisucursal

**Totales actuales (verificados 2026-07-10 contra `gym-administrator/db/scripts/202605_GYM-001/`):** **69 tablas** distribuidas en **12 schemas** (`saas`, `identidad`, `tenant`, `core`, `asistencia`, `finanzas`, `marketing`, `inventario`, `config`, `seguridad`, `sri`, `facturacion`).

Todas las tablas están consolidadas en una única story Liquibase `202605_GYM-001/`, dividida en tres subcarpetas por dominio:

- **`ddl/`** — 10 schemas base (saas, identidad, tenant, core, asistencia, finanzas, marketing, inventario, config, seguridad) y sus 46 tablas de plataforma / operación.
- **`ddl-facturacion/`** — Schemas `sri` (6 catálogos oficiales) y `facturacion` (17 tablas de comprobantes electrónicos y flujo SRI Ecuador).
- **`ddl-freemium/`** — Extras para REQ-SAAS-001: `tenant.pagos_pendientes_validacion`, `saas.config_plataforma` y seed inicial de datos bancarios.

Cada tabla se define **una única vez** en su `CREATE TABLE` (sin scripts `ALTER` posteriores) — una base de datos vacía se construye en una sola pasada de Liquibase. Los cambios REQ-SAAS-001 (Sub-fase 1.1) previamente vivían en la story `202608_GYM-003` (retirada); hoy están incorporados directamente al CREATE de las tablas afectadas: `saas.planes`, `tenant.companias`, `tenant.compania_planes`, `tenant.notificaciones_suscripcion`, `saas.actividad_plataforma`.

---

## Convención Global de Multitenancy

```
╔══════════════════════════════════════════════════════════════════════╗
║  REGLA: Todas las tablas contienen id_compania e id_sucursal (INT)  ║
║  Sin restricción FK — el filtrado es responsabilidad de la app      ║
╚══════════════════════════════════════════════════════════════════════╝

  id_compania  →  referencia lógica a companias.id   (sin FK constraint)
  id_sucursal  →  referencia lógica a sucursales.id  (sin FK constraint)

  EXCEPCIÓN — Tablas globales de plataforma (sin id_compania / id_sucursal):
  ┌──────────────────────────────────────────────────────────────────┐
  │  planes · caracteristicas · plan_caracteristicas                 │
  │  personas · usuarios_app                                         │
  │                                                                  │
  │  Son catálogos del sistema SaaS o identidades globales           │
  │  que trascienden a cualquier compañía                            │
  └──────────────────────────────────────────────────────────────────┘
```

---

## D0 — Infraestructura Multitenancy

```
╔══════════════════════╗   1         N   ╔═══════════════════════════╗
║      companias       ║═════════════════║        sucursales          ║
╠══════════════════════╣                 ╠═══════════════════════════╣
║ PK id                ║                 ║ PK id                     ║
║    nombre            ║                 ║ FK id_compania            ║  ← FK real
║    ruc               ║                 ║    nombre                 ║
║    logo_url          ║                 ║    direccion              ║
║    telefono          ║                 ║    es_principal BOOL      ║
║    whatsapp          ║                 ║    qr_token        ← NUEVO║  QR en la puerta
║    correo            ║                 ║    qr_token_expira ← NUEVO║  rotación periódica
╚══════════════════════╝                 ╚═══════════════════════════╝
                                 es_principal = TRUE → sucursal creada
                                 automáticamente al registrar la compañía.
                                 Si no agrega más sucursales, opera con esta.

  Flujo de entrada por QR:
  ┌──────────────────────────────────────────────────────────┐
  │  1. Gym muestra QR en la puerta (= sucursales.qr_token)  │
  │  2. Cliente lo escanea con su app móvil                  │
  │  3. App identifica al cliente por su sesión activa       │
  │  4. Servidor valida membresía → INSERT asistencia        │
  │  5. qr_token rota periódicamente (anti-suplantación)     │
  └──────────────────────────────────────────────────────────┘

  ══  FK real con restricción referencial
  ──  referencia lógica sin FK (resto de tablas)

  companias  →  cada gimnasio/negocio registrado en la plataforma
  sucursales →  cada sede física de una compañía (depende de companias)
```

---

## D0.5 — Planes & Características (SaaS)

```
 CATÁLOGO GLOBAL (sin id_compania / id_sucursal)
 ─────────────────────────────────────────────────────────────────────────

╔══════════════════════════════════╗   ╔═══════════════════════════╗
║             planes               ║   ║    plan_caracteristicas   ║
╠══════════════════════════════════╣<──╠═══════════════════════════╣
║ PK id                            ║   ║ FK id_plan                ║
║    nombre                        ║   ║ FK id_caracteristica      ║
║    descripcion                   ║   ╚══════════════╤════════════╝
║    precio_mensual                ║                  │ N
║    codigo                UNIQUE  ║  FREE/TRIAL/     ▼ 1
║    duracion_dias                 ║  PREMIUM   ╔═══════════════════════════╗
║    es_gratuito           BOOL    ║            ║     caracteristicas       ║
║    plan_degradacion_id           ║  self-FK   ╠═══════════════════════════╣
║    max_sucursales                ║            ║ PK id                     ║
║    max_clientes_activos          ║            ║    codigo         UNIQUE  ║
║    max_staff                     ║            ║    nombre                 ║
║    moneda                        ║  ISO4217   ║    modulo                 ║
║    es_legacy             BOOL    ║            ║    activo                 ║
║    activo                        ║            ╚═══════════════════════════╝
╚══════════╤═══════════════════════╝
           │ 1
           ▼ N
╔══════════════════════════════════╗
║       compania_planes            ║
╠══════════════════════════════════╣
║ PK id                            ║
║ FK id_compania                   ║  ← FK real a tenant.companias
║ FK id_plan                       ║  ← FK real a saas.planes
║    fecha_inicio                  ║
║    fecha_fin                     ║
║    dias_gracia                   ║  días extra tras vencimiento
║    fecha_ultimo_pago             ║
║    motivo_suspension             ║
║    estado                        ║  activo|en_gracia|vencido|
║                                  ║  suspendido|cancelado|programado|
║                                  ║  reemplazada
║    tipo_cambio                   ║  nuevo|renovacion|upgrade|downgrade|
║                                  ║  degradacion_auto|cancelacion|suspension
║    sobre_limite          BOOL    ║  RN-06 modo tolerancia
║    sobre_limite_hasta            ║  fecha límite de gracia (30 días)
║    causa_degradacion             ║  vencimiento|pago_rechazado|
║                                  ║  cancelacion_manual|suspension_root
║    id_compania_plan_orig         ║  FK al registro que reemplaza
║    credito_monto                 ║  valor prorrateado acreditado
╚═══════════════╤══════════════════╝
                │ 1
                ▼ N
╔══════════════════════════╗
║    pagos_suscripcion     ║
╠══════════════════════════╣
║ PK id                    ║
║ FK id_compania_plan      ║  → compania_planes.id
║    monto                 ║
║    fecha_pago            ║
║    periodo_desde         ║
║    periodo_hasta         ║
║    metodo_pago           ║  efectivo|transferencia|tarjeta
║    tipo_pago  ← NUEVO    ║  pago_completo|diferencia_upgrade|
║                          ║  credito_downgrade|renovacion
║    estado                ║  pagado|fallido|pendiente
║    referencia            ║  número de comprobante
╚══════════════════════════╝

 Ciclo de vida del estado en compania_planes:
 ┌──────────────────────────────────────────────────────────────────────┐
 │                                                                      │
 │           ┌──────────────────────────────────────────┐              │
 │           │           upgrade inmediato               │              │
 │           │    activo ──────────────> cancelado       │              │
 │           │      │                        │           │              │
 │           │      │                   nueva fila       │              │
 │           │      │                   tipo=upgrade     │              │
 │           │      │                   estado=activo    │              │
 │           └──────────────────────────────────────────┘              │
 │                  │                                                   │
 │           downgrade programado                                       │
 │                  │                                                   │
 │                  ├──> nueva fila tipo=downgrade estado=programado    │
 │                  │    (se activa cuando llega su fecha_inicio)       │
 │                  │                                                   │
 │             vence fecha_fin                                          │
 │                  │                                                   │
 │                  ▼                                                   │
 │             en_gracia ──(vence dias_gracia)──> vencido              │
 │                  │                                                   │
 │           pago registrado                                            │
 │                  │                                                   │
 │                  ▼                                                   │
 │           activo (nueva fila, tipo=renovacion)                      │
 │                  │                                                   │
 │           admin  ▼                                                   │
 │             suspendido                                               │
 └──────────────────────────────────────────────────────────────────────┘

 Comportamiento por estado:
 ┌─────────────┬─────────────────┬──────────────────────────────────────┐
 │ estado      │ acceso módulos  │ qué ve el usuario                    │
 ├─────────────┼─────────────────┼──────────────────────────────────────┤
 │ activo      │ completo        │ normal                               │
 │ programado  │ ninguno (futuro)│ invisible hasta su fecha_inicio      │
 │ en_gracia   │ completo        │ banner: "renueva en X días"          │
 │ vencido     │ ninguno         │ pantalla de renovación               │
 │ cancelado   │ ninguno         │ reemplazado por upgrade              │
 │ suspendido  │ ninguno         │ contactar soporte                    │
 └─────────────┴─────────────────┴──────────────────────────────────────┘

 Ejemplos de caracteristicas.codigo:
 ┌──────────────────────────────────────────────────────────────────┐
 │  PLAN BÁSICO          │  PLAN PREMIUM                           │
 │  ─────────────────    │  ──────────────────────────────────     │
 │  dashboard            │  + finanzas                            │
 │  clientes             │  + promociones_beneficios              │
 │  membresias           │                                        │
 │  asistencia           │  (futuro Plan Enterprise)              │
 │  mensajeria           │  + reportes_avanzados                  │
 │                       │  + api_integraciones                   │
 └──────────────────────────────────────────────────────────────────┘

 Notificaciones de suscripción (configurable por compañía):
 ─────────────────────────────────────────────────────────────────────

╔══════════════════════════════════╗   1     N   ╔══════════════════════════════╗
║   compania_planes                ║─────────────║  notificaciones_suscripcion  ║
╠══════════════════════════════════╣             ╠══════════════════════════════╣
║ PK id                            ║             ║ PK id                        ║
╚══════════════════════════════════╝             ║ FK id_compania_plan          ║
                                                 ║ FK id_compania               ║  filtrado multi-tenant
                                                 ║    tipo                      ║  VENCIMIENTO_TRIAL|
                                                 ║                              ║  VENCIMIENTO_PREMIUM|
                                                 ║                              ║  PAGO_RECHAZADO|...
                                                 ║    dias_antes                ║  ej: 15, 7, 3, 1, 0
                                                 ║    canal                     ║  email|whatsapp|banner
                                                 ║    estado                    ║  pendiente|enviado|
                                                 ║                              ║  fallido|reintentar
                                                 ║    fecha_envio               ║
                                                 ║    proximo_intento           ║  backoff exponencial
                                                 ║    intentos                  ║  contador de retries
                                                 ║    ultimo_error              ║  msg SMTP para debug
                                                 ║    descartado_at             ║  solo canal=banner
                                                 ╚══════════════════════════════╝

╔══════════════════════════════════╗
║   notif_buckets_globales         ║  ← GLOBAL, story 202607_GYM-003
╠══════════════════════════════════╣  Configuración de días de aviso
║ PK destinatario VARCHAR(10)      ║  (Fase 6 WhatsApp vencimiento)
║    CHECK IN('socio','dueno')     ║
║    dias_previo INT DEFAULT 3     ║  1..30, el aviso del día 0 es fijo
║    activo BOOLEAN DEFAULT TRUE   ║  cuando FALSE → solo día 0
║    modificado_por, modificado_at ║  auditoría
║    creacion_fecha, creacion_user ║
╚══════════════════════════════════╝
 Seed idempotente: socio=3, dueno=3 (ambos activos, día 0 siempre fijo)
 Los jobs (dueño y socio) leen dias_previo dinámicamente; fallback 3 si ausente

╔══════════════════════════════════╗
║   config_notif_suscripcion       ║  ← una fila por umbral configurado
╠══════════════════════════════════╣
║ PK (id_compania, dias_antes)     ║
║  · id_compania                   ║  referencia lógica a companias
║    dias_antes                    ║  INT — ej: 5 | 3 | 1 (configurable)
║    canal                         ║  email|whatsapp|ambos
║    activo                        ║  BOOL
╚══════════════════════════════════╝

 Ejemplo de configuración por compañía:
 ┌─────────────┬────────────┬──────────────┬────────┐
 │ id_compania │ dias_antes │ canal        │ activo │
 ├─────────────┼────────────┼──────────────┼────────┤
 │ 1           │ 5          │ whatsapp     │ true   │
 │ 1           │ 3          │ ambos        │ true   │
 │ 1           │ 1          │ ambos        │ true   │
 └─────────────┴────────────┴──────────────┴────────┘
 → Cambiar 3 por 4: UPDATE config_notif_suscripcion
     SET dias_antes = 4 WHERE id_compania = 1 AND dias_antes = 3;

 Job diario — query con umbrales dinámicos:
   SELECT cp.id, c.whatsapp, c.correo, cn.dias_antes, cn.canal
   FROM compania_planes cp
   JOIN companias c ON c.id = cp.id_compania
   JOIN config_notif_suscripcion cn
        ON cn.id_compania = cp.id_compania AND cn.activo = TRUE
   WHERE cp.estado = 'activo'
     AND cp.fecha_fin = CURRENT_DATE + cn.dias_antes * INTERVAL '1 day'
     AND NOT EXISTS (
       SELECT 1 FROM notificaciones_suscripcion ns
       WHERE ns.id_compania_plan = cp.id
         AND ns.dias_antes = cn.dias_antes
         AND ns.estado = 'enviado'
     );
   → Envía → inserta en notificaciones_suscripcion → no vuelve a enviar

 Flujo de validación en la app:
   request → middleware → compania_planes (estado IN activo|en_gracia)
   → plan_caracteristicas → caracteristicas.codigo == modulo_solicitado
   → permitir / denegar acceso
```

---

## D0.7 — Freemium & Auditoría SaaS (REQ-SAAS-001)

Extras persistidos para el flujo Free / Trial / Premium. Se distribuyen entre `ddl/` (tablas base tocadas por Sub-fase 1.1) y `ddl-freemium/` (tablas 100 % nuevas).

```
╔══════════════════════════════════════════╗
║   tenant.pagos_pendientes_validacion     ║  ← ddl-freemium/01
╠══════════════════════════════════════════╣
║ PK id                                    ║
║ FK id_compania         → tenant.companias║
║ FK id_plan_destino     → saas.planes     ║
║    monto / moneda                        ║
║    fecha_reporte / fecha_transferencia   ║
║    comprobante_url     (Cloudinary raw)  ║
║    comprobante_hash    SHA-256           ║
║    banco_origen / referencia             ║
║    hash_idempotencia   UNIQUE parcial    ║  previene reportes duplicados
║    estado              pendiente|        ║
║                        aprobado|rechazado║
║    motivo_rechazo                        ║
║ FK aprobado_por        → saas.usuarios_  ║
║                          plataforma      ║
║    fecha_aprobacion                      ║
║    activacion_programada   BOOL          ║  RN-05: Trial → Premium diferido
║    factura_emitida_id  (reservado fase 4)║
╚══════════════════════════════════════════╝
  Bandeja root/soporte: idx_pagos_pendientes_estado_fecha
  Historial por tenant: idx_pagos_pendientes_compania

╔══════════════════════════════════════════╗
║   saas.config_plataforma                 ║  ← ddl-freemium/02 + seed en 03
╠══════════════════════════════════════════╣
║ PK clave        (dot-notation)           ║
║    valor        (texto — parseado en app)║
║    descripcion                           ║
║ FK modificado_por → saas.usuarios_       ║
║                     plataforma           ║
║    modificado_at                         ║
╚══════════════════════════════════════════╝
  Uso: datos bancarios (pago.banco.nombre, .titular, ...),
  precios, umbrales editables sin redeploy.

╔══════════════════════════════════════════╗
║   saas.actividad_plataforma              ║  ← ddl/67 (extendida por D3)
╠══════════════════════════════════════════╣
║ PK id           BIGINT                   ║
║    tipo_evento  (TRIAL_ACTIVADO,         ║
║                 PAGO_APROBADO,           ║
║                 PLAN_DEGRADADO_AUTO, ...)║
║    modulo / entidad_id / entidad_nombre  ║
║    detalle      JSONB                    ║  payload estructurado
║    usuario      (login string)           ║
║ FK id_compania  → tenant.companias       ║  NULL = evento de plataforma
║    id_usuario_actor                      ║  sin FK dura (dos tablas posibles)
║    tipo_actor   OWNER|ROOT|STAFF|SISTEMA ║
║    ip           INET                     ║
║    fecha                                 ║
╚══════════════════════════════════════════╝
  Índices: fecha DESC, modulo, tipo_evento, usuario,
           (id_compania, fecha DESC) WHERE id_compania IS NOT NULL.
```

**Notas sobre planes en `saas.planes`:**

- `codigo` es el identificador estable usado por el backend (`FREE`, `TRIAL`, `PREMIUM`); `id` no es API-visible.
- `duracion_dias = NULL` significa plan permanente (Free); `60` es Trial; `30` es Premium mensual.
- `plan_degradacion_id` es un self-FK: al vencer un plan sin pago, se degrada automáticamente al plan referenciado (típicamente Free).
- `max_sucursales / max_clientes_activos / max_staff` son los hard limits validados por `LimiteRecursoService` con `pg_advisory_xact_lock()`; `NULL = ilimitado`.
- `es_legacy = TRUE` esconde el plan del catálogo público (`GET /planes/publicos`), preservando contratos migrados.
- `tenant.companias` incluye ahora `trial_usado BOOLEAN` + `fecha_trial_usado TIMESTAMPTZ` (RN-01, flag irrevocable) y campos fiscales SRI (`nombre_comercial`, `dir_matriz`, `obligado_contabilidad`, `contribuyente_especial`).

---

## D0.8 — Identidad Global

```
 TABLA GLOBAL (sin id_compania / id_sucursal)
 ─────────────────────────────────────────────────────────────────────

╔══════════════════════╗   1         N   ╔══════════════════════════════╗
║       personas       ║═════════════════║       usuarios_app           ║
╠══════════════════════╣                 ╠══════════════════════════════╣
║ PK id                ║                 ║ PK id                        ║
║    ci    (UNIQUE)    ║                 ║ FK id_persona                ║
║    nombre            ║                 ║  · id_compania  (sin FK)     ║
║    telefono          ║                 ║    login                     ║
║    correo            ║                 ║    password_hash             ║
║    foto_url          ║                 ║    requiere_cambio_pwd       ║
║    fecha_nacimiento  ║                 ║    activo                    ║
╚══════════╤═══════════╝                 ║    token_recuperacion        ║
           │ 1                           ║    ultimo_acceso             ║
           │                             ╚══════════════════════════════╝
           │                             UNIQUE (id_persona, id_compania)
           │ 1                           una credencial por gym por persona
           │
           ▼ N                ╔══════════════════════════════╗
╔══════════════════════╗      ║         biometria            ║  ← futuro
║      clientes        ║      ╠══════════════════════════════╣
╠══════════════════════╣      ║ PK id                        ║
║ PK id                ║      ║ FK id_persona                ║
║ FK id_persona        ║      ║  · id_compania  (sin FK)     ║
║  · id_compania       ║      ║    tipo  huella|facial|iris  ║
║  · id_sucursal       ║      ║    hash_datos  BYTEA         ║  encriptado AES-256
║    peso_kg           ║      ║    activo                    ║
║    estado            ║      ╚══════════════════════════════╝
║    codigo_carnet     ║      UNIQUE (id_persona, id_compania, tipo)
╚══════════════════════╝      un perfil biométrico por sensor por gym
UNIQUE (id_persona, id_compania)
datos gym-específicos por compañía

  codigo_carnet = código impreso en carnet físico — solo para búsqueda manual.
  La entrada al gym NO usa el carnet: usa el QR de la sucursal o biometría.
```

---

## D1 — Clientes & Membresías

```
╔══════════════════╗            ╔══════════════════════════════════╗
║    clientes      ║            ║        tipos_membresia           ║
╠══════════════════╣            ╠══════════════════════════════════╣
║ PK id            ║            ║ PK id                            ║
║ FK id_persona    ║            ║  · id_compania                   ║
║  · id_compania   ║            ║  · id_sucursal                   ║
║  · id_sucursal   ║            ║    nombre                        ║
║    estado        ║            ║    modo_control ← NUEVO          ║  calendario|accesos
║    codigo_carnet ║            ║    duracion_tipo / duracion_valor║
╚════════╤═════════╝            ║    dias_acceso  ← NUEVO          ║  solo si modo=accesos
         │ 1                    ║    precio                        ║
         │ 1                    ╚══════════════════╤═══════════════╝
         │                                         │ 1
         │                                         ▼ N
         │              ╔══════════════════════════════════╗
         │              ║          membresias              ║
         └─────────────>╠══════════════════════════════════╣
              N         ║ PK id                            ║
                        ║  · id_compania                   ║
                        ║  · id_sucursal                   ║
                        ║ FK id_cliente                    ║
                        ║ FK id_tipo_membresia             ║
                        ║ FK id_metodo_pago                ║
                        ║    fecha_inicio / fecha_fin      ║
                        ║    dias_acceso_total  ← NUEVO    ║  NULL si modo=calendario
                        ║    estado                        ║
                        ╚═══════════════╤══════════════════╝
                                        │ 1
                                        ▼ N
                        ╔══════════════════════════════════╗
                        ║        congelamientos            ║
                        ╠══════════════════════════════════╣
                        ║ PK id                            ║
                        ║  · id_compania                   ║
                        ║  · id_sucursal                   ║
                        ║ FK id_membresia                  ║
                        ║    motivo                        ║
                        ║    fecha_inicio / fecha_fin      ║
                        ║    retroactivo       BOOL        ║
                        ║    documento_respaldo            ║
                        ║ FK aprobado_por                  ║
                        ║    fecha_aprobacion              ║
                        ╚══════════════════════════════════╝
```

---

## D2 — Asistencia & Mensajería

```
╔══════════════════╗   1       N   ╔═══════════════════════╗
║    clientes      ║───────────────║      asistencias      ║
╠══════════════════╣               ╠═══════════════════════╣
║ PK id            ║               ║ PK id                 ║
╚════════╤═════════╝               ║  · id_compania        ║
         │ 1                       ║  · id_sucursal        ║
         │                         ║ FK id_cliente         ║
         ▼ N                       ║ FK id_membresia       ║
╔═════════════════════╗            ║    fecha / hora       ║
║    mensajes_log     ║            ║    metodo_registro    ║
╠═════════════════════╣            ╚═══════════════════════╝
║ PK id               ║
║  · id_compania      ║        ╔════════════════════════╗
║  · id_sucursal      ║   N    ║  plantillas_mensajes   ║
║ FK id_cliente       ║<───────╠════════════════════════╣
║ FK id_plantilla     ║        ║ PK id                  ║
║    tipo             ║        ║  · id_compania         ║
║    canal            ║        ║  · id_sucursal         ║
║    estado           ║        ║    tipo                ║
║    fecha_programada ║        ║    contenido           ║
╚═════════════════════╝        ╚════════════════════════╝

  metodo_registro: qr_cliente → cliente escaneó QR de la puerta con su app
                   biometrico → sensor biométrico identificó al cliente
                   manual     → recepcionista registró manualmente

  Tipos de mensaje: motivacional | ausencia_2d | recuperacion_5d |
                    recuperacion_10d | recuperacion_15d |
                    vencimiento_3d | vencimiento_hoy
```

---

## D3 — Finanzas

```
╔═════════════════════╗   1     N   ╔═════════════════════╗
║  categorias_ingreso ║─────────────║      ingresos       ║
╠═════════════════════╣             ╠═════════════════════╣
║ PK id               ║             ║ PK id               ║
║  · id_compania      ║             ║  · id_compania      ║
║  · id_sucursal      ║             ║  · id_sucursal      ║
║    nombre           ║             ║ FK id_categoria     ║
╚═════════════════════╝             ║ FK id_membresia (*)  ║
                                    ║    monto / fecha    ║
╔═════════════════════╗   1     N   ╚═════════════════════╝
║  categorias_egreso  ║─────────────╔═════════════════════╗
╠═════════════════════╣             ║      egresos        ║
║ PK id               ║             ╠═════════════════════╣
║  · id_compania      ║             ║ PK id               ║
║  · id_sucursal      ║             ║  · id_compania      ║
║    nombre           ║             ║  · id_sucursal      ║
╚═════════════════════╝             ║ FK id_categoria     ║
                                    ║    monto / fecha    ║
                                    ╚═════════════════════╝
(*) FK opcional — solo si el ingreso viene de una membresía
```

---

## D4 — Promociones & Beneficios

```
╔══════════════════════╗   1     N   ╔══════════════════════════╗
║     promociones      ║─────────────║   cliente_promociones    ║
╠══════════════════════╣             ╠══════════════════════════╣
║ PK id                ║             ║ PK id                    ║
║  · id_compania       ║             ║  · id_compania           ║
║  · id_sucursal       ║             ║  · id_sucursal           ║
║    nombre            ║             ║ FK id_promocion          ║
║    tipo              ║             ║ FK id_cliente            ║
║    activa            ║             ║ FK id_membresia (*)      ║
╚══════════════════════╝             ║    estado                ║
                                     ╚══════════════════════════╝

╔══════════════════════╗   1     N   ╔══════════════════════════╗
║   reglas_beneficios  ║─────────────║   cliente_beneficios    ║
╠══════════════════════╣             ╠══════════════════════════╣
║ PK id                ║             ║ PK id                    ║
║  · id_compania       ║             ║  · id_compania           ║
║  · id_sucursal       ║             ║  · id_sucursal           ║
║    meses_sin_faltas  ║             ║ FK id_regla              ║
║    tipo_beneficio    ║             ║ FK id_cliente            ║
║    descripcion       ║             ║    estado                ║
╚══════════════════════╝             ╚══════════════════════════╝

  Reglas base: 1 mes → 10% desc. | 3 meses → Nutricionista | 6 meses → Trofeo
```

---

## D5 — Usuarios & Permisos

```
╔════════════╗           ╔════════════════╗           ╔═══════════════╗
║   roles    ║  N      N ║  rol_permisos  ║  N      N ║   permisos   ║
╠════════════╣<──────────╠════════════════╣──────────>╠═══════════════╣
║ PK id      ║           ║  · id_compania ║           ║ PK id        ║
║  · id_comp ║           ║  · id_sucursal ║           ║  · id_comp   ║
║  · id_suc  ║           ║ FK id_rol      ║           ║  · id_suc    ║
║    nombre  ║           ║ FK id_permiso  ║           ║    nombre    ║
╚═════╤══════╝           ╚════════════════╝           ║    modulo    ║
      │ 1                                             ╚═══════════════╝
      ▼ N
╔═══════════════════╗   1         N   ╔════════════════════════╗
║     usuarios      ║─────────────────║   bitacora_accesos     ║
╠═══════════════════╣                 ╠════════════════════════╣
║ PK id             ║                 ║ PK id                  ║
║  · id_compania    ║                 ║  · id_compania         ║
║  · id_sucursal    ║                 ║  · id_sucursal         ║
║ FK id_rol         ║                 ║ FK id_usuario          ║
║ FK id_persona     ║  ←─ nombre y foto viven en personas
║    correo         ║                 ║    modulo              ║
║    activo         ║                 ║    accion              ║
╚═══════════════════╝                 ║    entidad_id          ║
UNIQUE (id_persona, id_compania)      ║    detalle (JSON)      ║
un empleado una cuenta por gym        ║    fecha               ║
                                      ╚════════════════════════╝
```

---

## D7 — Inventario & Ventas

```
╔═════════════════════╗         ╔══════════════════════╗
║ categorias_producto ║  1    N ║      productos       ║
╠═════════════════════╣─────────╠══════════════════════╣
║ PK id               ║         ║ PK id                ║
║  · id_compania      ║         ║  · id_compania       ║
║  · id_sucursal      ║         ║  · id_sucursal       ║
║    nombre           ║         ║ FK id_categoria      ║
╚═════════════════════╝         ║ FK id_proveedor      ║
                                ║    nombre            ║
╔═════════════════════╗         ║    codigo_barras     ║
║     proveedores     ║  1    N ║    precio_venta      ║
╠═════════════════════╣─────────║    precio_costo      ║
║ PK id               ║         ║    stock_minimo      ║
║  · id_compania      ║         ╚══════════╤═══════════╝
║  · id_sucursal      ║                    │ 1
║    nombre           ║          ┌─────────┴──────────┐
║    telefono         ║          ▼ 1                  ▼ N
╚═════════════════════╝  ╔═══════════════╗  ╔═══════════════════════╗
                         ║  inventario   ║  ║ movimientos_inventario║
                         ╠═══════════════╣  ╠═══════════════════════╣
                         ║ PK id         ║  ║ PK id                 ║
                         ║  · id_compania║  ║  · id_compania        ║
                         ║  · id_sucursal║  ║  · id_sucursal        ║
                         ║ FK id_producto║  ║ FK id_producto        ║
                         ║  stock_actual ║  ║ FK id_proveedor (*)   ║
                         ╚═══════════════╝  ║ FK id_venta (*)       ║
UNIQUE(id_producto,                         ║    tipo               ║
       id_compania,                         ║    cantidad           ║
       id_sucursal)                         ║    fecha              ║
                                            ╚═══════════════════════╝
                                            tipo: entrada|venta|ajuste|devolucion

╔══════════════════════╗  1    N  ╔══════════════════════╗
║        ventas        ║──────────║    detalle_ventas    ║
╠══════════════════════╣          ╠══════════════════════╣
║ PK id                ║          ║ PK id                ║
║  · id_compania       ║          ║ FK id_venta          ║
║  · id_sucursal       ║          ║ FK id_producto       ║
║ FK id_cliente (*)    ║          ║    cantidad          ║
║ FK id_metodo_pago    ║          ║    precio_unitario   ║
║    total             ║          ║    subtotal          ║
║    fecha             ║          ╚══════════════════════╝
╚══════════╤═══════════╝
           │ genera
           ▼
╔══════════════════════╗
║       ingresos       ║  ← tabla existente
╠══════════════════════╣
║ FK id_venta (nuevo)  ║
╚══════════════════════╝
(*) opcional
```

---

## D6 — Configuración del Sistema

```
╔══════════════════════╗         ╔════════════════════╗
║      gym_config      ║         ║    metodos_pago    ║
╠══════════════════════╣         ╠════════════════════╣
║ PK clave             ║         ║ PK id              ║
║  · id_compania       ║         ║  · id_compania     ║
║  · id_sucursal       ║         ║  · id_sucursal     ║
║    valor             ║         ║    nombre          ║
║    tipo              ║         ║    activo          ║
╚══════════════════════╝         ╚═════════╤══════════╝
                                           │ 1
  PK compuesta: (clave, id_compania,       ▼ N
                 id_sucursal)       ╔════════════════════╗
                                    ║    membresias      ║
  Ejemplos de claves:               ╠════════════════════╣
  gym_nombre, gym_logo_url,         ║ FK id_metodo_pago  ║
  whatsapp_token, dias_alerta,      ╚════════════════════╝
  meses_congelar_max
```

---

## Vista Global de Relaciones

```
  ┌───────────────────────────────────────────────────────────────────────────┐
  │ saas   — Plataforma SaaS global (Free / Trial / Premium)                  │
  │                                                                           │
  │  caracteristicas ──< plan_caracteristicas >── planes ──┐                  │
  │                                                        │ self-FK          │
  │                                                        └── plan_degradacion│
  │  usuarios_plataforma (root/soporte)                                       │
  │  actividad_plataforma (auditoría cross-servicio)                          │
  │  config_plataforma    (clave-valor runtime: datos bancarios, umbrales)    │
  │                                                        │                  │
  └────────────────────────────────────────────────────────┼──────────────────┘
                                                           │ 1
                                                           ▼ N
  ┌───────────────────────────────────────────────────────────────────────────┐
  │ tenant — Multitenancy                                                     │
  │                                                                           │
  │  companias ═══════> sucursales                                            │
  │      │  (trial_usado + datos SRI)                                         │
  │      ├── compania_planes ──> pagos_suscripcion                           │
  │      │       │  (sobre_limite, causa_degradacion, self-FK a orig)         │
  │      │       └──> notificaciones_suscripcion  (email + banner + retries)  │
  │      │                                                                    │
  │      ├── pagos_pendientes_validacion   (RN-08 transferencias)             │
  │      └── config_notif_suscripcion      (umbrales por compañía)            │
  │                                                                           │
  │  id_compania · id_sucursal presentes en todas las tablas operativas       │
  └───────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │ identidad — Personas / credenciales globales                     │
  │  personas ──> usuarios_app  (login por gym)                      │
  │           └── biometria     (huella, facial — futuro)            │
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │ config — Configuración por gym                                   │
  │  gym_config (clave-valor)   metodos_pago                         │
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │ core — Clientes & Membresías [Plan Free/Trial/Premium]           │
  │  tipos_membresia ──> membresias <── clientes                    │
  │                          │                                       │
  │                    congelamientos                                │
  └──────────────────────────┬───────────────────────────────────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
  ┌───────────────┐  ┌───────────────────┐  ┌──────────────────────┐
  │ asistencia    │  │ finanzas          │  │ marketing            │
  │ [Free/Trial/  │  │ [Premium]         │  │ [Premium]            │
  │  Premium]     │  │                   │  │                      │
  │ asistencias   │  │ ingresos ◄──┐    │  │ promociones          │
  │ mensajes_log  │  │ egresos     │    │  │ cliente_promoc.      │
  │ plantillas    │  │ categorias  │    │  │ reglas_benef.        │
  └───────────────┘  └─────────────┼────┘  │ cliente_benef.       │
                                   │        └──────────────────────┘
  ┌────────────────────────────────┼──────────────────────────────┐
  │ inventario         [Premium]   │                              │
  │  categorias_producto  productos  proveedores                  │
  │  stock  movimientos_inventario                                │
  │  ventas ──> detalle_ventas ────┘ (genera ingreso)             │
  └───────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │ seguridad — Usuarios staff / permisos                            │
  │  roles ── rol_permisos ── permisos                              │
  │   └──> usuarios ──> bitacora_accesos                            │
  │        └──> refresh_tokens                                       │
  └──────────────────────────────────────────────────────────────────┘

  ┌───────────────────────────────────────────────────────────────────┐
  │ sri — Catálogos oficiales (solo lectura, seeded)                  │
  │ facturacion — Facturación electrónica SRI Ecuador  [billing]      │
  │                                                                   │
  │  config_sri ─┬─ certificados ─┬─ puntos_emision ─ secuenciales   │
  │              │                 │                                  │
  │              └── comprobantes ─┼── comprobantes_detalle           │
  │                    │           ├── comprobante_detalle_impuestos  │
  │                    │           ├── comprobante_impuestos_totales  │
  │                    │           ├── comprobante_pagos              │
  │                    │           ├── comprobante_info_adicional     │
  │                    │           └── notas_credito_referencias      │
  │                    ├── envios_sri ── cola_envio                   │
  │                    ├── notificaciones_receptor                    │
  │                    ├── anulaciones                                │
  │                    └── (reportes_ats mensuales)                   │
  │                                                                   │
  │  finanzas.ingresos.id_comprobante ──> facturacion.comprobantes    │
  │  (por eso ddl-facturacion/ se ejecuta antes que ingresos)         │
  └───────────────────────────────────────────────────────────────────┘
```

---

## Resumen de Tablas (70 tablas en 12 schemas)

Verificado 2026-07-16 contando los `CREATE TABLE` de `db/scripts/202605_GYM-001/{ddl,ddl-facturacion,ddl-freemium}/` + story `202607_GYM-003` (notif_buckets_globales). Distribución por schema:

| Schema | Tablas | Propósito |
|--------|:---:|-----------|
| `saas` | 7 | Catálogos globales de plataforma (planes, características, usuarios root, actividad, config, notif buckets) |
| `identidad` | 3 | Personas globales, credenciales de app móvil, biometría |
| `tenant` | 7 | Compañías, sucursales, suscripciones, pagos, notificaciones, pagos pendientes |
| `core` | 4 | Clientes, tipos de membresía, membresías, congelamientos |
| `asistencia` | 3 | Asistencias, plantillas de mensajes, log de mensajes |
| `config` | 2 | gym_config (clave-valor por tenant), métodos de pago |
| `seguridad` | 6 | Roles, permisos, rol_permisos, usuarios staff, bitácora, refresh tokens |
| `finanzas` | 4 | Categorías/ingresos y categorías/egresos |
| `marketing` | 4 | Promociones, beneficios y sus asignaciones a clientes |
| `inventario` | 7 | Categorías, proveedores, productos, stock, ventas, detalle, movimientos |
| `sri` | 6 | Catálogos oficiales SRI Ecuador (comprobantes, identificación, formas de pago, impuestos, IVA, anulación NC) |
| `facturacion` | 17 | Facturación electrónica: config, certificados, secuenciales, comprobantes + detalle + impuestos + pagos + info adicional + NC + envíos + cola + notificaciones + anulaciones + ATS |
| **Total** | **69** | |

**Detalle por dominio** (Tenant = ¿la tabla filtra por `id_compania`?):

| # | Tabla | Dominio | PK | FK principales | Tenant |
|---|---|---|---|---|---|
| 1 | `saas.planes` | SaaS Global | `id` | `plan_degradacion_id` (self-FK) | No |
| 2 | `saas.caracteristicas` | SaaS Global | `id` | — | No |
| 3 | `saas.plan_caracteristicas` | SaaS Global | `(id_plan, id_caract)` | `id_plan`, `id_caracteristica` | No |
| 4 | `saas.usuarios_plataforma` | SaaS Global | `id` | — | No |
| 5 | `saas.actividad_plataforma` | SaaS Global | `id` | `id_compania` (nullable) | Parcial |
| 6 | `saas.config_plataforma` | SaaS Global | `clave` | `modificado_por` | No |
| 7 | `saas.notif_buckets_globales` | SaaS Global | `destinatario` | — | No |
| 8 | `identidad.personas` | Identidad Global | `id` | — | No |
| 9 | `identidad.usuarios_app` | Identidad Global | `id` | `id_persona` (**FK real**) | No |
| 10 | `identidad.biometria` | Identidad Global | `id` | `id_persona` (**FK real**) | No |
| 11 | `tenant.companias` | Multitenancy | `id` | — | No |
| 12 | `tenant.sucursales` | Multitenancy | `id` | `id_compania` (**FK real**) | Parcial |
| 13 | `tenant.compania_planes` | Multitenancy | `id` | `id_compania`, `id_plan`, `id_compania_plan_orig` (self-FK) | No |
| 14 | `tenant.pagos_suscripcion` | Multitenancy | `id` | `id_compania_plan` (**FK real**) | No |
| 15 | `tenant.config_notif_suscripcion` | Multitenancy | `(id_compania, dias_antes)` | — | No |
| 16 | `tenant.notificaciones_suscripcion` | Multitenancy | `id` | `id_compania_plan`, `id_compania` (**FK real**) | No |
| 17 | `tenant.pagos_pendientes_validacion` | Multitenancy | `id` | `id_compania`, `id_plan_destino`, `aprobado_por` | No |
| 18 | `core.clientes` | Clientes | `id` | `id_persona` (**FK real**) | Sí |
| 19 | `core.tipos_membresia` | Membresías | `id` | — | Sí |
| 20 | `core.membresias` | Membresías | `id` | `id_cliente`, `id_tipo_membresia`, `id_metodo_pago` | Sí |
| 21 | `core.congelamientos` | Membresías | `id` | `id_membresia` | Sí |
| 22 | `asistencia.asistencias` | Asistencia | `id` | `id_cliente`, `id_membresia` | Sí |
| 23 | `asistencia.plantillas_mensajes` | Mensajería | `id` | — | Sí |
| 24 | `asistencia.mensajes_log` | Mensajería | `id` | `id_cliente`, `id_plantilla` | Sí |
| 25 | `finanzas.categorias_ingreso` | Finanzas | `id` | — | Sí |
| 26 | `finanzas.ingresos` | Finanzas | `id` | `id_categoria`, `id_membresia`, `id_venta`, `id_comprobante` | Sí |
| 27 | `finanzas.categorias_egreso` | Finanzas | `id` | — | Sí |
| 28 | `finanzas.egresos` | Finanzas | `id` | `id_categoria` | Sí |
| 29 | `marketing.promociones` | Promociones | `id` | — | Sí |
| 30 | `marketing.cliente_promociones` | Promociones | `id` | `id_promocion`, `id_cliente`, `id_membresia` | Sí |
| 31 | `marketing.reglas_beneficios` | Beneficios | `id` | — | Sí |
| 32 | `marketing.cliente_beneficios` | Beneficios | `id` | `id_regla`, `id_cliente` | Sí |
| 33 | `seguridad.roles` | Usuarios | `id` | — | Sí |
| 34 | `seguridad.permisos` | Usuarios | `id` | — | Sí |
| 35 | `seguridad.rol_permisos` | Usuarios | `(id_rol, id_permiso)` | `id_rol`, `id_permiso` | Sí |
| 36 | `seguridad.usuarios` | Usuarios | `id` | `id_rol`, `id_persona` | Sí |
| 37 | `seguridad.bitacora_accesos` | Usuarios | `id` | `id_usuario` | Sí |
| 38 | `seguridad.refresh_tokens` | Usuarios | `id` | `id_usuario` | Sí |
| 39 | `config.gym_config` | Configuración | `(clave, id_compania, id_sucursal)` | — | Sí |
| 40 | `config.metodos_pago` | Configuración | `id` | — | Sí |
| 41 | `inventario.categorias_producto` | Inventario | `id` | — | Sí |
| 42 | `inventario.proveedores` | Inventario | `id` | — | Sí |
| 43 | `inventario.productos` | Inventario | `id` | `id_categoria`, `id_proveedor` | Sí |
| 44 | `inventario.stock` | Inventario | `id` | `id_producto` | Sí |
| 45 | `inventario.movimientos_inventario` | Inventario | `id` | `id_producto`, `id_proveedor`, `id_venta` | Sí |
| 46 | `inventario.ventas` | Inventario | `id` | `id_cliente`, `id_metodo_pago` | Sí |
| 47 | `inventario.detalle_ventas` | Inventario | `id` | `id_venta`, `id_producto` | Sí |
| 48 | `sri.tipos_comprobante` | SRI Catálogo | `codigo` | — | No |
| 49 | `sri.tipos_identificacion_comprador` | SRI Catálogo | `codigo` | — | No |
| 50 | `sri.formas_pago` | SRI Catálogo | `codigo` | — | No |
| 51 | `sri.tipos_impuesto` | SRI Catálogo | `codigo` | — | No |
| 52 | `sri.tarifas_iva` | SRI Catálogo | `codigo` | — | No |
| 53 | `sri.motivos_anulacion_nc` | SRI Catálogo | `codigo` | — | No |
| 54 | `facturacion.config_sri` | Facturación | `id_compania` | — | Sí |
| 55 | `facturacion.certificados` | Facturación | `id` | `id_compania` | Sí |
| 56 | `facturacion.puntos_emision` | Facturación | `id` | `id_compania`, `id_sucursal` | Sí |
| 57 | `facturacion.secuenciales` | Facturación | `id` | `id_punto_emision` | Sí |
| 58 | `facturacion.comprobantes` | Facturación | `id` | `id_compania`, `id_sucursal`, `id_punto_emision`, `id_cliente` | Sí |
| 59 | `facturacion.comprobantes_detalle` | Facturación | `id` | `id_comprobante` | Sí |
| 60 | `facturacion.comprobante_detalle_impuestos` | Facturación | `id` | `id_detalle` | Sí |
| 61 | `facturacion.comprobante_impuestos_totales` | Facturación | `id` | `id_comprobante` | Sí |
| 62 | `facturacion.comprobante_pagos` | Facturación | `id` | `id_comprobante` | Sí |
| 63 | `facturacion.comprobante_info_adicional` | Facturación | `id` | `id_comprobante` | Sí |
| 64 | `facturacion.notas_credito_referencias` | Facturación | `id` | `id_nota_credito`, `id_comprobante_referenciado` | Sí |
| 65 | `facturacion.envios_sri` | Facturación | `id` | `id_comprobante` | Sí |
| 66 | `facturacion.cola_envio` | Facturación | `id` | `id_comprobante` | Sí |
| 67 | `facturacion.notificaciones_receptor` | Facturación | `id` | `id_comprobante` | Sí |
| 68 | `facturacion.anulaciones` | Facturación | `id` | `id_comprobante` | Sí |
| 69 | `facturacion.reportes_ats` | Facturación | `id` | `id_compania` | Sí |

> **Tenant = Sí** → tabla incluye `id_compania INT` e `id_sucursal INT` sin restricción FK.
> **Tenant = No** → tabla de plataforma / catálogo global, sin filtrado por compañía.
> **Tenant = Parcial** → tiene FK real a `tenant.companias` pero puede tener `id_compania` NULL (eventos de sistema en `saas.actividad_plataforma`) o solo `id_compania` sin `id_sucursal` (`tenant.sucursales`).

---

## Definición Completa de Tablas

> Sintaxis PostgreSQL. `SERIAL` = auto-incremental. `TIMESTAMPTZ` = timestamp con zona horaria.

---

### SaaS Global

```sql
CREATE TABLE saas.planes (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre               VARCHAR(100)  NOT NULL,
  descripcion          TEXT,
  precio_mensual       DECIMAL(10,2) NOT NULL,
  -- REQ-SAAS-001 (esquema Free / Trial / Premium)
  codigo               VARCHAR(20)   UNIQUE,        -- FREE | TRIAL | PREMIUM
  duracion_dias        INTEGER,                     -- NULL = permanente
  es_gratuito          BOOLEAN       NOT NULL DEFAULT FALSE,
  plan_degradacion_id  INT           REFERENCES saas.planes(id),  -- self-FK
  max_sucursales       INTEGER,                     -- NULL = ilimitado
  max_clientes_activos INTEGER,
  max_staff            INTEGER,
  moneda               VARCHAR(3)    NOT NULL DEFAULT 'USD',
  es_legacy            BOOLEAN       NOT NULL DEFAULT FALSE,
  activo               BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado            BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario     VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha       TIMESTAMPTZ,
  modifica_usuario     VARCHAR(150)
);

CREATE TABLE saas.caracteristicas (
  id      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo  VARCHAR(50)  NOT NULL UNIQUE,  -- usado por el middleware
  nombre  VARCHAR(100) NOT NULL,
  modulo  VARCHAR(50)  NOT NULL,
  activo  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE saas.plan_caracteristicas (
  id_plan           INT NOT NULL REFERENCES saas.planes(id),
  id_caracteristica INT NOT NULL REFERENCES saas.caracteristicas(id),
  PRIMARY KEY (id_plan, id_caracteristica)
);

-- Auditoría cross-servicio de la plataforma SaaS
CREATE TABLE saas.actividad_plataforma (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tipo_evento      VARCHAR(50)  NOT NULL,          -- TRIAL_ACTIVADO, PAGO_APROBADO, ...
  modulo           VARCHAR(50)  NOT NULL,
  entidad_id       BIGINT,
  entidad_nombre   VARCHAR(200),
  detalle          JSONB,                          -- payload estructurado por evento
  usuario          VARCHAR(200) NOT NULL,
  -- REQ-SAAS-001 (D3): actor + tenant del evento
  id_compania      INT          REFERENCES tenant.companias(id),
  id_usuario_actor INT,                            -- FK lógica (saas.usuarios_plataforma o seguridad.usuarios)
  tipo_actor       VARCHAR(20)  NOT NULL DEFAULT 'SISTEMA'
                     CHECK (tipo_actor IN ('OWNER','ROOT','STAFF','SISTEMA')),
  ip               INET,
  fecha            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Config runtime editable por root (datos bancarios, umbrales, ...)
CREATE TABLE saas.config_plataforma (
  clave            VARCHAR(100) PRIMARY KEY,
  valor            TEXT         NOT NULL,
  descripcion      TEXT,
  modificado_por   INT          REFERENCES saas.usuarios_plataforma(id),
  modificado_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema'
);
```

---

### Multitenancy

```sql
CREATE TABLE tenant.companias (
  id                     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre                 VARCHAR(150) NOT NULL,
  ruc                    VARCHAR(20)  UNIQUE,          -- nullable: auto-registro público no pide RUC (se completa en el wizard de facturación)
  logo_url               VARCHAR(255),
  telefono               VARCHAR(20),
  whatsapp               VARCHAR(20),
  correo                 VARCHAR(150),
  -- Datos fiscales SRI (para facturación electrónica)
  nombre_comercial       VARCHAR(300),
  dir_matriz             VARCHAR(300),
  obligado_contabilidad  BOOLEAN      NOT NULL DEFAULT FALSE,
  contribuyente_especial VARCHAR(10),
  -- REQ-SAAS-001 RN-01: Trial único por tenant
  trial_usado            BOOLEAN      NOT NULL DEFAULT FALSE,
  fecha_trial_usado      TIMESTAMPTZ,
  activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado              BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario       VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha         TIMESTAMPTZ,
  modifica_usuario       VARCHAR(150)
);

CREATE TABLE tenant.sucursales (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL REFERENCES tenant.companias(id),
  nombre       VARCHAR(150) NOT NULL,
  direccion    VARCHAR(255),
  es_principal BOOLEAN      NOT NULL DEFAULT FALSE,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant.compania_planes (
  id                    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania           INT          NOT NULL REFERENCES tenant.companias(id),
  id_plan               INT          NOT NULL REFERENCES saas.planes(id),
  fecha_inicio          DATE         NOT NULL,
  fecha_fin             DATE         NOT NULL,
  dias_gracia           INT          NOT NULL DEFAULT 5,
  fecha_ultimo_pago     DATE,
  motivo_suspension     TEXT,
  estado                VARCHAR(20)  NOT NULL
                          CHECK (estado IN (
                            'activo','en_gracia','vencido',
                            'suspendido','cancelado','programado',
                            'reemplazada'
                          )),
  tipo_cambio           VARCHAR(20)  NOT NULL
                          CHECK (tipo_cambio IN (
                            'nuevo','renovacion','upgrade','downgrade',
                            'degradacion_auto','cancelacion','suspension'
                          )),
  -- REQ-SAAS-001 RN-06: modo SOBRE_LIMITE y trazabilidad de degradación
  sobre_limite          BOOLEAN      NOT NULL DEFAULT FALSE,
  sobre_limite_hasta    DATE,                    -- fecha límite de gracia (30 días)
  causa_degradacion     VARCHAR(30)
                          CHECK (
                            causa_degradacion IS NULL OR
                            causa_degradacion IN (
                              'vencimiento','pago_rechazado',
                              'cancelacion_manual','suspension_root'
                            )
                          ),
  id_compania_plan_orig INT          REFERENCES tenant.compania_planes(id),
  credito_monto         DECIMAL(10,2) NOT NULL DEFAULT 0,
  eliminado             BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario      VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha        TIMESTAMPTZ,
  modifica_usuario      VARCHAR(150)
);

CREATE TABLE tenant.pagos_suscripcion (
  id                INT          PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT          NOT NULL REFERENCES tenant.compania_planes(id),
  monto             DECIMAL(10,2) NOT NULL,
  fecha_pago        DATE         NOT NULL,
  periodo_desde     DATE,
  periodo_hasta     DATE,
  metodo_pago       VARCHAR(30)
                      CHECK (metodo_pago IN ('efectivo','transferencia','tarjeta')),
  tipo_pago         VARCHAR(30)
                      CHECK (tipo_pago IN (
                        'pago_completo','diferencia_upgrade',
                        'credito_downgrade','renovacion'
                      )),
  estado            VARCHAR(20)  NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pagado','fallido','pendiente')),
  referencia        VARCHAR(100),
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant.config_notif_suscripcion (
  id_compania  INT         NOT NULL,  -- referencia lógica a tenant.companias.id
  dias_antes   INT         NOT NULL,
  canal        VARCHAR(20) NOT NULL
                 CHECK (canal IN ('email','whatsapp','ambos')),
  activo       BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_compania, dias_antes)
);

-- Extendida por REQ-SAAS-001 D1: buzón de emails + banners in-app + retries
CREATE TABLE tenant.notificaciones_suscripcion (
  id                INT         PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT         NOT NULL REFERENCES tenant.compania_planes(id),
  id_compania       INT         REFERENCES tenant.companias(id),  -- filtrado multi-tenant sin JOIN
  dias_antes        INT         NOT NULL,
  tipo              VARCHAR(40),                                  -- VENCIMIENTO_TRIAL, PAGO_RECHAZADO, ...
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('email','whatsapp','banner')),
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente','enviado','fallido','reintentar')),
  fecha_envio       TIMESTAMPTZ,
  proximo_intento   TIMESTAMPTZ,                                  -- backoff exponencial
  intentos          INTEGER      NOT NULL DEFAULT 1,
  ultimo_error      TEXT,                                          -- msg SMTP para debug
  descartado_at     TIMESTAMPTZ,                                   -- solo canal='banner'
  eliminado         BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150)
);

-- REQ-SAAS-001 D2: buzón de pagos por transferencia (RN-08)
CREATE TABLE tenant.pagos_pendientes_validacion (
  id                    INT           PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania           INT           NOT NULL REFERENCES tenant.companias(id),
  id_plan_destino       INT           NOT NULL REFERENCES saas.planes(id),
  monto                 DECIMAL(10,2) NOT NULL,
  moneda                VARCHAR(3)    NOT NULL DEFAULT 'USD',
  fecha_reporte         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  fecha_transferencia   DATE          NOT NULL,
  comprobante_url       TEXT          NOT NULL,                    -- Cloudinary raw, authenticated
  comprobante_hash      VARCHAR(64),                                -- SHA-256 del archivo
  banco_origen          VARCHAR(80),
  referencia            VARCHAR(80),
  hash_idempotencia     VARCHAR(64)   NOT NULL,                    -- SHA-256(compania|monto|fecha|ref)
  estado                VARCHAR(20)   NOT NULL DEFAULT 'pendiente'
                          CHECK (estado IN ('pendiente','aprobado','rechazado')),
  motivo_rechazo        TEXT,
  aprobado_por          INT           REFERENCES saas.usuarios_plataforma(id),
  fecha_aprobacion      TIMESTAMPTZ,
  activacion_programada BOOLEAN       NOT NULL DEFAULT FALSE,       -- RN-05 upgrade agendado
  factura_emitida_id    INT,                                        -- reservado fase 4
  eliminado             BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario      VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha        TIMESTAMPTZ,
  modifica_usuario      VARCHAR(150)
);

-- Idempotencia + bandeja + histórico por tenant
CREATE UNIQUE INDEX ux_pagos_pendientes_hash
  ON tenant.pagos_pendientes_validacion(hash_idempotencia)
  WHERE estado IN ('pendiente','aprobado');

CREATE INDEX idx_pagos_pendientes_estado_fecha
  ON tenant.pagos_pendientes_validacion(estado, fecha_reporte DESC);

CREATE INDEX idx_pagos_pendientes_compania
  ON tenant.pagos_pendientes_validacion(id_compania, fecha_reporte DESC);
```

---

### Identidad Global

```sql
CREATE TABLE personas (
  id               SERIAL       PRIMARY KEY,
  ci               VARCHAR(20)  NOT NULL UNIQUE,  -- barrera global de identidad
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  foto_url         VARCHAR(255),
  fecha_nacimiento DATE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE usuarios_app (
  id                  SERIAL       PRIMARY KEY,
  id_persona          INT          NOT NULL REFERENCES personas(id),
  id_compania         INT          NOT NULL,        -- qué gym dio estas credenciales
  login               VARCHAR(150) NOT NULL,        -- CI, correo o teléfono según gym_config
  password_hash       VARCHAR(255) NOT NULL,
  requiere_cambio_pwd BOOLEAN      NOT NULL DEFAULT TRUE,
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso       TIMESTAMPTZ,
  token_recuperacion  VARCHAR(100),
  token_expira        TIMESTAMPTZ,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (id_persona, id_compania),   -- una credencial por gym por persona
  UNIQUE (id_compania, login)         -- login único dentro del gym
);
```

---

### Clientes

```sql
CREATE TABLE clientes (
  id            SERIAL       PRIMARY KEY,
  id_persona    INT          NOT NULL REFERENCES personas(id),
  id_compania   INT          NOT NULL,  -- referencia lógica
  id_sucursal   INT          NOT NULL,  -- referencia lógica
  -- datos específicos del gym (medidas, objetivos, etc.)
  peso_kg       DECIMAL(5,2),
  altura_cm     DECIMAL(5,1),
  objetivos     TEXT,
  lesiones      TEXT,
  estado        VARCHAR(20)  NOT NULL DEFAULT 'activo'
                  CHECK (estado IN (
                    'activo','proximo_vencer','vencido',
                    'congelado','riesgo_abandono'
                  )),
  fecha_ingreso DATE         NOT NULL DEFAULT CURRENT_DATE,
  qr_codigo     VARCHAR(100) UNIQUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ,
  UNIQUE (id_persona, id_compania)  -- una relación por gym por persona
);
```

---

### Membresías

```sql
CREATE TABLE tipos_membresia (
  id             SERIAL       PRIMARY KEY,
  id_compania    INT          NOT NULL,
  id_sucursal    INT          NOT NULL,
  nombre         VARCHAR(100) NOT NULL,
  duracion_tipo  VARCHAR(20)  NOT NULL
                   CHECK (duracion_tipo IN ('dias','semanas','meses','años')),
  duracion_valor INT          NOT NULL CHECK (duracion_valor > 0),
  precio         DECIMAL(10,2) NOT NULL CHECK (precio >= 0),
  activo         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE membresias (
  id                  SERIAL        PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_cliente          INT           NOT NULL REFERENCES clientes(id),
  id_tipo_membresia   INT           NOT NULL REFERENCES tipos_membresia(id),
  id_metodo_pago      INT           REFERENCES metodos_pago(id),
  id_usuario_registro INT           REFERENCES usuarios(id),
  fecha_inicio        DATE          NOT NULL,
  fecha_fin           DATE          NOT NULL,
  precio_pagado       DECIMAL(10,2) NOT NULL,
  descuento_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0,
  estado              VARCHAR(20)   NOT NULL DEFAULT 'activa'
                        CHECK (estado IN ('activa','vencida','congelada','anulada')),
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE congelamientos (
  id                   SERIAL       PRIMARY KEY,
  id_compania          INT          NOT NULL,
  id_sucursal          INT          NOT NULL,
  id_membresia         INT          NOT NULL REFERENCES membresias(id),
  fecha_inicio         DATE         NOT NULL,
  fecha_fin            DATE,
  motivo               VARCHAR(30)
                         CHECK (motivo IN (
                           'viaje','lesion','enfermedad','voluntario','otro'
                         )),
  detalle              TEXT,
  retroactivo          BOOLEAN      NOT NULL DEFAULT FALSE,
  documento_respaldo   VARCHAR(255),              -- URL/ruta del certificado
  aprobado_por         INT          REFERENCES usuarios(id),
  fecha_aprobacion     DATE,
  id_usuario_registro  INT          REFERENCES usuarios(id),
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  -- Si es retroactivo, debe tener documento y aprobación
  CONSTRAINT chk_retroactivo CHECK (
    retroactivo = FALSE
    OR (documento_respaldo IS NOT NULL AND aprobado_por IS NOT NULL)
  )
);
```

---

### Asistencia & Mensajería

```sql
CREATE TABLE asistencias (
  id               BIGSERIAL   PRIMARY KEY,
  id_compania      INT         NOT NULL,
  id_sucursal      INT         NOT NULL,
  id_cliente       INT         NOT NULL REFERENCES clientes(id),
  id_membresia     INT         NOT NULL REFERENCES membresias(id),
  fecha            DATE        NOT NULL,
  hora_entrada     TIME        NOT NULL,
  metodo_registro  VARCHAR(20) NOT NULL DEFAULT 'qr'
                     CHECK (metodo_registro IN ('qr','manual'))
);
CREATE INDEX idx_asistencias_cliente_fecha ON asistencias(id_compania, id_cliente, fecha);

CREATE TABLE plantillas_mensajes (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  tipo         VARCHAR(50)  NOT NULL
                 CHECK (tipo IN (
                   'motivacional','ausencia_2d',
                   'recuperacion_5d','recuperacion_10d','recuperacion_15d',
                   'vencimiento_3d','vencimiento_hoy'
                 )),
  nombre       VARCHAR(100) NOT NULL,
  contenido    TEXT         NOT NULL,  -- soporta variables: {nombre}, {dias}
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE mensajes_log (
  id                BIGSERIAL   PRIMARY KEY,
  id_compania       INT         NOT NULL,
  id_sucursal       INT         NOT NULL,
  id_cliente        INT         NOT NULL REFERENCES clientes(id),
  id_plantilla      INT         REFERENCES plantillas_mensajes(id),
  tipo              VARCHAR(50) NOT NULL,
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('whatsapp','email','llamada')),
  contenido         TEXT        NOT NULL,
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente','enviado','fallido')),
  fecha_programada  TIMESTAMPTZ,
  fecha_envio       TIMESTAMPTZ,
  id_usuario_envio  INT         REFERENCES usuarios(id)
);
```

---

### Finanzas

```sql
CREATE TABLE categorias_ingreso (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE ingresos (
  id                  SERIAL        PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES categorias_ingreso(id),
  id_membresia        INT           REFERENCES membresias(id),
  id_venta            INT           REFERENCES ventas(id),  -- si el ingreso viene de una venta
  monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0),
  descripcion         TEXT,
  fecha               DATE          NOT NULL DEFAULT CURRENT_DATE,
  id_usuario_registro INT           REFERENCES usuarios(id),
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE categorias_egreso (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE egresos (
  id                  SERIAL        PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES categorias_egreso(id),
  monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0),
  descripcion         TEXT,
  fecha               DATE          NOT NULL DEFAULT CURRENT_DATE,
  id_usuario_registro INT           REFERENCES usuarios(id),
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```

---

### Promociones & Beneficios

```sql
CREATE TABLE promociones (
  id                   SERIAL        PRIMARY KEY,
  id_compania          INT           NOT NULL,
  id_sucursal          INT           NOT NULL,
  nombre               VARCHAR(150)  NOT NULL,
  tipo                 VARCHAR(30)   NOT NULL
                         CHECK (tipo IN ('2x1','porcentaje','servicio_extra','regalo')),
  descripcion          TEXT,
  condiciones          TEXT,
  descuento_porcentaje DECIMAL(5,2),
  max_personas         INT,
  fecha_inicio         DATE,
  fecha_fin            DATE,
  activa               BOOLEAN       NOT NULL DEFAULT TRUE,
  aplica_a_fidelidad   BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE cliente_promociones (
  id                SERIAL      PRIMARY KEY,
  id_compania       INT         NOT NULL,
  id_sucursal       INT         NOT NULL,
  id_cliente        INT         NOT NULL REFERENCES clientes(id),
  id_promocion      INT         NOT NULL REFERENCES promociones(id),
  id_membresia      INT         REFERENCES membresias(id),
  fecha_asignacion  DATE        NOT NULL DEFAULT CURRENT_DATE,
  fecha_uso         DATE,
  estado            VARCHAR(20) NOT NULL DEFAULT 'asignada'
                      CHECK (estado IN ('asignada','usada','expirada'))
);

CREATE TABLE reglas_beneficios (
  id                SERIAL        PRIMARY KEY,
  id_compania       INT           NOT NULL,
  id_sucursal       INT           NOT NULL,
  meses_sin_faltas  INT           NOT NULL CHECK (meses_sin_faltas > 0),
  tipo_beneficio    VARCHAR(30)   NOT NULL
                      CHECK (tipo_beneficio IN ('descuento','servicio','regalo')),
  descripcion       VARCHAR(255)  NOT NULL,
  valor             DECIMAL(10,2),
  activo            BOOLEAN       NOT NULL DEFAULT TRUE,
  UNIQUE (id_compania, id_sucursal, meses_sin_faltas)
);

CREATE TABLE cliente_beneficios (
  id              SERIAL      PRIMARY KEY,
  id_compania     INT         NOT NULL,
  id_sucursal     INT         NOT NULL,
  id_cliente      INT         NOT NULL REFERENCES clientes(id),
  id_regla        INT         NOT NULL REFERENCES reglas_beneficios(id),
  fecha_otorgado  DATE        NOT NULL DEFAULT CURRENT_DATE,
  estado          VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                    CHECK (estado IN ('pendiente','aplicado','expirado'))
);
```

---

### Usuarios & Permisos

```sql
CREATE TABLE roles (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(50)  NOT NULL,
  descripcion  VARCHAR(255),
  UNIQUE (id_compania, nombre)
);

CREATE TABLE permisos (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  descripcion  VARCHAR(255),
  modulo       VARCHAR(50)  NOT NULL,
  UNIQUE (id_compania, nombre)
);

CREATE TABLE rol_permisos (
  id_compania   INT NOT NULL,
  id_sucursal   INT NOT NULL,
  id_rol        INT NOT NULL REFERENCES roles(id),
  id_permiso    INT NOT NULL REFERENCES permisos(id),
  PRIMARY KEY (id_compania, id_sucursal, id_rol, id_permiso)
);

CREATE TABLE usuarios (
  id            SERIAL       PRIMARY KEY,
  id_compania   INT          NOT NULL,
  id_sucursal   INT          NOT NULL,
  id_rol        INT          NOT NULL REFERENCES roles(id),
  id_persona    INT          NOT NULL REFERENCES identidad.personas(id),
  correo        VARCHAR(150) NOT NULL,     -- puede ser corporativo (ej: juan@mygym.com)
  password_hash VARCHAR(255) NOT NULL,
  activo        BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (id_persona, id_compania),        -- un empleado una cuenta por gym
  UNIQUE (id_compania, correo)
);

CREATE TABLE bitacora_accesos (
  id          BIGSERIAL   PRIMARY KEY,
  id_compania INT         NOT NULL,
  id_sucursal INT         NOT NULL,
  id_usuario  INT         NOT NULL REFERENCES usuarios(id),
  modulo      VARCHAR(50) NOT NULL,
  accion      VARCHAR(100) NOT NULL,
  entidad_id  INT,
  detalle     JSONB,       -- snapshot antes/después del cambio
  ip          VARCHAR(45),
  fecha       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bitacora_compania_fecha ON bitacora_accesos(id_compania, fecha);
```

---

### Inventario & Ventas

```sql
CREATE TABLE categorias_producto (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE proveedores (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(150) NOT NULL,
  telefono     VARCHAR(20),
  correo       VARCHAR(150),
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE productos (
  id              SERIAL        PRIMARY KEY,
  id_compania     INT           NOT NULL,
  id_sucursal     INT           NOT NULL,
  id_categoria    INT           NOT NULL REFERENCES categorias_producto(id),
  id_proveedor    INT           REFERENCES proveedores(id),
  nombre          VARCHAR(150)  NOT NULL,
  descripcion     TEXT,
  codigo_barras   VARCHAR(50)   UNIQUE,
  precio_venta    DECIMAL(10,2) NOT NULL CHECK (precio_venta >= 0),
  precio_costo    DECIMAL(10,2) NOT NULL CHECK (precio_costo >= 0),
  stock_minimo    INT           NOT NULL DEFAULT 0,
  activo          BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE inventario (
  id                   SERIAL      PRIMARY KEY,
  id_compania          INT         NOT NULL,
  id_sucursal          INT         NOT NULL,
  id_producto          INT         NOT NULL REFERENCES productos(id),
  stock_actual         INT         NOT NULL DEFAULT 0,
  ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (id_producto, id_compania, id_sucursal)
);

CREATE TABLE ventas (
  id               SERIAL        PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  id_cliente       INT           REFERENCES clientes(id),  -- NULL si es cliente externo
  id_metodo_pago   INT           REFERENCES metodos_pago(id),
  id_usuario_venta INT           REFERENCES usuarios(id),
  total            DECIMAL(10,2) NOT NULL CHECK (total >= 0),
  fecha            DATE          NOT NULL DEFAULT CURRENT_DATE,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE detalle_ventas (
  id               SERIAL        PRIMARY KEY,
  id_venta         INT           NOT NULL REFERENCES ventas(id),
  id_producto      INT           NOT NULL REFERENCES productos(id),
  cantidad         INT           NOT NULL CHECK (cantidad > 0),
  precio_unitario  DECIMAL(10,2) NOT NULL,
  subtotal         DECIMAL(10,2) NOT NULL
);

CREATE TABLE movimientos_inventario (
  id              BIGSERIAL     PRIMARY KEY,
  id_compania     INT           NOT NULL,
  id_sucursal     INT           NOT NULL,
  id_producto     INT           NOT NULL REFERENCES productos(id),
  id_proveedor    INT           REFERENCES proveedores(id),   -- si es entrada
  id_venta        INT           REFERENCES ventas(id),        -- si es salida por venta
  tipo            VARCHAR(20)   NOT NULL
                    CHECK (tipo IN ('entrada','venta','ajuste','devolucion')),
  cantidad        INT           NOT NULL,                     -- positivo entrada, negativo salida
  fecha           DATE          NOT NULL DEFAULT CURRENT_DATE,
  observacion     TEXT,
  id_usuario      INT           REFERENCES usuarios(id),
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_movimientos_producto ON movimientos_inventario(id_compania, id_producto, fecha);
```

---

### Configuración

```sql
CREATE TABLE gym_config (
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  clave        VARCHAR(100) NOT NULL,
  valor        TEXT,
  descripcion  VARCHAR(255),
  tipo         VARCHAR(20)  NOT NULL DEFAULT 'texto'
                 CHECK (tipo IN ('texto','numero','booleano','json')),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  PRIMARY KEY (id_compania, id_sucursal, clave)
);

CREATE TABLE metodos_pago (
  id           SERIAL       PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
```

**Claves relevantes de `gym_config` para congelamientos:**

```sql
-- Ejemplos de INSERT para configurar el comportamiento de congelamientos
INSERT INTO gym_config (id_compania, id_sucursal, clave, valor, tipo, descripcion) VALUES
  (1, 1, 'permite_congel_retroactivo',  'true', 'booleano', 'Permite aplicar congelamientos con fechas pasadas'),
  (1, 1, 'max_dias_congel_retroactivo', '90',   'numero',   'Máximo de días hacia atrás para un congelamiento retroactivo'),
  (1, 1, 'max_meses_congelar',          '6',    'numero',   'Máximo de meses que se puede congelar una membresía'),
  (1, 1, 'requiere_doc_retroactivo',    'true', 'booleano', 'Exige documento de respaldo para congelamientos retroactivos'),
  (1, 1, 'acceso_solo_sucursal_propia','false','booleano', 'Si true, el cliente solo puede entrar en la sucursal donde se registró');
```

---

### SRI (catálogos oficiales Ecuador)

Schema `sri` guarda catálogos publicados por el Servicio de Rentas Internas. Son datos globales (sin `id_compania`), solo lectura desde la app y precargados por el seed `ddl-facturacion/09_insert_seed_sri.sql`.

| Tabla | Contenido |
|-------|-----------|
| `sri.tipos_comprobante` | 01 factura, 04 nota crédito, 05 nota débito, 06 guía remisión, 07 retención, ... |
| `sri.tipos_identificacion_comprador` | 04 RUC, 05 cédula, 06 pasaporte, 07 consumidor final, 08 identificación exterior |
| `sri.formas_pago` | 01 efectivo, 15 compensación deudas, 16 tarjeta débito, 19 tarjeta crédito, 20 otros con SFP, ... |
| `sri.tipos_impuesto` | 2 IVA, 3 ICE, 5 IRBPNR |
| `sri.tarifas_iva` | 0 %, 5 %, 12 %, 13 %, 14 %, 15 %, no objeto, exento |
| `sri.motivos_anulacion_nc` | Motivos oficiales para anular una nota de crédito |

Todas usan `codigo VARCHAR(...)` como PK — el string publicado por el SRI (nunca reemplazar por ID interno).

---

### Facturación electrónica (schema `facturacion`)

17 tablas para el ciclo completo de emisión SRI: configuración por compañía, certificados, secuenciales por punto de emisión, comprobantes (factura, NC, retención), sus detalles, impuestos y pagos, envío al SRI y notificación al receptor.

```sql
-- Configuración fiscal por compañía (1:1)
CREATE TABLE facturacion.config_sri (
  id_compania             INT PRIMARY KEY REFERENCES tenant.companias(id),
  ambiente                VARCHAR(20) NOT NULL,   -- pruebas | produccion
  tipo_emision            VARCHAR(20) NOT NULL,   -- normal | contingencia
  email_facturacion       VARCHAR(150),
  ...
);

-- Certificados P12 encriptados
CREATE TABLE facturacion.certificados (
  id             INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania    INT NOT NULL REFERENCES tenant.companias(id),
  archivo_url    VARCHAR(255) NOT NULL,           -- Cloudinary raw
  password_enc   BYTEA        NOT NULL,           -- pgcrypto AES-256
  valido_desde   DATE,
  valido_hasta   DATE,
  activo         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Un punto de emisión + secuencial por (compañía, sucursal, tipo de comprobante)
CREATE TABLE facturacion.puntos_emision (
  id            INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania   INT NOT NULL REFERENCES tenant.companias(id),
  id_sucursal   INT NOT NULL,                    -- ref lógica
  cod_establecimiento VARCHAR(3) NOT NULL,       -- "001", "002" ...
  cod_punto_emision   VARCHAR(3) NOT NULL,
  UNIQUE (id_compania, cod_establecimiento, cod_punto_emision)
);

CREATE TABLE facturacion.secuenciales (
  id                 INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_punto_emision   INT NOT NULL REFERENCES facturacion.puntos_emision(id),
  cod_documento      VARCHAR(2) NOT NULL,        -- 01, 04, 07 ...
  ultimo_secuencial  INT        NOT NULL DEFAULT 0,
  UNIQUE (id_punto_emision, cod_documento)
);

-- Comprobante generado (cabecera)
CREATE TABLE facturacion.comprobantes (
  id                INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania       INT  NOT NULL REFERENCES tenant.companias(id),
  id_sucursal       INT  NOT NULL,
  clave_acceso      VARCHAR(49) NOT NULL UNIQUE, -- 49 dígitos SRI
  tipo_comprobante  VARCHAR(2)  NOT NULL,        -- FK lógica a sri.tipos_comprobante.codigo
  numero_secuencial VARCHAR(9)  NOT NULL,
  fecha_emision     DATE        NOT NULL,
  estado            VARCHAR(20) NOT NULL,        -- borrador | firmado | recibido |
                                                  -- autorizado | rechazado | anulado
  ...
);
```

**Función SQL relevante:** `facturacion.next_secuencial(...)` (script 26) atomiza el increment del secuencial para evitar duplicados en concurrencia.

**Tabla `finanzas.ingresos` toca `facturacion.comprobantes`:** cada ingreso puede referenciar un comprobante electrónico (`id_comprobante BIGINT REFERENCES facturacion.comprobantes(id)`). Por eso `ddl-facturacion/` se ejecuta **antes** que la tabla de ingresos en la story consolidada.

**Nota:** Los schemas `sri` y `facturacion` están **implementados en BD** pero no consumidos aún por ningún servicio. El futuro `billing-service` (spec en `specs/billing-service.md`) los consumirá.

---

## Casos de Negocio y Dónde se Configuran

Registro de todos los escenarios analizados durante el diseño, con la tabla o campo exacto que los resuelve.

---

### CASO 1 — Registrar un gimnasio con varias sucursales

**Situación:** Se registra "Gimnasio 1" con sucursales en Quito, Ibarra y Otavalo.

| Qué ocurre | Dónde |
|---|---|
| Datos del gimnasio | `companias` — una fila por gimnasio |
| Cada sede física | `sucursales` — una fila por sucursal, `id_compania` con FK real |
| Todas las operaciones filtradas por sede | Todas las tablas llevan `id_compania` + `id_sucursal` (sin FK) |

**Configuración:** No requiere configuración adicional. El registro es directo en `companias` y `sucursales`.

---

### CASO 2 — Compañía sin sucursales

**Situación:** Un gimnasio pequeño que opera en un solo local, sin necesidad de gestionar sedes.

| Qué ocurre | Dónde |
|---|---|
| Se crea automáticamente una sucursal "Sede Principal" | `sucursales.es_principal = TRUE` al registrar la compañía |
| El usuario nunca ve el selector de sucursal | La app oculta la UI si `COUNT(sucursales WHERE es_principal=FALSE) = 0` |
| Todas las tablas siguen usando `id_sucursal` | Apunta siempre a la sede principal creada automáticamente |

**Configuración:** Automático — la app inserta la sede principal al crear la compañía. No requiere acción del usuario.

---

### CASO 3 — La compañía no paga la suscripción mensual

**Situación:** El gimnasio contrató el plan Premium pero al mes siguiente no realiza el pago.

| Qué ocurre | Dónde |
|---|---|
| Se registra el intento de cobro fallido | `pagos_suscripcion.estado = 'fallido'` |
| La membresía pasa a período de gracia | `compania_planes.estado = 'en_gracia'` (job diario) |
| El acceso se mantiene con banner de aviso | Middleware valida `estado IN ('activo','en_gracia')` |
| Al agotar la gracia, se bloquea el acceso | `compania_planes.estado = 'vencido'` — 0 módulos habilitados |
| Al pagar, se crea un nuevo período activo | Nueva fila en `compania_planes` + fila en `pagos_suscripcion` |

**Configuración:**
```
compania_planes.dias_gracia     → días de tolerancia antes del bloqueo total
gym_config: (no aplica, es dato operativo)
```

---

### CASO 4 — Notificar al gimnasio antes del vencimiento

**Situación:** Avisar a la compañía que su suscripción está por vencer.

| Qué ocurre | Dónde |
|---|---|
| Cuándo enviar la alerta | `compania_planes.fecha_fin` — el job calcula `fecha_fin - dias_antes` |
| A quién notificar | `companias.whatsapp` y `companias.correo` |
| Evitar reenviar la misma alerta | `notificaciones_suscripcion` — el job verifica si ya fue enviada |
| Registro de cada notificación | `notificaciones_suscripcion.estado` = enviado / fallido |

**Configuración:**
```
config_notif_suscripcion  →  una fila por umbral activo (ej: 5 días, 3 días, 1 día)
                              campo canal: email | whatsapp | ambos
```

---

### CASO 5 — Los días de alerta son configurables (3, 4 o 5 días)

**Situación:** Hoy se notifica 3 días antes, mañana el dueño quiere cambiarlo a 5 días.

| Qué ocurre | Dónde |
|---|---|
| Cada umbral es una fila independiente | `config_notif_suscripcion (id_compania, dias_antes)` |
| Cambiar de 3 a 5 días | `UPDATE config_notif_suscripcion SET dias_antes=5 WHERE id_compania=1 AND dias_antes=3` |
| Agregar un aviso nuevo (ej: 7 días) | `INSERT INTO config_notif_suscripcion ...` |
| Desactivar un aviso sin borrarlo | `config_notif_suscripcion.activo = FALSE` |

**Configuración:** Panel de configuración de la compañía → sección "Alertas de suscripción". Opera directamente sobre `config_notif_suscripcion`.

---

### CASO 6 — Cambio de plan (upgrade / downgrade)

**Situación A — Upgrade inmediato** (Basic → Premium antes de que venza el ciclo):

| Qué ocurre | Dónde |
|---|---|
| Plan anterior se cierra | `compania_planes.estado = 'cancelado'` |
| Se crea el nuevo plan activo hoy | Nueva fila `compania_planes`, `tipo_cambio = 'upgrade'`, `id_compania_plan_orig` apunta al cancelado |
| Se cobra solo la diferencia prorrateada | `pagos_suscripcion.tipo_pago = 'diferencia_upgrade'` |
| El crédito por días no usados se registra | `compania_planes.credito_monto` en el nuevo registro |

**Situación B — Downgrade al próximo ciclo** (Premium → Basic al vencer):

| Qué ocurre | Dónde |
|---|---|
| El plan actual sigue activo hasta `fecha_fin` | `compania_planes.estado = 'activo'` sin cambios |
| El nuevo plan queda agendado | Nueva fila `compania_planes`, `tipo_cambio = 'downgrade'`, `estado = 'programado'` |
| Al llegar `fecha_inicio` del nuevo, se activa | Job diario activa filas con `estado='programado'` y `fecha_inicio = CURRENT_DATE` |

**Configuración:** No requiere `gym_config`. Es operación directa sobre `compania_planes`.

---

### CASO 7 — Cliente cambia de membresía mensual a tarjeta de 22 días

**Situación:** El cliente no renueva el plan mensual y compra una tarjeta de 22 días por $35.

| Qué ocurre | Dónde |
|---|---|
| La membresía mensual vencida queda en historial | `membresias.estado = 'vencida'` — la fila se conserva |
| El nuevo tipo "Tarjeta 22 días" se registra | `tipos_membresia` — si no existe, el admin lo crea una vez |
| Se crea la nueva membresía | Nueva fila en `membresias` con `id_tipo_membresia` apuntando al nuevo tipo |
| `fecha_fin` se calcula automáticamente | `fecha_inicio + duracion_valor days` — responsabilidad de la app |
| Se registra el ingreso | `ingresos` con `id_membresia` de la nueva membresía |
| El estado del cliente se reactiva | `clientes.estado = 'activo'` |

**Configuración:**
```
tipos_membresia  →  panel de administración → "Tipos de membresía"
                    el dueño define nombre, duración y precio
                    no requiere tocar código para agregar nuevos tipos
```

---

### CASO 8 — Cliente quiere pausar su membresía por 6 meses

**Situación:** El cliente solicita congelar su membresía de forma voluntaria por un período extendido.

| Qué ocurre | Dónde |
|---|---|
| Se registra el período de pausa | `congelamientos` — `fecha_inicio` y `fecha_fin` del congelamiento |
| La membresía queda pausada | `membresias.estado = 'congelada'` |
| El cliente queda como congelado | `clientes.estado = 'congelado'` |
| Al regresar, se calculan los días restantes | `fecha_fin_original + días_congelados` — la app suma todos los `congelamientos` del registro |
| Múltiples pausas en la misma membresía | Cada pausa es una fila independiente en `congelamientos` |

**Configuración:**
```
gym_config clave 'max_meses_congelar'  →  límite máximo de meses de pausa permitidos
                                           si el cliente pide 6 y el límite es 3, la app rechaza
```

---

### CASO 9 — Cliente ausente un mes por accidente con certificado médico

**Situación:** La membresía ya venció mientras el cliente estuvo en recuperación. Presenta el certificado y pide que le respeten el tiempo perdido.

| Qué ocurre | Dónde |
|---|---|
| Se registra el congelamiento retroactivo | `congelamientos.retroactivo = TRUE` con fechas pasadas |
| Se adjunta el certificado médico | `congelamientos.documento_respaldo` — URL/ruta del archivo |
| Un admin autoriza la operación | `congelamientos.aprobado_por` (FK → usuarios) + `fecha_aprobacion` |
| La membresía vencida se reactiva | `membresias.estado = 'activa'` |
| Se extiende la `fecha_fin` con días perdidos | `membresias.fecha_fin = fecha_fin_original + dias_congelados` |
| La base rechaza retroactivos sin documento | `CONSTRAINT chk_retroactivo` — validación a nivel de motor |

**Configuración:**
```
gym_config clave 'permite_congel_retroactivo'   →  true/false — habilita o bloquea la función
gym_config clave 'max_dias_congel_retroactivo'  →  ej: 90 — días máximos hacia atrás permitidos
gym_config clave 'requiere_doc_retroactivo'     →  true/false — exige certificado obligatorio
```

**Permisos requeridos** (tabla `permisos`):
```
congelamientos.retroactivo.crear   →  solo roles con este permiso pueden aplicar retroactivos
congelamientos.retroactivo.forzar  →  supera el límite de max_dias_congel_retroactivo
```

---

### CASO 10 — Cliente entra a una sucursal diferente de la misma compañía

**Situación:** El cliente tiene su membresía registrada en Quito pero se presenta en Ibarra a entrenar.

**Regla de negocio:** Un cliente puede entrar a cualquier sucursal de su misma compañía, pero no a la de otra compañía.

| Qué ocurre | Dónde |
|---|---|
| Validación ignora `id_sucursal` de la membresía | La query filtra por `id_compania`, no por `id_sucursal` |
| La barrera entre compañías es automática | El cliente no tiene membresía con `id_compania` diferente → 0 filas → denegado |
| Se registra en qué sucursal entró físicamente | `asistencias.id_sucursal` = sucursal donde escaneó el QR |
| Permite reportes de movilidad entre sedes | `asistencias.id_sucursal` ≠ `membresias.id_sucursal` identifica visitas cruzadas |

**Query de validación correcta:**
```sql
-- Filtra por id_compania, NO por id_sucursal
SELECT m.id FROM membresias m
WHERE m.id_cliente  = :cliente
  AND m.id_compania = :compania   -- barrera de seguridad entre compañías
  AND m.estado      = 'activa'
  AND m.fecha_fin  >= CURRENT_DATE;
```

**Configuración:**
```
gym_config clave 'acceso_solo_sucursal_propia'  →  false (default) = libre entre sedes
                                                    true = solo puede entrar en su sucursal
```

> No requiere tablas ni campos nuevos. El campo `id_compania` existente en `membresias` es la barrera natural. Solo cambia la lógica de la query en la app según el valor de `gym_config`.

---

### CASO 11 — App móvil: cliente accede con usuario y contraseña del gimnasio

**Situación:** El gimnasio entrega credenciales al cliente para que acceda a la app móvil y vea sus membresías, asistencia y QR.

**Problema previo:** Los datos personales (nombre, CI, correo) estaban duplicados en `clientes` para cada gym. Se rediseñó con tabla `personas` global.

| Qué ocurre | Dónde |
|---|---|
| Identidad de la persona centralizada | `personas` — una fila por CI, global, sin tenant |
| Relación persona ↔ gym | `clientes` — `id_persona FK + id_compania`, datos gym-específicos |
| Credenciales dadas por el gym | `usuarios_app` — `id_persona + id_compania`, login + password_hash |
| Primera vez: obliga a cambiar contraseña | `usuarios_app.requiere_cambio_pwd = TRUE` |
| Gym suspende acceso a la app | `usuarios_app.activo = FALSE` |
| Recuperar contraseña olvidada | `usuarios_app.token_recuperacion + token_expira` |
| Misma persona en dos gyms diferentes | Dos filas en `clientes` + dos filas en `usuarios_app`, una por gym |
| El CI evita duplicar la persona | `personas.ci UNIQUE` — al registrar, se busca primero en `personas` |
| La app solo ve datos del gym que hizo login | El `id_compania` del `usuarios_app` filtra todo |

**Flujo de registro de un cliente nuevo:**
```
1. Gym ingresa el CI del cliente
2. App busca en personas WHERE ci = '...'
   → No existe: INSERT INTO personas  (nueva persona)
   → Existe: reutiliza el id (persona ya registrada en otro gym)
3. INSERT INTO clientes (id_persona, id_compania, ...)
4. INSERT INTO usuarios_app (id_persona, id_compania, login, password_hash, requiere_cambio_pwd=TRUE)
5. Gym entrega credenciales al cliente
```

**Configuración:**
```
gym_config clave 'app_movil_habilitada'    →  true/false — el gym activa la app para sus clientes
gym_config clave 'app_movil_campo_login'   →  'ci' | 'correo' | 'telefono'
gym_config clave 'app_movil_pwd_min_largo' →  ej: 8
caracteristicas.codigo = 'app_movil'       →  solo planes que incluyan esta característica
```

---

### CASO 12 — Gimnasio vende suplementos y maneja inventario

**Situación:** El gimnasio quiere vender productos (proteínas, vitaminas, accesorios) y controlar el stock disponible por sucursal.

| Qué ocurre | Dónde |
|---|---|
| Catálogo de productos organizado | `categorias_producto` + `productos` |
| Proveedores de los productos | `proveedores` — por compañía/sucursal |
| Stock actual por sucursal | `inventario` — UNIQUE (id_producto, id_compania, id_sucursal) |
| Entrada de mercadería | `movimientos_inventario.tipo = 'entrada'` + aumenta `inventario.stock_actual` |
| Venta registrada con detalle | `ventas` (cabecera) + `detalle_ventas` (líneas por producto) |
| Stock descontado al vender | `movimientos_inventario.tipo = 'venta'` + reduce `inventario.stock_actual` |
| Ingreso financiero generado | `ingresos.id_venta` enlaza la venta con el módulo de finanzas |
| Alerta de stock mínimo | Job diario: `inventario.stock_actual <= productos.stock_minimo` |
| Venta a cliente del gym | `ventas.id_cliente` — opcional, puede ser cliente externo (NULL) |
| Historial completo de movimientos | `movimientos_inventario` — cada entrada, salida y ajuste |

**Configuración:**
```
caracteristicas.codigo = 'inventario'  →  módulo disponible en Plan Premium
categorias_ingreso                     →  agregar fila 'Venta de productos'
gym_config clave 'alerta_stock_minimo' →  true/false — activa notificaciones de stock bajo
```

---

### CASO 13 — Registro de entrada: cliente escanea QR del gimnasio / biométrico

**Situación:** El cliente llega al gimnasio y necesita registrar su entrada. El flujo correcto es que **el cliente lee el QR del gimnasio** con su app, no que el gym escanee un QR del cliente. En el futuro, también se contempla entrada por biométrico (huella, facial).

**Flujo QR (actual):**

| Paso | Qué ocurre | Dónde |
|---|---|---|
| Gym configura la puerta | Se coloca el QR del local visible en la entrada | `tenant.sucursales.qr_token` |
| Cliente llega y abre su app | La app está autenticada — conoce `id_persona` y `id_compania` | `identidad.usuarios_app` |
| Cliente escanea el QR de la puerta | La app lee `qr_token` e identifica la sucursal | `tenant.sucursales.qr_token` |
| Servidor valida membresía activa | Consulta `core.membresias` con `id_cliente + id_compania` | `core.membresias.estado = 'activa'` |
| Entrada registrada | `INSERT INTO asistencia.asistencias` | `metodo_registro = 'qr_cliente'` |
| El QR rota periódicamente | El gym regenera `qr_token` cada X horas para seguridad | `tenant.sucursales.qr_token_expira` |

**Flujo biométrico (futuro):**

| Paso | Qué ocurre | Dónde |
|---|---|---|
| Gym instala sensor | Se configura el lector (huella, facial, iris) en la sucursal | — |
| Registro de biometría del cliente | Se almacena la plantilla encriptada | `identidad.biometria` (AES-256) |
| Cliente pone dedo/cara en el sensor | El sistema identifica al cliente | `identidad.biometria.hash_datos` |
| Servidor valida membresía activa | Misma lógica que QR | `core.membresias` |
| Entrada registrada | `INSERT INTO asistencia.asistencias` | `metodo_registro = 'biometrico'` |

**Métodos de registro soportados:**
```
metodo_registro:
  'qr_cliente'  →  cliente escaneó el QR de la puerta con su app (flujo principal)
  'biometrico'  →  sensor biométrico identificó al cliente (futuro)
  'manual'      →  recepcionista registró la entrada manualmente (respaldo)
```

**Query de validación de entrada (aplica a todos los métodos):**
```sql
-- El id_compania del qr_token leído debe coincidir con el de la membresía
SELECT m.id FROM core.membresias m
JOIN core.clientes c ON c.id = m.id_cliente
WHERE c.id_persona   = :id_persona
  AND m.id_compania  = :id_compania_del_token  -- barrera entre compañías
  AND m.estado       = 'activa'
  AND m.fecha_fin   >= CURRENT_DATE;
```

**Nota sobre `clientes.codigo_carnet`:** El campo `codigo_carnet` en `core.clientes` ya NO es el mecanismo de entrada. Es un código impreso en el carnet físico del cliente, útil únicamente para búsquedas manuales en recepción (cuando el cliente olvidó el teléfono y se necesita buscarlo en el sistema).

**Configuración:**
```
gym_config clave 'qr_rotacion_horas'        →  cada cuántas horas se renueva el qr_token (ej: 24)
caracteristicas.codigo = 'app_movil'        →  solo planes que incluyan esta característica
caracteristicas.codigo = 'biometria'        →  característica futura, Plan Premium o Enterprise
```

---

### CASO 14 — Dos modelos de membresía: calendario vs. tarjeta de accesos

**Situación:** No todos los clientes compran membresías por fecha. Algunos compran una "tarjeta de 22 días" (configurable) donde cada vez que entran consumen un día de acceso. El sistema debe soportar ambos modelos con lógicas de validación completamente diferentes.

#### Modelo A — Membresía por calendario

| Característica | Detalle |
|---|---|
| Ejemplo | Mensual $35, Semanal $10, 30 días $28 |
| Control | `fecha_fin >= CURRENT_DATE` |
| El cliente no va | La fecha de vencimiento NO cambia |
| Asistencia | Se registra para seguimiento, mensajería y beneficios de fidelidad |
| Registro de entrada | Recomendado pero **no bloquea** si el cliente no lo hace |

#### Modelo B — Tarjeta de accesos (días de entrada)

| Característica | Detalle |
|---|---|
| Ejemplo | Tarjeta 22 accesos $35 (válida hasta 3 meses) |
| Control | `COUNT(asistencias) < dias_acceso_total AND fecha_fin >= CURRENT_DATE` |
| El cliente no va | Los accesos restantes se conservan indefinidamente hasta `fecha_fin` |
| Asistencia | Cada entrada **consume un acceso** — el registro es obligatorio |
| Registro de entrada | **Bloqueante:** sin registro no hay ingreso y no se descuenta un día |

**Dónde se configura:**

| Tabla | Campo | Descripción |
|---|---|---|
| `core.tipos_membresia` | `modo_control` | `'calendario'` o `'accesos'` — define el modelo |
| `core.tipos_membresia` | `duracion_tipo` + `duracion_valor` | Para `accesos`: plazo máximo (ej: 3 meses para usar los 22 días) |
| `core.tipos_membresia` | `dias_acceso` | Solo modo `accesos` — cuántos días trae la tarjeta (ej: 22) |
| `core.membresias` | `dias_acceso_total` | Copiado desde `tipos_membresia` al comprar — inmutable |
| `core.membresias` | `fecha_fin` | Vencimiento calendario para ambos modos (modo `accesos` = plazo máximo) |

**Cómo se calcula el saldo de accesos restantes (sin campo extra):**
```sql
-- Los accesos usados se derivan contando asistencias — no se duplica en un campo separado
SELECT
  m.dias_acceso_total,
  COUNT(a.id)                              AS dias_usados,
  m.dias_acceso_total - COUNT(a.id)        AS dias_restantes,
  m.fecha_fin                              AS vence_el
FROM core.membresias m
LEFT JOIN asistencia.asistencias a ON a.id_membresia = m.id
WHERE m.id = :id_membresia
GROUP BY m.id, m.dias_acceso_total, m.fecha_fin;
```

**Validación de entrada en la puerta según modelo:**
```sql
-- Modelo A (calendario): válido si fecha no pasó
SELECT m.id FROM core.membresias m
JOIN core.tipos_membresia t ON t.id = m.id_tipo_membresia
WHERE m.id_cliente  = :id_cliente
  AND m.id_compania = :id_compania
  AND m.estado      = 'activa'
  AND m.fecha_fin  >= CURRENT_DATE
  AND t.modo_control = 'calendario';

-- Modelo B (accesos): válido si fecha no pasó Y quedan días
SELECT m.id FROM core.membresias m
JOIN core.tipos_membresia t ON t.id = m.id_tipo_membresia
WHERE m.id_cliente  = :id_cliente
  AND m.id_compania = :id_compania
  AND m.estado      = 'activa'
  AND m.fecha_fin  >= CURRENT_DATE
  AND t.modo_control = 'accesos'
  AND (SELECT COUNT(*) FROM asistencia.asistencias a WHERE a.id_membresia = m.id)
      < m.dias_acceso_total;
```

**Estado del cliente y alertas para tarjeta de accesos:**

| Condición | Estado | Alerta |
|---|---|---|
| Quedan 5+ días | `activo` | — |
| Quedan 3 días o menos | `proximo_vencer` | WhatsApp: "te quedan X entradas" |
| Accesos = 0 | `vencido` | Renovación requerida |
| `fecha_fin` pasó aunque queden días | `vencido` | Accesos no usados pierden validez |

**Configuración:**
```
tipos_membresia     →  panel admin → "Tipos de membresía" → modo: calendario / accesos
                        campo 'dias_acceso' configurable (ej: 22, 30, 15)
gym_config clave 'alerta_accesos_restantes'  →  ej: 3 (avisa cuando quedan X accesos)
```
