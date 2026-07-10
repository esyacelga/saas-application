CREATE TABLE tenant.companias (
  id                     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre                 VARCHAR(150) NOT NULL,
  ruc                    VARCHAR(20)  NOT NULL UNIQUE,
  logo_url               VARCHAR(255),
  telefono               VARCHAR(20),
  whatsapp               VARCHAR(20),
  correo                 VARCHAR(150),
  -- Datos fiscales para facturación electrónica SRI
  nombre_comercial       VARCHAR(300),
  dir_matriz             VARCHAR(300),
  obligado_contabilidad  BOOLEAN      NOT NULL DEFAULT FALSE,
  contribuyente_especial VARCHAR(10),
  -- Trial único por tenant (REQ-SAAS-001, RN-01)
  trial_usado            BOOLEAN      NOT NULL DEFAULT FALSE,
  fecha_trial_usado      TIMESTAMPTZ,
  activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado              BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario       VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha         TIMESTAMPTZ,
  modifica_usuario       VARCHAR(150)
);

COMMENT ON COLUMN tenant.companias.nombre_comercial       IS 'Nombre comercial del establecimiento para el comprobante electrónico';
COMMENT ON COLUMN tenant.companias.dir_matriz             IS 'Dirección de la matriz registrada en el SRI';
COMMENT ON COLUMN tenant.companias.obligado_contabilidad  IS 'TRUE si la empresa está obligada a llevar contabilidad según el SRI';
COMMENT ON COLUMN tenant.companias.contribuyente_especial IS 'Número de resolución si es contribuyente especial, NULL si no aplica';
COMMENT ON COLUMN tenant.companias.trial_usado            IS 'Flag irrevocable: TRUE si la compañía ya activó su Trial alguna vez (RN-01)';
COMMENT ON COLUMN tenant.companias.fecha_trial_usado      IS 'Timestamp del momento en que se activó el Trial por primera y única vez';
