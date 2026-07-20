CREATE INDEX idx_membresias_cliente_estado
  ON core.membresias(id_cliente, estado, fecha_fin);

CREATE INDEX idx_membresias_compania_estado
  ON core.membresias(id_compania, estado, fecha_fin);

CREATE INDEX idx_membresias_pendientes_por_compania
  ON core.membresias(id_compania, creacion_fecha DESC)
  WHERE estado_pago = 'PENDIENTE' AND eliminado = FALSE;

-- UNIQUE parcial: garantiza que un cliente solo puede tener UNA membresía viva en
-- estado PENDIENTE simultáneamente (sin importar el origen). Cierra la race condition
-- del check-then-act en MembresiaService.solicitarMembresia: si dos requests concurrentes
-- pasan la validación previa, PostgreSQL rechaza el segundo INSERT y el
-- DataIntegrityMapper lo traduce a codigo=solicitud_ya_existe.
CREATE UNIQUE INDEX uq_membresias_pendiente_por_cliente_vivo
  ON core.membresias(id_cliente)
  WHERE estado_pago = 'PENDIENTE' AND eliminado = FALSE;

CREATE INDEX idx_clientes_compania_estado
  ON core.clientes(id_compania, estado);
