CREATE TABLE facturacion.notificaciones_receptor (
  id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  id_comprobante   BIGINT       NOT NULL REFERENCES facturacion.comprobantes(id),
  canal            VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
  destinatario     VARCHAR(150) NOT NULL,
  estado           VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
  proveedor_msg_id VARCHAR(100),
  intentos         SMALLINT     NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_notificaciones_canal  CHECK (canal  IN ('EMAIL', 'WHATSAPP')),
  CONSTRAINT chk_notificaciones_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'REBOTADO'))
);

COMMENT ON COLUMN facturacion.notificaciones_receptor.canal  IS 'EMAIL | WHATSAPP';
COMMENT ON COLUMN facturacion.notificaciones_receptor.estado IS 'PENDIENTE | ENVIADO | FALLIDO | REBOTADO';
