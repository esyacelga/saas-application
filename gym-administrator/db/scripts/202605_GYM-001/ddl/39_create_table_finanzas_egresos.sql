CREATE TABLE finanzas.egresos (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES finanzas.categorias_egreso(id),
  monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0),
  descripcion         TEXT,
  fecha               DATE          NOT NULL DEFAULT CURRENT_DATE,
  id_usuario_registro INT,
  eliminado           BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario    VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha      TIMESTAMPTZ,
  modifica_usuario    VARCHAR(150)
);
