CREATE INDEX idx_asistencias_cliente_fecha
  ON asistencia.asistencias(id_compania, id_cliente, fecha);

CREATE INDEX idx_asistencias_membresia
  ON asistencia.asistencias(id_membresia, fecha);

CREATE INDEX idx_mensajes_log_compania_cliente
  ON asistencia.mensajes_log(id_compania, id_cliente, estado);
