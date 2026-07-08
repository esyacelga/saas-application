CREATE TABLE saas.usuarios_plataforma (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_persona       INT          NOT NULL REFERENCES identidad.personas(id),
  correo           VARCHAR(150) NOT NULL UNIQUE,
  password_hash    VARCHAR(255) NOT NULL,
  rol              VARCHAR(30)  NOT NULL DEFAULT 'super_admin'
                     CHECK (rol IN ('super_admin','soporte','viewer')),
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso    TIMESTAMPTZ,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_persona)
);
