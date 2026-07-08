CREATE TABLE identidad.usuarios_app (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_persona          INT          NOT NULL REFERENCES identidad.personas(id),
  id_compania         INT          NOT NULL,
  login               VARCHAR(150) NOT NULL,
  password_hash       VARCHAR(255) NOT NULL,
  requiere_cambio_pwd BOOLEAN      NOT NULL DEFAULT TRUE,
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso       TIMESTAMPTZ,
  token_recuperacion  VARCHAR(100),
  token_expira        TIMESTAMPTZ,
  eliminado           BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario    VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha      TIMESTAMPTZ,
  modifica_usuario    VARCHAR(150),
  UNIQUE (id_persona, id_compania),
  UNIQUE (id_compania, login)
);
