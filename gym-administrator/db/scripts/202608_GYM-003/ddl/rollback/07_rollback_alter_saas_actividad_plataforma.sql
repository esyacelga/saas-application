-- Rollback: revierte los tipos de detalle e ip y elimina las nuevas columnas.
-- El cast INET -> TEXT y JSONB -> TEXT es seguro.
ALTER TABLE saas.actividad_plataforma
    ALTER COLUMN ip TYPE VARCHAR(45) USING host(ip);

ALTER TABLE saas.actividad_plataforma
    ALTER COLUMN detalle TYPE TEXT USING detalle::text;

DROP INDEX IF EXISTS saas.idx_actividad_plataforma_compania;

ALTER TABLE saas.actividad_plataforma
    DROP CONSTRAINT IF EXISTS ck_actividad_plataforma_tipo_actor;

ALTER TABLE saas.actividad_plataforma
    DROP COLUMN IF EXISTS tipo_actor,
    DROP COLUMN IF EXISTS id_usuario_actor,
    DROP COLUMN IF EXISTS id_compania;
