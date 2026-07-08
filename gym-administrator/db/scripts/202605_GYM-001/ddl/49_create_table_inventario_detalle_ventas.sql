CREATE TABLE inventario.detalle_ventas (
  id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_venta        INT           NOT NULL REFERENCES inventario.ventas(id),
  id_producto     INT           NOT NULL REFERENCES inventario.productos(id),
  cantidad        INT           NOT NULL CHECK (cantidad > 0),
  precio_unitario DECIMAL(10,2) NOT NULL,
  subtotal        DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
