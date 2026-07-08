CREATE TABLE asistencia.asistencias (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT         NOT NULL,
  id_sucursal      INT         NOT NULL,
  id_cliente       INT         NOT NULL REFERENCES core.clientes(id),
  id_membresia     INT         NOT NULL REFERENCES core.membresias(id),
  fecha            DATE        NOT NULL,
  hora_entrada     TIME        NOT NULL,
  metodo_registro  VARCHAR(20) NOT NULL DEFAULT 'qr_cliente'
                     CHECK (metodo_registro IN ('qr_cliente','biometrico','manual','app_cliente')),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_membresia, fecha)
);
