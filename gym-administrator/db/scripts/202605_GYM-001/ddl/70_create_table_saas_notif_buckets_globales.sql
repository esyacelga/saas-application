-- Buckets globales de aviso de vencimiento (WhatsApp/email) configurables por super_admin.
-- (ex story GYM-003, consolidado en la baseline al recrear la BD desde cero)
--
-- Contexto (feature "avisos por WhatsApp de vencimiento", Fase 6 / issue R1):
--   Los días de aviso previo dejan de estar hardcodeados en los jobs:
--     - socio  (attendance.MensajeriaJob)            -> 3 días
--     - dueño  (platform.NotificacionVencimientoJob) -> 3 días
--   El super_admin puede ajustar SOLO el aviso previo (los N días de antelación) desde el
--   panel, sin redeploy. El aviso del DÍA DEL VENCIMIENTO (0) queda FIJO, NO configurable
--   y fuera de esta tabla (es una constante del código en cada job).
--
-- Por qué NO va en tenant.config_notif_suscripcion:
--   esa tabla es POR TENANT (PK id_compania, dias_antes) y rige el CANAL por compañía. Los buckets
--   de aviso son GLOBALES a toda la plataforma (una sola política), así que viven en el esquema saas
--   (config de plataforma), no por tenant. config_notif_suscripcion sigue rigiendo canal por tenant.
--
-- Modelo: 1 fila por destinatario (solo 'socio' y 'dueno'), PK = destinatario. Auditoría calcada de
-- saas.config_plataforma (modificado_por/at + creacion_*).
CREATE TABLE saas.notif_buckets_globales (
    destinatario     VARCHAR(10)  PRIMARY KEY
                                  CHECK (destinatario IN ('socio', 'dueno')),
    dias_previo      INT          NOT NULL DEFAULT 3
                                  CHECK (dias_previo BETWEEN 1 AND 30),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    modificado_por   INT          REFERENCES saas.usuarios_plataforma(id),
    modificado_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema'
);

COMMENT ON TABLE  saas.notif_buckets_globales              IS 'Política GLOBAL (no por tenant) de días de aviso PREVIO de vencimiento, editable por super_admin sin redeploy. Una fila por destinatario. El aviso del día 0 (vence hoy) NO está aquí: es constante fija del código en cada job.';
COMMENT ON COLUMN saas.notif_buckets_globales.destinatario IS 'A quién aplica el bucket: socio (membresía del gym, attendance-service) o dueno (suscripción SaaS del owner, platform-service).';
COMMENT ON COLUMN saas.notif_buckets_globales.dias_previo  IS 'Días de antelación del aviso PREVIO (1..30). Default 3. El job dispara el aviso previo cuando diasParaVencer <= dias_previo. NO incluye el aviso del día 0 (ese es fijo en código).';
COMMENT ON COLUMN saas.notif_buckets_globales.activo       IS 'FALSE desactiva por completo el aviso previo de ese destinatario (el día 0 sigue rigiéndose por el código del job). Permite apagar avisos desde el panel sin borrar la fila.';

-- Seed: socio=3 y dueno=3 (decisión Fase 3: "el recordatorio son de tres días no más").
INSERT INTO saas.notif_buckets_globales (destinatario, dias_previo, activo, creacion_usuario) VALUES
    ('socio', 3, TRUE, 'sistema'),
    ('dueno', 3, TRUE, 'sistema');
