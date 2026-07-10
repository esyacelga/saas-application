DROP INDEX IF EXISTS tenant.idx_notif_banner_compania;
DROP INDEX IF EXISTS tenant.idx_notif_pendientes_retry;

ALTER TABLE tenant.notificaciones_suscripcion
    DROP CONSTRAINT IF EXISTS fk_notif_suscripcion_compania;

ALTER TABLE tenant.notificaciones_suscripcion
    DROP COLUMN IF EXISTS proximo_intento,
    DROP COLUMN IF EXISTS tipo,
    DROP COLUMN IF EXISTS id_compania;
