CREATE TABLE sri.tarifas_iva (
  codigo         CHAR(1)      NOT NULL,
  nombre         VARCHAR(20)  NOT NULL,
  porcentaje     DECIMAL(5,2) NOT NULL,
  vigente_desde  DATE,
  vigente_hasta  DATE,
  CONSTRAINT pk_sri_tarifas_iva PRIMARY KEY (codigo)
);
