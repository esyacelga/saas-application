-- GYM-002 — Actualiza el COMMENT ON COLUMN de identidad.personas.ci_validada.
--
-- Contexto:
--   El comentario original (baseline GYM-001, archivo
--   202605_GYM-001/ddl/14_create_table_identidad_personas.sql:21) decía
--   "La lógica que puebla este campo está pendiente de implementación",
--   pero desde 2026-07-14 el flag SÍ se calcula en el INSERT de personas
--   dentro de platform-service (PersonaPersistenceAdapter.resolverIdPersona,
--   línea 32) usando el verificador ecuatoriano módulo 10 (CedulaEcuatoriana).
--
--   Aún NO se calcula en: UPDATE de persona, backfill histórico, otras
--   rutas de creación (core/auth), ni se expone en el REST.
--   Ver: docs/gym-administrator/pendientes/validacion-cedula-persona.md
--
-- No se toca el DDL de la tabla — solo el metadato del comentario. Idempotente
-- por naturaleza: COMMENT ON COLUMN sobreescribe siempre. Seguro para Neon (prod)
-- y para la BD local gym-app-saas.

COMMENT ON COLUMN identidad.personas.ci_validada IS
  'TRUE cuando la cédula pasó el algoritmo del dígito verificador ecuatoriano '
  '(módulo 10 del Registro Civil, implementado en CedulaEcuatoriana). '
  'FALSE por defecto cuando: (a) el documento no es una cédula ecuatoriana '
  '(pasaporte, RUC, doc. extranjero), (b) la cédula no pasó el módulo 10, o '
  '(c) la persona fue creada por una ruta que aún no calcula el flag. '
  'Hoy solo lo puebla el INSERT de platform-service '
  '(PersonaPersistenceAdapter.resolverIdPersona). UPDATE, backfill, otras rutas '
  'de creación y exposición REST siguen pendientes — ver '
  'docs/gym-administrator/pendientes/validacion-cedula-persona.md.';
