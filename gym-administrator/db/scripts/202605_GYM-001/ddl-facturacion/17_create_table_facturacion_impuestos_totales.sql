CREATE TABLE facturacion.comprobante_impuestos_totales (
  id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania        INT           NOT NULL,
  id_sucursal        INT           NOT NULL,
  id_comprobante     BIGINT        NOT NULL REFERENCES facturacion.comprobantes(id),
  codigo_impuesto    CHAR(1)       NOT NULL,
  codigo_porcentaje  CHAR(1)       NOT NULL,
  base_imponible     DECIMAL(14,2) NOT NULL,
  valor              DECIMAL(14,2) NOT NULL,
  CONSTRAINT uq_facturacion_impuestos_totales UNIQUE (id_comprobante, codigo_impuesto, codigo_porcentaje)
);
