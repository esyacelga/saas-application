-- REQ-SAAS-001 — Sub-fase 1.6 — Ítem #4 (comprobante opcional) / Deuda técnica ítem #5
--
-- El endpoint POST /api/v1/companias/{idCompania}/pagos/reportar acepta la parte
-- multipart `comprobante` como OPCIONAL desde el ítem #4. Cuando el owner no adjunta
-- comprobante, la fila persistida en tenant.pagos_pendientes_validacion queda con
-- comprobante_url = NULL.
--
-- Este script relaja la restricción NOT NULL original de comprobante_url para que
-- el DDL de producción refleje la realidad del endpoint. NO se modifica la semántica
-- de los demás campos (comprobante_hash ya era nullable).
--
-- Idempotente: el ALTER solo se ejecuta si la columna aún es NOT NULL (evita fallar
-- en entornos donde el workaround temporal del test ya la haya soltado).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'tenant'
           AND table_name   = 'pagos_pendientes_validacion'
           AND column_name  = 'comprobante_url'
           AND is_nullable  = 'NO'
    ) THEN
        ALTER TABLE tenant.pagos_pendientes_validacion
            ALTER COLUMN comprobante_url DROP NOT NULL;
    END IF;
END $$;

COMMENT ON COLUMN tenant.pagos_pendientes_validacion.comprobante_url
    IS 'URL Cloudinary (resource_type=raw, access_mode=authenticated). Nunca pública. NULL cuando el owner reporta un pago sin adjuntar comprobante (REQ-SAAS-001 Sub-fase 1.6, ítem #4).';
