CREATE TABLE inventario.productos (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  id_categoria     INT           NOT NULL REFERENCES inventario.categorias_producto(id),
  id_proveedor     INT           REFERENCES inventario.proveedores(id),
  nombre           VARCHAR(150)  NOT NULL,
  descripcion      TEXT,
  codigo_barras    VARCHAR(50)   UNIQUE,
  precio_venta     DECIMAL(10,2) NOT NULL CHECK (precio_venta >= 0),
  precio_costo     DECIMAL(10,2) NOT NULL CHECK (precio_costo >= 0),
  stock_minimo     INT           NOT NULL DEFAULT 0,
  activo           BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
