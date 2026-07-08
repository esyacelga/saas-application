--liquibase formatted sql

--changeset gesyacelga:v1.0-020-core-clientes
--comment Datos gym-específicos de cada cliente (FK real a identidad.personas)
CREATE TABLE core.clientes (
  id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_persona    INT          NOT NULL REFERENCES identidad.personas(id),
  id_compania   INT          NOT NULL,
  id_sucursal   INT          NOT NULL,
  peso_kg       DECIMAL(5,2),
  altura_cm     DECIMAL(5,1),
  objetivos     TEXT,
  lesiones      TEXT,
  estado        VARCHAR(20)  NOT NULL DEFAULT 'activo'
                  CHECK (estado IN (
                    'activo','proximo_vencer','vencido',
                    'congelado','riesgo_abandono'
                  )),
  fecha_ingreso DATE         NOT NULL DEFAULT CURRENT_DATE,
  qr_codigo     VARCHAR(100) UNIQUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ,
  UNIQUE (id_persona, id_compania)
);
--rollback DROP TABLE IF EXISTS core.clientes;

--changeset gesyacelga:v1.0-021-core-tipos-membresia
--comment Catálogo de tipos de membresía por sucursal (días, semanas, meses, años)
CREATE TABLE core.tipos_membresia (
  id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania    INT           NOT NULL,
  id_sucursal    INT           NOT NULL,
  nombre         VARCHAR(100)  NOT NULL,
  duracion_tipo  VARCHAR(20)   NOT NULL
                   CHECK (duracion_tipo IN ('dias','semanas','meses','años')),
  duracion_valor INT           NOT NULL CHECK (duracion_valor > 0),
  precio         DECIMAL(10,2) NOT NULL CHECK (precio >= 0),
  activo         BOOLEAN       NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS core.tipos_membresia;

--changeset gesyacelga:v1.0-022-core-membresias
--comment Membresías activas e históricas de cada cliente
CREATE TABLE core.membresias (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_cliente          INT           NOT NULL REFERENCES core.clientes(id),
  id_tipo_membresia   INT           NOT NULL REFERENCES core.tipos_membresia(id),
  id_metodo_pago      INT,
  id_usuario_registro INT,
  fecha_inicio        DATE          NOT NULL,
  fecha_fin           DATE          NOT NULL,
  precio_pagado       DECIMAL(10,2) NOT NULL,
  descuento_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0,
  estado              VARCHAR(20)   NOT NULL DEFAULT 'activa'
                        CHECK (estado IN ('activa','vencida','congelada','anulada')),
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS core.membresias;

--changeset gesyacelga:v1.0-023-core-congelamientos
--comment Pausas de membresía; retroactivos requieren documento y aprobador
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
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_retroactivo CHECK (
    retroactivo = FALSE
    OR (documento_respaldo IS NOT NULL AND aprobado_por IS NOT NULL)
  )
);
--rollback DROP TABLE IF EXISTS core.congelamientos;
