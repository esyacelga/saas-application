-- REQ-SAAS-001 — Sub-fase 1.1 — Sección 11.3
--
-- Migración one-shot de tenants existentes:
--   1) Todo compania_planes en estado 'activo' o 'en_gracia' que apunte a un plan viejo
--      (id_plan de un plan cuyo codigo IS NULL, es decir NO es FREE/TRIAL/PREMIUM/LEGACY)
--      se re-apunta al plan LEGACY_GRANDFATHERED.
--      Se preservan fecha_fin, dias_gracia y credito_monto.
--      Al vencer su ciclo actual, el job de degradación los llevará a PREMIUM
--      (por la FK saas.planes.plan_degradacion_id del LEGACY).
--
--   2) Se marca trial_usado=TRUE + fecha_trial_usado=NOW() para TODAS las compañías
--      existentes al momento de la migración — no aplican al Trial de 2 meses.
--      Los tenants nuevos que se creen DESPUÉS de este changeset arrancarán con
--      trial_usado=FALSE por default (columna agregada en el script 02_alter_tenant_companias.sql).
--
-- Idempotente: el WHERE excluye las filas ya migradas (id_plan ya = LEGACY, o
-- trial_usado ya = TRUE) para poder correr múltiples veces sin efecto acumulado.

DO $$
DECLARE
    v_plan_legacy INT;
    v_migrated    INT;
    v_flagged     INT;
BEGIN
    SELECT id INTO v_plan_legacy
      FROM saas.planes
     WHERE codigo = 'LEGACY_GRANDFATHERED';

    IF v_plan_legacy IS NULL THEN
        RAISE EXCEPTION 'REQ-SAAS-001: no se encontró el plan LEGACY_GRANDFATHERED. Ejecuta primero el seed dml/01_seed_saas_planes_freemium.sql';
    END IF;

    ---------------------------------------------------------------------------
    -- 1. Re-apuntar suscripciones vigentes a LEGACY_GRANDFATHERED
    ---------------------------------------------------------------------------
    UPDATE tenant.compania_planes cp
       SET id_plan           = v_plan_legacy,
           modifica_fecha    = NOW(),
           modifica_usuario  = 'REQ-SAAS-001'
     WHERE cp.estado IN ('activo','en_gracia')
       AND cp.id_plan IN (
             SELECT p.id
               FROM saas.planes p
              WHERE p.codigo IS NULL   -- plan viejo del esquema anterior
                   OR p.codigo NOT IN ('FREE','TRIAL','PREMIUM','LEGACY_GRANDFATHERED')
           );

    GET DIAGNOSTICS v_migrated = ROW_COUNT;
    RAISE NOTICE 'REQ-SAAS-001: % suscripciones vigentes migradas a LEGACY_GRANDFATHERED', v_migrated;

    ---------------------------------------------------------------------------
    -- 2. Marcar trial_usado en TODAS las compañías existentes
    ---------------------------------------------------------------------------
    UPDATE tenant.companias
       SET trial_usado        = TRUE,
           fecha_trial_usado  = NOW(),
           modifica_fecha     = NOW(),
           modifica_usuario   = 'REQ-SAAS-001'
     WHERE trial_usado = FALSE;

    GET DIAGNOSTICS v_flagged = ROW_COUNT;
    RAISE NOTICE 'REQ-SAAS-001: % compañías marcadas con trial_usado=TRUE', v_flagged;
END $$;
