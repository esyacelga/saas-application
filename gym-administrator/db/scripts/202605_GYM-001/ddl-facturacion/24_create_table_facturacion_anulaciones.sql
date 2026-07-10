CREATE TABLE facturacion.anulaciones (
  id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania            INT          NOT NULL,
  id_sucursal            INT          NOT NULL,
  id_comprobante         BIGINT       NOT NULL REFERENCES facturacion.comprobantes(id),
  motivo                 TEXT         NOT NULL,
  estado                 VARCHAR(20)  NOT NULL DEFAULT 'SOLICITADA',
  id_comprobante_nc      BIGINT       REFERENCES facturacion.comprobantes(id),
  id_usuario_solicita    INT          NOT NULL,
  id_usuario_aprueba     INT,
  fecha_solicitud        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  fecha_resolucion       TIMESTAMPTZ,
  observacion_resolucion TEXT,
  CONSTRAINT chk_anulaciones_estado CHECK (estado IN ('SOLICITADA', 'APROBADA', 'RECHAZADA', 'EJECUTADA'))
);

COMMENT ON COLUMN facturacion.anulaciones.estado             IS 'SOLICITADA | APROBADA | RECHAZADA | EJECUTADA';
COMMENT ON COLUMN facturacion.anulaciones.id_comprobante_nc  IS 'Nota de crédito generada al ejecutar la anulación';
