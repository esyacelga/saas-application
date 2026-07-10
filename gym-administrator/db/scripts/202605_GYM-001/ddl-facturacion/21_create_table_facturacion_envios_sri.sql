CREATE TABLE facturacion.envios_sri (
  id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania     INT          NOT NULL,
  id_sucursal     INT          NOT NULL,
  id_comprobante  BIGINT       NOT NULL REFERENCES facturacion.comprobantes(id),
  tipo_operacion  VARCHAR(20)  NOT NULL,
  endpoint_url    VARCHAR(300) NOT NULL,
  request_soap    TEXT,
  response_soap   TEXT,
  http_status     INT,
  duracion_ms     INT,
  exitoso         BOOLEAN      NOT NULL DEFAULT FALSE,
  estado_sri      VARCHAR(50),
  codigo_error    VARCHAR(20),
  mensaje_error   TEXT,
  intento_numero  SMALLINT     NOT NULL DEFAULT 1,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_envios_sri_tipo_operacion CHECK (tipo_operacion IN ('RECEPCION', 'AUTORIZACION'))
);

COMMENT ON COLUMN facturacion.envios_sri.tipo_operacion IS 'RECEPCION | AUTORIZACION';
