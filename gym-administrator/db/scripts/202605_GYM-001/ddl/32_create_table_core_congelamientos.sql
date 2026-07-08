CREATE TABLE core.congelamientos (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT          NOT NULL,
  id_sucursal          INT          NOT NULL,
  id_membresia         INT          NOT NULL REFERENCES core.membresias(id),
  fecha_inicio         DATE         NOT NULL,
  fecha_fin            DATE,
  motivo               VARCHAR(30)
                         CHECK (motivo IN (
                           'viaje','lesion','enfermedad','voluntario','otro'
                         )),
  detalle              TEXT,
  retroactivo          BOOLEAN      NOT NULL DEFAULT FALSE,
  documento_respaldo   VARCHAR(255),
  aprobado_por         INT,
  fecha_aprobacion     DATE,
  id_usuario_registro  INT,
  eliminado            BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario     VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha       TIMESTAMPTZ,
  modifica_usuario     VARCHAR(150),
  CONSTRAINT chk_retroactivo CHECK (
    retroactivo = FALSE
    OR (documento_respaldo IS NOT NULL AND aprobado_por IS NOT NULL)
  )
);
