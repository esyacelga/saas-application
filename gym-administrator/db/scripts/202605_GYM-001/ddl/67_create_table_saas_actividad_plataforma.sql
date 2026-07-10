CREATE TABLE saas.actividad_plataforma (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_evento      VARCHAR(50)  NOT NULL,
    modulo           VARCHAR(50)  NOT NULL,
    entidad_id       BIGINT,
    entidad_nombre   VARCHAR(200),
    detalle          JSONB,
    usuario          VARCHAR(200) NOT NULL,
    -- Actor y tenant del evento (REQ-SAAS-001, D3)
    id_compania      INT          REFERENCES tenant.companias(id),
    id_usuario_actor INT,
    tipo_actor       VARCHAR(20)  NOT NULL DEFAULT 'SISTEMA'
                       CHECK (tipo_actor IN ('OWNER','ROOT','STAFF','SISTEMA')),
    ip               INET,
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_actividad_plataforma_fecha    ON saas.actividad_plataforma (fecha DESC);
CREATE INDEX idx_actividad_plataforma_modulo   ON saas.actividad_plataforma (modulo);
CREATE INDEX idx_actividad_plataforma_evento   ON saas.actividad_plataforma (tipo_evento);
CREATE INDEX idx_actividad_plataforma_usuario  ON saas.actividad_plataforma (usuario);
CREATE INDEX idx_actividad_plataforma_compania
    ON saas.actividad_plataforma (id_compania, fecha DESC)
    WHERE id_compania IS NOT NULL;

COMMENT ON COLUMN saas.actividad_plataforma.detalle          IS 'Payload estructurado del evento. Schema documentado por evento en sección 6bis del REQ-SAAS-001.';
COMMENT ON COLUMN saas.actividad_plataforma.id_compania      IS 'FK al tenant afectado por el evento. NULL para eventos de plataforma pura (sistema).';
COMMENT ON COLUMN saas.actividad_plataforma.id_usuario_actor IS 'ID del actor humano. FK a seguridad.usuarios (staff/owner) o saas.usuarios_plataforma (root) según tipo_actor. Sin FK dura porque referencia dos tablas.';
COMMENT ON COLUMN saas.actividad_plataforma.tipo_actor       IS 'Clasificación del actor que originó el evento: OWNER / ROOT / STAFF / SISTEMA';
COMMENT ON COLUMN saas.actividad_plataforma.ip               IS 'Dirección IP del actor (INET). NULL para eventos originados por el sistema.';
