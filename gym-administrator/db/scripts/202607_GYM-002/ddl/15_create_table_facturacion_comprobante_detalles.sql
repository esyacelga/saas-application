CREATE TABLE facturacion.comprobante_detalles (
  id                    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania           INT           NOT NULL,
  id_sucursal           INT           NOT NULL,
  id_comprobante        BIGINT        NOT NULL REFERENCES facturacion.comprobantes(id),
  orden                 SMALLINT      NOT NULL,
  codigo_principal      VARCHAR(25),
  codigo_auxiliar       VARCHAR(25),
  descripcion           VARCHAR(300)  NOT NULL,
  cantidad              DECIMAL(14,6) NOT NULL,
  precio_unitario       DECIMAL(14,6) NOT NULL,
  descuento             DECIMAL(14,2) NOT NULL DEFAULT 0,
  precio_total_sin_imp  DECIMAL(14,2) NOT NULL,
  id_producto           INT,
  id_tipo_membresia     INT,
  CONSTRAINT uq_facturacion_comprobante_detalles UNIQUE (id_comprobante, orden)
);
