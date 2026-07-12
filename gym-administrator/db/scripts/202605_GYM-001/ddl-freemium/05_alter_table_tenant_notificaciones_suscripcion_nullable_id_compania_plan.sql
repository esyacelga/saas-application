-- REQ-SAAS-001 — Sub-fase 1.6 — Deuda técnica descubierta durante ítem #5
--
-- tenant.notificaciones_suscripcion.id_compania_plan se creó como NOT NULL en el
-- baseline (ddl/21_...sql) asumiendo que TODA notificación de suscripción refería
-- siempre a un CompaniaPlan concreto (los avisos previos VENCIMIENTO_TRIAL /
-- VENCIMIENTO_PREMIUM / TRIAL_ACTIVADO sí lo tienen).
--
-- Sin embargo, el email transaccional PAGO_RECHAZADO (RechazarPagoService) NO tiene
-- un CompaniaPlan asociado — el pago fue rechazado precisamente porque el owner
-- intentaba pagar/renovar; puede o no existir un CompaniaPlan activo. El servicio
-- inserta con id_compania_plan = null y el INSERT falla silenciosamente por el
-- onErrorResume(err -> Mono.empty()) defensivo (fire-and-forget), de modo que
-- la notificación NUNCA se encolaba en producción.
--
-- Este script relaja el NOT NULL para permitir emails transaccionales sin plan:
--   * PAGO_RECHAZADO           -> id_compania_plan NULL
--   * TRIAL_ACTIVADO           -> id_compania_plan NOT NULL (el trial recién creado)
--   * VENCIMIENTO_TRIAL        -> id_compania_plan NOT NULL (plan que vence)
--   * VENCIMIENTO_PREMIUM      -> id_compania_plan NOT NULL (plan que vence)
--
-- El filtrado multi-tenant sigue usando id_compania (nullable-safe, ya existía),
-- así que aflojar la FK no rompe queries. El índice
-- idx_notif_compania_tipo_dias(id_compania_plan, dias_antes) sigue siendo válido:
-- btree indexa NULLs por default en PostgreSQL y el job de notificaciones periódico
-- (RN-07) filtra por tipo IN ('VENCIMIENTO_TRIAL','VENCIMIENTO_PREMIUM'), que
-- exigen id_compania_plan NOT NULL, por lo que las filas con NULL nunca
-- aparecerán en ese predicado.
--
-- Idempotente: el ALTER solo se ejecuta si la columna aún es NOT NULL.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'tenant'
           AND table_name   = 'notificaciones_suscripcion'
           AND column_name  = 'id_compania_plan'
           AND is_nullable  = 'NO'
    ) THEN
        ALTER TABLE tenant.notificaciones_suscripcion
            ALTER COLUMN id_compania_plan DROP NOT NULL;
    END IF;
END $$;

COMMENT ON COLUMN tenant.notificaciones_suscripcion.id_compania_plan
    IS 'FK al plan asociado. NULL para emails transaccionales sin plan (PAGO_RECHAZADO). NOT NULL en la práctica para VENCIMIENTO_TRIAL / VENCIMIENTO_PREMIUM / TRIAL_ACTIVADO.';
