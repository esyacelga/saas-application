CREATE TABLE facturacion.cola_envio (
  id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT          NOT NULL,
  id_sucursal       INT          NOT NULL,
  id_comprobante    BIGINT       NOT NULL REFERENCES facturacion.comprobantes(id),
  estado            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
  proxima_ejecucion TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  intentos          SMALLINT     NOT NULL DEFAULT 0,
  max_intentos      SMALLINT     NOT NULL DEFAULT 5,
  ultimo_error      TEXT,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_facturacion_cola_envio_comprobante UNIQUE (id_comprobante),
  CONSTRAINT chk_cola_envio_estado CHECK (estado IN ('PENDIENTE', 'PROCESANDO', 'COMPLETADO', 'FALLIDO_DEFINITIVO'))
);

COMMENT ON COLUMN facturacion.cola_envio.estado IS 'PENDIENTE | PROCESANDO | COMPLETADO | FALLIDO_DEFINITIVO';
