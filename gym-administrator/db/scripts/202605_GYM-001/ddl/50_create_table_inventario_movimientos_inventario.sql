CREATE TABLE inventario.movimientos_inventario (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  id_producto      INT           NOT NULL REFERENCES inventario.productos(id),
  id_proveedor     INT           REFERENCES inventario.proveedores(id),
  id_venta         INT           REFERENCES inventario.ventas(id),
  tipo             VARCHAR(20)   NOT NULL
                     CHECK (tipo IN ('entrada','venta','ajuste','devolucion')),
  cantidad         INT           NOT NULL CHECK (cantidad <> 0),
  fecha            DATE          NOT NULL DEFAULT CURRENT_DATE,
  observacion      TEXT,
  id_usuario       INT,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
