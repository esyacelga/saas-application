CREATE TABLE facturacion.certificados (
  id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT          NOT NULL,
  id_sucursal         INT          NOT NULL,
  alias               VARCHAR(100) NOT NULL,
  entidad_emisora     VARCHAR(50)  NOT NULL,
  p12_cifrado         BYTEA        NOT NULL,
  password_cifrado    BYTEA        NOT NULL,
  subject_cn          VARCHAR(200),
  ruc_certificado     VARCHAR(13),
  fecha_emision       DATE,
  fecha_vencimiento   DATE,
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  revocado            BOOLEAN      NOT NULL DEFAULT FALSE,
  motivo_revocacion   TEXT,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  created_by          VARCHAR(150) NOT NULL DEFAULT 'sistema',
  CONSTRAINT uq_facturacion_certificados UNIQUE (id_compania, id_sucursal, alias)
);

COMMENT ON COLUMN facturacion.certificados.entidad_emisora IS 'BCE, SECURITY_DATA, ANF';
COMMENT ON COLUMN facturacion.certificados.p12_cifrado     IS 'Archivo .p12 cifrado en reposo';
COMMENT ON COLUMN facturacion.certificados.password_cifrado IS 'Contraseña del .p12 cifrada en reposo';
