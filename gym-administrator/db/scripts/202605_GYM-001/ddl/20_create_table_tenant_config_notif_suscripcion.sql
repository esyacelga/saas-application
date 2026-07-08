CREATE TABLE tenant.config_notif_suscripcion (
  id_compania      INT         NOT NULL,
  dias_antes       INT         NOT NULL,
  canal            VARCHAR(20) NOT NULL
                     CHECK (canal IN ('email','whatsapp','ambos')),
  activo           BOOLEAN     NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN     NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  PRIMARY KEY (id_compania, dias_antes)
);
