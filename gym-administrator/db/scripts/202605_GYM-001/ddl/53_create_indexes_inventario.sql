CREATE INDEX idx_movimientos_producto
  ON inventario.movimientos_inventario(id_compania, id_producto, fecha);

CREATE INDEX idx_stock_compania_sucursal
  ON inventario.stock(id_compania, id_sucursal);
