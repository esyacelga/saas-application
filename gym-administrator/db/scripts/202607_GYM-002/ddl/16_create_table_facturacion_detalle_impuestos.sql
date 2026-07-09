CREATE TABLE facturacion.comprobante_detalle_impuestos (
  id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania        INT           NOT NULL,
  id_sucursal        INT           NOT NULL,
  id_detalle         BIGINT        NOT NULL REFERENCES facturacion.comprobante_detalles(id),
  codigo_impuesto    CHAR(1)       NOT NULL REFERENCES sri.tipos_impuesto(codigo),
  codigo_porcentaje  CHAR(1)       NOT NULL,
  tarifa             DECIMAL(5,2)  NOT NULL,
  base_imponible     DECIMAL(14,2) NOT NULL,
  valor              DECIMAL(14,2) NOT NULL,
  CONSTRAINT uq_facturacion_detalle_impuestos UNIQUE (id_detalle, codigo_impuesto, codigo_porcentaje)
);
