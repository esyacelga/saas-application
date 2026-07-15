CREATE TABLE sri.formas_pago (
  codigo      CHAR(2)     NOT NULL,
  nombre      VARCHAR(80) NOT NULL,
  -- G10 · Bancarización: el SRI exige que el excedente sobre USD 500 se pague por
  -- un medio que utilice el sistema financiero. TRUE marca las formas de pago que
  -- cumplen esa condición (débito, dinero electrónico, prepago, crédito, otros).
  bancarizada BOOLEAN     NOT NULL DEFAULT FALSE,
  activo      BOOLEAN     NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_sri_formas_pago PRIMARY KEY (codigo)
);
