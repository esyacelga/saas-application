CREATE TABLE marketing.reglas_beneficios (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT           NOT NULL,
  id_sucursal      INT           NOT NULL,
  meses_sin_faltas INT           NOT NULL CHECK (meses_sin_faltas > 0),
  tipo_beneficio   VARCHAR(30)   NOT NULL
                     CHECK (tipo_beneficio IN ('descuento','servicio','regalo')),
  descripcion      VARCHAR(255)  NOT NULL,
  valor            DECIMAL(10,2),
  activo           BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_compania, id_sucursal, meses_sin_faltas)
);
