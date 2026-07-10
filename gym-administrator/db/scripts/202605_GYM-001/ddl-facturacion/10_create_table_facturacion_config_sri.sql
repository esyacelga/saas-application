CREATE TABLE facturacion.config_sri (
  id_compania              INT          NOT NULL,
  id_sucursal              INT          NOT NULL,
  razon_social             VARCHAR(300) NOT NULL,
  nombre_comercial         VARCHAR(300),
  ruc                      VARCHAR(13)  NOT NULL,
  dir_establecimiento      VARCHAR(300),
  obligado_contabilidad    BOOLEAN      NOT NULL DEFAULT FALSE,
  contribuyente_especial   VARCHAR(10),
  ambiente                 CHAR(1)      NOT NULL DEFAULT '1',
  tipo_emision             CHAR(1)      NOT NULL DEFAULT '1',
  facturacion_activa       BOOLEAN      NOT NULL DEFAULT FALSE,
  email_notificacion       VARCHAR(255),
  updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_by               VARCHAR(150) NOT NULL DEFAULT 'sistema',
  CONSTRAINT pk_facturacion_config_sri PRIMARY KEY (id_compania, id_sucursal),
  CONSTRAINT chk_config_sri_ambiente      CHECK (ambiente      IN ('1', '2')),
  CONSTRAINT chk_config_sri_tipo_emision  CHECK (tipo_emision  IN ('1'))
);

COMMENT ON COLUMN facturacion.config_sri.ambiente     IS '1=pruebas, 2=produccion';
COMMENT ON COLUMN facturacion.config_sri.tipo_emision IS '1=normal';
