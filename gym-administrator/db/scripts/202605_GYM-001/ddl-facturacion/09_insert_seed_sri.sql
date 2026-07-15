-- Seed: sri.tipos_comprobante
INSERT INTO sri.tipos_comprobante (codigo, nombre, version, activo) VALUES
  ('01', 'FACTURA',                  '2.1.0', TRUE),
  ('03', 'LIQUIDACION_COMPRA',       '1.1.0', TRUE),
  ('04', 'NOTA_CREDITO',             '1.1.0', TRUE),
  ('05', 'NOTA_DEBITO',              '1.0.0', TRUE),
  ('06', 'GUIA_REMISION',            '1.1.0', TRUE),
  ('07', 'COMPROBANTE_RETENCION',    '2.0.0', TRUE);

-- Seed: sri.tipos_identificacion_comprador
INSERT INTO sri.tipos_identificacion_comprador (codigo, nombre) VALUES
  ('04', 'RUC'),
  ('05', 'CEDULA'),
  ('06', 'PASAPORTE'),
  ('07', 'CONSUMIDOR_FINAL'),
  ('08', 'ID_EXTERIOR');

-- Seed: sri.formas_pago
-- bancarizada = TRUE en 16-20 (medios que utilizan el sistema financiero).
INSERT INTO sri.formas_pago (codigo, nombre, bancarizada, activo) VALUES
  ('01', 'SIN_UTILIZACION_SISTEMA_FINANCIERO',       FALSE, TRUE),
  ('15', 'COMPENSACION_DEUDAS',                      FALSE, TRUE),
  ('16', 'TARJETA_DEBITO',                           TRUE,  TRUE),
  ('17', 'DINERO_ELECTRONICO',                       TRUE,  TRUE),
  ('18', 'TARJETA_PREPAGO',                          TRUE,  TRUE),
  ('19', 'TARJETA_CREDITO',                          TRUE,  TRUE),
  ('20', 'OTROS_CON_UTILIZACION_SISTEMA_FINANCIERO', TRUE,  TRUE),
  ('21', 'ENDOSO_TITULOS',                           FALSE, TRUE);

-- Seed: sri.tipos_impuesto
INSERT INTO sri.tipos_impuesto (codigo, nombre) VALUES
  ('2', 'IVA'),
  ('3', 'ICE'),
  ('5', 'IRBPNR');

-- Seed: sri.tarifas_iva
-- El IVA vigente en Ecuador a julio 2026 es el 15% (codigo '4')
INSERT INTO sri.tarifas_iva (codigo, nombre, porcentaje, vigente_desde, vigente_hasta) VALUES
  ('0', 'IVA_0',          0.00,  NULL,         NULL),
  ('2', 'IVA_12',         12.00, '2008-01-01', '2024-03-31'),
  ('3', 'IVA_14',         14.00, '2016-06-01', '2017-05-31'),
  ('4', 'IVA_15',         15.00, '2024-04-01', NULL),
  ('6', 'NO_OBJETO_IMP',  0.00,  NULL,         NULL),
  ('7', 'EXENTO_IVA',     0.00,  NULL,         NULL),
  ('8', 'IVA_8_FERIADO',  8.00,  NULL,         NULL);

-- Seed: sri.motivos_anulacion_nc
INSERT INTO sri.motivos_anulacion_nc (codigo, descripcion) VALUES
  ('DEVOLUCION',    'Devolución de mercadería'),
  ('DESCUENTO',     'Descuento comercial'),
  ('ANULACION',     'Anulación de factura'),
  ('ERROR_PRECIO',  'Error en precio'),
  ('ERROR_CALIDAD', 'Diferencia de calidad');
