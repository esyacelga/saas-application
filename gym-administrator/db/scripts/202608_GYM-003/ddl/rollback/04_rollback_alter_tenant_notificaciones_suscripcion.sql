DROP INDEX IF EXISTS tenant.idx_notif_compania_tipo_dias;

ALTER TABLE tenant.notificaciones_suscripcion
    DROP CONSTRAINT IF EXISTS ck_notif_suscripcion_estado,
    DROP CONSTRAINT IF EXISTS ck_notif_suscripcion_canal;

-- Restaura los CHECK originales del script 21_*.sql
ALTER TABLE tenant.notificaciones_suscripcion
    ADD CONSTRAINT notificaciones_suscripcion_canal_check
        CHECK (canal IN ('email','whatsapp')),
    ADD CONSTRAINT notificaciones_suscripcion_estado_check
        CHECK (estado IN ('enviado','fallido'));

ALTER TABLE tenant.notificaciones_suscripcion
    DROP COLUMN IF EXISTS ultimo_error,
    DROP COLUMN IF EXISTS intentos,
    DROP COLUMN IF EXISTS descartado_at;
