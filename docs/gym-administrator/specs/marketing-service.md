# Marketing Service — Especificación de Desarrollo

> **ESTADO:** 📋 Planeado — sin implementar. Este servicio NO existe en el código todavía; es una especificación de diseño futura. Ver [../../STATUS.md](../../STATUS.md).

> **Servicio:** marketing-service  
> **Esquemas BD:** `marketing`  
> **Tablas:** 4 tablas (promociones · cliente_promociones · reglas_beneficios · cliente_beneficios)  
> **Plan requerido:** Premium  
> **Depende de:** auth-service (JWT) · platform-service (`/modulos/check` → `marketing`) · attendance-service (racha de asistencia) · core-service (estado del cliente)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Dos sistemas de fidelización](#4-dos-sistemas-de-fidelización)
5. [API — Contratos de endpoints](#5-api--contratos-de-endpoints)
6. [Flujos principales](#6-flujos-principales)
7. [Casos de prueba](#7-casos-de-prueba)
8. [Datos semilla (seeds)](#8-datos-semilla-seeds)
9. [Variables de entorno requeridas](#9-variables-de-entorno-requeridas)
10. [Reglas de negocio críticas](#10-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Marketing Service gestiona **dos mecanismos de retención de clientes**: promociones comerciales (ej: 2x1, descuentos) y beneficios automáticos por fidelidad (ej: descuento por 1 mes sin faltas).

**Responsabilidades:**
- CRUD de promociones y reglas de beneficios
- Asignar promociones manualmente a clientes
- Ejecutar el job diario que evalúa rachas de asistencia y otorga beneficios automáticamente
- Proveer a Core Service el descuento aplicable al vender una membresía
- Marcar beneficios como aplicados o expirados

**Fuera de alcance:**
- Envío de notificaciones (→ Attendance Service, que ya maneja mensajería)
- Cobros y descuentos en factura (→ Finance Service + Core Service)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `Dueño` | `Recepción` | `Entrenador` | `cliente` (app) |
|---|:---:|:---:|:---:|:---:|:---:|
| CRUD promociones | ✅ | ✅ | ❌ | ❌ | ❌ |
| CRUD reglas de beneficios | ✅ | ✅ | ❌ | ❌ | ❌ |
| Asignar promoción a cliente | ✅ | ✅ | ✅ | ❌ | ❌ |
| Ver promociones disponibles | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ver beneficios del cliente | ✅ | ✅ | ✅ | ❌ | ✅ (solo los suyos) |
| Aplicar beneficio al vender | ✅ | ✅ | ✅ | ❌ | ❌ |

---

## 3. Tablas involucradas

### marketing.promociones
```sql
id                   INT     PK, identity
id_compania          INT     NOT NULL
id_sucursal          INT     NOT NULL
nombre               VARCHAR(150) NOT NULL
tipo                 VARCHAR(30)  NOT NULL
                       CHECK IN ('2x1','porcentaje','servicio_extra','regalo')
descripcion          TEXT
condiciones          TEXT                     -- texto libre de condiciones
descuento_porcentaje DECIMAL(5,2)             -- solo para tipo='porcentaje'
max_personas         INT                      -- para tipo='2x1': cuántas pueden aprovecharla
fecha_inicio         DATE
fecha_fin            DATE
activa               BOOLEAN NOT NULL DEFAULT TRUE
aplica_a_fidelidad   BOOLEAN NOT NULL DEFAULT FALSE  -- true: asignada automáticamente por beneficio
```

### marketing.cliente_promociones
```sql
id               INT     PK, identity
id_compania      INT     NOT NULL
id_sucursal      INT     NOT NULL
id_cliente       INT     FK → core.clientes(id)        NOT NULL
id_promocion     INT     FK → marketing.promociones(id) NOT NULL
id_membresia     INT                                    -- membresía donde se aplicó
fecha_asignacion DATE    NOT NULL DEFAULT CURRENT_DATE
fecha_uso        DATE
estado           VARCHAR(20) NOT NULL DEFAULT 'asignada'
                   CHECK IN ('asignada','usada','expirada')
```

### marketing.reglas_beneficios
```sql
id               INT     PK, identity
id_compania      INT     NOT NULL
id_sucursal      INT     NOT NULL
meses_sin_faltas INT     NOT NULL CHECK (meses_sin_faltas > 0)
tipo_beneficio   VARCHAR(30) NOT NULL
                   CHECK IN ('descuento','servicio','regalo')
descripcion      VARCHAR(255) NOT NULL
valor            DECIMAL(10,2)       -- porcentaje si descuento, null si servicio/regalo
activo           BOOLEAN NOT NULL DEFAULT TRUE
UNIQUE (id_compania, id_sucursal, meses_sin_faltas)
```
> Ejemplos: 1 mes sin faltas → descuento 10% · 3 meses → sesión nutricionista · 6 meses → trofeo

### marketing.cliente_beneficios
```sql
id             INT     PK, identity
id_compania    INT     NOT NULL
id_sucursal    INT     NOT NULL
id_cliente     INT     FK → core.clientes(id)              NOT NULL
id_regla       INT     FK → marketing.reglas_beneficios(id) NOT NULL
fecha_otorgado DATE    NOT NULL DEFAULT CURRENT_DATE
estado         VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                 CHECK IN ('pendiente','aplicado','expirado')
```

---

## 4. Dos sistemas de fidelización

### Sistema A — Promociones comerciales (manual/programado)

```
Dueño crea promoción (ej: 2x1 en junio)
      │
      ├─ Recepcionista asigna a cliente → cliente_promociones (estado='asignada')
      │
      └─ Al vender membresía: Core Service consulta
           GET /marketing/clientes/{id}/promociones-disponibles
           Aplica el descuento → UPDATE cliente_promociones SET estado='usada'
```

### Sistema B — Beneficios por fidelidad (automático por job)

```
Job diario a las 00:20 UTC
      │
      ├─ Para cada cliente con membresía activa:
      │
      ├─ Llama Attendance Service:
      │    GET /clientes/{id}/asistencias/racha-perfecta?meses=1
      │    GET /clientes/{id}/asistencias/racha-perfecta?meses=3
      │    GET /clientes/{id}/asistencias/racha-perfecta?meses=6
      │
      ├─ Para cada racha alcanzada:
      │    Busca regla_beneficio WHERE meses_sin_faltas = racha Y activo = TRUE
      │    Verifica que no tenga ya ese beneficio en el período actual
      │    INSERT cliente_beneficios (estado='pendiente')
      │
      └─ Al renovar membresía: Core Service consulta beneficios pendientes
           GET /marketing/clientes/{id}/beneficios-pendientes
           Aplica el descuento → UPDATE cliente_beneficios SET estado='aplicado'
```

---

## 5. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 5.1 Promociones

#### `GET /marketing/promociones` — Listar promociones activas
```json
// Response 200
[
  {
    "id": 1,
    "nombre": "2x1 Junio",
    "tipo": "2x1",
    "descripcion": "Trae un amigo y paguen la mitad cada uno",
    "fecha_inicio": "2026-06-01",
    "fecha_fin": "2026-06-30",
    "activa": true,
    "aplica_a_fidelidad": false
  }
]
```

#### `POST /marketing/promociones` — Crear promoción
```json
{
  "nombre": "Descuento verano",
  "tipo": "porcentaje",
  "descuento_porcentaje": 15.00,
  "descripcion": "15% en todas las membresías de julio",
  "fecha_inicio": "2026-07-01",
  "fecha_fin": "2026-07-31"
}
// Response 201
```

#### `PUT /marketing/promociones/{id}` — Actualizar
```json
{ "activa": false }
// Response 200
```

---

### 5.2 Asignación de Promociones a Clientes

#### `POST /marketing/clientes/{id}/promociones` — Asignar promoción
```json
{
  "id_promocion": 1
}
// Response 201
// 409 si ya tiene esa promoción asignada sin usar
```

#### `GET /marketing/clientes/{id}/promociones-disponibles` — Promociones sin usar
```json
// Usado por Core Service al vender membresía
// Response 200
[
  {
    "id_cliente_promocion": 5,
    "promocion": { "id": 1, "nombre": "2x1 Junio", "tipo": "2x1" },
    "fecha_asignacion": "2026-06-01"
  }
]
```

#### `PUT /marketing/cliente-promociones/{id}/aplicar` — Marcar como usada
```json
// Llamado por Core Service al cerrar la venta
{ "id_membresia": 13 }
// Response 200
```

---

### 5.3 Reglas de Beneficios

#### `GET /marketing/reglas-beneficios` — Listar reglas del gym
```json
// Response 200
[
  { "id": 1, "meses_sin_faltas": 1, "tipo_beneficio": "descuento", "descripcion": "10% en próxima membresía", "valor": 10.00 },
  { "id": 2, "meses_sin_faltas": 3, "tipo_beneficio": "servicio",  "descripcion": "1 sesión con nutricionista", "valor": null },
  { "id": 3, "meses_sin_faltas": 6, "tipo_beneficio": "regalo",    "descripcion": "Trofeo de asistencia perfecta", "valor": null }
]
```

#### `POST /marketing/reglas-beneficios` — Crear regla
```json
{
  "meses_sin_faltas": 1,
  "tipo_beneficio": "descuento",
  "descripcion": "10% en próxima renovación",
  "valor": 10.00
}
// Response 201
// 409 si ya existe regla para ese número de meses en el mismo gym
```

---

### 5.4 Beneficios de Clientes

#### `GET /marketing/clientes/{id}/beneficios` — Historial de beneficios
```json
// Response 200
[
  {
    "id": 10,
    "regla": { "meses_sin_faltas": 1, "descripcion": "10% en próxima membresía" },
    "fecha_otorgado": "2026-05-01",
    "estado": "pendiente"
  }
]
```

#### `GET /marketing/clientes/{id}/beneficios-pendientes` — Solo pendientes
```json
// Usado por Core Service al vender membresía
// Response 200
[
  {
    "id_cliente_beneficio": 10,
    "tipo_beneficio": "descuento",
    "valor": 10.00,
    "descripcion": "10% en próxima renovación"
  }
]
```

#### `PUT /marketing/cliente-beneficios/{id}/aplicar` — Marcar como aplicado
```json
// Llamado por Core Service
// Response 200
```

---

## 6. Flujos principales

### Flujo: Job diario de beneficios por fidelidad (00:20 UTC)

```
Para cada cliente con membresía activa:
      │
      ├─ GET attendance-service /clientes/{id}/asistencias/racha-perfecta?meses=1
      │   GET attendance-service /clientes/{id}/asistencias/racha-perfecta?meses=3
      │   GET attendance-service /clientes/{id}/asistencias/racha-perfecta?meses=6
      │
      ├─ Para cada racha_perfecta=true:
      │    Busca regla WHERE meses_sin_faltas = :meses AND activo = TRUE
      │    Verifica: ¿Ya tiene cliente_beneficio para esta regla en el período?
      │    Si no → INSERT cliente_beneficios (estado='pendiente')
      │
      └─ Notifica al cliente (via Attendance Service o directo a proveedor)
```

### Flujo: Aplicar descuento al renovar membresía

```
Core Service recibe POST /clientes/{id}/membresias
      │
      ├─ GET marketing-service /clientes/{id}/beneficios-pendientes
      │    → [{ tipo: 'descuento', valor: 10.00 }]
      │
      ├─ GET marketing-service /clientes/{id}/promociones-disponibles
      │    → [{ id_cliente_promocion: 5, tipo: 'porcentaje', descuento: 15 }]
      │
      ├─ Aplica el mejor descuento (o suma si el gym lo permite)
      │
      ├─ INSERT membresias con descuento_aplicado = valor
      │
      └─ PUT marketing-service /cliente-beneficios/{id}/aplicar
         PUT marketing-service /cliente-promociones/{id}/aplicar { id_membresia }
```

---

## 7. Casos de prueba

### TC-MKT — Marketing Service

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-MKT-001 | Crear promoción porcentaje | tipo=porcentaje, valor=15 | 201 |
| TC-MKT-002 | Crear promoción 2x1 | tipo=2x1 | 201 |
| TC-MKT-003 | Asignar misma promoción dos veces | 2do POST misma promoción mismo cliente | 409 |
| TC-MKT-004 | Promoción expirada no aparece en disponibles | fecha_fin = ayer | No incluida en disponibles |
| TC-MKT-005 | Promoción inactiva no aparece | activa=false | No incluida en disponibles |
| TC-MKT-006 | Crear regla duplicada por meses | meses_sin_faltas=1 ya existe | 409 |
| TC-MKT-007 | Job otorga beneficio a cliente con 1 mes perfecto | racha=true, regla activa | INSERT cliente_beneficios |
| TC-MKT-008 | Job no duplica beneficio mismo período | beneficio ya otorgado este mes | SKIP |
| TC-MKT-009 | Aplicar beneficio pendiente | PUT /aplicar | estado='aplicado' |
| TC-MKT-010 | Aplicar beneficio ya aplicado | estado='aplicado' | 409 |
| TC-MKT-011 | Cliente ve solo sus beneficios | JWT tipo=cliente | 200 solo los suyos, 403 si pide los de otro |
| TC-MKT-012 | Datos de otra compañía invisibles | JWT compañía 1 | No ve datos de compañía 2 |

---

## 8. Datos semilla (seeds)

```sql
-- Reglas de beneficios para gym de prueba
INSERT INTO marketing.reglas_beneficios
  (id_compania, id_sucursal, meses_sin_faltas, tipo_beneficio, descripcion, valor)
VALUES
  (1, 1, 1, 'descuento', '10% de descuento en próxima membresía',          10.00),
  (1, 1, 3, 'servicio',  '1 sesión gratuita con nutricionista',             null),
  (1, 1, 6, 'regalo',    'Trofeo de asistencia perfecta + foto de progreso', null);
```

---

## 9. Variables de entorno requeridas

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gym_administrator
DB_USER=gym_user
DB_PASSWORD=***

AUTH_SERVICE_URL=http://auth-service:8080
ATTENDANCE_SERVICE_URL=http://attendance-service:8083
CORE_SERVICE_URL=http://core-service:8082

BENEFITS_JOB_CRON=0 20 0 * * *    # 00:20 UTC cada día
```

---

## 10. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | No puede haber dos reglas de beneficio con el mismo `meses_sin_faltas` en el mismo gym | UNIQUE en BD |
| RN-02 | Un cliente no recibe el mismo beneficio dos veces en el mismo período de asistencia | Job diario — verificar antes de INSERT |
| RN-03 | Un beneficio `pendiente` solo se aplica una vez — luego pasa a `aplicado` | PUT /aplicar |
| RN-04 | Una promoción `usada` o `expirada` no aparece en disponibles para nuevas ventas | GET /promociones-disponibles |
| RN-05 | La lógica de qué descuento aplicar al vender (beneficio vs promoción vs ambos) es responsabilidad de Core Service | Core Service llama a este servicio para consultar |
| RN-06 | El job solo otorga beneficios si la regla está `activo=true` | Job diario |

---

*Marketing Service Spec v1.0 · Gym Administrator · Mayo 2026*
