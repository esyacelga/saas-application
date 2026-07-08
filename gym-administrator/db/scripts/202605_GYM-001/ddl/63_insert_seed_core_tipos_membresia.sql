DO $$
DECLARE
  v_id_compania INT;
  v_id_sucursal INT;
BEGIN

  SELECT id INTO v_id_compania
  FROM tenant.companias
  WHERE ruc = '0000000000001'
  LIMIT 1;

  SELECT id INTO v_id_sucursal
  FROM tenant.sucursales
  WHERE id_compania = v_id_compania
    AND es_principal = TRUE
  LIMIT 1;

  IF v_id_compania IS NULL OR v_id_sucursal IS NULL THEN
    RAISE NOTICE 'No se insertaron tipos de membresia semilla porque no existe la compania/sucursal base.';
    RETURN;
  END IF;

  INSERT INTO core.tipos_membresia (
    id_compania,
    id_sucursal,
    nombre,
    modo_control,
    duracion_tipo,
    duracion_valor,
    dias_acceso,
    precio,
    activo,
    eliminado,
    creacion_usuario
  )
  VALUES
    (v_id_compania, v_id_sucursal, 'Mensual', 'calendario', 'meses', 1, NULL, 35.00, TRUE, FALSE, 'sistema'),
    (v_id_compania, v_id_sucursal, 'Trimestral', 'calendario', 'meses', 3, NULL, 90.00, TRUE, FALSE, 'sistema'),
    (v_id_compania, v_id_sucursal, 'Anual', 'calendario', 'meses', 12, NULL, 300.00, TRUE, FALSE, 'sistema')
  ON CONFLICT (id_compania, nombre) DO NOTHING;

END $$;