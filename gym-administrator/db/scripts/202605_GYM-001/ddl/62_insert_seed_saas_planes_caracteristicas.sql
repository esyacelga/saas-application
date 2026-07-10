-- Seed del esquema definitivo Free / Trial / Premium (REQ-SAAS-001).
--   FREE    -> $0, permanente, límites 1/50/2
--   TRIAL   -> $0, 60 días, features Premium, sin límites; degrada a FREE al vencer
--   PREMIUM -> $29.99, 30 días, todas las features; degrada a FREE al vencer
DO $$
DECLARE
  v_plan_free    INT;
  v_plan_trial   INT;
  v_plan_premium INT;
BEGIN

  -- ── Planes ──────────────────────────────────────────────────────────────────

  INSERT INTO saas.planes (
    nombre, descripcion, precio_mensual, activo, creacion_usuario,
    codigo, duracion_dias, es_gratuito,
    max_sucursales, max_clientes_activos, max_staff,
    moneda, es_legacy
  ) VALUES
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
     'USD', FALSE)
  ON CONFLICT (codigo) DO NOTHING;

  SELECT id INTO v_plan_free    FROM saas.planes WHERE codigo = 'FREE';
  SELECT id INTO v_plan_trial   FROM saas.planes WHERE codigo = 'TRIAL';
  SELECT id INTO v_plan_premium FROM saas.planes WHERE codigo = 'PREMIUM';

  -- Degradación al vencer: Trial -> Free, Premium -> Free, Free -> NULL (permanente)
  UPDATE saas.planes SET plan_degradacion_id = v_plan_free
   WHERE codigo IN ('TRIAL','PREMIUM') AND plan_degradacion_id IS DISTINCT FROM v_plan_free;

  -- ── Características ──────────────────────────────────────────────────────────

  INSERT INTO saas.caracteristicas (codigo, nombre, modulo, activo, creacion_usuario) VALUES
    -- core
    ('CLIENTES',        'Gestión de Clientes',               'core',       TRUE, 'sistema'),
    ('MEMBRESIAS',      'Membresías y Congelamientos',        'core',       TRUE, 'sistema'),
    -- asistencia
    ('ASISTENCIA',      'Control de Asistencia',              'asistencia', TRUE, 'sistema'),
    ('MENSAJERIA',      'Mensajes Automáticos (WhatsApp)',     'asistencia', TRUE, 'sistema'),
    -- seguridad
    ('SEGURIDAD',       'Roles y Usuarios de Staff',          'seguridad',  TRUE, 'sistema'),
    -- config
    ('CONFIGURACION',   'Configuración del Gimnasio',         'config',     TRUE, 'sistema'),
    -- finanzas (Premium)
    ('FINANZAS',        'Finanzas (Ingresos y Egresos)',       'finanzas',   TRUE, 'sistema'),
    -- marketing (Premium)
    ('MARKETING',       'Promociones y Beneficios',           'marketing',  TRUE, 'sistema'),
    -- inventario (Premium)
    ('INVENTARIO',      'Inventario y Punto de Venta',        'inventario', TRUE, 'sistema')
  ON CONFLICT (codigo) DO NOTHING;

  -- ── Asignación de características por plan ───────────────────────────────────

  -- Free: subconjunto básico (core + asistencia + seguridad + config)
  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_free, id, 'sistema'
  FROM saas.caracteristicas
  WHERE codigo IN ('CLIENTES', 'MEMBRESIAS', 'ASISTENCIA', 'MENSAJERIA', 'SEGURIDAD', 'CONFIGURACION')
  ON CONFLICT DO NOTHING;

  -- Trial y Premium: todas las características activas
  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_trial, id, 'sistema'
  FROM saas.caracteristicas
  WHERE activo = TRUE
  ON CONFLICT DO NOTHING;

  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_premium, id, 'sistema'
  FROM saas.caracteristicas
  WHERE activo = TRUE
  ON CONFLICT DO NOTHING;

END $$;
