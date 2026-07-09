ALTER TABLE saas.planes
    DROP CONSTRAINT IF EXISTS fk_saas_planes_degradacion,
    DROP CONSTRAINT IF EXISTS ux_saas_planes_codigo;

ALTER TABLE saas.planes
    DROP COLUMN IF EXISTS codigo,
    DROP COLUMN IF EXISTS duracion_dias,
    DROP COLUMN IF EXISTS es_gratuito,
    DROP COLUMN IF EXISTS plan_degradacion_id,
    DROP COLUMN IF EXISTS max_sucursales,
    DROP COLUMN IF EXISTS max_clientes_activos,
    DROP COLUMN IF EXISTS max_staff,
    DROP COLUMN IF EXISTS moneda,
    DROP COLUMN IF EXISTS es_legacy;
