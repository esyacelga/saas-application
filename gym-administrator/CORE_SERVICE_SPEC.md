# Core Service — Especificación de Desarrollo

> **Servicio:** core-service  
> **Esquemas BD:** `core`  
> **Tablas:** 4 tablas (clientes · tipos_membresia · membresias · congelamientos)  
> **Depende de:** auth-service (JWT) · platform-service (`/modulos/check`) · auth-service (`identidad.personas` lectura)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Los dos modelos de membresía](#4-los-dos-modelos-de-membresía)
5. [Estados del cliente](#5-estados-del-cliente)
6. [API — Contratos de endpoints](#6-api--contratos-de-endpoints)
7. [Flujos principales](#7-flujos-principales)
8. [Casos de prueba](#8-casos-de-prueba)
9. [Datos semilla (seeds)](#9-datos-semilla-seeds)
10. [Variables de entorno requeridas](#10-variables-de-entorno-requeridas)
11. [Reglas de negocio críticas](#11-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Core Service gestiona el **núcleo operativo del gimnasio**: clientes, tipos de membresía, contratos de membresía y congelamientos.

Expone además el endpoint de **validación de acceso** que el Attendance Service llama en cada escaneo de QR para determinar si la membresía del cliente está vigente.

**Responsabilidades:**
- Registrar clientes (buscando o creando la persona global en `identidad.personas`)
- Gestionar el catálogo de tipos de membresía del gym
- Vender, renovar y anular membresías
- Congelar y reactivar membresías con compensación de días
- Mantener actualizado el estado de cada cliente (job diario)
- Exponer validación de acceso para el Attendance Service

**Fuera de alcance:**
- Registro de asistencia (→ Attendance Service)
- Cobros y reportes financieros (→ Finance Service)
- Envío de mensajes WhatsApp (→ Notification Service, disparado desde Attendance)
- Promociones y beneficios (→ Marketing Service)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `admin_compania` / `Dueño` | `Recepción` | `Entrenador` | `Contador` | `cliente` |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| CRUD tipos de membresía | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Registrar cliente nuevo | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Ver ficha de cliente | ✅ | ✅ | ✅ | ✅ (solo sus) | ❌ | ✅ (solo la suya) |
| Editar datos del cliente | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Vender membresía | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Anular membresía | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Congelar membresía | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Aprobar congelamiento retroactivo | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Reactivar congelamiento | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Ver historial de membresías | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ (solo la suya) |

> **Permiso requerido para módulo:** `clientes:leer` / `clientes:crear` / `membresias:crear` etc.  
> Este servicio verifica con Platform Service que el gym tenga los módulos `clientes` y `membresias` activos.

---

## 3. Tablas involucradas

### core.clientes
```sql
id            INT     PK, identity
id_persona    INT     FK → identidad.personas(id)  NOT NULL
id_compania   INT     NOT NULL                     -- ref. lógica (sin FK)
id_sucursal   INT     NOT NULL                     -- sucursal donde se registró
peso_kg       DECIMAL(5,2)                         -- datos físicos opcionales
altura_cm     DECIMAL(5,1)
objetivos     TEXT
lesiones      TEXT                                 -- info para entrenadores
estado        VARCHAR(20) NOT NULL DEFAULT 'activo'
                CHECK IN ('activo','proximo_vencer','vencido','congelado','riesgo_abandono')
fecha_ingreso DATE    NOT NULL DEFAULT CURRENT_DATE
codigo_carnet VARCHAR(100) UNIQUE                  -- código para búsqueda manual en recepción
created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at    TIMESTAMPTZ
UNIQUE (id_persona, id_compania)                   -- una ficha por persona por gym
```

### core.tipos_membresia
```sql
id             INT     PK, identity
id_compania    INT     NOT NULL
id_sucursal    INT     NOT NULL
nombre         VARCHAR(100) NOT NULL
modo_control   VARCHAR(20)  NOT NULL DEFAULT 'calendario'
                 CHECK IN ('calendario','accesos')
duracion_tipo  VARCHAR(20)  NOT NULL
                 CHECK IN ('dias','semanas','meses','años')
duracion_valor INT     NOT NULL CHECK (duracion_valor > 0)
dias_acceso    INT     CHECK (dias_acceso IS NULL OR dias_acceso > 0)
                         -- solo usado cuando modo_control='accesos'
                         -- define cuántas entradas trae la tarjeta (ej: 22)
precio         DECIMAL(10,2) NOT NULL CHECK (precio >= 0)
activo         BOOLEAN NOT NULL DEFAULT TRUE
CONSTRAINT chk_accesos_requiere_dias CHECK (
  modo_control <> 'accesos' OR dias_acceso IS NOT NULL
)
UNIQUE (id_compania, nombre)
```

### core.membresias
```sql
id                  INT     PK, identity
id_compania         INT     NOT NULL
id_sucursal         INT     NOT NULL
id_cliente          INT     FK → core.clientes(id)         NOT NULL
id_tipo_membresia   INT     FK → core.tipos_membresia(id)  NOT NULL
id_metodo_pago      INT                -- ref. lógica a config.metodos_pago.id
id_usuario_registro INT                -- ref. lógica a seguridad.usuarios.id
fecha_inicio        DATE    NOT NULL
fecha_fin           DATE    NOT NULL   -- vencimiento calendario o plazo máximo para accesos
dias_acceso_total   INT                -- NULL para calendario; copiado de tipos_membresia al comprar
precio_pagado       DECIMAL(10,2) NOT NULL
descuento_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0
estado              VARCHAR(20)   NOT NULL DEFAULT 'activa'
                      CHECK IN ('activa','vencida','congelada','anulada')
created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
```
> `dias_acceso_usados` **NO se almacena** — se calcula con:  
> `SELECT COUNT(*) FROM asistencia.asistencias WHERE id_membresia = :id`

### core.congelamientos
```sql
id                  INT     PK, identity
id_compania         INT     NOT NULL
id_sucursal         INT     NOT NULL
id_membresia        INT     FK → core.membresias(id)  NOT NULL
fecha_inicio        DATE    NOT NULL
fecha_fin           DATE                    -- NULL mientras está activo el congelamiento
motivo              VARCHAR(30) CHECK IN ('viaje','lesion','enfermedad','voluntario','otro')
detalle             TEXT
retroactivo         BOOLEAN NOT NULL DEFAULT FALSE
documento_respaldo  VARCHAR(255)            -- URL del documento (certificado médico, etc.)
aprobado_por        INT                     -- ref. lógica a seguridad.usuarios.id
fecha_aprobacion    DATE
id_usuario_registro INT
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
CONSTRAINT chk_retroactivo CHECK (
  retroactivo = FALSE
  OR (documento_respaldo IS NOT NULL AND aprobado_por IS NOT NULL)
)
```

---

## 4. Los dos modelos de membresía

### Modo `calendario` — vence por fecha

```
Cliente paga membresía mensual (ej: 30 días × $35)
        │
        ├─ fecha_inicio = hoy
        ├─ fecha_fin    = hoy + 30 días
        ├─ dias_acceso_total = NULL
        │
        └─ Membresía activa mientras: fecha_inicio ≤ HOY ≤ fecha_fin
           Si el cliente no va, la fecha sigue corriendo
           El registro de asistencia es opcional (para seguimiento y beneficios)
```

### Modo `accesos` — vence por entradas consumidas

```
Cliente compra tarjeta de 22 entradas × $35 (válida hasta en 3 meses)
        │
        ├─ fecha_inicio      = hoy
        ├─ fecha_fin         = hoy + 3 meses  (plazo máximo)
        ├─ dias_acceso_total = 22             (copiado de tipos_membresia)
        │
        └─ Membresía activa mientras:
             accesos_usados < 22  (COUNT de asistencias)
             Y fecha_fin ≥ HOY    (no expiró el plazo)
           
           Si el cliente no va: NO descuenta entradas
           El registro de entrada ES OBLIGATORIO para consumir un acceso
           UNIQUE (id_membresia, fecha) → máximo 1 entrada por día por membresía
```

### Tabla comparativa

| Concepto | `calendario` | `accesos` |
|---|---|---|
| Vence por | Fecha | Entradas consumidas o fecha límite |
| `dias_acceso_total` | NULL | Copiado de `tipos_membresia.dias_acceso` |
| El tiempo corre si no va | Sí | No (solo corre el plazo máximo) |
| Registro asistencia | Opcional | Obligatorio para consumir acceso |
| `dias_acceso_usados` | No aplica | `COUNT(asistencias WHERE id_membresia=x)` |

---

### Ejemplos de tipos de membresía (`modo_control = 'calendario'`)

Estos son los tipos de membresía más comunes por tiempo. El backend calcula `fecha_fin`
al momento de la venta usando `duracion_valor` y `duracion_tipo`.

#### Mensual
```
nombre         = 'Mensual'
modo_control   = 'calendario'
duracion_valor = 1
duracion_tipo  = 'meses'
dias_acceso    = NULL

Ejemplo de venta el 2026-06-14:
  fecha_inicio = 2026-06-14
  fecha_fin    = 2026-07-14      ← fecha_inicio + 1 mes
  dias restantes hoy = fecha_fin - CURRENT_DATE = 30
```

#### Trimestral
```
nombre         = 'Trimestral'
modo_control   = 'calendario'
duracion_valor = 3
duracion_tipo  = 'meses'
dias_acceso    = NULL

Ejemplo de venta el 2026-06-14:
  fecha_inicio = 2026-06-14
  fecha_fin    = 2026-09-14      ← fecha_inicio + 3 meses
  dias restantes hoy = fecha_fin - CURRENT_DATE = 92
```

#### Anual
```
nombre         = 'Anual'
modo_control   = 'calendario'
duracion_valor = 1
duracion_tipo  = 'años'
dias_acceso    = NULL

Ejemplo de venta el 2026-06-14:
  fecha_inicio = 2026-06-14
  fecha_fin    = 2027-06-14      ← fecha_inicio + 1 año
  dias restantes hoy = fecha_fin - CURRENT_DATE = 365
```

#### Cómo el backend calcula `fecha_fin` al vender

```java
LocalDate fechaInicio = LocalDate.now();
LocalDate fechaFin = switch (tipoMembresia.getDuracionTipo()) {
    case "dias"    -> fechaInicio.plusDays(tipoMembresia.getDuracionValor());
    case "semanas" -> fechaInicio.plusWeeks(tipoMembresia.getDuracionValor());
    case "meses"   -> fechaInicio.plusMonths(tipoMembresia.getDuracionValor());
    case "años"    -> fechaInicio.plusYears(tipoMembresia.getDuracionValor());
};
// fecha_fin queda guardada en core.membresias — no se recalcula después
```

> `fecha_fin` se fija en el momento de la venta y no cambia salvo congelamientos.
> Los días restantes se obtienen siempre como `fecha_fin - CURRENT_DATE` en cualquier consulta.

---

### Ejemplos de tipos de membresía (`modo_control = 'accesos'`)

El cliente paga por un número fijo de entradas, no por tiempo. El tiempo solo sirve
como plazo máximo — si no usa todas las entradas antes de `fecha_fin`, las pierde.

#### Tarjeta 12 entradas (válida 2 meses)
```
nombre         = 'Tarjeta 12 entradas'
modo_control   = 'accesos'
duracion_valor = 2
duracion_tipo  = 'meses'       ← plazo máximo para usar las entradas
dias_acceso    = 12            ← entradas incluidas

Ejemplo de venta el 2026-06-14:
  fecha_inicio      = 2026-06-14
  fecha_fin         = 2026-08-14   ← plazo máximo (no puede ir después de esta fecha)
  dias_acceso_total = 12           ← se copia de tipos_membresia al crear la membresía

  Después de 5 visitas:
    accesos_usados    = COUNT(asistencias WHERE id_membresia = x) = 5
    accesos_restantes = 12 - 5 = 7
    membresía sigue activa porque 7 > 0 y fecha_fin >= hoy
```

#### Tarjeta 22 entradas (válida 3 meses)
```
nombre         = 'Tarjeta 22 entradas'
modo_control   = 'accesos'
duracion_valor = 3
duracion_tipo  = 'meses'
dias_acceso    = 22

Ejemplo de venta el 2026-06-14:
  fecha_inicio      = 2026-06-14
  fecha_fin         = 2026-09-14
  dias_acceso_total = 22
```

#### Regla de vencimiento para modo `accesos`

La membresía expira cuando ocurre **cualquiera** de estas dos condiciones:

```
accesos_usados >= dias_acceso_total   → agotó entradas
   O
CURRENT_DATE > fecha_fin              → venció el plazo máximo
```

#### Diferencia clave frente a `calendario`

```
calendario → si el cliente no va, el tiempo corre igual. Pierde días.
accesos    → si el cliente no va, NO descuenta entradas. Solo corre el plazo máximo.

Por eso la asistencia en modo 'accesos' es OBLIGATORIA registrarla:
cada INSERT en asistencia.asistencias consume 1 entrada.
La restricción UNIQUE (id_membresia, fecha) garantiza máximo 1 entrada por día.
```

---

## 5. Estados del cliente

El job diario recalcula el estado de cada cliente basándose en su membresía activa y patrones de asistencia.

```
                    membresía activa
                         │
          ┌──────────────┼────────────────┐
          ▼              ▼                ▼
   proximo_vencer      activo         congelado
   (≤3 días para       (normal)       (congelamiento
    vencer)                            activo)
          │
          ▼
        vencido ──(sin renovar + 15d sin asistir)──► riesgo_abandono
```

| Estado | Condición de transición | Acción del sistema |
|---|---|---|
| `activo` | Membresía vigente sin otras alertas | Normal |
| `proximo_vencer` | `fecha_fin - HOY ≤ 3 días` | WhatsApp de aviso |
| `vencido` | `fecha_fin < HOY` o accesos agotados | WhatsApp de vencimiento |
| `congelado` | Congelamiento activo sin `fecha_fin` | Sin mensajes |
| `riesgo_abandono` | Vencido + sin asistencias en 15 días | Campaña de recuperación |

### Lógica del job diario (cron)

```
Cada día a las 00:10 UTC (después del job de suscripciones):

Para cada cliente activo/proximo_vencer/vencido (no congelado):

  1. Busca membresía activa:
       SELECT * FROM core.membresias
       WHERE id_cliente = :id AND estado = 'activa'
       ORDER BY created_at DESC LIMIT 1

  2. Si no existe membresía activa → estado = 'vencido'

  3. Si modo_control = 'calendario':
       dias_restantes = fecha_fin - HOY
       Si dias_restantes < 0  → UPDATE membresias SET estado='vencida'
                                  UPDATE clientes SET estado='vencido'
       Si dias_restantes <= 3 → UPDATE clientes SET estado='proximo_vencer'
       Sino                   → UPDATE clientes SET estado='activo'

  4. Si modo_control = 'accesos':
       accesos_usados = COUNT(asistencias WHERE id_membresia = :id)
       Si accesos_usados >= dias_acceso_total OR fecha_fin < HOY:
            UPDATE membresias SET estado='vencida'
            UPDATE clientes SET estado='vencido'
       Sino:
            accesos_restantes = dias_acceso_total - accesos_usados
            Si accesos_restantes <= 3 → UPDATE clientes SET estado='proximo_vencer'
            Sino                      → UPDATE clientes SET estado='activo'

  5. Si estado='vencido':
       ultima_asistencia = MAX(fecha) FROM asistencias WHERE id_cliente=:id
       Si HOY - ultima_asistencia >= 15 → UPDATE clientes SET estado='riesgo_abandono'
```

---

## 6. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 6.1 Tipos de Membresía

#### `GET /tipos-membresia` — Listar tipos activos del gym
```json
// Filtrado automático por id_compania del JWT

// Response 200
[
  {
    "id": 1,
    "nombre": "Mensual",
    "modo_control": "calendario",
    "duracion_tipo": "meses",
    "duracion_valor": 1,
    "dias_acceso": null,
    "precio": 35.00,
    "activo": true
  },
  {
    "id": 2,
    "nombre": "Tarjeta 22 días",
    "modo_control": "accesos",
    "duracion_tipo": "meses",
    "duracion_valor": 3,
    "dias_acceso": 22,
    "precio": 35.00,
    "activo": true
  }
]
```

#### `POST /tipos-membresia` — Crear tipo
```json
// Requiere permiso: membresias:crear
// Request
{
  "nombre": "Tarjeta 30 días",
  "modo_control": "accesos",
  "duracion_tipo": "meses",
  "duracion_valor": 3,
  "dias_acceso": 30,
  "precio": 40.00
}
// Response 201
// 400 si modo_control='accesos' y dias_acceso es null
// 409 si nombre duplicado en la compañía
```

#### `PUT /tipos-membresia/{id}` — Actualizar
```json
// Request (solo campos a cambiar)
{ "precio": 38.00 }
// Response 200
// Nota: el cambio de precio NO afecta membresías ya vendidas
```

#### `PUT /tipos-membresia/{id}/desactivar`
```
// Response 200
// 409 si hay membresías activas de este tipo
```

---

### 6.2 Clientes

#### `GET /clientes` — Listar clientes del gym
```
// Query params: ?estado=activo&buscar=maria&page=1&limit=20
// Filtrado automático por id_compania del JWT

// Response 200
{
  "total": 150,
  "pagina": 1,
  "datos": [
    {
      "id": 1,
      "nombre": "María López",
      "ci": "1001234567",
      "telefono": "0991234567",
      "estado": "activo",
      "membresia_activa": {
        "tipo": "Mensual",
        "modo_control": "calendario",
        "fecha_fin": "2026-06-18",
        "dias_restantes": 30
      }
    }
  ]
}
```

#### `POST /clientes` — Registrar cliente nuevo
```json
// Requiere permiso: clientes:crear
// Request
{
  "ci": "1001234567",
  "nombre": "María López",
  "telefono": "0991234567",
  "correo": "maria@gmail.com",
  "fecha_nacimiento": "1990-05-15",
  "peso_kg": 62.5,
  "altura_cm": 165.0,
  "objetivos": "Bajar de peso y tonificar",
  "lesiones": "Rodilla derecha operada 2022",
  "id_sucursal": 1
}

// Response 201
{
  "id_cliente": 5,
  "id_persona": 10,
  "persona_existia": true
}

// Lógica interna:
// 1. Busca identidad.personas WHERE ci = :ci
//    Si no existe → crea la persona primero
// 2. Verifica que no tenga ya una ficha en esta compañía
// 3. INSERT core.clientes
// 4. Genera codigo_carnet único

// 409 si la persona ya es cliente de este gym
```

#### `GET /clientes/{id}` — Ficha completa del cliente
```json
// Response 200
{
  "id": 5,
  "persona": {
    "ci": "1001234567",
    "nombre": "María López",
    "telefono": "0991234567",
    "correo": "maria@gmail.com",
    "foto_url": null
  },
  "peso_kg": 62.5,
  "altura_cm": 165.0,
  "objetivos": "Bajar de peso y tonificar",
  "lesiones": "Rodilla derecha operada 2022",
  "estado": "activo",
  "fecha_ingreso": "2026-01-10",
  "codigo_carnet": "GYM1-00005",
  "membresia_activa": {
    "id": 12,
    "tipo": "Mensual",
    "modo_control": "calendario",
    "fecha_inicio": "2026-05-19",
    "fecha_fin": "2026-06-18",
    "dias_restantes": 30,
    "estado": "activa"
  }
}
```

#### `PUT /clientes/{id}` — Actualizar datos del cliente
```json
// Request (solo campos editables — CI y id_persona son inmutables)
{
  "peso_kg": 60.0,
  "objetivos": "Mantenimiento",
  "telefono": "0999999999"
}
// Response 200
// updated_at se actualiza automáticamente
```

#### `GET /clientes/ci/{ci}` — Buscar por cédula
```json
// Útil para recepción: verificar si la persona ya existe antes de registrarla
// Response 200 — incluye si ya es cliente en este gym
{
  "persona": { "id": 10, "ci": "1001234567", "nombre": "María López" },
  "es_cliente_en_este_gym": true,
  "id_cliente": 5
}
// 404 si la CI no existe en identidad.personas
```

---

### 6.3 Membresías

#### `GET /clientes/{id}/membresias` — Historial de membresías
```json
// Response 200
[
  {
    "id": 12,
    "tipo": "Mensual",
    "modo_control": "calendario",
    "fecha_inicio": "2026-05-19",
    "fecha_fin": "2026-06-18",
    "precio_pagado": 35.00,
    "descuento_aplicado": 0,
    "estado": "activa"
  },
  {
    "id": 8,
    "tipo": "Mensual",
    "fecha_inicio": "2026-04-19",
    "fecha_fin": "2026-05-18",
    "precio_pagado": 35.00,
    "estado": "vencida"
  }
]
```

#### `POST /clientes/{id}/membresias` — Vender membresía
```json
// Requiere permiso: membresias:crear
// Request
{
  "id_tipo_membresia": 1,
  "fecha_inicio": "2026-05-19",
  "id_metodo_pago": 2,
  "descuento_aplicado": 0
}

// Response 201
{
  "id": 13,
  "fecha_inicio": "2026-05-19",
  "fecha_fin": "2026-06-18",
  "precio_pagado": 35.00,
  "dias_acceso_total": null,
  "modo_control": "calendario"
}

// Lógica interna:
// 1. Calcula fecha_fin según duracion_tipo + duracion_valor
// 2. Si modo='accesos': copia dias_acceso → dias_acceso_total
// 3. Precio = tipos_membresia.precio - descuento
// 4. UPDATE clientes SET estado='activo', updated_at=NOW()

// 409 si ya tiene una membresía activa (debe anular primero)
```

#### `GET /membresias/{id}` — Detalle de membresía
```json
// Response 200 — para modo 'accesos' incluye conteo en tiempo real
{
  "id": 13,
  "tipo": "Tarjeta 22 días",
  "modo_control": "accesos",
  "fecha_inicio": "2026-05-19",
  "fecha_fin": "2026-08-19",
  "dias_acceso_total": 22,
  "dias_acceso_usados": 5,
  "dias_acceso_restantes": 17,
  "precio_pagado": 35.00,
  "estado": "activa",
  "congelamiento_activo": null
}
```

#### `PUT /membresias/{id}/anular` — Anular membresía
```json
// Requiere: rol Dueño o admin_compania
// Request
{ "motivo": "Solicitud del cliente" }
// Response 200
// UPDATE membresias SET estado='anulada'
// UPDATE clientes SET estado='vencido'
```

---

### 6.4 Congelamientos

#### `POST /membresias/{id}/congelar` — Congelar membresía
```json
// Request
{
  "fecha_inicio": "2026-05-20",
  "motivo": "viaje",
  "detalle": "Viaje de trabajo por 2 semanas",
  "retroactivo": false
}

// Response 201
{
  "id_congelamiento": 3,
  "fecha_inicio": "2026-05-20",
  "fecha_fin": null
}

// Efectos:
// INSERT core.congelamientos
// UPDATE core.membresias SET estado='congelada'
// UPDATE core.clientes SET estado='congelado'

// 409 si la membresía ya está congelada
// 422 si retroactivo=true y falta documento_respaldo o aprobado_por
```

#### `POST /membresias/{id}/congelar` — Congelamiento retroactivo
```json
// Request
{
  "fecha_inicio": "2026-05-10",
  "motivo": "enfermedad",
  "detalle": "Hospitalización 10 días",
  "retroactivo": true,
  "documento_respaldo": "https://storage/certificado-medico.pdf",
  "aprobado_por": 42
}
// Response 201
// Requiere: rol con permiso de aprobación (Dueño / admin_compania)
```

#### `PUT /congelamientos/{id}/reactivar` — Reactivar membresía

```json
// Response 200
{
  "fecha_fin_anterior": "2026-06-18",
  "dias_compensados": 14,
  "fecha_fin_nueva": "2026-07-02"
}

// Lógica interna:
// dias_congelados = HOY - congelamiento.fecha_inicio
// UPDATE congelamientos SET fecha_fin = HOY
// UPDATE membresias SET
//   estado = 'activa',
//   fecha_fin = fecha_fin + dias_congelados
// UPDATE clientes SET estado = 'activo' (o 'proximo_vencer' según nueva fecha_fin)
```

#### `GET /membresias/{id}/congelamientos` — Historial de congelamientos
```json
// Response 200
[
  {
    "id": 3,
    "fecha_inicio": "2026-05-20",
    "fecha_fin": "2026-06-03",
    "dias_congelados": 14,
    "motivo": "viaje",
    "retroactivo": false
  }
]
```

---

### 6.5 Validación de acceso (uso inter-servicio)

El Attendance Service llama a este endpoint en **cada escaneo de QR** antes de registrar la asistencia.

#### `GET /membresias/validar-acceso`
```
// Query params: ?id_cliente=5&id_compania=1
// Headers: Authorization: Bearer {JWT inter-servicio}

// Response 200 — acceso permitido
{
  "permitido": true,
  "id_membresia": 13,
  "modo_control": "accesos",
  "dias_acceso_restantes": 17,
  "fecha_fin": "2026-08-19"
}

// Response 403 — membresía vencida
{
  "permitido": false,
  "razon": "membresia_vencida",
  "ultima_membresia_fin": "2026-05-18"
}

// Response 403 — accesos agotados
{
  "permitido": false,
  "razon": "accesos_agotados",
  "accesos_usados": 22,
  "accesos_total": 22
}

// Response 403 — membresía congelada
{
  "permitido": false,
  "razon": "membresia_congelada"
}

// Response 404 — sin membresía
{
  "permitido": false,
  "razon": "sin_membresia"
}
```

---

## 7. Flujos principales

### Flujo: Registrar cliente y vender primera membresía

```
Recepcionista
      │
      ├─► GET /clientes/ci/1001234567
      │       │
      │       ├─ Persona existe, ya es cliente → ir directo a venta
      │       ├─ Persona existe, NO es cliente → POST /clientes (sin datos de persona)
      │       └─ Persona no existe → POST /clientes (con todos los datos)
      │
      └─► POST /clientes/{id}/membresias
              │
              ├─ Calcula fecha_fin
              ├─ Si modo='accesos': copia dias_acceso como dias_acceso_total
              ├─ INSERT membresias
              └─ UPDATE clientes SET estado='activo'
```

### Flujo: Reactivar membresía congelada

```
PUT /congelamientos/{id}/reactivar
        │
        ├─ dias_congelados = HOY - fecha_inicio_congelamiento
        │
        ├─ UPDATE congelamientos SET fecha_fin = HOY
        │
        ├─ UPDATE membresias SET
        │    estado = 'activa'
        │    fecha_fin = fecha_fin_original + dias_congelados
        │
        └─ UPDATE clientes SET estado = recalcular_estado()
             Si nueva fecha_fin - HOY <= 3 → proximo_vencer
             Sino → activo
```

### Flujo: Validación en escaneo QR (llamada desde Attendance)

```
Attendance Service llama GET /membresias/validar-acceso?id_cliente=5
        │
        ├─ Busca membresía activa del cliente en la compañía
        │
        ├─ Si modo='calendario':
        │    Válido si fecha_inicio ≤ HOY ≤ fecha_fin Y estado='activa'
        │
        ├─ Si modo='accesos':
        │    accesos_usados = COUNT(asistencias WHERE id_membresia = :id)
        │    Válido si accesos_usados < dias_acceso_total
        │             Y fecha_fin ≥ HOY
        │             Y estado = 'activa'
        │
        └─ Responde permitido: true/false con contexto
```

---

## 8. Casos de prueba

### TC-TIPO — Tipos de membresía

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-TIPO-001 | Crear tipo calendario | modo=calendario, sin dias_acceso | 201 |
| TC-TIPO-002 | Crear tipo accesos con dias_acceso | modo=accesos, dias_acceso=22 | 201 |
| TC-TIPO-003 | Crear tipo accesos sin dias_acceso | modo=accesos, dias_acceso=null | 400 |
| TC-TIPO-004 | Nombre duplicado en el mismo gym | nombre ya existente | 409 |
| TC-TIPO-005 | Mismo nombre en diferente gym | id_compania distinto | 201 (OK) |
| TC-TIPO-006 | Desactivar tipo sin membresías activas | tipo sin uso activo | 200 |
| TC-TIPO-007 | Desactivar tipo con membresías activas | tipo en uso | 409 |
| TC-TIPO-008 | Cambiar precio no afecta contratos vigentes | PUT precio | Membresías existentes sin cambio |

### TC-CLI — Clientes

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-CLI-001 | Registrar cliente con CI nueva | datos completos | 201, crea persona + cliente |
| TC-CLI-002 | Registrar cliente con CI existente | CI ya en identidad.personas | 201, reutiliza persona, `persona_existia: true` |
| TC-CLI-003 | CI ya es cliente en este gym | misma persona, mismo gym | 409 |
| TC-CLI-004 | CI ya es cliente en otro gym | misma persona, gym diferente | 201 (OK, gyms independientes) |
| TC-CLI-005 | Buscar por CI existente | GET /ci/1001234567 | 200 + datos + es_cliente=true/false |
| TC-CLI-006 | Buscar por CI inexistente | CI no registrada | 404 |
| TC-CLI-007 | Filtrar clientes por estado | ?estado=proximo_vencer | Solo clientes con ese estado |
| TC-CLI-008 | Buscar por nombre parcial | ?buscar=mar | Clientes que contengan "mar" |
| TC-CLI-009 | Entrenador ve solo sus clientes | JWT rol=Entrenador | Filtrado por entrenador asignado |
| TC-CLI-010 | Cliente ve solo su propia ficha | JWT tipo=cliente, GET /clientes/5 | 200 si es la suya, 403 si es otra |

### TC-MEM — Membresías

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-MEM-001 | Vender membresía calendario | tipo=calendario, 1 mes | fecha_fin = fecha_inicio + 30 días |
| TC-MEM-002 | Vender membresía accesos | tipo=accesos, 22 días | dias_acceso_total = 22, fecha_fin = +3 meses |
| TC-MEM-003 | Vender cuando ya hay membresía activa | cliente con membresía activa | 409 |
| TC-MEM-004 | Precio con descuento | descuento_aplicado = 5.00 | precio_pagado = precio_base - 5 |
| TC-MEM-005 | Anular membresía activa | PUT /anular con motivo | estado='anulada', cliente estado='vencido' |
| TC-MEM-006 | Recepcionista no puede anular | JWT rol=Recepción | 403 |
| TC-MEM-007 | Conteo de accesos en tiempo real | modo=accesos, 5 asistencias | dias_acceso_usados=5, restantes=17 |
| TC-MEM-008 | Cambio de tipo no afecta precio ya cobrado | membresía vendida | precio_pagado inmutable |

### TC-VAL — Validación de acceso

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-VAL-001 | Calendario vigente | HOY dentro del período | permitido=true |
| TC-VAL-002 | Calendario vencido | fecha_fin = ayer | permitido=false, razon=membresia_vencida |
| TC-VAL-003 | Accesos con entradas disponibles | 5 usadas de 22 | permitido=true, restantes=17 |
| TC-VAL-004 | Accesos agotados | 22 usadas de 22 | permitido=false, razon=accesos_agotados |
| TC-VAL-005 | Accesos dentro de límite pero plazo vencido | 5 usadas, fecha_fin=ayer | permitido=false, razon=membresia_vencida |
| TC-VAL-006 | Membresía congelada | estado=congelada | permitido=false, razon=membresia_congelada |
| TC-VAL-007 | Sin membresía | cliente sin contrato | permitido=false, razon=sin_membresia |

### TC-CONG — Congelamientos

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-CONG-001 | Congelar membresía activa | motivo=viaje | 201, membresía pasa a congelada |
| TC-CONG-002 | Congelar membresía ya congelada | estado=congelada | 409 |
| TC-CONG-003 | Congelar membresía vencida | estado=vencida | 422 |
| TC-CONG-004 | Reactivar compensa días correctamente | 14 días congelado | fecha_fin + 14 días |
| TC-CONG-005 | Retroactivo sin documento | retroactivo=true, sin documento | 422 (CONSTRAINT de BD) |
| TC-CONG-006 | Retroactivo con documento y aprobador | datos completos | 201 |
| TC-CONG-007 | Recepcionista no puede aprobar retroactivo | JWT rol=Recepción | 403 |
| TC-CONG-008 | Reactivar actualiza estado cliente | reactivar con 30 días restantes | estado='activo' |
| TC-CONG-009 | Reactivar con ≤3 días restantes | nueva fecha_fin = HOY + 2 | estado='proximo_vencer' |

### TC-JOB — Job diario de estados

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-JOB-001 | Membresía con 3 días → proximo_vencer | fecha_fin = HOY + 3 | cliente estado='proximo_vencer' |
| TC-JOB-002 | Membresía vencida ayer | fecha_fin = ayer | membresia estado='vencida', cliente='vencido' |
| TC-JOB-003 | Vencido + 15 días sin asistir | sin asistencias en 15 días | cliente estado='riesgo_abandono' |
| TC-JOB-004 | Accesos agotados → vencida | 22/22 accesos usados | membresía='vencida', cliente='vencido' |
| TC-JOB-005 | Cliente congelado no cambia estado | congelamiento activo | estado='congelado' preservado |

---

## 9. Datos semilla (seeds)

```sql
-- Tipos de membresía para gym de prueba (id_compania=1, id_sucursal=1)
INSERT INTO core.tipos_membresia
  (id_compania, id_sucursal, nombre, modo_control, duracion_tipo, duracion_valor, dias_acceso, precio)
VALUES
  (1, 1, 'Mensual',        'calendario', 'meses', 1, NULL, 35.00),
  (1, 1, 'Trimestral',     'calendario', 'meses', 3, NULL, 90.00),
  (1, 1, 'Tarjeta 22 días','accesos',    'meses', 3,   22, 35.00),
  (1, 1, 'Tarjeta 30 días','accesos',    'meses', 3,   30, 40.00);

-- Cliente de prueba (usando persona de seeds del Auth Service)
INSERT INTO core.clientes
  (id_persona, id_compania, id_sucursal, peso_kg, altura_cm, objetivos, codigo_carnet)
VALUES
  (1, 1, 1, 62.5, 165.0, 'Bajar de peso', 'GYM1-00001');

-- Membresía activa de prueba (modo calendario)
INSERT INTO core.membresias
  (id_compania, id_sucursal, id_cliente, id_tipo_membresia, fecha_inicio, fecha_fin,
   dias_acceso_total, precio_pagado, estado)
VALUES
  (1, 1, 1, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', NULL, 35.00, 'activa');
```

---

## 10. Variables de entorno requeridas

```env
# Base de datos
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gym_administrator
DB_USER=gym_user
DB_PASSWORD=***

# Servicios internos
AUTH_SERVICE_URL=http://auth-service:8080
PLATFORM_SERVICE_URL=http://platform-service:8081

# Cron de estados de clientes
CLIENT_STATUS_JOB_CRON=0 10 0 * * *    # 00:10 UTC cada día

# Código de carnet
CARNET_PREFIX=GYM                        # prefijo para codigo_carnet generado
```

---

## 11. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | `dias_acceso_usados` nunca se almacena — siempre se calcula con `COUNT(asistencias)` | GET /membresias/{id} y validar-acceso |
| RN-02 | `dias_acceso_total` se copia de `tipos_membresia` al vender — no cambia si el tipo cambia después | POST /membresias |
| RN-03 | No puede haber dos membresías activas simultáneas para el mismo cliente | POST /membresias |
| RN-04 | El congelamiento retroactivo requiere `documento_respaldo` Y `aprobado_por` — validado por `CONSTRAINT` en BD | POST /congelar |
| RN-05 | Al reactivar un congelamiento, `fecha_fin` de la membresía se extiende exactamente los días congelados | PUT /reactivar |
| RN-06 | Para modo `accesos`, `UNIQUE(id_membresia, fecha)` en asistencias impide consumir 2 accesos en un mismo día | Validado por BD, no por este servicio |
| RN-07 | El estado del cliente es calculado — nunca se actualiza directamente por la API, solo por el job diario o eventos de membresía | Job diario |
| RN-08 | Una persona puede ser cliente en múltiples gyms — `UNIQUE(id_persona, id_compania)` permite múltiples fichas | POST /clientes |
| RN-09 | Cambiar el precio de un `tipo_membresia` no altera membresías ya vendidas | PUT /tipos-membresia/{id} |
| RN-10 | `admin_compania` y staff solo pueden operar sobre clientes de su propio `id_compania` | Todos los endpoints |

---

*Core Service Spec v1.0 · Gym Administrator · Mayo 2026*
