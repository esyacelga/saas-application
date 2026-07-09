-- REQ-SAAS-001 — Sub-fase 1.1 — Sección 4 y 11
--
-- Seed idempotente de los 4 planes definitivos y clasificación de los planes viejos:
--   FREE                  -> $0, permanente, límites 1/50/2
--   TRIAL                 -> $0, 60 días, features Premium, sin límites
--   PREMIUM               -> $29.99, 30 días, todas las features
--   LEGACY_GRANDFATHERED  -> preserva contratos migrados; degrada a Premium al vencer
--
-- Se marcan los planes viejos (Básico / Premium / Enterprise) como activo=FALSE, es_legacy=TRUE
-- pero NO se eliminan (histórico). El match se hace por nombre exacto porque son los que
-- inserta el seed original en 62_insert_seed_saas_planes_caracteristicas.sql.
--
-- La FK plan_degradacion_id se resuelve en un segundo paso, una vez que las 4 filas
-- nuevas existen (evita ciclos de resolución durante el INSERT).

DO $$
DECLARE
    v_plan_free    INT;
    v_plan_trial   INT;
    v_plan_premium INT;
    v_plan_legacy  INT;
BEGIN
    ---------------------------------------------------------------------------
    -- 1. Insertar los 4 planes nuevos (idempotente por UNIQUE codigo)
    ---------------------------------------------------------------------------
    INSERT INTO saas.planes (
        nombre, descripcion, precio_mensual, activo, creacion_usuario,
        codigo, duracion_dias, es_gratuito,
        max_sucursales, max_clientes_activos, max_staff,
        moneda, es_legacy
    )
    VALUES
        ('Free',
         'Plan gratuito permanente con funcionalidades básicas',
         0.00, TRUE, 'sistema',
         'FREE', NULL, TRUE,
         1, 50, 2,
         'USD', FALSE),

        ('Trial',
         'Período de prueba de 60 días con todas las features Premium',
         0.00, TRUE, 'sistema',
         'TRIAL', 60, TRUE,
         NULL, NULL, NULL,
         'USD', FALSE),

        ('Premium',
         'Plan pago mensual con todas las funcionalidades',
         29.99, TRUE, 'sistema',
         'PREMIUM', 30, FALSE,
         NULL, NULL, NULL,
         'USD', FALSE),

        ('Legacy Grandfathered',
         'Plan de transición para tenants migrados del esquema anterior. Al vencer degrada a Premium.',
         0.00, TRUE, 'sistema',
         'LEGACY_GRANDFATHERED', NULL, TRUE,
         NULL, NULL, NULL,
         'USD', TRUE)
    ON CONFLICT (codigo) DO NOTHING;

    SELECT id INTO v_plan_free    FROM saas.planes WHERE codigo = 'FREE';
    SELECT id INTO v_plan_trial   FROM saas.planes WHERE codigo = 'TRIAL';
    SELECT id INTO v_plan_premium FROM saas.planes WHERE codigo = 'PREMIUM';
    SELECT id INTO v_plan_legacy  FROM saas.planes WHERE codigo = 'LEGACY_GRANDFATHERED';

    ---------------------------------------------------------------------------
    -- 2. Configurar plan_degradacion_id (idempotente)
    --    Trial   -> Free
    --    Premium -> Free
    --    Legacy  -> Premium
    --    Free    -> NULL (permanente)
    ---------------------------------------------------------------------------
    UPDATE saas.planes SET plan_degradacion_id = v_plan_free
     WHERE codigo = 'TRIAL'   AND (plan_degradacion_id IS DISTINCT FROM v_plan_free);

    UPDATE saas.planes SET plan_degradacion_id = v_plan_free
     WHERE codigo = 'PREMIUM' AND (plan_degradacion_id IS DISTINCT FROM v_plan_free);

    UPDATE saas.planes SET plan_degradacion_id = v_plan_premium
     WHERE codigo = 'LEGACY_GRANDFATHERED' AND (plan_degradacion_id IS DISTINCT FROM v_plan_premium);

    ---------------------------------------------------------------------------
    -- 3. Marcar planes históricos (Básico / Premium legacy / Enterprise) como
    --    inactivos y legacy — solo si aún no tienen código asignado (idempotente).
    --    El seed original (62_*.sql) creó estos tres nombres.
    ---------------------------------------------------------------------------
    UPDATE saas.planes
       SET activo           = FALSE,
           es_legacy        = TRUE,
           modifica_fecha   = NOW(),
           modifica_usuario = 'REQ-SAAS-001'
     WHERE codigo IS NULL
       AND nombre IN ('Básico','Premium','Enterprise');

    ---------------------------------------------------------------------------
    -- 4. Vincular características al plan FREE (subconjunto del Básico) y
    --    al plan TRIAL / PREMIUM (todas las características activas).
    --    Reutiliza la matriz cargada por 62_insert_seed_saas_planes_caracteristicas.sql.
    ---------------------------------------------------------------------------
    INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
    SELECT v_plan_free, c.id, 'sistema'
      FROM saas.caracteristicas c
     WHERE c.codigo IN ('CLIENTES','MEMBRESIAS','ASISTENCIA','MENSAJERIA','SEGURIDAD','CONFIGURACION')
    ON CONFLICT DO NOTHING;

    INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
    SELECT v_plan_trial, c.id, 'sistema'
      FROM saas.caracteristicas c
     WHERE c.activo = TRUE
    ON CONFLICT DO NOTHING;

    INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
    SELECT v_plan_premium, c.id, 'sistema'
      FROM saas.caracteristicas c
     WHERE c.activo = TRUE
    ON CONFLICT DO NOTHING;

    -- LEGACY_GRANDFATHERED preserva el set del Enterprise histórico (todas las features)
    INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
    SELECT v_plan_legacy, c.id, 'sistema'
      FROM saas.caracteristicas c
     WHERE c.activo = TRUE
    ON CONFLICT DO NOTHING;
END $$;
