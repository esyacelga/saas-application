-- GYM-003 — Seed multi-tenant del permiso 'membresias:confirmar_pago'.
--
-- Contexto (§4.9 del doc):
--   seguridad.permisos es una tabla POR-TENANT (UNIQUE (id_compania, nombre)),
--   no un catálogo global. Cada gimnasio tiene su propia fila del permiso.
--   Este seed hace backfill: crea la fila 'membresias:confirmar_pago' en cada
--   compañía existente en tenant.companias (incluye la compañía Sistema con
--   RUC '0000000000001', lo que permite que SUPER_ADMIN lo herede en flujos
--   posteriores).
--
--   NO se insertan filas en seguridad.rol_permisos: los nombres de rol no
--   están estandarizados por gimnasio (cada dueño llama a sus roles como
--   quiere), así que el vínculo rol ↔ permiso se hace manualmente desde la UI
--   "Editar rol" del panel admin (auth-service-frond-end).
--
-- id_sucursal:
--   Sigue la convención del baseline (61_insert_seed_usuario_root.sql):
--   sucursal principal (es_principal = TRUE) de la compañía; si ninguna está
--   marcada como principal, fallback a la primera sucursal por id.
--
-- Idempotencia:
--   Liquibase no re-ejecuta el mismo changeSet, pero el NOT EXISTS blinda
--   contra re-runs manuales del script o contra el caso raro donde la fila
--   ya haya sido insertada por otra vía (p. ej. seed manual de QA).
--
-- Compañías creadas DESPUÉS de esta migración:
--   No quedan cubiertas por este seed — es una limitación conocida y
--   documentada como HU-G (§7 del doc): añadir hook en el onboarding de
--   compañías, o migrar seguridad.permisos a catálogo global.

INSERT INTO seguridad.permisos (id_compania, id_sucursal, nombre, descripcion, modulo)
SELECT
  c.id,
  COALESCE(
    (SELECT s.id FROM tenant.sucursales s
       WHERE s.id_compania = c.id AND s.es_principal = TRUE
       LIMIT 1),
    (SELECT s.id FROM tenant.sucursales s
       WHERE s.id_compania = c.id
       ORDER BY s.id
       LIMIT 1)
  ) AS id_sucursal,
  'membresias:confirmar_pago',
  'Confirmar o rechazar el pago de una membresía',
  'core'
FROM tenant.companias c
WHERE NOT EXISTS (
  SELECT 1 FROM seguridad.permisos p
  WHERE p.id_compania = c.id
    AND p.nombre = 'membresias:confirmar_pago'
);
