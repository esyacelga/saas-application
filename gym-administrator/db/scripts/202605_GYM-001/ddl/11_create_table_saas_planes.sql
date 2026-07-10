CREATE TABLE saas.planes (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre               VARCHAR(100)  NOT NULL,
  descripcion          TEXT,
  precio_mensual       DECIMAL(10,2) NOT NULL,
  -- Esquema Free / Trial / Premium (REQ-SAAS-001)
  codigo               VARCHAR(20)   UNIQUE,
  duracion_dias        INTEGER,
  es_gratuito          BOOLEAN       NOT NULL DEFAULT FALSE,
  plan_degradacion_id  INT           REFERENCES saas.planes(id),
  max_sucursales       INTEGER,
  max_clientes_activos INTEGER,
  max_staff            INTEGER,
  moneda               VARCHAR(3)    NOT NULL DEFAULT 'USD',
  es_legacy            BOOLEAN       NOT NULL DEFAULT FALSE,
  activo               BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado            BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario     VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha       TIMESTAMPTZ,
  modifica_usuario     VARCHAR(150)
);

COMMENT ON COLUMN saas.planes.codigo               IS 'Identificador estable del plan (FREE, TRIAL, PREMIUM)';
COMMENT ON COLUMN saas.planes.duracion_dias        IS 'Duración del ciclo del plan en días. NULL = permanente (Free)';
COMMENT ON COLUMN saas.planes.es_gratuito          IS 'TRUE si el plan no requiere pago (Free / Trial)';
COMMENT ON COLUMN saas.planes.plan_degradacion_id  IS 'Plan destino al que se degrada la suscripción al vencer';
COMMENT ON COLUMN saas.planes.max_sucursales       IS 'Máximo de sucursales permitidas. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.max_clientes_activos IS 'Máximo de clientes con membresía vigente. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.max_staff            IS 'Máximo de usuarios staff. NULL = ilimitado';
COMMENT ON COLUMN saas.planes.moneda               IS 'Código ISO 4217 de la moneda del precio (default USD)';
COMMENT ON COLUMN saas.planes.es_legacy            IS 'TRUE para planes históricos que solo existen para preservar contratos migrados';
