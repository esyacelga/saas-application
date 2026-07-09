ALTER TABLE tenant.companias
    DROP COLUMN IF EXISTS trial_usado,
    DROP COLUMN IF EXISTS fecha_trial_usado;
