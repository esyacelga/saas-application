CREATE TABLE tenant.sucursales (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL REFERENCES tenant.companias(id),
  nombre           VARCHAR(150) NOT NULL,
  direccion        VARCHAR(255),
  es_principal     BOOLEAN      NOT NULL DEFAULT FALSE,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  qr_token         VARCHAR(100) UNIQUE,
  qr_token_expira  TIMESTAMPTZ,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
