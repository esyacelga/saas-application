-- GYM-003 — Índice parcial para el dashboard "Ventas pendientes".
--
-- Consulta que soporta:
--   SELECT ... FROM core.membresias
--    WHERE id_compania = :idCompania
--      AND estado_pago = 'PENDIENTE'
--      AND eliminado   = FALSE
--    ORDER BY creacion_fecha DESC;
--
--   (§4.8 del doc — nueva sección "Pipeline de ventas" del panel admin).
--
-- Por qué es parcial:
--   La mayoría de filas son estado_pago = 'PAGADO' (ventas cobradas al
--   momento). Un índice completo sobre (id_compania, creacion_fecha) sería
--   grande y ya lo cubren índices de listados normales. Restringir con
--   WHERE estado_pago='PENDIENTE' AND eliminado=FALSE reduce el tamaño ~99%
--   y hace la búsqueda del dashboard casi O(1).
--
-- Convención de nombre: prefijo idx_ como el resto de índices de core.membresias
-- en el baseline (idx_membresias_cliente, idx_membresias_estado, etc.).

CREATE INDEX idx_membresias_pendientes_por_compania
  ON core.membresias(id_compania, creacion_fecha DESC)
  WHERE estado_pago = 'PENDIENTE' AND eliminado = FALSE;
