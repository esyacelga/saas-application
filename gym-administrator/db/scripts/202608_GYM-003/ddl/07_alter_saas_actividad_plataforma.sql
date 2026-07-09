-- REQ-SAAS-001 — Sub-fase 1.1 — Decisión D3 (Migrar schema de saas.actividad_plataforma)
-- Sección 6bis del requerimiento.
--
-- Estado actual de la tabla:
--   id BIGINT PK, tipo_evento VARCHAR(50), modulo VARCHAR(50), entidad_id BIGINT,
--   entidad_nombre VARCHAR(200), detalle TEXT, usuario VARCHAR(200), ip VARCHAR(45),
--   fecha TIMESTAMPTZ.
--
-- Cambios de este script:
--   1. Agregar id_compania INT NULL REFERENCES tenant.companias(id) — permite filtrar
--      auditoría por tenant. NULL para eventos de sistema/plataforma.
--   2. Agregar id_usuario_actor INT NULL — id del actor humano (owner/root/staff)
--      cuando aplica. La columna "usuario" existente (VARCHAR) se conserva para el
--      texto legible del usuario.
--   3. Agregar tipo_actor VARCHAR(20) NOT NULL DEFAULT 'SISTEMA' — clasificación del actor:
--      OWNER / ROOT / STAFF / SISTEMA. Las filas existentes quedan como 'SISTEMA'.
--   4. Convertir detalle TEXT -> JSONB con USING detalle::jsonb. Salvaguarda con nullif
--      para textos vacíos y try/catch a nivel de conversión.
--   5. Convertir ip VARCHAR(45) -> INET con USING nullif(ip,'')::inet. Se protege contra
--      NULL y strings vacías; IPs malformadas se limpian a NULL antes del cast.
--
-- NO se borran filas existentes (quedan con id_compania=NULL, tipo_actor='SISTEMA').
-- NO se agrega CHECK sobre el nombre del evento — es string libre extendible.

-- ── 1. id_compania ─────────────────────────────────────────────────────────
ALTER TABLE saas.actividad_plataforma
    ADD COLUMN IF NOT EXISTS id_compania INT REFERENCES tenant.companias(id);

CREATE INDEX IF NOT EXISTS idx_actividad_plataforma_compania
    ON saas.actividad_plataforma (id_compania, fecha DESC)
    WHERE id_compania IS NOT NULL;

COMMENT ON COLUMN saas.actividad_plataforma.id_compania IS 'FK al tenant afectado por el evento. NULL para eventos de plataforma pura (sistema).';

-- ── 2. id_usuario_actor ────────────────────────────────────────────────────
ALTER TABLE saas.actividad_plataforma
    ADD COLUMN IF NOT EXISTS id_usuario_actor INT;

COMMENT ON COLUMN saas.actividad_plataforma.id_usuario_actor IS 'ID del actor humano. FK a seguridad.usuarios (staff/owner) o saas.usuarios_plataforma (root) según tipo_actor. Sin FK dura porque referencia dos tablas.';

-- ── 3. tipo_actor ──────────────────────────────────────────────────────────
ALTER TABLE saas.actividad_plataforma
    ADD COLUMN IF NOT EXISTS tipo_actor VARCHAR(20) NOT NULL DEFAULT 'SISTEMA';

ALTER TABLE saas.actividad_plataforma
    ADD CONSTRAINT ck_actividad_plataforma_tipo_actor
        CHECK (tipo_actor IN ('OWNER','ROOT','STAFF','SISTEMA'));

COMMENT ON COLUMN saas.actividad_plataforma.tipo_actor IS 'Clasificación del actor que originó el evento: OWNER / ROOT / STAFF / SISTEMA';

-- ── 4. detalle TEXT -> JSONB (con salvaguarda) ─────────────────────────────
-- Antes del cast, se normalizan las filas existentes: NULL o texto vacío -> NULL.
-- Si alguna fila tiene texto no-JSON válido, la conversión fallará y el DBA debe
-- limpiarla manualmente antes de reintentar (el requerimiento pide no borrar filas).
UPDATE saas.actividad_plataforma
   SET detalle = NULL
 WHERE detalle IS NOT NULL AND btrim(detalle) = '';

ALTER TABLE saas.actividad_plataforma
    ALTER COLUMN detalle TYPE JSONB
    USING CASE
              WHEN detalle IS NULL THEN NULL
              ELSE detalle::jsonb
          END;

COMMENT ON COLUMN saas.actividad_plataforma.detalle IS 'Payload estructurado del evento. Schema documentado por evento en sección 6bis del REQ-SAAS-001.';

-- ── 5. ip VARCHAR(45) -> INET (con salvaguarda) ────────────────────────────
-- Vaciar strings malformadas antes del cast para no bloquear la migración.
UPDATE saas.actividad_plataforma
   SET ip = NULL
 WHERE ip IS NOT NULL
   AND (btrim(ip) = '' OR ip !~ '^[0-9a-fA-F:.]+(/[0-9]+)?$');

ALTER TABLE saas.actividad_plataforma
    ALTER COLUMN ip TYPE INET
    USING nullif(btrim(ip), '')::inet;

COMMENT ON COLUMN saas.actividad_plataforma.ip IS 'Dirección IP del actor (INET). NULL para eventos originados por el sistema.';
