CREATE TABLE facturacion.secuenciales (
  id                  INT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT    NOT NULL,
  id_sucursal         INT    NOT NULL,
  cod_establecimiento CHAR(3) NOT NULL,
  cod_punto_emision   CHAR(3) NOT NULL,
  tipo_comprobante    CHAR(2) NOT NULL REFERENCES sri.tipos_comprobante(codigo),
  ultimo_secuencial   INT    NOT NULL DEFAULT 0,
  CONSTRAINT uq_facturacion_secuenciales UNIQUE (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, tipo_comprobante)
);
