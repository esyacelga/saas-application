# Finance Service — Especificación de Desarrollo

> **Servicio:** finance-service  
> **Esquemas BD:** `finanzas`  
> **Tablas:** 4 tablas (categorias_ingreso · ingresos · categorias_egreso · egresos)  
> **Plan requerido:** Premium  
> **Depende de:** auth-service (JWT) · platform-service (`/modulos/check` → `finanzas`)  
> **Es llamado por:** core-service (al vender membresía) · inventory-service (al registrar venta)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Integración con otros servicios](#4-integración-con-otros-servicios)
5. [API — Contratos de endpoints](#5-api--contratos-de-endpoints)
6. [Flujos principales](#6-flujos-principales)
7. [Casos de prueba](#7-casos-de-prueba)
8. [Datos semilla (seeds)](#8-datos-semilla-seeds)
9. [Variables de entorno requeridas](#9-variables-de-entorno-requeridas)
10. [Reglas de negocio críticas](#10-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Finance Service es el **libro mayor del gimnasio**: registra todos los ingresos y egresos, los categoriza y genera los reportes financieros para la toma de decisiones.

**Responsabilidades:**
- Registrar ingresos (manual, o llamado automáticamente al vender membresía o producto)
- Registrar egresos (sueldos, servicios, insumos, otros)
- Gestionar catálogos de categorías de ingresos y egresos
- Generar reportes de ingresos vs egresos por período
- Calcular proyección de ingresos del mes siguiente

**Fuera de alcance:**
- Procesar pagos con tarjeta o transferencia (→ pasarela de pago externa)
- Facturación electrónica (→ módulo futuro)
- Nómina de empleados (→ módulo futuro)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `Dueño` | `Recepción` | `Contador` | `Entrenador` |
|---|:---:|:---:|:---:|:---:|:---:|
| Ver reportes financieros | ✅ | ✅ | ❌ | ✅ | ❌ |
| Exportar reportes | ✅ | ✅ | ❌ | ✅ | ❌ |
| Registrar ingreso manual | ✅ | ✅ | ✅ | ✅ | ❌ |
| Registrar egreso | ✅ | ✅ | ❌ | ✅ | ❌ |
| CRUD categorías | ✅ | ✅ | ❌ | ✅ | ❌ |
| Ver detalle de transacciones | ✅ | ✅ | ❌ | ✅ | ❌ |

> **Permiso requerido:** `finanzas:leer` / `finanzas:crear` / `finanzas:exportar`

---

## 3. Tablas involucradas

### finanzas.categorias_ingreso
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(100) NOT NULL
activo       BOOLEAN NOT NULL DEFAULT TRUE
```
> Ejemplos: Membresías · Ventas tienda · Entrenamiento personalizado · Otros

### finanzas.ingresos
```sql
id                  INT     PK, identity
id_compania         INT     NOT NULL
id_sucursal         INT     NOT NULL
id_categoria        INT     FK → finanzas.categorias_ingreso(id)  NOT NULL
id_membresia        INT                -- ref. lógica → core.membresias.id (si es por membresía)
id_venta            INT                -- ref. lógica → inventario.ventas.id (si es por venta)
monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0)
descripcion         TEXT
fecha               DATE    NOT NULL DEFAULT CURRENT_DATE
id_usuario_registro INT
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
```
> `id_membresia` e `id_venta` son referencias lógicas (sin FK) para desacoplar servicios.  
> Si ambos son NULL: ingreso registrado manualmente.

### finanzas.categorias_egreso
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(100) NOT NULL
activo       BOOLEAN NOT NULL DEFAULT TRUE
```
> Ejemplos: Sueldos · Arriendo · Servicios básicos · Insumos · Mantenimiento · Otros

### finanzas.egresos
```sql
id                  INT     PK, identity
id_compania         INT     NOT NULL
id_sucursal         INT     NOT NULL
id_categoria        INT     FK → finanzas.categorias_egreso(id)  NOT NULL
monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0)
descripcion         TEXT
fecha               DATE    NOT NULL DEFAULT CURRENT_DATE
id_usuario_registro INT
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

---

## 4. Integración con otros servicios

```
Core Service (venta de membresía)
      └─► POST /ingresos { id_membresia, monto, id_categoria='Membresías' }

Inventory Service (venta de producto)
      └─► POST /ingresos { id_venta, monto, id_categoria='Ventas tienda' }

Finance Service (reportes)
      └─► Consulta sus propias tablas — sin llamadas externas
```

> La integración es **fire-and-forget**: si Finance Service falla al crear el ingreso, Core e Inventory Service registran la transacción igualmente y encolan el reintento.

---

## 5. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 5.1 Categorías de Ingreso

#### `GET /finanzas/categorias-ingreso`
```json
// Response 200
[
  { "id": 1, "nombre": "Membresías",              "activo": true },
  { "id": 2, "nombre": "Ventas tienda",            "activo": true },
  { "id": 3, "nombre": "Entrenamiento personal",  "activo": true },
  { "id": 4, "nombre": "Otros",                   "activo": true }
]
```

#### `POST /finanzas/categorias-ingreso`
```json
{ "nombre": "Alquiler de casilleros" }
// Response 201
```

#### `PUT /finanzas/categorias-ingreso/{id}/desactivar`
```
// 409 si tiene ingresos registrados
```

---

### 5.2 Ingresos

#### `GET /finanzas/ingresos` — Listar con filtros
```
// Query params: ?desde=2026-05-01&hasta=2026-05-31&id_categoria=1&page=1&limit=50

// Response 200
{
  "total_periodo": 3500.00,
  "total_registros": 85,
  "datos": [
    {
      "id": 210,
      "categoria": "Membresías",
      "monto": 35.00,
      "descripcion": "Membresía mensual - María López",
      "fecha": "2026-05-19",
      "origen": "membresia",
      "id_referencia": 13
    }
  ]
}
```

#### `POST /finanzas/ingresos` — Registrar ingreso
```json
// Request
{
  "id_categoria": 1,
  "monto": 35.00,
  "descripcion": "Membresía mensual - María López",
  "fecha": "2026-05-19",
  "id_membresia": 13
}
// Response 201
```

---

### 5.3 Categorías de Egreso y Egresos

Misma estructura que ingresos.

#### `GET /finanzas/egresos` — Listar egresos
```
// Mismos query params que ingresos
// Response 200 con total_periodo y datos
```

#### `POST /finanzas/egresos` — Registrar egreso
```json
{
  "id_categoria": 2,
  "monto": 800.00,
  "descripcion": "Sueldo recepcionista mayo",
  "fecha": "2026-05-31"
}
// Response 201
```

---

### 5.4 Reportes

#### `GET /finanzas/reporte/resumen` — Resumen del período
```json
// Query params: ?desde=2026-05-01&hasta=2026-05-31

// Response 200
{
  "periodo": { "desde": "2026-05-01", "hasta": "2026-05-31" },
  "total_ingresos": 3500.00,
  "total_egresos": 2100.00,
  "utilidad": 1400.00,
  "margen": 40.0,
  "ingresos_por_categoria": [
    { "categoria": "Membresías",    "monto": 2800.00, "porcentaje": 80.0 },
    { "categoria": "Ventas tienda", "monto":  700.00, "porcentaje": 20.0 }
  ],
  "egresos_por_categoria": [
    { "categoria": "Sueldos",       "monto": 1500.00, "porcentaje": 71.4 },
    { "categoria": "Servicios",     "monto":  600.00, "porcentaje": 28.6 }
  ]
}
```

#### `GET /finanzas/reporte/mensual` — Evolución mes a mes
```json
// Query params: ?año=2026

// Response 200
{
  "año": 2026,
  "meses": [
    { "mes": "2026-01", "ingresos": 3200.00, "egresos": 2000.00, "utilidad": 1200.00 },
    { "mes": "2026-02", "ingresos": 3400.00, "egresos": 2050.00, "utilidad": 1350.00 },
    ...
  ]
}
```

#### `GET /finanzas/reporte/proyeccion` — Proyección próximo mes
```json
// Response 200
{
  "mes_proyectado": "2026-06",
  "ingresos_estimados": 3600.00,
  "base_calculo": "promedio_3_meses",
  "membresías_por_vencer": 12,
  "tasa_renovacion_historica": 0.85,
  "ingresos_membresías_proyectados": 357.00
}
```

---

## 6. Flujos principales

### Flujo: Ingreso automático al vender membresía

```
Core Service → POST /finanzas/ingresos
  {
    id_categoria: [id de 'Membresías'],
    monto: membresia.precio_pagado,
    descripcion: "Membresía {tipo} - {nombre_cliente}",
    fecha: membresia.fecha_inicio,
    id_membresia: membresia.id
  }
```

### Flujo: Reporte mensual para dashboard

```
GET /finanzas/reporte/resumen?desde=2026-05-01&hasta=2026-05-31
      │
      ├─ SUM(ingresos.monto) WHERE período y compañía
      ├─ SUM(egresos.monto)  WHERE período y compañía
      ├─ GROUP BY id_categoria para desgloses
      └─ Calcula utilidad y margen
```

---

## 7. Casos de prueba

### TC-FIN — Finance Service

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-FIN-001 | Registrar ingreso manual | datos válidos | 201 |
| TC-FIN-002 | Registrar egreso | datos válidos | 201 |
| TC-FIN-003 | Ingreso automático desde Core | POST con id_membresia | 201, origen='membresia' |
| TC-FIN-004 | Ingreso automático desde Inventory | POST con id_venta | 201, origen='venta' |
| TC-FIN-005 | Monto cero o negativo | monto=0 | 400 (CHECK monto > 0) |
| TC-FIN-006 | Reporte resumen período sin datos | gym nuevo | totales en 0, sin error |
| TC-FIN-007 | Reporte agrupa correctamente por categoría | múltiples ingresos misma categoría | suma correcta por categoría |
| TC-FIN-008 | Recepcionista no ve reportes | JWT rol=Recepción, GET /reporte | 403 |
| TC-FIN-009 | Desactivar categoría con ingresos | categoría usada | 409 |
| TC-FIN-010 | Filtro por categoría en listado | ?id_categoria=1 | Solo ingresos de esa categoría |
| TC-FIN-011 | Ingresos de otra compañía invisibles | JWT compañía 1, hay datos de compañía 2 | Solo ve los de compañía 1 |
| TC-FIN-012 | Proyección con historial de 3 meses | 3 meses de datos | Proyección basada en promedio |

---

## 8. Datos semilla (seeds)

```sql
-- Categorías de ingreso para gym de prueba
INSERT INTO finanzas.categorias_ingreso (id_compania, id_sucursal, nombre) VALUES
  (1, 1, 'Membresías'),
  (1, 1, 'Ventas tienda'),
  (1, 1, 'Entrenamiento personal'),
  (1, 1, 'Otros ingresos');

-- Categorías de egreso para gym de prueba
INSERT INTO finanzas.categorias_egreso (id_compania, id_sucursal, nombre) VALUES
  (1, 1, 'Sueldos'),
  (1, 1, 'Arriendo'),
  (1, 1, 'Servicios básicos'),
  (1, 1, 'Insumos y mantenimiento'),
  (1, 1, 'Otros egresos');
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
PLATFORM_SERVICE_URL=http://platform-service:8081

# Proyección: número de meses históricos a promediar
PROYECCION_MESES_BASE=3
```

---

## 10. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | `monto` debe ser siempre positivo — entradas y salidas son tablas separadas | CHECK en BD |
| RN-02 | Los ingresos son inmutables — no se editan, se anulan con un egreso de ajuste | No exponer PUT en ingresos |
| RN-03 | `id_membresia` e `id_venta` son opcionales — un ingreso puede ser 100% manual | POST /ingresos |
| RN-04 | No se puede desactivar una categoría que tenga registros asociados | PUT /desactivar |
| RN-05 | Solo `Dueño`, `Contador` y `super_admin` acceden a reportes y exportaciones | Middleware de permisos |
| RN-06 | Los ingresos de otra compañía nunca son visibles — filtro automático por `id_compania` del JWT | Todos los endpoints |

---

*Finance Service Spec v1.0 · Gym Administrator · Mayo 2026*
