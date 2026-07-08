CREATE TABLE core.tipos_membresia (
  id               INT GENERATED ALWAYS AS IDENTITY        PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  nombre           VARCHAR(100)  NOT NULL,
  modo_control     VARCHAR(20)   NOT NULL DEFAULT 'calendario'
                     CHECK (modo_control IN ('calendario','accesos')),
  duracion_tipo    VARCHAR(20)   NOT NULL
                     CHECK (duracion_tipo IN ('dias','semanas','meses','años')),
  duracion_valor   INT           NOT NULL CHECK (duracion_valor > 0),
  dias_acceso      INT           CHECK (dias_acceso IS NULL OR dias_acceso > 0),
  precio           DECIMAL(10,2) NOT NULL CHECK (precio >= 0),
  activo           BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),

  CONSTRAINT chk_accesos_requiere_dias CHECK (
    modo_control <> 'accesos' OR dias_acceso IS NOT NULL
  ),
  UNIQUE (id_compania, nombre)
);
