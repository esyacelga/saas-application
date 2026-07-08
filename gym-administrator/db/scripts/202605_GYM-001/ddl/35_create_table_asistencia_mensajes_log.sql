CREATE TABLE asistencia.mensajes_log (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT         NOT NULL,
  id_sucursal       INT         NOT NULL,
  id_cliente        INT         NOT NULL REFERENCES core.clientes(id),
  id_plantilla      INT         REFERENCES asistencia.plantillas_mensajes(id),
  tipo              VARCHAR(50) NOT NULL,
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('whatsapp','email','llamada')),
  contenido         TEXT        NOT NULL,
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente','enviado','fallido')),
  fecha_programada  TIMESTAMPTZ,
  fecha_envio       TIMESTAMPTZ,
  id_usuario_envio  INT,
  eliminado         BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150)
);
