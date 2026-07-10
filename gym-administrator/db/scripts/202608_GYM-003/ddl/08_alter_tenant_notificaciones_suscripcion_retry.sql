-- REQ-SAAS-001 — Sub-fase 1.5 — Cola de emails con retry backoff exponencial.
--
-- Se agregan las columnas necesarias para:
--   * id_compania          → filtrado multi-tenant sin JOIN a compania_planes.
--   * tipo                 → clasificar la notificación: VENCIMIENTO_TRIAL,
--                            VENCIMIENTO_PREMIUM, PAGO_RECHAZADO, etc.
--   * proximo_intento      → programar reintentos con backoff exponencial
--                            (30s, 2m, 10m, 1h). Cuando estado IN ('pendiente','reintentar')
--                            el worker solo agarra filas donde
--                            proximo_intento IS NULL OR proximo_intento <= NOW().
--
-- Nota sobre nombres de columnas:
--   La columna de estado en el DDL original (script 21_*.sql) se llama 'estado',
--   no 'estado_envio'. El requerimiento 1.5 la referencia como 'estado_envio' —
--   mantenemos el nombre físico 'estado' para no romper compatibilidad; el CHECK
--   ya definido en el script 04_alter_tenant_notificaciones_suscripcion permite
--   ('pendiente','enviado','fallido','reintentar'). El estado transitorio
--   'procesando' NO se agrega — el worker hace claim + release en la misma
--   transacción y nunca lo persiste.

ALTER TABLE tenant.notificaciones_suscripcion
    ADD COLUMN IF NOT EXISTS id_compania      INT,
    ADD COLUMN IF NOT EXISTS tipo             VARCHAR(40),
    ADD COLUMN IF NOT EXISTS proximo_intento  TIMESTAMPTZ;

-- FK a tenant.companias para consistencia referencial.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'tenant.notificaciones_suscripcion'::regclass
          AND conname = 'fk_notif_suscripcion_compania'
    ) THEN
        ALTER TABLE tenant.notificaciones_suscripcion
            ADD CONSTRAINT fk_notif_suscripcion_compania
            FOREIGN KEY (id_compania) REFERENCES tenant.companias(id);
    END IF;
END $$;

COMMENT ON COLUMN tenant.notificaciones_suscripcion.id_compania      IS 'FK al tenant afectado — filtrado multi-tenant sin JOIN.';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.tipo             IS 'VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM / PAGO_RECHAZADO / TRIAL_ACTIVADO.';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.proximo_intento  IS 'Fecha/hora mínima en la que un worker puede volver a intentar el envío. NULL = ejecutable inmediatamente.';

-- Índice parcial para el claim con FOR UPDATE SKIP LOCKED — solo indexa filas
-- que el worker podría reclamar (evita hinchar el índice con enviadas/fallidas).
CREATE INDEX IF NOT EXISTS idx_notif_pendientes_retry
    ON tenant.notificaciones_suscripcion (estado, proximo_intento)
    WHERE estado IN ('pendiente', 'reintentar');

-- Índice para el filtro por tenant en el endpoint GET /banners-activos.
CREATE INDEX IF NOT EXISTS idx_notif_banner_compania
    ON tenant.notificaciones_suscripcion (id_compania, canal, descartado_at)
    WHERE canal = 'banner';
