CREATE TABLE marketing.promociones (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT           NOT NULL,
  id_sucursal          INT           NOT NULL,
  nombre               VARCHAR(150)  NOT NULL,
  tipo                 VARCHAR(30)   NOT NULL
                         CHECK (tipo IN ('2x1','porcentaje','servicio_extra','regalo')),
  descripcion          TEXT,
  condiciones          TEXT,
  descuento_porcentaje DECIMAL(5,2),
  max_personas         INT,
  fecha_inicio         DATE,
  fecha_fin            DATE,
  activa               BOOLEAN       NOT NULL DEFAULT TRUE,
  aplica_a_fidelidad   BOOLEAN       NOT NULL DEFAULT FALSE,
  eliminado            BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario     VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha       TIMESTAMPTZ,
  modifica_usuario     VARCHAR(150)
);
