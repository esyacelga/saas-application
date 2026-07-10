CREATE TABLE sri.motivos_anulacion_nc (
  id          INT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo      VARCHAR(20)  NOT NULL,
  descripcion VARCHAR(200) NOT NULL,
  CONSTRAINT uq_sri_motivos_anulacion_nc_codigo UNIQUE (codigo)
);
