CREATE TABLE seguridad.usuarios (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  id_rol           INT          NOT NULL REFERENCES seguridad.roles(id),
  id_persona       INT          NOT NULL REFERENCES identidad.personas(id),
  correo           VARCHAR(150) NOT NULL,
  password_hash    VARCHAR(255) NOT NULL,
  requiere_cambio_pwd BOOLEAN      NOT NULL DEFAULT FALSE,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso    TIMESTAMPTZ,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_persona, id_compania),
  UNIQUE (id_compania, correo)
);
