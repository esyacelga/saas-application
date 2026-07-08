CREATE TABLE core.clientes (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_persona       INT          NOT NULL REFERENCES identidad.personas(id),
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  peso_kg          DECIMAL(5,2),
  altura_cm        DECIMAL(5,1),
  objetivos        TEXT,
  lesiones         TEXT,
  estado           VARCHAR(20)  NOT NULL DEFAULT 'activo'
                     CHECK (estado IN (
                       'activo','proximo_vencer','vencido',
                       'congelado','riesgo_abandono'
                     )),
  fecha_ingreso    DATE         NOT NULL DEFAULT CURRENT_DATE,
  sexo             VARCHAR(1)   CHECK (sexo IN ('M', 'F', 'O')),
  codigo_carnet    VARCHAR(100) UNIQUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  UNIQUE (id_persona, id_compania)
);
