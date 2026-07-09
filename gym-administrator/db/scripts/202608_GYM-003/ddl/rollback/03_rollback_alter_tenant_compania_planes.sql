DROP INDEX IF EXISTS tenant.idx_compania_planes_sobre_limite;
DROP INDEX IF EXISTS tenant.idx_compania_planes_estado_fecha_fin;
DROP INDEX IF EXISTS tenant.ux_compania_plan_vigente;

ALTER TABLE tenant.compania_planes
    DROP CONSTRAINT IF EXISTS ck_compania_planes_tipo_cambio,
    DROP CONSTRAINT IF EXISTS ck_compania_planes_estado,
    DROP CONSTRAINT IF EXISTS ck_compania_planes_causa_degradacion;

-- Restaura los CHECK originales (mismo cuerpo del script 18_create_table_tenant_compania_planes.sql).
ALTER TABLE tenant.compania_planes
    ADD CONSTRAINT compania_planes_estado_check
        CHECK (estado IN (
            'activo','en_gracia','vencido',
            'suspendido','cancelado','programado'
        )),
    ADD CONSTRAINT compania_planes_tipo_cambio_check
        CHECK (tipo_cambio IN (
            'nuevo','renovacion','upgrade','downgrade'
        ));

ALTER TABLE tenant.compania_planes
    DROP COLUMN IF EXISTS causa_degradacion,
    DROP COLUMN IF EXISTS sobre_limite_hasta,
    DROP COLUMN IF EXISTS sobre_limite;
