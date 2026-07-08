CREATE TABLE tenant.notificaciones_suscripcion (
  id                INT         PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT         NOT NULL REFERENCES tenant.compania_planes(id),
  dias_antes        INT         NOT NULL,
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('email','whatsapp')),
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('enviado','fallido')),
  fecha_envio       TIMESTAMPTZ,
  eliminado         BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150)
);
