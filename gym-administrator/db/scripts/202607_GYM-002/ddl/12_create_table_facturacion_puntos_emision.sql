CREATE TABLE facturacion.puntos_emision (
  id                  INT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT          NOT NULL,
  id_sucursal         INT          NOT NULL,
  cod_establecimiento CHAR(3)      NOT NULL,
  cod_punto_emision   CHAR(3)      NOT NULL,
  descripcion         VARCHAR(100),
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado           BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  created_by          VARCHAR(150) NOT NULL DEFAULT 'sistema',
  CONSTRAINT uq_facturacion_puntos_emision UNIQUE (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision)
);
