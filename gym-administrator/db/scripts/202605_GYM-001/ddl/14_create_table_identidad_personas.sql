CREATE TABLE identidad.personas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ci               VARCHAR(20)  NOT NULL,
  sexo             CHAR(1)      CHECK (sexo IN ('M', 'F')),
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  foto_url         VARCHAR(255),
  fecha_nacimiento DATE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
