-- REQ-SAAS-001 — Sub-fase 1.1
-- Amplía saas.planes con los atributos del nuevo esquema Free / Trial / Premium:
--   codigo             -> identificador estable del plan (FREE, TRIAL, PREMIUM, LEGACY_GRANDFATHERED)
--   duracion_dias      -> NULL = permanente (Free), 60 = Trial, 30 = Premium
--   es_gratuito        -> flag utilitario para queries de planes gratuitos
--   plan_degradacion_id-> FK auto-referencial: plan destino al vencer (Trial->Free, Premium->Free, LEGACY->Premium)
--   max_sucursales / max_clientes_activos / max_staff -> hard limits (NULL = ilimitado)
--   moneda             -> preparado para futura internacionalización (default USD)
--   es_legacy          -> planes históricos que se ocultan del auto-registro pero se preservan
ALTER TABLE saas.planes
    ADD COLUMN IF NOT EXISTS codigo               VARCHAR(20),
    ADD COLUMN IF NOT EXISTS duracion_dias        INTEGER,
    ADD COLUMN IF NOT EXISTS es_gratuito          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS plan_degradacion_id  INT,
    ADD COLUMN IF NOT EXISTS max_sucursales       INTEGER,
    ADD COLUMN IF NOT EXISTS max_clientes_activos INTEGER,
    ADD COLUMN IF NOT EXISTS max_staff            INTEGER,
    ADD COLUMN IF NOT EXISTS moneda               VARCHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS es_legacy            BOOLEAN NOT NULL DEFAULT FALSE;

-- Constraint UNIQUE sobre codigo (permite NULL en filas viejas hasta que la seed las clasifique)
ALTER TABLE saas.planes
    ADD CONSTRAINT ux_saas_planes_codigo UNIQUE (codigo);

-- FK auto-referencial para el plan de degradación
ALTER TABLE saas.planes
    ADD CONSTRAINT fk_saas_planes_degradacion
        FOREIGN KEY (plan_degradacion_id) REFERENCES saas.planes(id);

COMMENT ON COLUMN saas.planes.codigo               IS 'Identificador estable del plan (FREE, TRIAL, PREMIUM, LEGACY_GRANDFATHERED)';
COMMENT ON COLUMN saas.planes.duracion_dias        IS 'Duración del ciclo del plan en días. NULL = permanente (Free)';
COMMENT ON COLUMN saas.planes.es_gratuito          IS 'TRUE si el plan no requiere pago (Free / Trial)';
COMMENT ON COLUMN saas.planes.plan_degradacion_id  IS 'Plan destino al que se degrada la suscripción al vencer';
COMMENT ON COLUMN saas.planes.max_sucursales       IS 'Máximo de sucursales permitidas. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.max_clientes_activos IS 'Máximo de clientes con membresía vigente. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.max_staff            IS 'Máximo de usuarios staff. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.moneda               IS 'Código ISO 4217 de la moneda del precio (default USD)';
COMMENT ON COLUMN saas.planes.es_legacy            IS 'TRUE para planes históricos que solo existen para preservar contratos migrados';
