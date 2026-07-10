CREATE TABLE tenant.notificaciones_suscripcion (
  id                INT         PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT         NOT NULL REFERENCES tenant.compania_planes(id),
  id_compania       INT         REFERENCES tenant.companias(id),
  dias_antes        INT         NOT NULL,
  tipo              VARCHAR(40),
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('email','whatsapp','banner')),
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente','enviado','fallido','reintentar')),
  fecha_envio       TIMESTAMPTZ,
  proximo_intento   TIMESTAMPTZ,
  intentos          INTEGER      NOT NULL DEFAULT 1,
  ultimo_error      TEXT,
  descartado_at     TIMESTAMPTZ,
  eliminado         BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150)
);

COMMENT ON COLUMN tenant.notificaciones_suscripcion.id_compania     IS 'FK al tenant afectado — filtrado multi-tenant sin JOIN.';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.tipo            IS 'VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM / PAGO_RECHAZADO / TRIAL_ACTIVADO.';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.proximo_intento IS 'Fecha/hora mínima en la que un worker puede volver a intentar el envío. NULL = ejecutable inmediatamente.';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.intentos        IS 'Cantidad de intentos de envío (para retry exponencial con canal=''email'')';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.ultimo_error    IS 'Mensaje del último error SMTP/render (troubleshooting operativo)';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.descartado_at   IS 'Timestamp en que el owner descartó el banner in-app (solo aplica cuando canal=''banner'')';
