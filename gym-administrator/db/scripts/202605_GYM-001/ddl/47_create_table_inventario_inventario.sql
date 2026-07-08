CREATE TABLE inventario.stock (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT         NOT NULL,
  id_sucursal          INT         NOT NULL,
  id_producto          INT         NOT NULL REFERENCES inventario.productos(id),
  stock_actual         INT         NOT NULL DEFAULT 0,
  ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  eliminado            BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario     VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha       TIMESTAMPTZ,
  modifica_usuario     VARCHAR(150),
  UNIQUE (id_producto, id_compania, id_sucursal)
);
