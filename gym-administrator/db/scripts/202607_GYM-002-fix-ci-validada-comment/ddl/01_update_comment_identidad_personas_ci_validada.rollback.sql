-- Rollback GYM-002-1 — restaura el COMMENT original del baseline GYM-001
-- (texto exacto del archivo 202605_GYM-001/ddl/14_create_table_identidad_personas.sql:21).

COMMENT ON COLUMN identidad.personas.ci_validada IS
  'TRUE cuando la cédula pasó el algoritmo del dígito verificador ecuatoriano '
  '(módulo 10 del Registro Civil). FALSE por defecto: aún no validada, o el '
  'documento no es una cédula ecuatoriana (pasaporte, RUC, doc. extranjero). '
  'La lógica que puebla este campo está pendiente de implementación.';
