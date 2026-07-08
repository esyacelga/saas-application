# Inventory Service — Especificación de Desarrollo

> **Servicio:** inventory-service  
> **Esquemas BD:** `inventario`  
> **Tablas:** 7 tablas (categorias_producto · proveedores · productos · stock · ventas · detalle_ventas · movimientos_inventario)  
> **Plan requerido:** Premium  
> **Depende de:** auth-service (JWT) · platform-service (`/modulos/check` → `inventario`)  
> **Llama a:** finance-service (al registrar venta → crea ingreso automático)  
> **Estado:** Listo para desarrollar

---

## Tabla de Contenidos

1. [Responsabilidad del servicio](#1-responsabilidad-del-servicio)
2. [Quién puede hacer qué](#2-quién-puede-hacer-qué)
3. [Tablas involucradas](#3-tablas-involucradas)
4. [Flujo de una venta completa](#4-flujo-de-una-venta-completa)
5. [API — Contratos de endpoints](#5-api--contratos-de-endpoints)
6. [Flujos principales](#6-flujos-principales)
7. [Casos de prueba](#7-casos-de-prueba)
8. [Datos semilla (seeds)](#8-datos-semilla-seeds)
9. [Variables de entorno requeridas](#9-variables-de-entorno-requeridas)
10. [Reglas de negocio críticas](#10-reglas-de-negocio-críticas)

---

## 1. Responsabilidad del servicio

El Inventory Service gestiona el **punto de venta y control de stock** del gimnasio: desde el catálogo de productos hasta la trazabilidad completa de cada movimiento de inventario.

**Responsabilidades:**
- CRUD de categorías, proveedores y productos
- Registrar entradas de mercadería de proveedores
- Punto de venta: registrar ventas con múltiples líneas de detalle
- Mantener actualizado `stock.stock_actual` en cada movimiento
- Alertar cuando el stock baja del mínimo configurado
- Registrar ajustes y devoluciones con trazabilidad completa
- Llamar a Finance Service al completar una venta para crear el ingreso correspondiente

**Fuera de alcance:**
- Facturación electrónica (→ módulo futuro)
- Gestión de pagos con tarjeta (→ pasarela externa)

---

## 2. Quién puede hacer qué

| Acción | `super_admin` | `Dueño` | `Recepción` | `Contador` | `Entrenador` |
|---|:---:|:---:|:---:|:---:|:---:|
| CRUD categorías y proveedores | ✅ | ✅ | ❌ | ❌ | ❌ |
| CRUD productos | ✅ | ✅ | ❌ | ❌ | ❌ |
| Ver catálogo y stock | ✅ | ✅ | ✅ | ✅ | ❌ |
| Registrar venta | ✅ | ✅ | ✅ | ❌ | ❌ |
| Registrar entrada de mercadería | ✅ | ✅ | ❌ | ❌ | ❌ |
| Registrar ajuste / devolución | ✅ | ✅ | ❌ | ❌ | ❌ |
| Ver reportes de ventas | ✅ | ✅ | ❌ | ✅ | ❌ |

> **Permiso requerido:** `inventario:leer` / `inventario:crear`

---

## 3. Tablas involucradas

### inventario.categorias_producto
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(100) NOT NULL
activo       BOOLEAN NOT NULL DEFAULT TRUE
```

### inventario.proveedores
```sql
id           INT     PK, identity
id_compania  INT     NOT NULL
id_sucursal  INT     NOT NULL
nombre       VARCHAR(150) NOT NULL
telefono     VARCHAR(20)
correo       VARCHAR(150)
activo       BOOLEAN NOT NULL DEFAULT TRUE
```

### inventario.productos
```sql
id              INT     PK, identity
id_compania     INT     NOT NULL
id_sucursal     INT     NOT NULL
id_categoria    INT     FK → inventario.categorias_producto(id)  NOT NULL
id_proveedor    INT     FK → inventario.proveedores(id)          -- proveedor habitual
nombre          VARCHAR(150) NOT NULL
descripcion     TEXT
codigo_barras   VARCHAR(50)  UNIQUE
precio_venta    DECIMAL(10,2) NOT NULL CHECK (precio_venta >= 0)
precio_costo    DECIMAL(10,2) NOT NULL CHECK (precio_costo >= 0)
stock_minimo    INT    NOT NULL DEFAULT 0    -- alerta cuando stock_actual <= este valor
activo          BOOLEAN NOT NULL DEFAULT TRUE
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### inventario.stock
```sql
id                   INT     PK, identity
id_compania          INT     NOT NULL
id_sucursal          INT     NOT NULL
id_producto          INT     FK → inventario.productos(id)  NOT NULL
stock_actual         INT     NOT NULL DEFAULT 0
ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW()
UNIQUE (id_producto, id_compania, id_sucursal)
```
> Una fila por producto por sucursal. Se actualiza en cada movimiento.

### inventario.ventas
```sql
id               INT     PK, identity
id_compania      INT     NOT NULL
id_sucursal      INT     NOT NULL
id_cliente       INT     FK → core.clientes(id)   -- NULL si venta sin cliente registrado
id_metodo_pago   INT                               -- ref. lógica a config.metodos_pago.id
id_usuario_venta INT                               -- ref. lógica a seguridad.usuarios.id
total            DECIMAL(10,2) NOT NULL CHECK (total >= 0)
fecha            DATE    NOT NULL DEFAULT CURRENT_DATE
created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### inventario.detalle_ventas
```sql
id               INT     PK, identity
id_venta         INT     FK → inventario.ventas(id)    NOT NULL
id_producto      INT     FK → inventario.productos(id) NOT NULL
cantidad         INT     NOT NULL CHECK (cantidad > 0)
precio_unitario  DECIMAL(10,2) NOT NULL
subtotal         DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED
```
> `subtotal` es calculado por la BD — nunca enviarlo en el request.

### inventario.movimientos_inventario
```sql
id              BIGINT  PK, identity
id_compania     INT     NOT NULL
id_sucursal     INT     NOT NULL
id_producto     INT     FK → inventario.productos(id)  NOT NULL
id_proveedor    INT     FK → inventario.proveedores(id) -- solo para tipo='entrada'
id_venta        INT     FK → inventario.ventas(id)      -- solo para tipo='venta'
tipo            VARCHAR(20) NOT NULL CHECK IN ('entrada','venta','ajuste','devolucion')
cantidad        INT     NOT NULL CHECK (cantidad <> 0)
                          -- positivo: entrada/devolucion, negativo: venta/ajuste de baja
fecha           DATE    NOT NULL DEFAULT CURRENT_DATE
observacion     TEXT
id_usuario      INT
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```
> Audit log completo e inmutable. Cada cambio en stock genera un movimiento.

---

## 4. Flujo de una venta completa

```
Cajero registra venta en el punto de venta
        │
        ├─ 1. POST /ventas (cabecera)
        │       { id_cliente, id_metodo_pago, lineas: [...] }
        │
        ├─ 2. INSERT inventario.ventas       → id_venta
        │
        ├─ 3. Para cada línea:
        │       INSERT inventario.detalle_ventas
        │          (subtotal es GENERATED — no se envía)
        │       UPDATE inventario.stock
        │          SET stock_actual = stock_actual - cantidad
        │          WHERE id_producto = :id AND id_sucursal = :id
        │       INSERT inventario.movimientos_inventario
        │          { tipo='venta', cantidad= -cantidad, id_venta }
        │
        ├─ 4. Verificar alertas de stock mínimo
        │       Si stock_actual <= stock_minimo:
        │          Emitir evento/notificación al admin
        │
        └─ 5. Llamar Finance Service (fire-and-forget):
                POST /finanzas/ingresos
                   { id_venta, monto=total, id_categoria='Ventas tienda' }
```

---

## 5. API — Contratos de endpoints

### Base URL: `/api/v1`

---

### 5.1 Catálogo

#### `GET /inventario/productos` — Listar productos
```
// Query params: ?buscar=proteina&id_categoria=1&activo=true&stock_bajo=true

// Response 200
[
  {
    "id": 1,
    "nombre": "Proteína Whey 2kg",
    "codigo_barras": "7890001234567",
    "precio_venta": 45.00,
    "categoria": "Suplementos",
    "stock_actual": 8,
    "stock_minimo": 5,
    "alerta_stock": false
  }
]
```

#### `POST /inventario/productos` — Crear producto
```json
{
  "id_categoria": 1,
  "id_proveedor": 2,
  "nombre": "Proteína Whey 2kg",
  "codigo_barras": "7890001234567",
  "precio_venta": 45.00,
  "precio_costo": 28.00,
  "stock_minimo": 5
}
// Response 201
// Al crear, INSERT en inventario.stock con stock_actual=0
```

#### `PUT /inventario/productos/{id}` — Actualizar
```json
{ "precio_venta": 47.00, "stock_minimo": 3 }
// Response 200
```

---

### 5.2 Stock y Movimientos

#### `GET /inventario/stock` — Ver stock actual por sucursal
```json
// Query params: ?id_sucursal=1&alerta=true (solo los por debajo del mínimo)

// Response 200
[
  {
    "producto": "Proteína Whey 2kg",
    "stock_actual": 3,
    "stock_minimo": 5,
    "alerta": true,
    "ultima_actualizacion": "2026-05-19T09:14:00Z"
  }
]
```

#### `POST /inventario/movimientos/entrada` — Registrar entrada de mercadería
```json
{
  "id_proveedor": 2,
  "items": [
    { "id_producto": 1, "cantidad": 20, "observacion": "Factura #F001-2026" },
    { "id_producto": 2, "cantidad": 15 }
  ]
}

// Response 201
{
  "movimientos_creados": 2,
  "stock_actualizado": [
    { "producto": "Proteína Whey 2kg", "stock_anterior": 3, "stock_nuevo": 23 }
  ]
}
// Efecto: UPDATE stock, INSERT movimientos (tipo='entrada', cantidad positiva)
```

#### `POST /inventario/movimientos/ajuste` — Ajuste de inventario
```json
{
  "id_producto": 1,
  "cantidad": -2,
  "observacion": "Producto vencido / dañado"
}
// cantidad negativa = baja de stock, positiva = corrección al alza
// Response 201
```

#### `GET /inventario/movimientos` — Historial de movimientos
```
// Query params: ?id_producto=1&tipo=venta&desde=2026-05-01

// Response 200
[
  {
    "id": 500,
    "producto": "Proteína Whey 2kg",
    "tipo": "venta",
    "cantidad": -1,
    "fecha": "2026-05-19",
    "id_venta": 42,
    "usuario": "Juan Pérez"
  }
]
```

---

### 5.3 Ventas

#### `POST /inventario/ventas` — Registrar venta completa
```json
{
  "id_cliente": 5,
  "id_metodo_pago": 1,
  "lineas": [
    { "id_producto": 1, "cantidad": 1 },
    { "id_producto": 3, "cantidad": 2 }
  ]
}

// Response 201
{
  "id_venta": 42,
  "total": 55.00,
  "lineas": [
    { "producto": "Proteína Whey 2kg", "cantidad": 1, "precio_unitario": 45.00, "subtotal": 45.00 },
    { "producto": "Barra proteica",    "cantidad": 2, "precio_unitario":  5.00, "subtotal": 10.00 }
  ],
  "alertas_stock": []
}

// 400 si algún producto no tiene stock suficiente
// Incluye alertas si algún producto queda bajo el mínimo
```

#### `GET /inventario/ventas` — Historial de ventas
```
// Query params: ?desde=2026-05-01&hasta=2026-05-31&id_cliente=5

// Response 200
{
  "total_ventas": 42,
  "total_monto": 1890.00,
  "datos": [ ... ]
}
```

#### `GET /inventario/ventas/{id}` — Detalle de venta
```json
// Response 200
{
  "id": 42,
  "fecha": "2026-05-19",
  "cliente": "María López",
  "total": 55.00,
  "lineas": [
    { "producto": "Proteína Whey 2kg", "cantidad": 1, "subtotal": 45.00 },
    { "producto": "Barra proteica",    "cantidad": 2, "subtotal": 10.00 }
  ]
}
```

---

### 5.4 Alertas de Stock

#### `GET /inventario/alertas` — Productos por debajo del mínimo
```json
// Response 200
[
  {
    "id_producto": 1,
    "nombre": "Proteína Whey 2kg",
    "stock_actual": 3,
    "stock_minimo": 5,
    "proveedor": "SuplementsEcuador",
    "proveedor_telefono": "0998765432"
  }
]
```

---

## 6. Flujos principales

### Flujo: Entrada de mercadería del proveedor

```
POST /inventario/movimientos/entrada
      │
      ├─ Para cada item:
      │    UPDATE inventario.stock SET
      │      stock_actual = stock_actual + cantidad,
      │      ultima_actualizacion = NOW()
      │    WHERE id_producto = :id AND id_compania = :id AND id_sucursal = :id
      │
      │    INSERT inventario.movimientos_inventario
      │      { tipo='entrada', cantidad=+N, id_proveedor, observacion }
      │
      └─ Response con stock resultante por producto
```

### Flujo: Devolución de cliente

```
POST /inventario/movimientos/devolucion
      { id_venta_origen: 42, id_producto: 1, cantidad: 1, motivo: "Producto dañado" }
      │
      ├─ Verifica que id_venta exista y pertenezca a la compañía
      ├─ UPDATE stock SET stock_actual = stock_actual + 1
      ├─ INSERT movimientos { tipo='devolucion', cantidad=+1 }
      └─ (Opcional) Llamar Finance Service para registrar egreso de devolución
```

---

## 7. Casos de prueba

### TC-INV — Inventory Service

| ID | Descripción | Input | Resultado esperado |
|---|---|---|---|
| TC-INV-001 | Crear producto inicializa stock en 0 | POST /productos | stock_actual=0 en inventario.stock |
| TC-INV-002 | Entrada de mercadería incrementa stock | cantidad=20 | stock_actual += 20, movimiento tipo='entrada' |
| TC-INV-003 | Venta decrementa stock | vender 1 unidad | stock_actual -= 1, movimiento tipo='venta' |
| TC-INV-004 | Venta sin stock suficiente | stock=0, vender 1 | 400 stock insuficiente |
| TC-INV-005 | subtotal es GENERATED, no se puede enviar | POST linea con subtotal | 400 o ignorado (GENERATED) |
| TC-INV-006 | Alerta stock mínimo al vender | stock queda en 3, mínimo=5 | Alerta en response + notificación |
| TC-INV-007 | GET /alertas solo muestra bajo mínimo | 10 productos, 2 bajo mínimo | Solo los 2 en alerta |
| TC-INV-008 | Venta crea ingreso en Finance Service | POST /ventas exitoso | Finance Service recibe POST /ingresos |
| TC-INV-009 | Finance Service caído no cancela venta | fire-and-forget | Venta se crea, ingreso se reintenta |
| TC-INV-010 | Ajuste negativo reduce stock | cantidad=-2 | stock_actual -= 2 |
| TC-INV-011 | Ajuste a valor negativo de stock | stock=1, ajuste=-3 | 400 stock no puede ser negativo |
| TC-INV-012 | Historial de movimientos inmutable | GET /movimientos | No se pueden editar |
| TC-INV-013 | Venta sin cliente registrado | id_cliente=null | 201 OK (venta anónima permitida) |
| TC-INV-014 | Recepcionista no puede ajustar stock | JWT rol=Recepción, POST /ajuste | 403 |
| TC-INV-015 | Datos de otra compañía invisibles | JWT compañía 1 | No ve productos ni ventas de compañía 2 |

---

## 8. Datos semilla (seeds)

```sql
-- Categorías de producto para gym de prueba
INSERT INTO inventario.categorias_producto (id_compania, id_sucursal, nombre) VALUES
  (1, 1, 'Suplementos'),
  (1, 1, 'Implementos deportivos'),
  (1, 1, 'Bebidas'),
  (1, 1, 'Snacks saludables');

-- Proveedor de prueba
INSERT INTO inventario.proveedores (id_compania, id_sucursal, nombre, telefono) VALUES
  (1, 1, 'Suplementos Ecuador', '0998765432');

-- Producto de prueba
INSERT INTO inventario.productos
  (id_compania, id_sucursal, id_categoria, id_proveedor,
   nombre, codigo_barras, precio_venta, precio_costo, stock_minimo)
VALUES
  (1, 1, 1, 1, 'Proteína Whey 2kg', '7890001234567', 45.00, 28.00, 5);

-- Stock inicial (en 0 — se actualiza con entradas)
INSERT INTO inventario.stock (id_compania, id_sucursal, id_producto, stock_actual) VALUES
  (1, 1, 1, 0);
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
FINANCE_SERVICE_URL=http://finance-service:8084

# ID de categoría de ingreso "Ventas tienda" en Finance Service
FINANCE_CATEGORIA_VENTAS_ID=2
```

---

## 10. Reglas de negocio críticas

| # | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | `subtotal` en detalle_ventas es `GENERATED` por la BD — nunca se acepta en el request | INSERT detalle_ventas |
| RN-02 | `cantidad` en movimientos_inventario nunca puede ser 0 — `CHECK (cantidad <> 0)` | INSERT movimientos |
| RN-03 | No se puede vender más stock del disponible — verificar antes del INSERT | POST /ventas |
| RN-04 | `stock_actual` nunca puede quedar negativo | UPDATE stock antes de confirmar |
| RN-05 | Toda modificación de stock genera un registro en `movimientos_inventario` — sin excepciones | INSERT/UPDATE de stock |
| RN-06 | `movimientos_inventario` es inmutable — solo se inserta, nunca se modifica ni elimina | No exponer PUT/DELETE |
| RN-07 | La llamada a Finance Service es fire-and-forget — un fallo del Finance Service no cancela la venta | POST /ventas |
| RN-08 | Al crear un producto se crea automáticamente su fila en `inventario.stock` con `stock_actual=0` | POST /productos |

---

*Inventory Service Spec v1.0 · Gym Administrator · Mayo 2026*
