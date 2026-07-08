CREATE INDEX idx_compania_planes_compania_estado
  ON tenant.compania_planes(id_compania, estado);

CREATE INDEX idx_compania_planes_vencimiento
  ON tenant.compania_planes(fecha_fin)
  WHERE estado = 'activo';
