CREATE TABLE facturacion.comprobantes_detalle (
  id                          BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_comprobante              BIGINT         NOT NULL
                                             REFERENCES facturacion.comprobantes(id)
                                             ON DELETE CASCADE,
  id_compania                 INT            NOT NULL,
  id_sucursal                 INT            NOT NULL,
  codigo_principal            VARCHAR(25)    NOT NULL,
  codigo_auxiliar             VARCHAR(25),
  descripcion                 VARCHAR(300)   NOT NULL,
  cantidad                    DECIMAL(18,6)  NOT NULL,
  precio_unitario             DECIMAL(18,6)  NOT NULL,
  descuento                   DECIMAL(18,2)  NOT NULL DEFAULT 0,
  precio_total_sin_impuesto   DECIMAL(18,2)  NOT NULL,
  orden                       SMALLINT       NOT NULL DEFAULT 1,
  creado_en                   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_comprobantes_detalle_orden UNIQUE (id_comprobante, orden),
  CONSTRAINT chk_comprobantes_detalle_cantidad_positiva CHECK (cantidad > 0),
  CONSTRAINT chk_comprobantes_detalle_precio_no_negativo CHECK (precio_unitario >= 0)
);

-- Permite cargar todos los ítems de un comprobante en una sola consulta (JOIN principal desde comprobantes)
CREATE INDEX idx_comp_det_comprobante
    ON facturacion.comprobantes_detalle(id_comprobante);

-- Permite filtrar o agregar ítems por empresa/sucursal sin pasar por el comprobante padre
CREATE INDEX idx_comp_det_empresa
    ON facturacion.comprobantes_detalle(id_compania, id_sucursal);
