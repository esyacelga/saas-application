CREATE INDEX idx_ingresos_compania_fecha
  ON finanzas.ingresos(id_compania, fecha);

CREATE INDEX idx_egresos_compania_fecha
  ON finanzas.egresos(id_compania, fecha);

-- Consulta de ingresos que ya tienen comprobante SRI emitido (parcial: la mayoría no lo tiene).
CREATE INDEX idx_ingresos_comprobante
  ON finanzas.ingresos(id_comprobante)
  WHERE id_comprobante IS NOT NULL;
