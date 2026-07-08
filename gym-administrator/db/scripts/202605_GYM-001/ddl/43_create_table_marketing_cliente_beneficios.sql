CREATE TABLE marketing.cliente_beneficios (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT         NOT NULL,
  id_sucursal      INT         NOT NULL,
  id_cliente       INT         NOT NULL REFERENCES core.clientes(id),
  id_regla         INT         NOT NULL REFERENCES marketing.reglas_beneficios(id),
  fecha_otorgado   DATE        NOT NULL DEFAULT CURRENT_DATE,
  estado           VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                     CHECK (estado IN ('pendiente','aplicado','expirado')),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
