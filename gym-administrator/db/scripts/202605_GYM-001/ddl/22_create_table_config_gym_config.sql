CREATE TABLE config.gym_config (
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  clave            VARCHAR(100) NOT NULL,
  valor            TEXT,
  descripcion      VARCHAR(255),
  tipo             VARCHAR(20)  NOT NULL DEFAULT 'texto'
                     CHECK (tipo IN ('texto','numero','booleano','json')),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  PRIMARY KEY (id_compania, id_sucursal, clave)
);
