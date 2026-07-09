CREATE TABLE facturacion.reportes_ats (
  id                INT           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT           NOT NULL,
  id_sucursal       INT           NOT NULL,
  anio              SMALLINT      NOT NULL,
  mes               SMALLINT      NOT NULL,
  fecha_generacion  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  xml_path          VARCHAR(500),
  total_ventas      DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_iva         DECIMAL(14,2) NOT NULL DEFAULT 0,
  num_comprobantes  INT           NOT NULL DEFAULT 0,
  estado            VARCHAR(20)   NOT NULL DEFAULT 'GENERADO',
  CONSTRAINT uq_facturacion_reportes_ats UNIQUE (id_compania, id_sucursal, anio, mes),
  CONSTRAINT chk_reportes_ats_mes    CHECK (mes BETWEEN 1 AND 12),
  CONSTRAINT chk_reportes_ats_estado CHECK (estado IN ('GENERADO', 'PRESENTADO', 'CORREGIDO'))
);

COMMENT ON COLUMN facturacion.reportes_ats.estado IS 'GENERADO | PRESENTADO | CORREGIDO';
