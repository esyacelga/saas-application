# Attendance Service — Especificación de Desarrollo

> **Servicio:** attendance-service  
> **Esquemas BD:** `asistencia`  
> **Tablas:** 3 tablas (asistencias · plantillas_mensajes · mensajes_log)  
> **Depende de:** auth-service (JWT) · platform-service (`/modulos/check`) · core-service (`/membresias/validar-acceso`)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Los tres métodos de registro](#4-los-tres-métodos-de-registro)
5. [Lógica de mensajería automática](#5-lógica-de-mensajería-automática)
6. [API — Contratos de endpoints](#6-api--contratos-de-endpoints)
7. [Flujos principales](#7-flujos-principales)
8. [Casos de prueba](#8-casos-de-prueba)
9. [Datos semilla (seeds)](#9-datos-semilla-seeds)
10. [Variables de entorno requeridas](#10-variables-de-entorno-requeridas)
11. [Reglas de negocio críticas](#11-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Attendance Service gestiona el **registro de entradas al gimnasio** y la **comunicación automática con clientes** en función de sus patrones de asistencia.

**Responsabilidades:**
- Validar el QR token escaneado por el cliente con su app móvil
- Registrar la asistencia llamando primero al Core Service para validar membresía
- Soportar registro manual por recepcionista y (futuro) registro biométrico
- Gestionar plantillas de mensajes por tipo y gym
- Ejecutar el job diario de mensajería automática por WhatsApp/email
- Proveer historial de asistencia y estadísticas al dashboard

**Fuera de alcance:**
- Validar si la membresía está vigente (→ Core Service `/membresias/validar-acceso`)
- Gestionar el QR token de la sucursal (→ Platform Service)
- Beneficios por asistencia perfecta (→ Marketing Service, que consulta este servicio)
- Envío físico de mensajes WhatsApp (→ proveedor externo: Twilio / Meta Cloud API)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `Dueño` / `admin_compania` | `Recepción` | `Entrenador` | `cliente` (app) |
|---|:---:|:---:|:---:|:---:|:---:|
| Registrar entrada por QR | — | — | — | — | ✅ |
| Registrar entrada manual | ✅ | ✅ | ✅ | ❌ | ❌ |
| Ver asistencias de todos los clientes | ✅ | ✅ | ✅ | ✅ (solo sus clientes) | ❌ |
| Ver sus propias asistencias | — | — | — | — | ✅ |
| CRUD plantillas de mensajes | ✅ | ✅ | ❌ | ❌ | ❌ |
| Ver log de mensajes enviados | ✅ | ✅ | ✅ | ❌ | ❌ |
| Enviar mensaje manual | ✅ | ✅ | ✅ | ❌ | ❌ |
| Ver estadísticas del día | ✅ | ✅ | ✅ | ❌ | ❌ |

---

## 3. Tablas involucradas

### asistencia.asistencias
```sql
id               BIGINT  PK, identity
id_compania      INT     NOT NULL
id_sucursal      INT     NOT NULL
id_cliente       INT     FK → core.clientes(id)    NOT NULL
id_membresia     INT     FK → core.membresias(id)  NOT NULL
fecha            DATE    NOT NULL
hora_entrada     TIME    NOT NULL
metodo_registro  VARCHAR(20) NOT NULL DEFAULT 'qr_cliente'
                   CHECK IN ('qr_cliente','biometrico','manual')
UNIQUE (id_membresia, fecha)
```
> El `UNIQUE (id_membresia, fecha)` es la salvaguarda a nivel de BD:  
> — Para membresías `accesos`: impide consumir 2 entradas en el mismo día  
> — Para membresías `calendario`: el registro es informativo pero también es único por día

### asistencia.plantillas_mensajes
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
tipo         VARCHAR(50) NOT NULL
               CHECK IN (
                 'motivacional',
                 'ausencia_2d',
                 'recuperacion_5d',
                 'recuperacion_10d',
                 'recuperacion_15d',
                 'vencimiento_3d',
                 'vencimiento_hoy'
               )
nombre       VARCHAR(100) NOT NULL
contenido    TEXT         NOT NULL
activo       BOOLEAN      NOT NULL DEFAULT TRUE
```
> Cada tipo puede tener **múltiples plantillas activas** — el job elige una al azar.  
> Variables de sustitución en el contenido: `{nombre}`, `{dias}`, `{fecha_vencimiento}`, `{accesos_restantes}`

### asistencia.mensajes_log
```sql
id               BIGINT  PK, identity
id_compania      INT     NOT NULL
id_sucursal      INT     NOT NULL
id_cliente       INT     FK → core.clientes(id)                     NOT NULL
id_plantilla     INT     FK → asistencia.plantillas_mensajes(id)    -- NULL si es mensaje manual
tipo             VARCHAR(50) NOT NULL
canal            VARCHAR(20) NOT NULL CHECK IN ('whatsapp','email','llamada')
contenido        TEXT        NOT NULL    -- contenido final ya con variables sustituidas
estado           VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                   CHECK IN ('pendiente','enviado','fallido')
fecha_programada TIMESTAMPTZ             -- cuándo debe enviarse (para mensajes agendados)
fecha_envio      TIMESTAMPTZ             -- cuándo se envió efectivamente
id_usuario_envio INT                     -- NULL si lo envió el job automático
```

---

## 4. Los tres métodos de registro

### `qr_cliente` — El cliente escanea el QR del gym

```
App móvil del cliente
      │
      ├─ Lee qr_token del cartel en la puerta de la sucursal
      ├─ Envía POST /asistencias/qr { qr_token }
      │    con JWT tipo='cliente'
      │
Attendance Service:
      ├─ Busca tenant.sucursales WHERE qr_token = :token
      │    Si no existe → 404 (QR inválido)
      │    Si qr_token_expira < NOW() → 410 (QR expirado)
      │    Si sucursal.id_compania ≠ JWT.id_compania → 403
      │
      ├─ Llama Core Service: GET /membresias/validar-acceso?id_cliente=X&id_compania=Y
      │    Si no permitido → 403 con razon
      │
      ├─ INSERT asistencia.asistencias
      │    Si viola UNIQUE (id_membresia, fecha) → 409 "Ya registraste tu entrada hoy"
      │
      └─ Responde con confirmación + datos de membresía actualizados
```

### `manual` — El recepcionista registra la entrada

```
Recepcionista en panel web
      │
      ├─ Busca cliente por CI, nombre o codigo_carnet
      ├─ Envía POST /asistencias/manual { id_cliente }
      │    con JWT tipo='staff'
      │
      └─ Mismo flujo desde la validación del Core Service en adelante
         metodo_registro = 'manual'
         id_usuario_registro = JWT.sub
```

### `biometrico` — Sensor biométrico (fase futura)

```
Sensor en puerta
      │
      ├─ Captura plantilla biométrica
      ├─ Envía POST /asistencias/biometrico { hash_capturado, id_sucursal }
      │
Attendance Service:
      ├─ Busca identidad.biometria WHERE hash_datos ≈ :hash AND id_compania = :id
      │    (comparación con librería biométrica, no comparación exacta de bytes)
      ├─ Obtiene id_persona → busca core.clientes WHERE id_persona = X
      └─ Continúa igual que los otros métodos
```

---

## 5. Lógica de mensajería automática

### Escalada de mensajes por ausencia

```
Último registro en asistencias para el cliente
           │
           ▼
   días_ausente = HOY - ultima_asistencia.fecha
   (si nunca asistió: días_ausente = HOY - membresia.fecha_inicio)
           │
     ┌─────┴──────┬──────────┬───────────┬───────────┐
     ▼            ▼          ▼           ▼           ▼
   = 2 días     = 5 días  = 10 días  = 15 días  > 15 días
   ausencia_2d  recup_5d  recup_10d  recup_15d   (sin mensaje,
   WhatsApp     WhatsApp  WhatsApp   WhatsApp +   ya se envió)
                          + alerta   promo
                          al admin   especial
```

### Mensajes de vencimiento de membresía

```
dias_para_vencer = membresia.fecha_fin - HOY

Si = 3 → tipo 'vencimiento_3d'
Si = 0 → tipo 'vencimiento_hoy'

Para modo 'accesos':
  accesos_restantes = dias_acceso_total - COUNT(asistencias)
  Si accesos_restantes = 3 → tipo 'vencimiento_3d' (con variable {accesos_restantes})
  Si accesos_restantes = 0 → tipo 'vencimiento_hoy'
```

### Reglas anti-spam del job

Para evitar enviar el mismo mensaje múltiples veces:

```
Antes de INSERT en mensajes_log, verificar:

SELECT COUNT(*) FROM asistencia.mensajes_log
WHERE id_cliente = :id
  AND tipo = :tipo
  AND fecha_programada >= :ultima_asistencia_del_cliente

Si COUNT > 0 → ya fue enviado en este ciclo de ausencia → SKIP
```
> Cuando el cliente vuelve a asistir, su `ultima_asistencia` avanza, reiniciando el ciclo.

### Selección de plantilla (aleatorio)

```sql
SELECT contenido FROM asistencia.plantillas_mensajes
WHERE id_compania = :id_compania
  AND tipo = :tipo
  AND activo = TRUE
ORDER BY RANDOM()
LIMIT 1;
```
> Si no hay plantilla del tipo solicitado → el job usa el texto por defecto del sistema y registra una advertencia en los logs.

### Variables de sustitución en plantillas

| Variable | Se reemplaza por |
|---|---|
| `{nombre}` | `identidad.personas.nombre` del cliente |
| `{dias}` | Días de ausencia |
| `{fecha_vencimiento}` | `membresias.fecha_fin` formateada |
| `{accesos_restantes}` | `dias_acceso_total - COUNT(asistencias)` |
| `{gym_nombre}` | `tenant.companias.nombre` |
| `{gym_whatsapp}` | `tenant.companias.whatsapp` |

---

## 6. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 6.1 Registro de Asistencia

#### `POST /asistencias/qr` — Entrada por QR (cliente desde app)
```json
// Requiere: JWT tipo='cliente'
// Request
{
  "qr_token": "gym-1-abc123xyz"
}

// Response 201 — entrada registrada
{
  "id_asistencia": 500,
  "fecha": "2026-05-19",
  "hora_entrada": "07:42:00",
  "sucursal": "Sede Principal",
  "membresia": {
    "tipo": "Tarjeta 22 días",
    "modo_control": "accesos",
    "accesos_usados": 6,
    "accesos_restantes": 16,
    "fecha_fin": "2026-08-19"
  }
}

// Response 409 — ya registrado hoy
{
  "codigo": "ya_registrado_hoy",
  "mensaje": "Ya registraste tu entrada hoy",
  "primera_entrada": "07:42:00"
}

// Response 403 — membresía sin acceso
{
  "codigo": "membresia_vencida",
  "mensaje": "Tu membresía venció el 2026-05-18",
  "ultima_membresia_fin": "2026-05-18"
}

// Response 404 — QR inválido
// Response 410 — QR expirado (qr_token_expira < NOW())
// Response 403 — QR de otro gym
```

#### `POST /asistencias/manual` — Entrada manual (recepcionista)
```json
// Requiere: JWT staff + permiso asistencia:crear
// Request
{
  "id_cliente": 5,
  "fecha": "2026-05-19",
  "hora_entrada": "09:15:00"
}

// Response 201 — igual que QR pero con metodo_registro='manual'

// Response 409 — ya existe entrada ese día
// Response 403 — membresía sin acceso (aun así puede continuar con override)
```

#### `POST /asistencias/manual/override` — Forzar entrada sin membresía válida
```json
// Requiere: JWT staff + rol Dueño o admin_compania
// Para casos excepcionales: cliente que viene a pagar y entrar
// Request
{
  "id_cliente": 5,
  "fecha": "2026-05-19",
  "hora_entrada": "09:15:00",
  "motivo_override": "Cliente viene a renovar y entrar hoy"
}
// Response 201 — registrado con motivo
// Genera alerta en bitácora de seguridad
```

---

### 6.2 Consulta de Asistencias

#### `GET /clientes/{id}/asistencias` — Historial de un cliente
```
// Query params: ?desde=2026-05-01&hasta=2026-05-31&id_membresia=12
// Requiere: permiso asistencia:leer O JWT tipo=cliente (solo la suya)

// Response 200
{
  "cliente": { "id": 5, "nombre": "María López" },
  "total_en_periodo": 12,
  "asistencias": [
    {
      "id": 500,
      "fecha": "2026-05-19",
      "hora_entrada": "07:42:00",
      "sucursal": "Sede Principal",
      "metodo_registro": "qr_cliente"
    }
  ]
}
```

#### `GET /clientes/{id}/asistencias/ultimos-30` — Mapa de calor últimos 30 días
```json
// Usado por el dashboard y la app del cliente

// Response 200
{
  "cliente_id": 5,
  "dias_asistidos": 12,
  "dias_ausente": 18,
  "racha_actual": 3,
  "racha_maxima_mes": 7,
  "detalle": [
    { "fecha": "2026-05-19", "asistio": true,  "hora": "07:42:00" },
    { "fecha": "2026-05-18", "asistio": true,  "hora": "08:15:00" },
    { "fecha": "2026-05-17", "asistio": true,  "hora": "07:58:00" },
    { "fecha": "2026-05-16", "asistio": false, "hora": null },
    ...
  ]
}
```

#### `GET /asistencias/hoy` — Asistencias del día (dashboard)
```
// Query params: ?id_sucursal=1

// Response 200
{
  "fecha": "2026-05-19",
  "total_entradas": 47,
  "por_metodo": {
    "qr_cliente": 42,
    "manual": 4,
    "biometrico": 1
  },
  "ultimas_entradas": [
    { "hora": "09:14:00", "cliente": "Carlos Ruiz",  "metodo": "qr_cliente" },
    { "hora": "09:10:00", "cliente": "Ana Pérez",    "metodo": "manual" }
  ]
}
```

#### `GET /asistencias/estadisticas` — KPIs de asistencia
```
// Query params: ?periodo=mes&año=2026&mes=5

// Response 200
{
  "periodo": "2026-05",
  "total_entradas": 850,
  "promedio_diario": 28,
  "clientes_activos": 95,
  "clientes_sin_asistir_7d": 18,
  "clientes_sin_asistir_15d": 7,
  "dia_mas_concurrido": { "fecha": "2026-05-06", "entradas": 52 },
  "hora_pico": "07:00-08:00"
}
```

---

### 6.3 Plantillas de Mensajes

#### `GET /plantillas` — Listar plantillas del gym
```json
// Filtrado automático por id_compania del JWT

// Response 200
[
  {
    "id": 1,
    "tipo": "ausencia_2d",
    "nombre": "Motivacional suave",
    "contenido": "Hola {nombre} 👋 Te extrañamos en {gym_nombre}. ¡Hoy es un buen día para volver!",
    "activo": true
  },
  {
    "id": 2,
    "tipo": "ausencia_2d",
    "nombre": "Motivacional energético",
    "contenido": "¡{nombre}! 💪 Llevas {dias} días sin entrenar. Tu mejor versión te está esperando.",
    "activo": true
  }
]
```

#### `POST /plantillas` — Crear plantilla
```json
// Request
{
  "tipo": "recuperacion_5d",
  "nombre": "Recuperación semana",
  "contenido": "Hey {nombre}, llevamos {dias} días sin verte 😔 ¿Todo bien? En {gym_nombre} te esperamos con los brazos abiertos. 🏋️"
}
// Response 201
```

#### `PUT /plantillas/{id}` — Actualizar plantilla
```json
{ "contenido": "nuevo texto", "activo": true }
// Response 200
```

#### `DELETE /plantillas/{id}` — Eliminar plantilla
```
// Response 204
// 409 si es la única plantilla activa del tipo
```

---

### 6.4 Log de Mensajes

#### `GET /mensajes` — Consultar log de mensajes
```
// Query params: ?id_cliente=5&tipo=ausencia_2d&estado=fallido&desde=2026-05-01

// Response 200
{
  "total": 45,
  "datos": [
    {
      "id": 210,
      "cliente": "María López",
      "tipo": "ausencia_2d",
      "canal": "whatsapp",
      "contenido": "Hola María 👋 Te extrañamos...",
      "estado": "enviado",
      "fecha_envio": "2026-05-17T00:15:00Z",
      "automatico": true
    }
  ]
}
```

#### `POST /mensajes/enviar` — Enviar mensaje manual
```json
// Requiere: JWT staff + permiso asistencia:crear
// Request
{
  "id_cliente": 5,
  "canal": "whatsapp",
  "id_plantilla": 3
}

// Response 201
{
  "id": 220,
  "contenido_enviado": "Hola María 👋 Te extrañamos...",
  "estado": "enviado"
}
```

#### `POST /mensajes/reenviar/{id}` — Reintentar mensaje fallido
```json
// Response 200
// Solo aplicable a mensajes con estado='fallido'
// 422 si mensaje ya fue enviado exitosamente
```

---

## 7. Flujos principales

### Flujo: Registro por QR (camino feliz)

```
App móvil del cliente
      │
      ├─► POST /asistencias/qr { qr_token: "gym-1-abc123xyz" }
      │         JWT tipo='cliente', id_compania=1, id_persona=10
      │
Attendance Service:
      │
      ├─ 1. Busca sucursal por qr_token
      │       tenant.sucursales WHERE qr_token = 'gym-1-abc123xyz'
      │       → id_sucursal=1, id_compania=1
      │
      ├─ 2. Verifica JWT.id_compania == sucursal.id_compania
      │
      ├─ 3. Llama Core Service:
      │       GET /membresias/validar-acceso?id_cliente=5&id_compania=1
      │       → { permitido: true, id_membresia: 13, modo_control: 'accesos',
      │            dias_acceso_restantes: 17 }
      │
      ├─ 4. INSERT asistencia.asistencias
      │       { id_compania:1, id_sucursal:1, id_cliente:5, id_membresia:13,
      │         fecha:HOY, hora_entrada:NOW(), metodo_registro:'qr_cliente' }
      │       Si UNIQUE viola → 409 "ya registrado hoy"
      │
      └─► Response 201 con datos de membresía actualizados
```

### Flujo: Job diario de mensajería (00:15 UTC)

```
Para cada cliente con membresía activa:
      │
      ├─ Calcula días_ausente:
      │    ultima = MAX(fecha) FROM asistencias WHERE id_cliente=:id
      │    dias_ausente = HOY - ultima (o fecha_inicio si nunca asistió)
      │
      ├─ Determina tipo de mensaje:
      │    dias_ausente=2  → 'ausencia_2d'
      │    dias_ausente=5  → 'recuperacion_5d'
      │    dias_ausente=10 → 'recuperacion_10d' + alerta admin
      │    dias_ausente=15 → 'recuperacion_15d'
      │
      ├─ Verifica anti-spam:
      │    ¿Ya se envió este tipo desde la ultima asistencia?
      │    Si sí → SKIP
      │
      ├─ Selecciona plantilla aleatoria activa del tipo
      │
      ├─ Sustituye variables {nombre}, {dias}, etc.
      │
      ├─ INSERT mensajes_log (estado='pendiente')
      │
      ├─ Llama proveedor de mensajería (Twilio / Meta API)
      │    Éxito → UPDATE estado='enviado', fecha_envio=NOW()
      │    Error  → UPDATE estado='fallido'
      │
      └─ Repite para mensajes de vencimiento de membresía
```

### Flujo: Verificación de racha perfecta (inter-servicio)

```
Marketing Service llama a este servicio para calcular beneficios:

GET /clientes/{id}/asistencias/racha-perfecta?meses=1

      ├─ Cuenta días hábiles de asistencia en los últimos N meses
      ├─ Verifica si asistió todos los días que tenía membresía activa
      └─ Responde { racha_perfecta: true/false, dias_asistidos: 28, dias_con_membresia: 30 }
```

---

## 8. Casos de prueba

### TC-QR — Registro por QR

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-QR-001 | Escaneo válido, membresía activa calendario | QR válido, membresía vigente | 201, entrada registrada |
| TC-QR-002 | Escaneo válido, membresía accesos con saldo | 5 usados de 22 | 201, accesos_restantes=17 |
| TC-QR-003 | Doble escaneo mismo día | 2do escaneo mismo día | 409 "ya registrado hoy" |
| TC-QR-004 | QR token inválido | token no existe en BD | 404 |
| TC-QR-005 | QR token expirado | qr_token_expira < NOW() | 410 |
| TC-QR-006 | QR de otro gym | JWT.id_compania ≠ sucursal.id_compania | 403 |
| TC-QR-007 | Membresía vencida | estado='vencida' | 403 razon=membresia_vencida |
| TC-QR-008 | Accesos agotados | 22/22 usados | 403 razon=accesos_agotados |
| TC-QR-009 | Membresía congelada | estado='congelada' | 403 razon=membresia_congelada |
| TC-QR-010 | Sin membresía | cliente sin contrato activo | 403 razon=sin_membresia |
| TC-QR-011 | JWT staff intenta usar endpoint QR | JWT tipo='staff' | 403 |
| TC-QR-012 | Modo accesos: el UNIQUE impide 2 entradas mismo día | INSERT directo a BD | UNIQUE constraint violation |

### TC-MAN — Registro Manual

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-MAN-001 | Registro manual exitoso | JWT staff, cliente con membresía | 201 metodo='manual' |
| TC-MAN-002 | Recepción registra doble entrada | mismo cliente, mismo día | 409 |
| TC-MAN-003 | Cliente JWT app intenta registro manual | JWT tipo='cliente' | 403 |
| TC-MAN-004 | Override sin rol Dueño | JWT Recepción | 403 |
| TC-MAN-005 | Override con rol Dueño, membresía vencida | JWT Dueño, motivo obligatorio | 201 + alerta bitácora |

### TC-ASI — Consultas de asistencia

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-ASI-001 | Historial cliente con filtro de fechas | ?desde=2026-05-01&hasta=2026-05-31 | Solo asistencias del período |
| TC-ASI-002 | Últimos 30 días incluye días sin asistir | cliente con 12 asistencias en 30 días | 30 entradas, 12 asistio=true |
| TC-ASI-003 | Cliente ve solo sus asistencias | JWT tipo=cliente, pide /clientes/5 | 200 si es la suya, 403 si es otra |
| TC-ASI-004 | Dashboard hoy por sucursal | ?id_sucursal=1 | Total del día filtrado por sede |
| TC-ASI-005 | Estadísticas mes con cero asistencias | gym recién creado | totales en 0, sin error |

### TC-PLT — Plantillas de mensajes

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-PLT-001 | Crear plantilla con variables válidas | contenido con {nombre} | 201 |
| TC-PLT-002 | Listar solo plantillas de mi gym | JWT gym 1 | No devuelve plantillas del gym 2 |
| TC-PLT-003 | Eliminar plantilla única del tipo | único activo de 'ausencia_2d' | 409 (no quedaría ninguna) |
| TC-PLT-004 | Eliminar cuando hay 2+ del mismo tipo | 2 activas del tipo | 204 OK |
| TC-PLT-005 | Job usa plantilla del gym, no del sistema | gym con plantilla propia | Usa la del gym |
| TC-PLT-006 | Job usa texto default si no hay plantilla | sin plantilla para el tipo | Texto default, sin error |

### TC-MSG — Mensajería automática (job)

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-MSG-001 | Cliente 2 días sin asistir | ultima_asistencia = hace 2 días | INSERT mensajes_log tipo=ausencia_2d |
| TC-MSG-002 | Cliente 5 días sin asistir | dias_ausente = 5 | INSERT tipo=recuperacion_5d |
| TC-MSG-003 | Cliente 10 días sin asistir | dias_ausente = 10 | INSERT tipo=recuperacion_10d + alerta admin |
| TC-MSG-004 | Anti-spam: no reenviar mismo tipo en mismo ciclo | mensaje ya enviado desde ultima asistencia | SKIP, sin duplicado |
| TC-MSG-005 | Ciclo reinicia al volver a asistir | cliente vuelve después de 5 días | Siguiente ausencia empieza desde 0 |
| TC-MSG-006 | Vencimiento calendario 3 días | fecha_fin = HOY+3 | INSERT tipo=vencimiento_3d |
| TC-MSG-007 | Vencimiento accesos 3 restantes | accesos_restantes = 3 | INSERT tipo=vencimiento_3d |
| TC-MSG-008 | Vencimiento hoy | fecha_fin = HOY | INSERT tipo=vencimiento_hoy |
| TC-MSG-009 | Variables sustituidas correctamente | {nombre}, {dias} en contenido | Texto final con valores reales |
| TC-MSG-010 | Mensaje fallido queda en log | proveedor retorna error | estado='fallido' en mensajes_log |
| TC-MSG-011 | Reenvío de mensaje fallido | POST /mensajes/reenviar/{id} | Nuevo intento, estado actualizado |
| TC-MSG-012 | No enviar a clientes congelados | estado='congelado' | SKIP para mensajes de ausencia |

---

## 9. Datos semilla (seeds)

```sql
-- Plantillas de mensajes para gym de prueba (id_compania=1, id_sucursal=1)

-- Ausencia 2 días (motivacional suave)
INSERT INTO asistencia.plantillas_mensajes (id_compania, id_sucursal, tipo, nombre, contenido) VALUES
(1, 1, 'ausencia_2d', 'Motivacional suave',
 'Hola {nombre} 👋 Te extrañamos en {gym_nombre}. ¡Hoy es un buen día para volver! 💪'),

(1, 1, 'ausencia_2d', 'Motivacional energético',
 '¡{nombre}! Llevas {dias} días sin entrenar. Tu mejor versión te está esperando. 🏋️'),

-- Recuperación 5 días
(1, 1, 'recuperacion_5d', 'Recuperación semana',
 'Hey {nombre} 😊 Llevamos {dias} días sin verte. ¿Todo bien? En {gym_nombre} te esperamos.'),

-- Recuperación 10 días
(1, 1, 'recuperacion_10d', 'Recuperación urgente',
 '{nombre}, han pasado {dias} días 😟 Recuerda que tu membresía vence el {fecha_vencimiento}. ¡Vuelve pronto!'),

-- Recuperación 15 días
(1, 1, 'recuperacion_15d', 'Oferta de recuperación',
 '{nombre}, llevamos {dias} días sin verte 💙 Tenemos una oferta especial para ti. Escríbenos al {gym_whatsapp}.'),

-- Vencimiento 3 días
(1, 1, 'vencimiento_3d', 'Aviso vencimiento',
 'Hola {nombre} ⏰ Tu membresía vence en {dias} días ({fecha_vencimiento}). ¡Renueva y sigue sin parar!'),

-- Vencimiento hoy
(1, 1, 'vencimiento_hoy', 'Vencimiento hoy',
 '{nombre}, tu membresía venció hoy 😥 Acércate a {gym_nombre} o escríbenos para renovar. ¡Te esperamos!');
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
CORE_SERVICE_URL=http://core-service:8082

# Proveedor de mensajería WhatsApp
WHATSAPP_PROVIDER=twilio              # twilio | meta
TWILIO_ACCOUNT_SID=***
TWILIO_AUTH_TOKEN=***
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886

# Proveedor de email
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USER=apikey
SMTP_PASSWORD=***
EMAIL_FROM=noreply@gymadministrator.com

# Cron jobs
MESSAGING_JOB_CRON=0 15 0 * * *    # 00:15 UTC cada día (después del job de Core)

# Override de registro manual sin membresía
ALLOW_MANUAL_OVERRIDE=true           # false en ambientes prod más restrictivos
```

---

## 11. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | Antes de registrar asistencia, siempre validar membresía en Core Service | POST /asistencias/* |
| RN-02 | `UNIQUE (id_membresia, fecha)` impide que modo `accesos` consuma 2 entradas en 1 día | INSERT asistencias (validado por BD) |
| RN-03 | El QR token se valida contra `tenant.sucursales` — si expiró (`qr_token_expira < NOW()`) → 410 | POST /asistencias/qr |
| RN-04 | JWT del cliente debe pertenecer al mismo `id_compania` que el QR escaneado | POST /asistencias/qr |
| RN-05 | El job de mensajería no envía a clientes con estado='congelado' | Job diario |
| RN-06 | Anti-spam: un mismo tipo de mensaje no se reenvía hasta que el cliente asista de nuevo | Job diario |
| RN-07 | Si no existe plantilla activa para el tipo, usar texto default del sistema — nunca fallar silenciosamente | Job diario |
| RN-08 | El override de entrada sin membresía requiere rol Dueño y queda registrado en bitácora | POST /asistencias/manual/override |
| RN-09 | Los mensajes de vencimiento aplican tanto a modo `calendario` (por fecha) como a `accesos` (por entradas restantes) | Job diario |
| RN-10 | `mensajes_log` es inmutable — no se puede editar ni eliminar registros, solo agregar nuevos | Todos los endpoints de mensajes |

---

*Attendance Service Spec v1.0 · Gym Administrator · Mayo 2026*
