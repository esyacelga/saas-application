CREATE TABLE saas.actividad_plataforma (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_evento    VARCHAR(50)  NOT NULL,
    modulo         VARCHAR(50)  NOT NULL,
    entidad_id     BIGINT,
    entidad_nombre VARCHAR(200),
    detalle        TEXT,
    usuario        VARCHAR(200) NOT NULL,
    ip             VARCHAR(45),
    fecha          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_actividad_plataforma_fecha    ON saas.actividad_plataforma (fecha DESC);
CREATE INDEX idx_actividad_plataforma_modulo   ON saas.actividad_plataforma (modulo);
CREATE INDEX idx_actividad_plataforma_evento   ON saas.actividad_plataforma (tipo_evento);
CREATE INDEX idx_actividad_plataforma_usuario  ON saas.actividad_plataforma (usuario);
