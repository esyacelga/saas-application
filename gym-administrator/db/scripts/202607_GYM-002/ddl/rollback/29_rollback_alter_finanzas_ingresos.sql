DROP INDEX IF EXISTS idx_ingresos_comprobante;

ALTER TABLE finanzas.ingresos
    DROP COLUMN IF EXISTS id_comprobante;
