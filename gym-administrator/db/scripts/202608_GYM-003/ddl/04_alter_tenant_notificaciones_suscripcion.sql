-- REQ-SAAS-001 — Sub-fase 1.1 — Decisión D1 (Extender, no crear tabla nueva)
--
-- La tabla tenant.notificaciones_suscripcion ya persiste envíos de notificaciones
-- de vencimiento. Se EXTIENDE para soportar:
--   - canal 'banner' (in-app) además de 'email' y 'whatsapp'
--   - estado 'pendiente' y 'reintentar' (el CHECK original solo aceptaba 'enviado'/'fallido'
--     pero la columna definía default='pendiente' — bug preexistente que se corrige aquí)
--   - tracking de intentos, último error y descarte del banner por el owner
--
-- NOTA sobre columnas ya existentes:
--   * dias_antes INT NOT NULL ya existe en el DDL original (script 21_*.sql).
--     No se agrega ni modifica.
--
-- Todas las nuevas columnas son NULL o tienen default para no bloquear filas existentes.

-- ── Nuevas columnas ─────────────────────────────────────────────────────────
ALTER TABLE tenant.notificaciones_suscripcion
    ADD COLUMN IF NOT EXISTS descartado_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS intentos        INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS ultimo_error    TEXT;

COMMENT ON COLUMN tenant.notificaciones_suscripcion.descartado_at IS 'Timestamp en que el owner descartó el banner in-app (solo aplica cuando canal=''banner'')';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.intentos      IS 'Cantidad de intentos de envío (para retry exponencial con canal=''email'')';
COMMENT ON COLUMN tenant.notificaciones_suscripcion.ultimo_error  IS 'Mensaje del último error SMTP/render (troubleshooting operativo)';

-- ── Ampliación de CHECK: canal ──────────────────────────────────────────────
DO $$
DECLARE
    v_cons_name TEXT;
BEGIN
    SELECT conname INTO v_cons_name
    FROM pg_constraint
    WHERE conrelid = 'tenant.notificaciones_suscripcion'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%canal%'
    LIMIT 1;

    IF v_cons_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tenant.notificaciones_suscripcion DROP CONSTRAINT %I', v_cons_name);
    END IF;
END $$;

ALTER TABLE tenant.notificaciones_suscripcion
    ADD CONSTRAINT ck_notif_suscripcion_canal
        CHECK (canal IN ('email','whatsapp','banner'));

-- ── Ampliación de CHECK: estado ─────────────────────────────────────────────
-- El CHECK original solo aceptaba ('enviado','fallido') pero el default de la columna
-- era 'pendiente' — se completa el conjunto y se agrega 'reintentar'.
DO $$
DECLARE
    v_cons_name TEXT;
BEGIN
    SELECT conname INTO v_cons_name
    FROM pg_constraint
    WHERE conrelid = 'tenant.notificaciones_suscripcion'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%estado%'
    LIMIT 1;

    IF v_cons_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tenant.notificaciones_suscripcion DROP CONSTRAINT %I', v_cons_name);
    END IF;
END $$;

ALTER TABLE tenant.notificaciones_suscripcion
    ADD CONSTRAINT ck_notif_suscripcion_estado
        CHECK (estado IN ('pendiente','enviado','fallido','reintentar'));

-- ── Índice para el predicado idempotente del job de notificaciones (RN-07) ─
-- Busca "¿ya envié notif con dias_antes >= X para este compania_plan y tipo?"
CREATE INDEX IF NOT EXISTS idx_notif_compania_tipo_dias
    ON tenant.notificaciones_suscripcion(id_compania_plan, dias_antes);
