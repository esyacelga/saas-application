CREATE TABLE facturacion.comprobante_pagos (
  id                   BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT           NOT NULL,
  id_sucursal          INT           NOT NULL,
  id_comprobante       BIGINT        NOT NULL REFERENCES facturacion.comprobantes(id),
  forma_pago           CHAR(2)       NOT NULL REFERENCES sri.formas_pago(codigo),
  total                DECIMAL(14,2) NOT NULL,
  plazo                INT,
  unidad_tiempo        VARCHAR(20),
  id_metodo_pago_gym   INT,
  CONSTRAINT chk_comprobante_pagos_unidad_tiempo CHECK (unidad_tiempo IN ('dias', 'meses', 'anios') OR unidad_tiempo IS NULL)
);
