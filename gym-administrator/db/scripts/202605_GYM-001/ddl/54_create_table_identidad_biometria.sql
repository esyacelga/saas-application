CREATE TABLE identidad.biometria (
  id               INT GENERATED ALWAYS AS IDENTITY       PRIMARY KEY,
  id_persona       INT          NOT NULL REFERENCES identidad.personas(id),
  id_compania      INT          NOT NULL,
  tipo             VARCHAR(20)  NOT NULL
                     CHECK (tipo IN ('huella','facial','iris')),
  hash_datos       BYTEA        NOT NULL,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_persona, id_compania, tipo)
);
