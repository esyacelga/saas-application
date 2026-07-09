CREATE TABLE facturacion.notas_credito_referencias (
  id                   BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT           NOT NULL,
  id_sucursal          INT           NOT NULL,
  id_comprobante       BIGINT        NOT NULL REFERENCES facturacion.comprobantes(id),
  cod_doc_modificado   CHAR(2)       NOT NULL DEFAULT '01',
  num_doc_modificado   VARCHAR(17)   NOT NULL,
  fecha_emision_modif  DATE          NOT NULL,
  id_motivo_anulacion  INT           REFERENCES sri.motivos_anulacion_nc(id),
  razon                TEXT          NOT NULL,
  valor_modificado     DECIMAL(14,2) NOT NULL,
  CONSTRAINT uq_facturacion_nc_referencias_comprobante UNIQUE (id_comprobante)
);

COMMENT ON COLUMN facturacion.notas_credito_referencias.num_doc_modificado IS 'Formato: 001-001-000000001';
