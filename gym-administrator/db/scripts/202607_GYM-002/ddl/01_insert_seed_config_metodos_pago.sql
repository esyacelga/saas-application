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
    RAISE NOTICE 'No se insertaron metodos de pago semilla porque no existe la compania/sucursal base.';
    RETURN;
  END IF;

  -- Idempotencia: WHERE NOT EXISTS por método (config.metodos_pago no tiene UNIQUE sobre (id_compania, nombre)).
  -- creacion_usuario se omite intencionalmente para que aplique el DEFAULT 'sistema' de la tabla.

  INSERT INTO config.metodos_pago (id_compania, id_sucursal, nombre, activo, eliminado)
  SELECT v_id_compania, v_id_sucursal, 'Efectivo', TRUE, FALSE
  WHERE NOT EXISTS (
    SELECT 1 FROM config.metodos_pago
    WHERE id_compania = v_id_compania AND nombre = 'Efectivo'
  );

  INSERT INTO config.metodos_pago (id_compania, id_sucursal, nombre, activo, eliminado)
  SELECT v_id_compania, v_id_sucursal, 'Tarjeta', TRUE, FALSE
  WHERE NOT EXISTS (
    SELECT 1 FROM config.metodos_pago
    WHERE id_compania = v_id_compania AND nombre = 'Tarjeta'
  );

  INSERT INTO config.metodos_pago (id_compania, id_sucursal, nombre, activo, eliminado)
  SELECT v_id_compania, v_id_sucursal, 'Transferencia', TRUE, FALSE
  WHERE NOT EXISTS (
    SELECT 1 FROM config.metodos_pago
    WHERE id_compania = v_id_compania AND nombre = 'Transferencia'
  );

END $$;
