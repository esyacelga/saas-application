CREATE TABLE facturacion.comprobante_info_adicional (
  id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_comprobante BIGINT       NOT NULL REFERENCES facturacion.comprobantes(id),
  orden          SMALLINT     NOT NULL,
  nombre         VARCHAR(100) NOT NULL,
  valor          VARCHAR(300) NOT NULL,
  CONSTRAINT uq_facturacion_info_adicional UNIQUE (id_comprobante, orden)
);
