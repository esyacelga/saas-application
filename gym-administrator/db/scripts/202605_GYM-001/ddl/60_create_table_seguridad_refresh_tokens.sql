CREATE TABLE seguridad.refresh_tokens (
  id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  token            VARCHAR(128) NOT NULL UNIQUE,
  tipo_usuario     VARCHAR(20)  NOT NULL
                     CHECK (tipo_usuario IN ('plataforma','staff','cliente')),
  id_usuario       INT          NOT NULL,
  id_compania      INT,                       -- NULL for tipo_usuario = 'plataforma'
  expira_en        TIMESTAMPTZ  NOT NULL,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);

CREATE INDEX idx_refresh_tokens_token ON seguridad.refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_usuario ON seguridad.refresh_tokens(id_usuario, tipo_usuario);
