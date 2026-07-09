CREATE TABLE sri.formas_pago (
  codigo CHAR(2)     NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  activo BOOLEAN     NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_sri_formas_pago PRIMARY KEY (codigo)
);
