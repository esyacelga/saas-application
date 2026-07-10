# Platform Service — Especificación de Desarrollo

> **ESTADO:** ✅ **Actualizado 2026-07-10** — Spec de diseño de un servicio YA implementado. El **código es la fuente de verdad**; se verifica aquí contra commits 6bd7f0b–c1a5b75 (REQ-SAAS-001 Sub-fases 1.1–1.5). Para la API real ver `docs/<servicio>/` y el CLAUDE.md del servicio. Ver [../../STATUS.md](../../STATUS.md).

> **Servicio:** platform-service  
> **Esquemas BD:** `saas` · `tenant`  
> **Tablas:** 9 tablas (3 saas + 6 tenant)  
> **Depende de:** auth-service (JWT válido en cada request)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Ciclo de vida de la suscripción](#4-ciclo-de-vida-de-la-suscripción)
5. [Middleware de acceso a módulos](#5-middleware-de-acceso-a-módulos)
6. [API — Contratos de endpoints](#6-api--contratos-de-endpoints)
7. [Flujos principales](#7-flujos-principales)
8. [Casos de prueba](#8-casos-de-prueba)
9. [Datos semilla (seeds)](#9-datos-semilla-seeds)
10. [Variables de entorno requeridas](#10-variables-de-entorno-requeridas)
11. [Reglas de negocio críticas](#11-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Platform Service gestiona **todo el ciclo de vida de un gym como cliente de la plataforma SaaS**: desde su registro hasta la suspensión, pasando por la asignación de planes, cobros y notificaciones de vencimiento.

También expone el **endpoint de validación de módulos** que todos los demás servicios llaman en cada request para verificar que el gym tiene un plan activo que incluye el módulo solicitado.

**Responsabilidades:**
- Gestionar el catálogo de planes y características (super_admin)
- Registrar nuevos gimnasios en la plataforma
- Gestionar sucursales y el QR token de entrada por sede
- Controlar el estado de la suscripción de cada gym
- Registrar pagos de suscripción
- Configurar y disparar notificaciones de vencimiento
- Exponer el middleware de validación de acceso a módulos

**Fuera de alcance:**
- Autenticación de usuarios (→ Auth Service)
- Operación interna del gym: clientes, membresías (→ Core Service)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `soporte` | `viewer` | `admin_compania` |
|---|:---:|:---:|:---:|:---:|
| CRUD planes y características | ✅ | ❌ | ❌ | ❌ |
| Registrar nuevo gym | ✅ | ❌ | ❌ | ❌ |
| Ver todas las compañías | ✅ | ✅ | ✅ | ❌ |
| Ver / editar su propia compañía | ✅ | ✅ | ✅ | ✅ (solo la suya) |
| Crear / editar sucursales | ✅ | ❌ | ❌ | ✅ (solo las suyas) |
| Renovar QR token de sucursal | ✅ | ❌ | ❌ | ✅ (solo las suyas) |
| Gestionar suscripción (upgrade/downgrade) | ✅ | ❌ | ❌ | ❌ |
| Ver historial de suscripción | ✅ | ✅ | ✅ | ✅ (solo la suya) |
| Registrar / confirmar pagos | ✅ | ✅ | ❌ | ❌ |
| Suspender compañía | ✅ | ❌ | ❌ | ❌ |
| Configurar alertas de vencimiento | ✅ | ❌ | ❌ | ✅ (solo la suya) |

---

## 3. Tablas involucradas

### saas.planes
```sql
id             INT     PK, identity
nombre         VARCHAR(100)  NOT NULL
descripcion    TEXT
precio_mensual DECIMAL(10,2) NOT NULL
activo         BOOLEAN       NOT NULL DEFAULT TRUE
created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
```
> Catálogo global — sin `id_compania`. Gestionado solo por el operador de la plataforma.

### saas.caracteristicas
```sql
id      INT    PK, identity
codigo  VARCHAR(50)  NOT NULL UNIQUE   -- clave que usan los otros servicios para validar acceso
nombre  VARCHAR(100) NOT NULL
modulo  VARCHAR(50)  NOT NULL          -- clientes | membresias | finanzas | marketing | inventario
activo  BOOLEAN      NOT NULL DEFAULT TRUE
```
> El `codigo` es el identificador que el middleware de módulos compara contra el plan activo.  
> Ejemplo: `codigo = 'finanzas'` → el plan incluye el módulo de finanzas.

### saas.plan_caracteristicas
```sql
id_plan           INT  FK → saas.planes(id)
id_caracteristica INT  FK → saas.caracteristicas(id)
PRIMARY KEY (id_plan, id_caracteristica)
```

### tenant.companias
```sql
id         INT    PK, identity
nombre     VARCHAR(150) NOT NULL
ruc        VARCHAR(20)  NOT NULL UNIQUE
logo_url   VARCHAR(255)
telefono   VARCHAR(20)
whatsapp   VARCHAR(20)
correo     VARCHAR(150)
activo     BOOLEAN      NOT NULL DEFAULT TRUE
created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
```

### tenant.sucursales
```sql
id              INT    PK, identity
id_compania     INT    FK → tenant.companias(id)  NOT NULL
nombre          VARCHAR(150) NOT NULL
direccion       VARCHAR(255)
es_principal    BOOLEAN      NOT NULL DEFAULT FALSE
activo          BOOLEAN      NOT NULL DEFAULT TRUE
qr_token        VARCHAR(100) UNIQUE               -- token que los clientes escanean al entrar
qr_token_expira TIMESTAMPTZ                       -- NULL = no expira
created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
```
> El `qr_token` es generado por este servicio (UUID v4 o token aleatorio).  
> El Attendance Service lo consume para validar el escaneo del cliente.

### tenant.compania_planes
```sql
id                    INT    PK, identity
id_compania           INT    FK → tenant.companias(id)  NOT NULL
id_plan               INT    FK → saas.planes(id)       NOT NULL
fecha_inicio          DATE   NOT NULL
fecha_fin             DATE   NOT NULL
dias_gracia           INT    NOT NULL DEFAULT 5
fecha_ultimo_pago     DATE
motivo_suspension     TEXT
estado                VARCHAR(20) NOT NULL
                        CHECK IN ('activo','en_gracia','vencido','suspendido','cancelado','programado')
tipo_cambio           VARCHAR(20) NOT NULL
                        CHECK IN ('nuevo','renovacion','upgrade','downgrade')
id_compania_plan_orig INT    FK → tenant.compania_planes(id)  -- plan anterior al que reemplaza
credito_monto         DECIMAL(10,2) NOT NULL DEFAULT 0        -- crédito a favor por downgrade
created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
```
> **Inmutabilidad:** nunca se hace UPDATE del estado. Cada cambio es una nueva fila.  
> La fila activa es la que tiene `estado IN ('activo','en_gracia')`.

### tenant.pagos_suscripcion
```sql
id               INT    PK, identity
id_compania_plan INT    FK → tenant.compania_planes(id)  NOT NULL
monto            DECIMAL(10,2) NOT NULL
fecha_pago       DATE   NOT NULL
periodo_desde    DATE
periodo_hasta    DATE
metodo_pago      VARCHAR(30) CHECK IN ('efectivo','transferencia','tarjeta')
tipo_pago        VARCHAR(30) CHECK IN ('pago_completo','diferencia_upgrade','credito_downgrade','renovacion')
estado           VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                   CHECK IN ('pagado','fallido','pendiente')
referencia       VARCHAR(100)    -- número de transacción bancaria
created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### tenant.config_notif_suscripcion
```sql
id_compania  INT         NOT NULL
dias_antes   INT         NOT NULL    -- ej: 7, 3, 1 → alertar cuando quedan N días
canal        VARCHAR(20) NOT NULL CHECK IN ('email','whatsapp','ambos')
activo       BOOLEAN     NOT NULL DEFAULT TRUE
PRIMARY KEY (id_compania, dias_antes)
```
> Cada gym configura cuándo y por qué canal quiere recibir alertas de vencimiento.

### tenant.notificaciones_suscripcion
```sql
id               INT    PK, identity
id_compania_plan INT    FK → tenant.compania_planes(id)  NOT NULL
dias_antes       INT    NOT NULL
canal            VARCHAR(20) NOT NULL CHECK IN ('email','whatsapp')
estado           VARCHAR(20) NOT NULL DEFAULT 'pendiente' CHECK IN ('pendiente','enviado','fallido')
fecha_envio      TIMESTAMPTZ
```
> Generadas por el job diario de notificaciones. Solo lectura vía API.

---

## 4. Ciclo de vida de la suscripción

```
                    ┌─────────────────────────────────────────┐
                    │            ESTADOS POSIBLES              │
                    └─────────────────────────────────────────┘

  [Registro]──► activo ──(fecha_fin alcanzada)──► en_gracia ──(gracia agotada)──► vencido
                  │                                                                    │
                  │◄──────────────────── renovacion (nueva fila) ───────────────────►│
                  │
                  ├──(upgrade)──► cancelado  +  nueva fila activo (tipo=upgrade)
                  │                              pago: diferencia de precio
                  │
                  ├──(downgrade)──► cancelado  +  nueva fila programado (tipo=downgrade)
                  │                               efectivo en fecha_inicio del nuevo plan
                  │                               pago: credito_monto a favor
                  │
                  └──(admin suspende)──► suspendido
```

### Tabla de estados

| Estado | Acceso a módulos | Qué ve el admin del gym |
|---|---|---|
| `activo` | Completo según plan | Normal |
| `en_gracia` | Completo según plan | Banner: "Renueva en X días" |
| `vencido` | Ninguno | Pantalla de renovación |
| `programado` | Ninguno (aún no empieza) | Invisible hasta su `fecha_inicio` |
| `cancelado` | Ninguno | Reemplazado por upgrade/downgrade |
| `suspendido` | Ninguno | Pantalla de contacto a soporte |

### Lógica del job diario (cron)

```
Cada día a las 00:05 UTC:
  1. UPDATE compania_planes SET estado='en_gracia'
     WHERE estado='activo' AND fecha_fin < CURRENT_DATE

  2. UPDATE compania_planes SET estado='vencido'
     WHERE estado='en_gracia'
     AND fecha_fin + dias_gracia < CURRENT_DATE

  3. UPDATE compania_planes SET estado='activo'
     WHERE estado='programado' AND fecha_inicio <= CURRENT_DATE

  4. Para cada compania_plan activo/en_gracia:
     Calcular días restantes
     Si coincide con config_notif_suscripcion.dias_antes:
       INSERT notificaciones_suscripcion + disparar envío
```

---

## 5. Middleware de acceso a módulos

Todos los servicios (Core, Attendance, Finance, etc.) llaman a este endpoint **en cada request** para verificar que el gym tiene plan activo con el módulo habilitado.

```
GET /modulos/check?id_compania=1&codigo=finanzas
Authorization: Bearer {JWT inter-servicio}

Response 200 → acceso permitido
Response 403 → plan no incluye este módulo
Response 402 → plan vencido o suspendido
```

### Lógica interna del check

```sql
SELECT c.codigo
FROM tenant.compania_planes cp
JOIN saas.plan_caracteristicas pc ON pc.id_plan = cp.id_plan
JOIN saas.caracteristicas c       ON c.id = pc.id_caracteristica
WHERE cp.id_compania = :id_compania
  AND cp.estado IN ('activo', 'en_gracia')
  AND c.codigo = :codigo
  AND c.activo = TRUE
LIMIT 1;
```

> Este endpoint debe tener **caché por compañía** (TTL 5 minutos) para no golpear la BD en cada request del sistema.

---

## 6. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 6.1 Planes

**Requiere:** JWT tipo `plataforma` con `rol_plataforma = super_admin`

#### `GET /planes` — Listar planes activos
```json
// Response 200
[
  {
    "id": 1,
    "nombre": "Básico",
    "descripcion": "Funciones esenciales para gyms pequeños",
    "precio_mensual": 29.99,
    "activo": true,
    "caracteristicas": [
      { "codigo": "clientes", "nombre": "Gestión de clientes", "modulo": "clientes" },
      { "codigo": "membresias", "nombre": "Membresías", "modulo": "membresias" }
    ]
  }
]
```

#### `POST /planes` — Crear plan
```json
// Request
{
  "nombre": "Premium",
  "descripcion": "Todas las funcionalidades",
  "precio_mensual": 59.99,
  "id_caracteristicas": [1, 2, 3, 4, 5]
}
// Response 201
```

#### `PUT /planes/{id}` — Actualizar plan
```json
// Request (campos a modificar)
{ "precio_mensual": 64.99 }
// Response 200
// Nota: cambiar precio NO afecta contratos activos — solo nuevas suscripciones
```

#### `PUT /planes/{id}/caracteristicas` — Asignar características (bulk)
```json
// Request
{ "id_caracteristicas": [1, 2, 3, 4, 5, 6] }
// Response 200
```

#### `PUT /planes/{id}/desactivar`
```
// Response 200
// 409 si hay compañías con plan activo en este plan
```

---

### 6.2 Características

**Requiere:** JWT tipo `plataforma`

#### `GET /caracteristicas` — Listar todas
```json
// Response 200
[
  { "id": 1, "codigo": "clientes",    "nombre": "Gestión de clientes",   "modulo": "clientes" },
  { "id": 2, "codigo": "membresias",  "nombre": "Membresías",            "modulo": "membresias" },
  { "id": 3, "codigo": "asistencia",  "nombre": "Control de asistencia", "modulo": "asistencia" },
  { "id": 4, "codigo": "finanzas",    "nombre": "Módulo de finanzas",    "modulo": "finanzas" },
  { "id": 5, "codigo": "marketing",   "nombre": "Promociones y beneficios", "modulo": "marketing" },
  { "id": 6, "codigo": "inventario",  "nombre": "Inventario y ventas",   "modulo": "inventario" }
]
```

#### `POST /caracteristicas` — Crear característica
```json
// Request
{ "codigo": "reportes_avanzados", "nombre": "Reportes avanzados", "modulo": "reportes" }
// Response 201
// 409 si codigo ya existe
```

---

### 6.3 Compañías

#### `GET /companias` — Listar
```
// JWT plataforma → todas las compañías
// JWT staff (admin_compania) → solo la suya (filtro automático por id_compania del JWT)

// Response 200
[
  {
    "id": 1,
    "nombre": "Gym Power Quito",
    "ruc": "1792345678001",
    "telefono": "022345678",
    "whatsapp": "0991234567",
    "correo": "info@gympower.com",
    "activo": true,
    "plan_activo": {
      "nombre": "Premium",
      "estado": "activo",
      "fecha_fin": "2026-06-18",
      "dias_restantes": 30
    }
  }
]
```

#### `POST /companias` — Registrar nuevo gym
```json
// Requiere: JWT plataforma super_admin
// Request
{
  "nombre": "Gym Power Quito",
  "ruc": "1792345678001",
  "telefono": "022345678",
  "whatsapp": "0991234567",
  "correo": "info@gympower.com",
  "plan_inicial": {
    "id_plan": 1,
    "fecha_inicio": "2026-05-19",
    "meses": 1
  },
  "sucursal_principal": {
    "nombre": "Sede Principal",
    "direccion": "Av. Amazonas N35-17"
  }
}

// Response 201
{
  "id_compania": 1,
  "id_compania_plan": 1,
  "id_sucursal": 1,
  "qr_token": "gym-1-abc123xyz"
}

// 409 si RUC ya existe
```
> Al registrar un gym, el servicio crea automáticamente:  
> 1. La fila en `tenant.companias`  
> 2. La fila en `tenant.compania_planes` con `tipo_cambio='nuevo'`  
> 3. La sucursal principal con su `qr_token` generado

#### `POST /companias/wizard` — Registro completo (onboarding)
```json
// Requiere: JWT plataforma super_admin
// Extiende POST /companias: además crea rol SUPER_ADMIN, permisos y usuarios del gym.
// El campo id_persona referencia identidad.personas(id) — el nombre del usuario se obtiene
// de esa tabla; no se almacena nombre en seguridad.usuarios directamente.
// Request
{
  "nombre": "Gym Power Quito",
  "ruc": "1792345678001",
  "telefono": "022345678",
  "whatsapp": "0991234567",
  "correo": "info@gympower.com",
  "id_plan": 1,
  "nombre_sucursal": "Sede Principal",
  "direccion_sucursal": "Av. Amazonas N35-17",
  "usuario_principal": {
    "id_persona": 10,
    "correo": "admin@gympower.com",
    "password": "Password123"
  },
  "usuarios_adicionales": [
    { "id_persona": 11, "correo": "recep@gympower.com", "password": "Password456" }
  ]
}

// Response 201
{
  "id_compania": 1,
  "id_compania_plan": 1,
  "id_sucursal": 1,
  "qr_token": "gym-1-abc123xyz",
  "usuario_principal": {
    "id": 1,
    "id_persona": 10,
    "correo": "admin@gympower.com"
  },
  "usuarios_creados": 2
}

// 409 si RUC ya existe
// 400 si falta id_persona, correo o password en usuario_principal
```
> El wizard ejecuta en secuencia atómica:  
> 1. `tenant.companias` + `tenant.compania_planes` + `tenant.sucursales` + notif por defecto  
> 2. Rol `SUPER_ADMIN` en `seguridad.roles` con todos los permisos en `seguridad.permisos`  
> 3. Usuarios en `seguridad.usuarios` con `id_persona` FK → `identidad.personas(id)`

#### `PUT /companias/{id}` — Actualizar datos
```json
// Request (campos editables por admin_compania)
{
  "nombre": "Gym Power Quito Norte",
  "telefono": "022345679",
  "whatsapp": "0992222222",
  "logo_url": "https://cdn.gympower.com/logo.png"
}
// Response 200
// admin_compania solo puede editar su propia compañía
```

#### `PUT /companias/{id}/suspender`
```json
// Requiere: JWT plataforma super_admin
// Request
{ "motivo": "Falta de pago por 30 días" }
// Response 200
// Efecto: UPDATE compania_planes SET estado='suspendido', motivo_suspension=?
//         WHERE id_compania=? AND estado IN ('activo','en_gracia')
```

---

### 6.4 Sucursales

#### `GET /companias/{id_compania}/sucursales` — Listar sucursales
```json
// Response 200
[
  {
    "id": 1,
    "nombre": "Sede Principal",
    "direccion": "Av. Amazonas N35-17",
    "es_principal": true,
    "activo": true,
    "qr_token": "gym-1-abc123xyz",
    "qr_token_expira": null
  }
]
```

#### `POST /companias/{id_compania}/sucursales` — Crear sucursal
```json
// Request
{
  "nombre": "Sede Norte",
  "direccion": "Av. Brasil y Gaspar de Villarroel",
  "es_principal": false
}
// Response 201 — incluye qr_token generado automáticamente
```

#### `PUT /sucursales/{id}` — Actualizar datos
```json
{ "nombre": "Sede Norte Quito", "direccion": "nueva dirección" }
// Response 200
```

#### `POST /sucursales/{id}/qr/renovar` — Regenerar QR token
```json
// Request (opcional: establecer expiración)
{ "expira_en_horas": 720 }

// Response 200
{
  "qr_token": "gym-1-newtoken987",
  "qr_token_expira": "2026-06-19T00:00:00Z"
}
```
> Al renovar el QR, el token anterior queda inválido inmediatamente.  
> El Attendance Service rechazará escaneos con el token viejo.

---

### 6.5 Suscripciones

#### `GET /companias/{id}/suscripcion` — Suscripción activa
```json
// Response 200
{
  "id": 3,
  "plan": { "id": 2, "nombre": "Premium", "precio_mensual": 59.99 },
  "estado": "activo",
  "fecha_inicio": "2026-05-19",
  "fecha_fin": "2026-06-18",
  "dias_restantes": 30,
  "dias_gracia": 5,
  "tipo_cambio": "nuevo"
}
// 404 si no tiene suscripción activa
```

#### `GET /companias/{id}/suscripcion/historial` — Historial completo
```json
// Response 200 — array de todos los compania_planes ordenados por created_at DESC
```

#### `POST /companias/{id}/suscripcion/renovar`
```json
// Requiere: JWT plataforma super_admin
// Request
{
  "id_plan": 2,
  "meses": 1,
  "id_pago": 15
}
// Response 201 — nueva fila con tipo_cambio='renovacion'
```

#### `POST /companias/{id}/suscripcion/upgrade`
```json
// Request
{
  "id_plan_nuevo": 3,
  "fecha_inicio": "2026-05-19"
}

// Response 201
{
  "id_compania_plan_nuevo": 10,
  "credito_aplicado": 15.50,
  "monto_a_pagar": 44.50,
  "plan_anterior_cancelado": true
}
// Efecto:
// 1. UPDATE plan actual → estado='cancelado'
// 2. INSERT nuevo plan con tipo_cambio='upgrade', credito_monto=días proporcionales
```

#### `POST /companias/{id}/suscripcion/downgrade`
```json
// Request
{
  "id_plan_nuevo": 1,
  "fecha_inicio": "2026-06-19"
}

// Response 201
{
  "id_compania_plan_nuevo": 11,
  "estado": "programado",
  "efectivo_desde": "2026-06-19",
  "credito_generado": 12.00
}
// El plan actual sigue activo hasta fecha_fin
// El nuevo plan queda en estado='programado' hasta su fecha_inicio
```

---

### 6.6 Pagos de Suscripción

#### `GET /companias/{id}/pagos` — Historial de pagos
```json
// Response 200
[
  {
    "id": 15,
    "monto": 59.99,
    "fecha_pago": "2026-05-19",
    "tipo_pago": "pago_completo",
    "metodo_pago": "transferencia",
    "estado": "pagado",
    "referencia": "TXN-20260519-001"
  }
]
```

#### `POST /pagos` — Registrar pago
```json
// Requiere: JWT plataforma super_admin o soporte
// Request
{
  "id_compania_plan": 3,
  "monto": 59.99,
  "fecha_pago": "2026-05-19",
  "periodo_desde": "2026-05-19",
  "periodo_hasta": "2026-06-18",
  "metodo_pago": "transferencia",
  "tipo_pago": "pago_completo",
  "referencia": "TXN-20260519-001"
}
// Response 201
```

#### `PUT /pagos/{id}/confirmar`
```json
// Confirmar pago pendiente → estado='pagado'
// Response 200
// Efecto adicional: si compania_plan estaba 'vencido', lo reactiva a 'activo'
//                  y extiende fecha_fin según el período pagado
```

---

### 6.7 Configuración de Notificaciones

#### `GET /companias/{id}/notif-config` — Ver configuración
```json
// Response 200
[
  { "dias_antes": 7,  "canal": "whatsapp", "activo": true },
  { "dias_antes": 3,  "canal": "ambos",    "activo": true },
  { "dias_antes": 1,  "canal": "email",    "activo": true }
]
```

#### `PUT /companias/{id}/notif-config` — Reemplazar configuración (bulk)
```json
// Request
[
  { "dias_antes": 7, "canal": "whatsapp", "activo": true },
  { "dias_antes": 3, "canal": "ambos",    "activo": true }
]
// Response 200 — reemplaza todas las configuraciones anteriores
```

---

### 6.8 Validación de módulos (uso inter-servicio)

#### `GET /modulos/check`
```
// Query params: ?id_compania=1&codigo=finanzas
// Headers: Authorization: Bearer {JWT de cualquier tipo válido}

// Response 200 — acceso permitido
{ "permitido": true, "plan": "Premium" }

// Response 402 — plan vencido o suspendido
{ "permitido": false, "razon": "plan_vencido" }

// Response 403 — plan activo pero sin ese módulo
{ "permitido": false, "razon": "modulo_no_incluido" }
```

---

### 6.9 Nuevos endpoints REQ-SAAS-001 (Sub-fases 1.3–1.5)

#### `GET /api/v1/planes/publicos` — Catálogo público (sin auth)

```json
// Retorna solo FREE, TRIAL, PREMIUM con toda la información Freemium

// Response 200
[
  {
    "id": 1,
    "codigo": "FREE",
    "nombre": "Plan Free",
    "descripcion": "Acceso básico permanente",
    "precio_mensual": 0.00,
    "duracion_dias": null,
    "es_gratuito": true,
    "max_sucursales": 1,
    "max_clientes_activos": 50,
    "max_staff": 2,
    "moneda": "USD"
  },
  {
    "id": 2,
    "codigo": "TRIAL",
    "nombre": "Plan Trial",
    "precio_mensual": 0.00,
    "duracion_dias": 60,
    "es_gratuito": true,
    "max_sucursales": null,
    "max_clientes_activos": null,
    "max_staff": null,
    "moneda": "USD"
  },
  {
    "id": 3,
    "codigo": "PREMIUM",
    "nombre": "Plan Premium",
    "precio_mensual": 29.99,
    "duracion_dias": 30,
    "es_gratuito": false,
    "max_sucursales": null,
    "max_clientes_activos": null,
    "max_staff": null,
    "moneda": "USD"
  }
]
```

#### `POST /api/v1/companias/{id}/suscripcion/trial` — Activar Trial (RN-01)

```json
// Requiere: JWT staff (owner o admin del tenant)
// Valida: trial_usado = false

// Request (body vacío o no requerido)

// Response 201
{
  "id": 15,
  "id_plan": 2,
  "estado": "ACTIVO",
  "fecha_inicio": "2026-07-10",
  "fecha_fin": "2026-09-08",
  "dias_restantes": 60,
  "dias_gracia": 5,
  "tipo_cambio": "NUEVO"
}

// Errors:
// 403 — acceso denegado (no es owner/admin del tenant)
// 409 — trial_usado = true o ya hay suscripción activa
```

#### `POST /api/v1/companias/{id}/suscripcion/cancelar` — Cancelar suscripción

```json
// Requiere: JWT staff (owner o admin del tenant)

// Request
{
  "motivo": "No lo estoy usando" // optional
}

// Response 204 No Content

// Errors:
// 403 — acceso denegado
// 400 — no hay suscripción cancelable (Free o ya vencida)
```

#### `GET /api/v1/companias/{id}/uso-limites` — Consultar límites (HU-04)

```json
// Requiere: JWT staff (owner o admin del tenant)

// Response 200
{
  "plan_codigo": "FREE",
  "sucursales": {
    "actual": 1,
    "maximo": 1
  },
  "clientes_activos": {
    "actual": 48,
    "maximo": 50
  },
  "staff": {
    "actual": 2,
    "maximo": 2
  },
  "sobre_limite": false,
  "sobre_limite_hasta": null
}

// Errors:
// 403 — acceso denegado
// 404 — sin suscripción activa
```

#### `POST /api/v1/companias/{id}/pagos/reportar` — Reportar pago (RN-08, multipart)

```
// Requiere: JWT staff (owner o admin del tenant)
// Rate limit: 3 reportes/hora/tenant

// Request: multipart/form-data
POST /api/v1/companias/{id}/pagos/reportar
  comprobante: file (JPEG/PNG/PDF, max 5MB)
  id_plan_destino: long
  monto: decimal
  fecha_transferencia: date (YYYY-MM-DD)
  banco_origen: string (optional)
  referencia: string (optional)

// Response 201
{
  "id": 42,
  "id_compania": 5,
  "id_plan_destino": 3,
  "monto": "29.99",
  "moneda": "USD",
  "fecha_reporte": "2026-07-10T14:30:00Z",
  "comprobante_url": "https://res.cloudinary.com/...pago-42.pdf",
  "banco_origen": "Banco Pichincha",
  "referencia": "TXN-001",
  "estado": "PENDIENTE",
  "hash_idempotencia": "sha256(...)"
}

// Errors:
// 400 — MIME inválido, tamaño > 5MB, validación fallida
// 403 — acceso denegado
// 409 — hash_idempotencia duplicado (ya existe pago pendiente/aprobado con esos datos)
// 429 — rate limit excedido (máx 3/hora)
```

#### `GET /api/v1/plataforma/pagos-pendientes` — Bandeja (root/soporte, HU-05)

```json
// Requiere: JWT plataforma (root o soporte)
// Query params: ?estado=PENDIENTE&pagina=1&limit=20

// Response 200
{
  "total": 5,
  "pagina": 1,
  "limit": 20,
  "datos": [
    {
      "id": 42,
      "id_compania": 5,
      "id_compania_nombre": "Gym Power",
      "id_plan_destino": 3,
      "monto": "29.99",
      "moneda": "USD",
      "fecha_reporte": "2026-07-10T14:30:00Z",
      "fecha_transferencia": "2026-07-10",
      "comprobante_url": "https://res.cloudinary.com/...pago-42.pdf",
      "banco_origen": "Banco Pichincha",
      "referencia": "TXN-001",
      "estado": "PENDIENTE",
      "motivo_rechazo": null,
      "aprobado_por": null,
      "fecha_aprobacion": null,
      "activacion_programada": false
    }
  ]
}

// Errors:
// 403 — acceso denegado (no es root/soporte)
```

#### `POST /api/v1/plataforma/pagos-pendientes/{id}/aprobar` — Aprobar pago

```json
// Requiere: JWT plataforma (root o soporte)

// Request (body vacío o no requerido)

// Response 200
{
  "id_pago": 42,
  "id_compania_plan": 16,
  "estado": "ACTIVO"
}

// Errors:
// 403 — acceso denegado
// 404 — pago no encontrado
// 409 — pago ya fue procesado (idempotencia: affectedRows == 0)
```

#### `POST /api/v1/plataforma/pagos-pendientes/{id}/rechazar` — Rechazar pago

```json
// Requiere: JWT plataforma (root o soporte)

// Request
{
  "motivo_rechazo": "Comprobante no corresponde al monto"
}

// Response 204 No Content

// Errors:
// 403 — acceso denegado
// 404 — pago no encontrado
// 409 — pago ya procesado
```

#### `GET /api/v1/companias/{id}/banners-activos` — Listar banners (Sub-fase 1.5)

```json
// Requiere: JWT staff (owner o admin del tenant)

// Response 200 (array puede estar vacío)
[
  {
    "id": 101,
    "id_compania_plan": 15,
    "tipo": "VENCIMIENTO_TRIAL",
    "dias_antes": 7,
    "mensaje": "Tu plan Trial vence en 7 días",
    "fecha_envio": "2026-07-03T03:15:00Z",
    "descartado_at": null
  }
]

// Errors:
// 403 — acceso denegado
```

#### `POST /api/v1/companias/{id}/banners/{idBanner}/descartar` — Descartar banner (Sub-fase 1.5)

```
// Requiere: JWT staff (owner o admin del tenant)

// Request (body vacío)

// Response 204 No Content

// Errors:
// 403 — acceso denegado
// 404 — banner no encontrado para este tenant
```

---

## 7. Flujos principales

### Flujo: Registrar gym nuevo

```
super_admin llama POST /companias
        │
        ├─ Validar RUC único en tenant.companias
        ├─ INSERT tenant.companias
        ├─ INSERT tenant.compania_planes
        │    tipo_cambio='nuevo', estado='activo'
        │    fecha_fin = fecha_inicio + meses
        ├─ INSERT tenant.sucursales (sede principal)
        │    qr_token = generar UUID aleatorio
        ├─ INSERT config_notif_suscripcion (defaults: 7d, 3d, 1d por whatsapp)
        └─ Responde con ids creados
```

### Flujo: Upgrade de plan

```
super_admin llama POST /companias/{id}/suscripcion/upgrade
        │
        ├─ Verifica plan nuevo es más caro que el actual
        ├─ Calcula crédito proporcional (días restantes del plan actual)
        ├─ UPDATE plan actual → estado='cancelado'
        ├─ INSERT nuevo compania_planes
        │    tipo_cambio='upgrade'
        │    id_compania_plan_orig = id del plan cancelado
        │    credito_monto = crédito calculado
        │    estado='activo'
        ├─ INSERT pagos_suscripcion
        │    monto = precio_plan_nuevo - credito
        │    tipo_pago='diferencia_upgrade'
        │    estado='pendiente' (se confirma cuando se recibe el pago)
        └─ Responde con monto a pagar
```

### Flujo: Job diario de notificaciones

```
Cron 00:05 UTC — ejecutado por este servicio
        │
        ├─ Transicionar estados vencidos (activo→en_gracia, en_gracia→vencido)
        ├─ Activar planes programados (programado→activo)
        │
        └─ Para cada plan activo/en_gracia:
               días_restantes = fecha_fin - HOY (+ dias_gracia si en_gracia)
               │
               ├─ Buscar config_notif_suscripcion WHERE dias_antes = días_restantes
               │    AND activo = TRUE
               │
               └─ Para cada configuración encontrada:
                      INSERT notificaciones_suscripcion (estado='pendiente')
                      Llamar servicio de mensajería (email / whatsapp)
                      UPDATE notificaciones_suscripcion SET estado='enviado'/'fallido'
```

---

## 8. Casos de prueba

### TC-PLAN — Planes y características

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-PLAN-001 | Crear plan con características válidas | datos completos + ids de características | 201 |
| TC-PLAN-002 | Listar planes incluye sus características | GET /planes | 200 con array de características por plan |
| TC-PLAN-003 | Desactivar plan sin suscriptores activos | plan sin gyms activos | 200 |
| TC-PLAN-004 | Desactivar plan con suscriptores activos | plan con 3 gyms activos | 409 + detalle |
| TC-PLAN-005 | staff intenta crear plan | JWT tipo=staff | 403 |
| TC-PLAN-006 | Asignar características a plan (bulk) | ids válidos | 200 |
| TC-PLAN-007 | Cambiar precio no afecta contratos vigentes | PUT precio | Contratos existentes sin cambio |

### TC-COMP — Compañías

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-COMP-001 | Registrar gym nuevo con plan y sede | datos completos | 201 + ids creados |
| TC-COMP-002 | RUC duplicado | RUC ya registrado | 409 |
| TC-COMP-003 | Registro crea sucursal principal con qr_token | POST /companias exitoso | sucursal creada con qr_token no nulo |
| TC-COMP-004 | Registro crea config de notificaciones por defecto | POST /companias exitoso | 3 filas en config_notif (7d, 3d, 1d) |
| TC-COMP-005 | admin_compania edita su propia compañía | JWT con id_compania=1, edita compañía 1 | 200 |
| TC-COMP-006 | admin_compania intenta editar otra compañía | JWT con id_compania=1, edita compañía 2 | 403 |
| TC-COMP-007 | Suspender compañía activa | motivo válido | 200 + plan pasa a suspendido |
| TC-COMP-008 | viewer intenta suspender | JWT rol=viewer | 403 |

### TC-SUC — Sucursales

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-SUC-001 | Crear sucursal genera qr_token único | POST sucursal | qr_token generado y único |
| TC-SUC-002 | Renovar QR invalida token anterior | POST /qr/renovar | nuevo token, viejo rechazado por Attendance |
| TC-SUC-003 | Renovar QR con expiración | `expira_en_horas: 24` | qr_token_expira = NOW()+24h |
| TC-SUC-004 | admin_compania crea sucursal en su gym | JWT compañía 1, crea en compañía 1 | 201 |
| TC-SUC-005 | admin_compania crea sucursal en otro gym | JWT compañía 1, crea en compañía 2 | 403 |

### TC-SUSC — Ciclo de vida de suscripción

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-SUSC-001 | Plan activo → en_gracia al vencer | fecha_fin = ayer, job diario | estado='en_gracia' |
| TC-SUSC-002 | en_gracia → vencido al agotar gracia | fecha_fin + dias_gracia = ayer, job | estado='vencido' |
| TC-SUSC-003 | Renovar plan vencido | POST /renovar con pago | nuevo plan activo, anterior queda como historial |
| TC-SUSC-004 | Upgrade de plan | plan básico → premium | plan anterior cancelado, nuevo activo, monto a pagar calculado |
| TC-SUSC-005 | Upgrade verifica que plan nuevo es mayor precio | plan premium → básico vía upgrade | 400 (usar downgrade) |
| TC-SUSC-006 | Downgrade crea plan programado | plan premium → básico | estado='programado', efectivo en fecha_inicio |
| TC-SUSC-007 | Plan programado se activa en su fecha | job diario, fecha_inicio = hoy | estado='activo' |
| TC-SUSC-008 | Historial preserva todos los cambios | 3 renovaciones + 1 upgrade | historial con 4 filas |

### TC-PAY — Pagos

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-PAY-001 | Registrar pago pendiente | datos válidos | 201 + estado='pendiente' |
| TC-PAY-002 | Confirmar pago reactiva plan vencido | PUT /confirmar en plan vencido | plan vuelve a activo, fecha_fin extendida |
| TC-PAY-003 | Confirmar pago ya pagado | PUT /confirmar en pago 'pagado' | 409 |
| TC-PAY-004 | admin_compania no puede registrar pagos | JWT tipo=staff | 403 |

### TC-NOTIF — Notificaciones

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-NOTIF-001 | Configurar alertas a 7 y 3 días | PUT notif-config con 2 entradas | 200, reemplaza config anterior |
| TC-NOTIF-002 | Job genera notificación en día correcto | plan con 7 días restantes, config dias_antes=7 | INSERT en notificaciones_suscripcion |
| TC-NOTIF-003 | No genera notificación si config inactiva | activo=false | Sin INSERT |
| TC-NOTIF-004 | No se puede crear notificación manualmente | POST /notificaciones | 405 |

### TC-MW — Middleware de módulos

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-MW-001 | Plan activo con módulo solicitado | codigo=finanzas, plan Premium | 200 permitido=true |
| TC-MW-002 | Plan activo sin módulo solicitado | codigo=finanzas, plan Básico | 403 modulo_no_incluido |
| TC-MW-003 | Plan vencido | estado=vencido | 402 plan_vencido |
| TC-MW-004 | Plan en gracia sigue permitiendo acceso | estado=en_gracia | 200 permitido=true |
| TC-MW-005 | Plan suspendido bloquea todo | estado=suspendido | 402 plan_suspendido |
| TC-MW-006 | Respuesta cacheada (misma compañía, mismo código) | 2 requests seguidos | 2do desde caché, sin hit a BD |

---

## 9. Datos semilla (seeds)

```sql
-- Características del sistema
INSERT INTO saas.caracteristicas (codigo, nombre, modulo) VALUES
  ('clientes',    'Gestión de clientes',        'clientes'),
  ('membresias',  'Membresías',                 'membresias'),
  ('asistencia',  'Control de asistencia',      'asistencia'),
  ('finanzas',    'Módulo de finanzas',         'finanzas'),
  ('marketing',   'Promociones y beneficios',   'marketing'),
  ('inventario',  'Inventario y ventas',        'inventario');

-- Plan Básico (clientes + membresías + asistencia)
INSERT INTO saas.planes (nombre, descripcion, precio_mensual) VALUES
  ('Básico', 'Funciones esenciales: clientes, membresías y asistencia', 29.99);

INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica)
  SELECT 1, id FROM saas.caracteristicas WHERE codigo IN ('clientes','membresias','asistencia');

-- Plan Premium (todo)
INSERT INTO saas.planes (nombre, descripcion, precio_mensual) VALUES
  ('Premium', 'Todas las funcionalidades incluidas', 59.99);

INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica)
  SELECT 2, id FROM saas.caracteristicas;

-- Compañía de prueba
INSERT INTO tenant.companias (nombre, ruc, telefono, whatsapp, correo) VALUES
  ('Gym Power Quito', '1792345678001', '022345678', '0991234567', 'info@gympower.com');

-- Suscripción activa (plan Premium)
INSERT INTO tenant.compania_planes
  (id_compania, id_plan, fecha_inicio, fecha_fin, estado, tipo_cambio) VALUES
  (1, 2, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 'activo', 'nuevo');

-- Sucursal principal con QR
INSERT INTO tenant.sucursales
  (id_compania, nombre, direccion, es_principal, qr_token) VALUES
  (1, 'Sede Principal', 'Av. Amazonas N35-17', TRUE, 'test-qr-token-gym1-sede1');

-- Configuración de notificaciones por defecto
INSERT INTO tenant.config_notif_suscripcion (id_compania, dias_antes, canal) VALUES
  (1, 7, 'whatsapp'),
  (1, 3, 'ambos'),
  (1, 1, 'email');
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

# Caché (para middleware de módulos)
REDIS_HOST=localhost
REDIS_PORT=6379
MODULE_CHECK_CACHE_TTL_SECONDS=300     # 5 minutos

# QR token
QR_TOKEN_LENGTH=32                      # bytes aleatorios → hex = 64 chars

# Cron de estados y notificaciones
SUBSCRIPTION_JOB_CRON=0 5 0 * * *      # 00:05 UTC cada día
```

---

## 11. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | El historial de suscripción es inmutable — nunca UPDATE de estado, siempre INSERT nueva fila | POST renovar / upgrade / downgrade |
| RN-02 | Solo puede haber UNA fila activa (estado=activo o en_gracia) por compañía a la vez | Al crear nuevo plan, verificar antes |
| RN-03 | Plan en estado `en_gracia` sigue permitiendo acceso a módulos hasta agotar la gracia | Middleware de módulos |
| RN-04 | Un upgrade cancela el plan actual en el mismo momento — el crédito es proporcional a días no usados | POST /upgrade |
| RN-05 | Un downgrade NO cancela el plan actual — el nuevo queda `programado` hasta su fecha_inicio | POST /downgrade |
| RN-06 | Cambiar el precio de un plan no afecta contratos ya firmados | PUT /planes/{id} |
| RN-07 | Al registrar un gym se crean automáticamente: plan inicial, sucursal principal y configuración de notificaciones | POST /companias |
| RN-08 | Al renovar el QR token, el token anterior queda inválido inmediatamente | POST /sucursales/{id}/qr/renovar |
| RN-09 | `admin_compania` solo puede leer y modificar recursos de su propio `id_compania` | Todos los endpoints tenant |
| RN-10 | El endpoint `/modulos/check` debe usar caché — nunca golpear la BD en cada request | GET /modulos/check |

### Nuevas reglas REQ-SAAS-001 (Sub-fases 1.3–1.5)

> **Namespace separado:** las reglas del esquema freemium usan el prefijo `RN-SAAS-` para evitar colisión con RN-01…RN-10 preexistentes en este spec (que cubren reglas generales del platform-service). La columna **Origen** referencia la regla del requerimiento fuente [`planes-saas-freemium.md`](../requirements/planes-saas-freemium.md#reglas-de-negocio-canónicas).

| # | Regla | Origen | Dónde se aplica |
|---|---|---|---|
| RN-SAAS-001 | Trial único por tenant — `trial_usado` es irreversible | RN-01 | `ActivarTrialService.activar()` valida `trial_usado=false` |
| RN-SAAS-002 | Degradación automática sin período de gracia cuando destino=Free | RN-03 | `SubscriptionJobService.procesar()` orden de operaciones |
| RN-SAAS-003 | Upgrade Trial→Premium agendado: `PROGRAMADO` se activa antes de degradar Trial vencido | RN-03 + RN-05 | Job: activar PROGRAMADO (paso 1) → degradar (paso 2) |
| RN-SAAS-004 | Hard limits por plan (Free: 1 sucursal, 50 clientes, 2 staff) | RN-06 | `LimiteRecursoService.validarPuedeCrear()` con `pg_advisory_xact_lock()` |
| RN-SAAS-005 | Reporte de pago: validación MIME por magic bytes, máx 5MB | RN-08 (impl) | `ReportarPagoService.reportar()` valida antes de cargar a Cloudinary |
| RN-SAAS-006 | Idempotencia de reportes: `hash_idempotencia = SHA-256(idCompania + monto + fechaTransferencia + referencia)` | RN-08 | `PagoPendienteValidacionPersistenceAdapter` constraint `UNIQUE(hash_idempotencia) WHERE estado IN ('PENDIENTE','APROBADO')` |
| RN-SAAS-007 | Idempotencia de aprobación: `UPDATE WHERE estado='PENDIENTE'` y verificar `affectedRows==1` | RN-08 | `AprobarPagoService.aprobar()` |
| RN-SAAS-008 | Rate limit de reportes: máx 3 por tenant por hora | RN-08 (impl) | `PostgresRateLimiter` contra tabla `rate_limit_buckets` |
| RN-SAAS-009 | Notificaciones idempotentes: un bucket (15, 7, 3, 1, 0 días) por (compania_plan, tipo, canal) | RN-07 | `NotificacionRepository.existsIdempotente()` predicado |
| RN-SAAS-010 | Retry exponencial de emails: 30s → 2m → 10m → 1h, `fallido` tras 4 intentos (DLQ implícita) | RN-07 (impl) | `EmailQueueProcessorJob.procesarLote()` actualiza `proximo_intento` |
| RN-SAAS-011 | Planes públicos: omitir `es_legacy=true` y `activo=false` | (nueva impl) | `PlanPublicoController.listarPublicos()` |
| RN-SAAS-012 | Cancelación de suscripción: si es Trial, degrada inmediato a Free; Premium completa el mes | RN-09 | `CancelarSuscripcionService.cancelar()` lógica condicional por plan |

**Reglas del requerimiento no cubiertas por RN-SAAS-XXX:**

- **RN-02** (Registro con Trial por defecto): se cumple a nivel de flujo de registro, no requirió regla de implementación separada.
- **RN-04** (Preservación de datos al degradar): garantía a nivel de DDL — degradar cambia `compania_planes` pero no borra datos operativos.
- **RN-10** (Prevención de suscripciones solapadas): implementada por constraint parcial `UNIQUE(id_compania) WHERE estado IN ('activo','en_gracia')` en `tenant.compania_planes` (Sub-fase 1.1, changeset GYM-003-3).

---

## 12. Decisiones arquitectónicas REQ-SAAS-001

Ver sección completa en `docs/gym-administrator/requirements/planes-saas-freemium-implementacion.md#decisiones-arquitectónicas-d1–d6`.

| Decisión | Rationale |
|----------|-----------|
| **D1** — Extender `notificaciones_suscripcion` | Evita fragmentación; una única tabla para auditoría. |
| **D2** — Nueva tabla `pagos_pendientes_validacion` (separada de `pagos_suscripcion`) | Separar "buzón de pendiente validación" de "históricamente pagado". |
| **D3** — Migración de `saas.actividad_plataforma` | Auditoría con actor, IP, JSONB detalle. |
| **D4** — Enums en DB (minúsculas), Java (UPPERCASE), mapping manual | Coherencia SQL + idiomatismo Java. |
| **D5** — EmailAdapter duplicado en cada servicio, no compartido | Independencia de deployments. |
| **D6** — Cola de emails en Postgres + `FOR UPDATE SKIP LOCKED`, sin Redis | Disponibilidad en entornos sin Redis remoto. |

---

*Platform Service Spec v1.0–1.4 · Gym Administrator · Mayo 2026 · Actualizado 2026-07-10 (REQ-SAAS-001 Sub-fases 1.3–1.5)*
