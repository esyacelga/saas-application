--liquibase formatted sql

--changeset gesyacelga:v1.0-035-inventario-categorias-producto
--comment Categorías del catálogo de productos por sucursal
CREATE TABLE inventario.categorias_producto (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS inventario.categorias_producto;

--changeset gesyacelga:v1.0-036-inventario-proveedores
--comment Proveedores de productos por sucursal
CREATE TABLE inventario.proveedores (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(150) NOT NULL,
  telefono     VARCHAR(20),
  correo       VARCHAR(150),
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS inventario.proveedores;

--changeset gesyacelga:v1.0-037-inventario-productos
--comment Catálogo de productos con precio de venta, costo y stock mínimo
CREATE TABLE inventario.productos (
  id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania     INT           NOT NULL,
  id_sucursal     INT           NOT NULL,
  id_categoria    INT           NOT NULL REFERENCES inventario.categorias_producto(id),
  id_proveedor    INT           REFERENCES inventario.proveedores(id),
  nombre          VARCHAR(150)  NOT NULL,
  descripcion     TEXT,
  codigo_barras   VARCHAR(50)   UNIQUE,
  precio_venta    DECIMAL(10,2) NOT NULL CHECK (precio_venta >= 0),
  precio_costo    DECIMAL(10,2) NOT NULL CHECK (precio_costo >= 0),
  stock_minimo    INT           NOT NULL DEFAULT 0,
  activo          BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS inventario.productos;

--changeset gesyacelga:v1.0-038-inventario-inventario
--comment Stock actual por producto/compañía/sucursal; se actualiza con cada movimiento
CREATE TABLE inventario.inventario (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT         NOT NULL,
  id_sucursal          INT         NOT NULL,
  id_producto          INT         NOT NULL REFERENCES inventario.productos(id),
  stock_actual         INT         NOT NULL DEFAULT 0,
  ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (id_producto, id_compania, id_sucursal)
);
--rollback DROP TABLE IF EXISTS inventario.inventario;

--changeset gesyacelga:v1.0-039-inventario-ventas
--comment Cabecera de ventas; cliente puede ser NULL si es comprador externo
CREATE TABLE inventario.ventas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  id_cliente       INT           REFERENCES core.clientes(id),
  id_metodo_pago   INT,
  id_usuario_venta INT,
  total            DECIMAL(10,2) NOT NULL CHECK (total >= 0),
  fecha            DATE          NOT NULL DEFAULT CURRENT_DATE,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS inventario.ventas;

--changeset gesyacelga:v1.0-040-inventario-detalle-ventas
--comment Líneas de detalle por producto en cada venta
CREATE TABLE inventario.detalle_ventas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_venta         INT           NOT NULL REFERENCES inventario.ventas(id),
  id_producto      INT           NOT NULL REFERENCES inventario.productos(id),
  cantidad         INT           NOT NULL CHECK (cantidad > 0),
  precio_unitario  DECIMAL(10,2) NOT NULL,
  subtotal         DECIMAL(10,2) NOT NULL
);
--rollback DROP TABLE IF EXISTS inventario.detalle_ventas;

--changeset gesyacelga:v1.0-041-inventario-movimientos
--comment Audit log completo de entradas, ventas, ajustes y devoluciones de stock
CREATE TABLE inventario.movimientos_inventario (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania     INT           NOT NULL,
  id_sucursal     INT           NOT NULL,
  id_producto     INT           NOT NULL REFERENCES inventario.productos(id),
  id_proveedor    INT           REFERENCES inventario.proveedores(id),
  id_venta        INT           REFERENCES inventario.ventas(id),
  tipo            VARCHAR(20)   NOT NULL
                    CHECK (tipo IN ('entrada','venta','ajuste','devolucion')),
  cantidad        INT           NOT NULL,
  fecha           DATE          NOT NULL DEFAULT CURRENT_DATE,
  observacion     TEXT,
  id_usuario      INT,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_movimientos_producto
  ON inventario.movimientos_inventario(id_compania, id_producto, fecha);
--rollback DROP INDEX IF EXISTS inventario.idx_movimientos_producto;
--rollback DROP TABLE IF EXISTS inventario.movimientos_inventario;
