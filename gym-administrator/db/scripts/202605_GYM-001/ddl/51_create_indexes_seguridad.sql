CREATE INDEX idx_bitacora_compania_fecha
  ON seguridad.bitacora_accesos(id_compania, fecha);

CREATE INDEX idx_usuarios_compania
  ON seguridad.usuarios(id_compania);
