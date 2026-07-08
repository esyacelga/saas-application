CREATE TABLE inventario.ventas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  id_cliente       INT           REFERENCES core.clientes(id),
  id_metodo_pago   INT,
  id_usuario_venta INT,
  total            DECIMAL(10,2) NOT NULL CHECK (total >= 0),
  fecha            DATE          NOT NULL DEFAULT CURRENT_DATE,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
