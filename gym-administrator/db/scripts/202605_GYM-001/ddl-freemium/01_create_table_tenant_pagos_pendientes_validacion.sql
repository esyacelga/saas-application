-- REQ-SAAS-001 — Sub-fase 1.1 — Decisión D2 (Coexistir con tenant.pagos_suscripcion)
-- RN-08 (Pago Premium por transferencia con validación manual)
--
-- Buzón de pagos reportados por el owner que aún NO fueron aprobados por root/soporte.
-- Cuando el root aprueba, se materializa una fila definitiva en tenant.pagos_suscripcion
-- (histórico contable) y se activa la suscripción Premium correspondiente.
--
-- Índices:
--   - ux_pagos_pendientes_hash: idempotencia (RN-08) — un mismo hash no puede coexistir
--     como PENDIENTE o APROBADO dos veces.
--   - idx_pagos_pendientes_estado_fecha: bandeja de pendientes para el panel root.
--   - idx_pagos_pendientes_compania: histórico de reportes por tenant.
CREATE TABLE tenant.pagos_pendientes_validacion (
    id                     INT           PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    id_compania            INT           NOT NULL REFERENCES tenant.companias(id),
    id_plan_destino        INT           NOT NULL REFERENCES saas.planes(id),
    monto                  DECIMAL(10,2) NOT NULL,
    moneda                 VARCHAR(3)    NOT NULL DEFAULT 'USD',
    fecha_reporte          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    fecha_transferencia    DATE          NOT NULL,
    comprobante_url        TEXT          NOT NULL,
    comprobante_hash       VARCHAR(64),
    banco_origen           VARCHAR(80),
    referencia             VARCHAR(80),
    hash_idempotencia      VARCHAR(64)   NOT NULL,
    estado                 VARCHAR(20)   NOT NULL DEFAULT 'pendiente'
                             CHECK (estado IN ('pendiente','aprobado','rechazado')),
    motivo_rechazo         TEXT,
    aprobado_por           INT           REFERENCES saas.usuarios_plataforma(id),
    fecha_aprobacion       TIMESTAMPTZ,
    activacion_programada  BOOLEAN       NOT NULL DEFAULT FALSE,
    factura_emitida_id     INT,
    eliminado              BOOLEAN       NOT NULL DEFAULT FALSE,
    creacion_fecha         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    creacion_usuario       VARCHAR(150)  NOT NULL DEFAULT 'sistema',
    modifica_fecha         TIMESTAMPTZ,
    modifica_usuario       VARCHAR(150)
);

COMMENT ON TABLE  tenant.pagos_pendientes_validacion                        IS 'Buzón de pagos reportados por el owner pendientes de aprobación manual por root/soporte (RN-08). Al aprobarse, se materializa una fila en tenant.pagos_suscripcion.';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.id_plan_destino        IS 'Plan que se activará al aprobar el pago (normalmente PREMIUM)';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.comprobante_url        IS 'URL Cloudinary (resource_type=raw, access_mode=authenticated). Nunca pública.';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.comprobante_hash       IS 'SHA-256 del archivo subido — auditoría e integridad';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.hash_idempotencia      IS 'SHA-256(id_compania|monto|fecha_transferencia|referencia) — previene reportes duplicados';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.estado                 IS 'pendiente / aprobado / rechazado (minúsculas, ver decisión D4)';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.aprobado_por           IS 'FK al usuario root/soporte que aprobó o rechazó';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.activacion_programada  IS 'TRUE si es upgrade Trial→Premium agendado (RN-05): el Premium se activa al vencer el Trial';
COMMENT ON COLUMN tenant.pagos_pendientes_validacion.factura_emitida_id     IS 'Reservado para fase 4 (facturación SRI). NULL en fase 1.';

-- ── Índices ────────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX ux_pagos_pendientes_hash
    ON tenant.pagos_pendientes_validacion(hash_idempotencia)
    WHERE estado IN ('pendiente','aprobado');

CREATE INDEX idx_pagos_pendientes_estado_fecha
    ON tenant.pagos_pendientes_validacion(estado, fecha_reporte DESC);

CREATE INDEX idx_pagos_pendientes_compania
    ON tenant.pagos_pendientes_validacion(id_compania, fecha_reporte DESC);
