ALTER TABLE tenant.companias
    DROP COLUMN IF EXISTS nombre_comercial,
    DROP COLUMN IF EXISTS dir_matriz,
    DROP COLUMN IF EXISTS obligado_contabilidad,
    DROP COLUMN IF EXISTS contribuyente_especial;
