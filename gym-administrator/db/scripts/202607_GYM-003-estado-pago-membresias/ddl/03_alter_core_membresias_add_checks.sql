-- GYM-003 — Agrega los dos CHECKs de consistencia en core.membresias.
--
--   1) ck_membresias_fechas_por_estado_pago
--      Garantiza que las fechas de vigencia estén ausentes mientras la
--      membresía esté en PENDIENTE y presentes cuando pase a PAGADO. Evita
--      estados intermedios ambiguos ("PENDIENTE pero con fechas viejas de
--      una pre-carga" o "PAGADO sin fechas").
--
--   2) ck_membresias_motivo_si_eliminado
--      Garantiza que el rechazo (soft-delete) tenga siempre los cuatro
--      campos de auditoría poblados (eliminado + fecha_eliminacion +
--      eliminado_por + motivo_eliminacion), y que una membresía no-eliminada
--      no tenga ninguno de esos campos. Evita registros a medio rechazar.
--
-- Compatibilidad con datos existentes:
--   Tras el backfill del script 01 y el DROP NOT NULL del script 02, todas
--   las filas existentes cumplen ambas condiciones:
--     - estado_pago = 'PAGADO' y fechas NOT NULL  → cumple CHECK 1.
--     - eliminado = FALSE y los 3 campos de auditoría NULL → cumple CHECK 2.
--   Por eso la creación del CHECK no requiere NOT VALID + posterior VALIDATE.

ALTER TABLE core.membresias
  ADD CONSTRAINT ck_membresias_fechas_por_estado_pago CHECK (
    (estado_pago = 'PENDIENTE' AND fecha_inicio IS NULL AND fecha_fin IS NULL)
    OR
    (estado_pago = 'PAGADO' AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL)
  );

ALTER TABLE core.membresias
  ADD CONSTRAINT ck_membresias_motivo_si_eliminado CHECK (
    (eliminado = FALSE AND motivo_eliminacion IS NULL AND fecha_eliminacion IS NULL AND eliminado_por IS NULL)
    OR
    (eliminado = TRUE AND motivo_eliminacion IS NOT NULL AND fecha_eliminacion IS NOT NULL AND eliminado_por IS NOT NULL)
  );
