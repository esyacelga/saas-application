CREATE INDEX idx_ingresos_compania_fecha
  ON finanzas.ingresos(id_compania, fecha);

CREATE INDEX idx_egresos_compania_fecha
  ON finanzas.egresos(id_compania, fecha);
