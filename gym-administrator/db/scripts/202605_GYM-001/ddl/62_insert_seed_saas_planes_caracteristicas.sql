DO $$
DECLARE
  v_plan_basico   INT;
  v_plan_premium  INT;
  v_plan_enterprise INT;
BEGIN

  -- ── Planes ──────────────────────────────────────────────────────────────────

  INSERT INTO saas.planes (nombre, descripcion, precio_mensual, activo, creacion_usuario) VALUES
    ('Básico',      'Funcionalidades esenciales para gestionar un gimnasio',              29.99,  TRUE, 'sistema'),
    ('Premium',     'Funcionalidades avanzadas: finanzas, marketing e inventario',        59.99,  TRUE, 'sistema'),
    ('Enterprise',  'Plan completo con soporte prioritario y sin límite de sucursales',  99.99,  TRUE, 'sistema')
  ON CONFLICT DO NOTHING;

  SELECT id INTO v_plan_basico    FROM saas.planes WHERE nombre = 'Básico';
  SELECT id INTO v_plan_premium   FROM saas.planes WHERE nombre = 'Premium';
  SELECT id INTO v_plan_enterprise FROM saas.planes WHERE nombre = 'Enterprise';

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

  -- Básico: core + asistencia + seguridad + config
  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_basico, id, 'sistema'
  FROM saas.caracteristicas
  WHERE codigo IN ('CLIENTES', 'MEMBRESIAS', 'ASISTENCIA', 'MENSAJERIA', 'SEGURIDAD', 'CONFIGURACION')
  ON CONFLICT DO NOTHING;

  -- Premium: todo lo de Básico + finanzas + marketing + inventario
  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_premium, id, 'sistema'
  FROM saas.caracteristicas
  WHERE activo = TRUE
  ON CONFLICT DO NOTHING;

  -- Enterprise: igual que Premium (diferencia está en soporte y límites, no en módulos)
  INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica, creacion_usuario)
  SELECT v_plan_enterprise, id, 'sistema'
  FROM saas.caracteristicas
  WHERE activo = TRUE
  ON CONFLICT DO NOTHING;

END $$;