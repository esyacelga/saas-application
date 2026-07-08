CREATE INDEX idx_membresias_cliente_estado
  ON core.membresias(id_cliente, estado, fecha_fin);

CREATE INDEX idx_membresias_compania_estado
  ON core.membresias(id_compania, estado, fecha_fin);

CREATE INDEX idx_clientes_compania_estado
  ON core.clientes(id_compania, estado);
