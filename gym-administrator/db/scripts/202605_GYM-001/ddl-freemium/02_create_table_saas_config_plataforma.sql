-- REQ-SAAS-001 — Sub-fase 1.1 — Sección 11.4
--
-- Tabla clave-valor para configuración runtime de la plataforma SaaS.
-- Su primer uso es persistir los datos bancarios que el owner ve al reportar
-- un pago (pago.banco.nombre, .numero, .titular, etc.).
-- Ediciones son responsabilidad de usuarios root desde /platform/config.
CREATE TABLE saas.config_plataforma (
    clave            VARCHAR(100) PRIMARY KEY,
    valor            TEXT         NOT NULL,
    descripcion      TEXT,
    modificado_por   INT          REFERENCES saas.usuarios_plataforma(id),
    modificado_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema'
);

COMMENT ON TABLE  saas.config_plataforma            IS 'Configuración runtime editable por root (datos bancarios, textos, umbrales). No requiere redeploy.';
COMMENT ON COLUMN saas.config_plataforma.clave      IS 'Identificador dot-notation de la configuración (p.ej. pago.banco.nombre)';
COMMENT ON COLUMN saas.config_plataforma.valor      IS 'Valor serializado como texto. La aplicación se encarga del parseo (bool/int/json)';
COMMENT ON COLUMN saas.config_plataforma.descripcion IS 'Descripción legible mostrada en el admin panel al editar';
