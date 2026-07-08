CREATE TABLE seguridad.bitacora_accesos (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  id_usuario       INT          NOT NULL REFERENCES seguridad.usuarios(id),
  modulo           VARCHAR(50)  NOT NULL,
  accion           VARCHAR(100) NOT NULL,
  entidad_id       INT,
  detalle          JSONB,
  ip               VARCHAR(45),
  fecha            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
