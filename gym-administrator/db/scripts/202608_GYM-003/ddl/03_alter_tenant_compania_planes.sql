-- REQ-SAAS-001 — Sub-fase 1.1 — RN-06, RN-03, RN-10, RN-09
-- 1) Nuevas columnas para el modo SOBRE_LIMITE y la trazabilidad de degradación.
-- 2) Ampliación de los CHECK constraints de estado y tipo_cambio para incluir los
--    nuevos valores de la máquina de estados (reemplazada, degradacion_auto,
--    cancelacion, suspension). Se preservan TODOS los valores anteriores.
-- 3) Índices necesarios para el job diario y para queries de sobre-límite.

-- ── Nuevas columnas ─────────────────────────────────────────────────────────
ALTER TABLE tenant.compania_planes
    ADD COLUMN IF NOT EXISTS sobre_limite         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sobre_limite_hasta   DATE,
    ADD COLUMN IF NOT EXISTS causa_degradacion    VARCHAR(30);

COMMENT ON COLUMN tenant.compania_planes.sobre_limite       IS 'TRUE cuando la suscripción activa tiene más recursos que los que permite el plan actual (RN-06)';
COMMENT ON COLUMN tenant.compania_planes.sobre_limite_hasta IS 'Fecha límite de gracia (30 días) para reducir los recursos excedidos';
COMMENT ON COLUMN tenant.compania_planes.causa_degradacion  IS 'Causa de la última degradación automática: vencimiento / pago_rechazado / cancelacion_manual / suspension_root';

-- CHECK de causa_degradacion (usa minúsculas, ver decisión D4)
ALTER TABLE tenant.compania_planes
    ADD CONSTRAINT ck_compania_planes_causa_degradacion
        CHECK (
            causa_degradacion IS NULL
            OR causa_degradacion IN (
                'vencimiento',
                'pago_rechazado',
                'cancelacion_manual',
                'suspension_root'
            )
        );

-- ── Ampliación de CHECK: estado ─────────────────────────────────────────────
-- El nombre del constraint original es autogenerado por Postgres (compania_planes_estado_check).
-- Se descubre dinámicamente para no depender de un nombre fijo.
DO $$
DECLARE
    v_cons_name TEXT;
BEGIN
    SELECT conname INTO v_cons_name
    FROM pg_constraint
    WHERE conrelid = 'tenant.compania_planes'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%estado%'
      AND pg_get_constraintdef(oid) NOT ILIKE '%tipo_cambio%'
    LIMIT 1;

    IF v_cons_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tenant.compania_planes DROP CONSTRAINT %I', v_cons_name);
    END IF;
END $$;

ALTER TABLE tenant.compania_planes
    ADD CONSTRAINT ck_compania_planes_estado
        CHECK (estado IN (
            'activo',
            'en_gracia',
            'vencido',
            'suspendido',
            'cancelado',
            'programado',
            'reemplazada'
        ));

-- ── Ampliación de CHECK: tipo_cambio ────────────────────────────────────────
DO $$
DECLARE
    v_cons_name TEXT;
BEGIN
    SELECT conname INTO v_cons_name
    FROM pg_constraint
    WHERE conrelid = 'tenant.compania_planes'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%tipo_cambio%'
    LIMIT 1;

    IF v_cons_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE tenant.compania_planes DROP CONSTRAINT %I', v_cons_name);
    END IF;
END $$;

ALTER TABLE tenant.compania_planes
    ADD CONSTRAINT ck_compania_planes_tipo_cambio
        CHECK (tipo_cambio IN (
            'nuevo',
            'renovacion',
            'upgrade',
            'downgrade',
            'degradacion_auto',
            'cancelacion',
            'suspension'
        ));

-- ── Índices ────────────────────────────────────────────────────────────────
-- RN-10: prevenir dos suscripciones "vigentes" del mismo tenant.
CREATE UNIQUE INDEX IF NOT EXISTS ux_compania_plan_vigente
    ON tenant.compania_planes(id_compania)
    WHERE estado IN ('activo','en_gracia');

-- Soporte para SubscriptionJobService (busca "vencidos" y "por vencer" diariamente).
CREATE INDEX IF NOT EXISTS idx_compania_planes_estado_fecha_fin
    ON tenant.compania_planes(estado, fecha_fin);

-- Soporte para el job de archivado automático (RN-06): tenants con sobre_limite=true.
CREATE INDEX IF NOT EXISTS idx_compania_planes_sobre_limite
    ON tenant.compania_planes(id_compania)
    WHERE sobre_limite = TRUE;
