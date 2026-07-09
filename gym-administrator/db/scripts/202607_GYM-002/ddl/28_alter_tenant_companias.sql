-- Agrega columnas fiscales requeridas para facturación electrónica SRI
-- a la tabla tenant.companias. Todas las columnas son nullable para no
-- forzar migración de datos en entornos con registros existentes.
ALTER TABLE tenant.companias
    ADD COLUMN IF NOT EXISTS nombre_comercial        VARCHAR(300),
    ADD COLUMN IF NOT EXISTS dir_matriz              VARCHAR(300),
    ADD COLUMN IF NOT EXISTS obligado_contabilidad   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS contribuyente_especial  VARCHAR(10);

COMMENT ON COLUMN tenant.companias.nombre_comercial       IS 'Nombre comercial del establecimiento para el comprobante electrónico';
COMMENT ON COLUMN tenant.companias.dir_matriz             IS 'Dirección de la matriz registrada en el SRI';
COMMENT ON COLUMN tenant.companias.obligado_contabilidad  IS 'TRUE si la empresa está obligada a llevar contabilidad según el SRI';
COMMENT ON COLUMN tenant.companias.contribuyente_especial IS 'Número de resolución si es contribuyente especial, NULL si no aplica';
