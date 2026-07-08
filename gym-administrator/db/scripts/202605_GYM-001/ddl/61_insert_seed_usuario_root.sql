CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_id_compania INT;
  v_id_sucursal INT;
  v_id_rol      INT;
BEGIN

  -- Compania semilla del sistema
  INSERT INTO tenant.companias (nombre, ruc, correo, activo, creacion_fecha, creacion_usuario)
  VALUES ('Sistema', '0000000000001', 'sistema@gym-admin.local', TRUE, NOW(), 'sistema')
  ON CONFLICT (ruc) DO NOTHING;

  SELECT id INTO v_id_compania
  FROM tenant.companias
  WHERE ruc = '0000000000001';

  -- Sucursal principal semilla
  INSERT INTO tenant.sucursales (id_compania, nombre, es_principal, activo, creacion_fecha, creacion_usuario)
  VALUES (v_id_compania, 'Principal', TRUE, TRUE, NOW(), 'sistema')
  ON CONFLICT DO NOTHING;

  SELECT id INTO v_id_sucursal
  FROM tenant.sucursales
  WHERE id_compania = v_id_compania AND es_principal = TRUE
  LIMIT 1;

  -- Rol SUPER_ADMIN
  INSERT INTO seguridad.roles (id_compania, id_sucursal, nombre, descripcion, creacion_fecha, creacion_usuario)
  VALUES (v_id_compania, v_id_sucursal, 'SUPER_ADMIN', 'Rol con acceso total al sistema', NOW(), 'sistema')
  ON CONFLICT (id_compania, nombre) DO NOTHING;

  SELECT id INTO v_id_rol
  FROM seguridad.roles
  WHERE id_compania = v_id_compania AND nombre = 'SUPER_ADMIN';

  -- Permisos de todos los módulos
  INSERT INTO seguridad.permisos (id_compania, id_sucursal, nombre, descripcion, modulo) VALUES
 
    -- identidad
    (v_id_compania, v_id_sucursal, 'identidad:gestionar_personas',    'Registrar y editar personas',                       'identidad'),
    -- seguridad
    (v_id_compania, v_id_sucursal, 'roles:leer',                      'Permiso solo de lectura de roles',                  'seguridad'),
    (v_id_compania, v_id_sucursal, 'roles:crear',                     'Permite crear un nuevo rol',                        'seguridad'),
    (v_id_compania, v_id_sucursal, 'roles:editar',                    'Permite editar roles',                              'seguridad'),
    (v_id_compania, v_id_sucursal, 'usuarios:leer',                   'Permiso solo de lectura de usuarios',               'seguridad'),
    (v_id_compania, v_id_sucursal, 'usuarios:crear',                  'Permite crear usuarios',                            'seguridad'),
    (v_id_compania, v_id_sucursal, 'usuarios:editar',                 'Permite editar y eliminar usuarios',                'seguridad'),
    -- config
    (v_id_compania, v_id_sucursal, 'config:gestionar_gym',            'Editar configuración general del gimnasio',         'config'),
    (v_id_compania, v_id_sucursal, 'config:gestionar_metodos_pago',   'Activar y configurar métodos de pago',              'config'),
    -- core
    (v_id_compania, v_id_sucursal, 'clientes:leer',                   'Consultar listado y detalle de clientes',           'core'),
    (v_id_compania, v_id_sucursal, 'membresias:leer',                 'Consultar listado y detalle de membresías',         'core'),
    -- asistencia
    (v_id_compania, v_id_sucursal, 'asistencia:registrar',            'Registrar entradas y salidas de clientes',          'asistencia'),
    -- finanzas
    (v_id_compania, v_id_sucursal, 'finanzas:gestionar_categorias',   'Administrar categorías de ingreso',                 'finanzas'),
    -- marketing
    (v_id_compania, v_id_sucursal, 'marketing:gestionar_promociones', 'Crear y editar promociones',                        'marketing'),
    -- inventario
    (v_id_compania, v_id_sucursal, 'inventario:gestionar_categorias', 'Administrar categorías de productos',               'inventario')
  ON CONFLICT (id_compania, nombre) DO NOTHING;

  -- Asignar todos los permisos al rol SUPER_ADMIN
  INSERT INTO seguridad.rol_permisos (id_rol, id_permiso)
  SELECT v_id_rol, id
  FROM seguridad.permisos
  WHERE id_compania = v_id_compania
  ON CONFLICT DO NOTHING;

  -- Persona base para el usuario root del gym
  INSERT INTO identidad.personas (ci, nombre, correo, creacion_fecha, creacion_usuario)
  VALUES ('0000000000000', 'root', 'root@gym-admin.local', NOW(), 'sistema')
  ON CONFLICT DO NOTHING;

  -- Usuario root con BCrypt de 'seya1922'
  INSERT INTO seguridad.usuarios
    (id_compania, id_sucursal, id_rol, id_persona, correo, password_hash, creacion_fecha, creacion_usuario)
  SELECT
    v_id_compania,
    v_id_sucursal,
    v_id_rol,
    p.id,
    'root@gym-admin.local',
    crypt('seya1922', gen_salt('bf', 12)),
    NOW(),
    'sistema'
  FROM identidad.personas p WHERE p.ci = '0000000000000'
  ON CONFLICT (id_compania, correo) DO NOTHING;

END $$;

-- Persona base para el operador root de plataforma SaaS
INSERT INTO identidad.personas (ci, nombre, correo, creacion_fecha, creacion_usuario)
VALUES ('0000000000000', 'root', 'root@gym-admin.local', NOW(), 'sistema')
ON CONFLICT DO NOTHING;

-- Usuario root a nivel de plataforma SaaS
INSERT INTO saas.usuarios_plataforma (id_persona, correo, password_hash, rol, creacion_fecha, creacion_usuario)
SELECT
  p.id,
  'root@gym-admin.local',
  crypt('seya1922', gen_salt('bf', 12)),
  'super_admin',
  NOW(),
  'sistema'
FROM identidad.personas p WHERE p.ci = '0000000000000'
ON CONFLICT (correo) DO NOTHING;
