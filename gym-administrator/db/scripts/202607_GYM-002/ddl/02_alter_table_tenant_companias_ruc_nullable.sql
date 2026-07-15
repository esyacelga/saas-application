-- Adelgazar el registro de gimnasios — RUC opcional (disclosure progresivo).
--
-- El auto-registro público de un gimnasio ya no pide el RUC: un dueño que solo
-- quiere probar la app no está obligado a dar datos tributarios. El RUC se solicita
-- más tarde, en el wizard de configuración de facturación, cuando el gym decide
-- facturar. Por eso la columna tenant.companias.ruc deja de ser NOT NULL.
--
-- El UNIQUE NO se toca: en Postgres un índice único sobre una columna nullable
-- permite MÚLTIPLES NULL (varios gyms sin RUC no chocan) y sigue impidiendo dos
-- compañías con el mismo RUC real. Es exactamente el comportamiento deseado.
--
-- El registro por operador de plataforma (endpoint /wizard) sigue enviando el RUC;
-- esta migración solo relaja la obligatoriedad a nivel de esquema.
--
-- Idempotente: el ALTER solo se ejecuta si la columna aún es NOT NULL.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'tenant'
           AND table_name   = 'companias'
           AND column_name  = 'ruc'
           AND is_nullable  = 'NO'
    ) THEN
        ALTER TABLE tenant.companias
            ALTER COLUMN ruc DROP NOT NULL;
    END IF;
END $$;

COMMENT ON COLUMN tenant.companias.ruc
    IS 'RUC del contribuyente. NULL cuando el gimnasio se registró sin facturar (se completa luego en el wizard de facturación). UNIQUE permite varios NULL e impide RUC reales duplicados.';
