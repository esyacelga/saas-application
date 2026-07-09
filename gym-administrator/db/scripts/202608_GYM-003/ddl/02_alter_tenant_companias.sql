-- REQ-SAAS-001 — Sub-fase 1.1 — RN-01 (Trial único por tenant)
-- Agrega el flag irrevocable de "Trial ya usado" y la fecha del evento.
-- Ambas columnas son nullable o con default seguro para no romper filas existentes.
ALTER TABLE tenant.companias
    ADD COLUMN IF NOT EXISTS trial_usado         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fecha_trial_usado   TIMESTAMPTZ;

COMMENT ON COLUMN tenant.companias.trial_usado       IS 'Flag irrevocable: TRUE si la compañía ya activó su Trial alguna vez (RN-01)';
COMMENT ON COLUMN tenant.companias.fecha_trial_usado IS 'Timestamp del momento en que se activó el Trial por primera y única vez';
