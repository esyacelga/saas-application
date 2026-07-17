-- GYM-003 — Afloja el NOT NULL de fecha_inicio y fecha_fin en core.membresias.
--
-- Contexto:
--   Cuando una membresía se crea en estado_pago = 'PENDIENTE' (venta desde PWA,
--   HU-B) todavía no existe fecha de vigencia — esas fechas se calculan al
--   confirmar el pago (POST /membresias/{id}/confirmar-pago). Mientras la
--   membresía viva como PENDIENTE, ambas fechas deben ser NULL para comunicar
--   sin ambigüedad "aún no aplica".
--
--   La consistencia entre estado_pago y las fechas la garantiza el CHECK
--   ck_membresias_fechas_por_estado_pago que se añade en el script 03.
--
-- Compatibilidad:
--   Las filas existentes (todas PAGADO tras el backfill del script 01) siguen
--   teniendo sus fechas pobladas — quitar NOT NULL no las afecta. Metadata-only
--   en Postgres, no re-escribe la tabla.

ALTER TABLE core.membresias
  ALTER COLUMN fecha_inicio DROP NOT NULL,
  ALTER COLUMN fecha_fin    DROP NOT NULL;
