CREATE TABLE tenant.companias (
  id                     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre                 VARCHAR(150) NOT NULL,
  -- RUC opcional: el auto-registro público no pide datos tributarios (disclosure
  -- progresivo). El RUC se completa luego en el wizard de facturación. UNIQUE sobre
  -- columna nullable permite varios NULL e impide RUC reales duplicados.
  ruc                    VARCHAR(20)  UNIQUE,
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
  -- Opt-in WhatsApp del dueño (ex GYM-002, consolidado en la baseline)
  acepta_whatsapp         BOOLEAN     NOT NULL DEFAULT FALSE,
  fecha_consentimiento_wa TIMESTAMPTZ,
  activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado              BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario       VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha         TIMESTAMPTZ,
  modifica_usuario       VARCHAR(150)
);

COMMENT ON COLUMN tenant.companias.ruc                    IS 'RUC del contribuyente. NULL cuando el gimnasio se registró sin facturar (se completa luego en el wizard de facturación). UNIQUE permite varios NULL e impide RUC reales duplicados.';
COMMENT ON COLUMN tenant.companias.nombre_comercial       IS 'Nombre comercial del establecimiento para el comprobante electrónico';
COMMENT ON COLUMN tenant.companias.dir_matriz             IS 'Dirección de la matriz registrada en el SRI';
COMMENT ON COLUMN tenant.companias.obligado_contabilidad  IS 'TRUE si la empresa está obligada a llevar contabilidad según el SRI';
COMMENT ON COLUMN tenant.companias.contribuyente_especial IS 'Número de resolución si es contribuyente especial, NULL si no aplica';
COMMENT ON COLUMN tenant.companias.trial_usado            IS 'Flag irrevocable: TRUE si la compañía ya activó su Trial alguna vez (RN-01)';
COMMENT ON COLUMN tenant.companias.fecha_trial_usado      IS 'Timestamp del momento en que se activó el Trial por primera y única vez';
COMMENT ON COLUMN tenant.companias.acepta_whatsapp         IS 'TRUE solo cuando el dueño de la compañía dio opt-in explícito para recibir avisos de vencimiento de suscripción por WhatsApp. FALSE por defecto: sin este flag NUNCA se envía WhatsApp. Se captura en onboarding o config de notificaciones.';
COMMENT ON COLUMN tenant.companias.fecha_consentimiento_wa IS 'Timestamp del momento en que el dueño aceptó recibir WhatsApp (prueba mínima de opt-in ante Meta). NULL mientras acepta_whatsapp = FALSE.';
