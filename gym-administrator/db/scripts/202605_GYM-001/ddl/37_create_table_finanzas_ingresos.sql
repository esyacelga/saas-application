CREATE TABLE finanzas.ingresos (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES finanzas.categorias_ingreso(id),
  id_membresia        INT,
  id_venta            INT,
  id_comprobante      BIGINT        REFERENCES facturacion.comprobantes(id),
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

COMMENT ON COLUMN finanzas.ingresos.id_comprobante IS 'FK opcional al comprobante electrónico SRI generado para este ingreso';
