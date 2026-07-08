CREATE TABLE tenant.companias (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre           VARCHAR(150) NOT NULL,
  ruc              VARCHAR(20)  NOT NULL UNIQUE,
  logo_url         VARCHAR(255),
  telefono         VARCHAR(20),
  whatsapp         VARCHAR(20),
  correo           VARCHAR(150),
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
