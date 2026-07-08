CREATE INDEX idx_cliente_promociones_cliente
  ON marketing.cliente_promociones(id_cliente, estado);

CREATE INDEX idx_cliente_beneficios_cliente
  ON marketing.cliente_beneficios(id_cliente, estado);
