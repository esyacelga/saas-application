CREATE TABLE sri.tipos_comprobante (
  codigo  CHAR(2)      NOT NULL,
  nombre  VARCHAR(50)  NOT NULL,
  version VARCHAR(10)  NOT NULL,
  activo  BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_sri_tipos_comprobante PRIMARY KEY (codigo)
);
