CREATE TABLE marketing.cliente_promociones (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT         NOT NULL,
  id_sucursal      INT         NOT NULL,
  id_cliente       INT         NOT NULL REFERENCES core.clientes(id),
  id_promocion     INT         NOT NULL REFERENCES marketing.promociones(id),
  id_membresia     INT,
  fecha_asignacion DATE        NOT NULL DEFAULT CURRENT_DATE,
  fecha_uso        DATE,
  estado           VARCHAR(20) NOT NULL DEFAULT 'asignada'
                     CHECK (estado IN ('asignada','usada','expirada')),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
