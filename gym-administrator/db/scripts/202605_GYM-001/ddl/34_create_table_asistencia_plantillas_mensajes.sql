CREATE TABLE asistencia.plantillas_mensajes (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  tipo             VARCHAR(50)  NOT NULL
                     CHECK (tipo IN (
                       'motivacional','ausencia_2d',
                       'recuperacion_5d','recuperacion_10d','recuperacion_15d',
                       'vencimiento_3d','vencimiento_hoy'
                     )),
  nombre           VARCHAR(100) NOT NULL,
  contenido        TEXT         NOT NULL,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
