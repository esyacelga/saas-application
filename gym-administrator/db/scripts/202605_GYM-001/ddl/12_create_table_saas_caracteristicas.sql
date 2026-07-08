CREATE TABLE saas.caracteristicas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo           VARCHAR(50)  NOT NULL UNIQUE,
  nombre           VARCHAR(100) NOT NULL,
  modulo           VARCHAR(50)  NOT NULL,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
