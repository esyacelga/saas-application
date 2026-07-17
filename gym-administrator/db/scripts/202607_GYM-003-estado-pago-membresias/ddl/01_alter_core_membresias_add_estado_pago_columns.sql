-- GYM-003 — Añade columnas de estado de pago y auditoría de rechazo a core.membresias.
--
-- Contexto:
--   Hoy toda venta de membresía asume pago recibido. Para habilitar el flujo
--   "vendida ahora, pagada después" (HU-B: compra desde la PWA del socio) y para
--   tener una bandeja de "ventas por cobrar" en el panel admin, se separa el
--   momento de la venta del momento del pago mediante la columna estado_pago.
--
--   Adicionalmente, para poder rechazar una pendiente sin perder trazabilidad,
--   se reutiliza la columna `eliminado` (BOOLEAN NOT NULL DEFAULT FALSE) que
--   YA EXISTE en el baseline (202605_GYM-001/ddl/31_create_table_core_membresias.sql
--   línea 17) y se agregan sus tres columnas de auditoría hermanas.
--
-- Backfill:
--   El DEFAULT 'PAGADO' en el mismo ADD COLUMN puebla todas las filas existentes
--   con el valor correcto (todas las membresías históricas son cobradas). No se
--   requiere UPDATE adicional. Después de este changeSet quedan listas para el
--   CHECK ck_membresias_fechas_por_estado_pago del script 03.
--
-- Idempotencia / seguridad:
--   No es idempotente por naturaleza (ADD COLUMN falla si ya existe), pero
--   Liquibase evita re-ejecutar el changeSet vía databasechangelog. En Neon (prod)
--   los ADD COLUMN son metadata-only + backfill in-place; en BD local (gym-app-saas)
--   la tabla tiene un volumen que hace el backfill instantáneo.

ALTER TABLE core.membresias
  ADD COLUMN estado_pago         VARCHAR(20) NOT NULL DEFAULT 'PAGADO'
    CHECK (estado_pago IN ('PENDIENTE','PAGADO')),
  ADD COLUMN fecha_eliminacion   TIMESTAMPTZ NULL,
  ADD COLUMN eliminado_por       INT NULL,
  ADD COLUMN motivo_eliminacion  VARCHAR(30) NULL
    CHECK (motivo_eliminacion IN (
      'SOCIO_CAMBIO_OPINION','ERROR_DE_VENTA','DUPLICADA','DATOS_INCORRECTOS','OTRO'
    ));
