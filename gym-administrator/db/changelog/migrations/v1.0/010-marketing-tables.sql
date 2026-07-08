--liquibase formatted sql

--changeset gesyacelga:v1.0-031-marketing-promociones
--comment Campañas y promociones (2x1, descuentos, servicios extra, regalos)
CREATE TABLE marketing.promociones (
  id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania          INT           NOT NULL,
  id_sucursal          INT           NOT NULL,
  nombre               VARCHAR(150)  NOT NULL,
  tipo                 VARCHAR(30)   NOT NULL
                         CHECK (tipo IN ('2x1','porcentaje','servicio_extra','regalo')),
  descripcion          TEXT,
  condiciones          TEXT,
  descuento_porcentaje DECIMAL(5,2),
  max_personas         INT,
  fecha_inicio         DATE,
  fecha_fin            DATE,
  activa               BOOLEAN       NOT NULL DEFAULT TRUE,
  aplica_a_fidelidad   BOOLEAN       NOT NULL DEFAULT FALSE
);
--rollback DROP TABLE IF EXISTS marketing.promociones;

--changeset gesyacelga:v1.0-032-marketing-cliente-promociones
--comment Asignación y uso de promociones por cliente
CREATE TABLE marketing.cliente_promociones (
  id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT         NOT NULL,
  id_sucursal       INT         NOT NULL,
  id_cliente        INT         NOT NULL REFERENCES core.clientes(id),
  id_promocion      INT         NOT NULL REFERENCES marketing.promociones(id),
  id_membresia      INT,
  fecha_asignacion  DATE        NOT NULL DEFAULT CURRENT_DATE,
  fecha_uso         DATE,
  estado            VARCHAR(20) NOT NULL DEFAULT 'asignada'
                      CHECK (estado IN ('asignada','usada','expirada'))
);
--rollback DROP TABLE IF EXISTS marketing.cliente_promociones;

--changeset gesyacelga:v1.0-033-marketing-reglas-beneficios
--comment Reglas de beneficios por fidelidad (1 mes → 10%, 3 meses → nutricionista, etc.)
CREATE TABLE marketing.reglas_beneficios (
  id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT           NOT NULL,
  id_sucursal       INT           NOT NULL,
  meses_sin_faltas  INT           NOT NULL CHECK (meses_sin_faltas > 0),
  tipo_beneficio    VARCHAR(30)   NOT NULL
                      CHECK (tipo_beneficio IN ('descuento','servicio','regalo')),
  descripcion       VARCHAR(255)  NOT NULL,
  valor             DECIMAL(10,2),
  activo            BOOLEAN       NOT NULL DEFAULT TRUE,
  UNIQUE (id_compania, id_sucursal, meses_sin_faltas)
);
--rollback DROP TABLE IF EXISTS marketing.reglas_beneficios;

--changeset gesyacelga:v1.0-034-marketing-cliente-beneficios
--comment Beneficios otorgados a clientes por cumplir reglas de fidelidad
CREATE TABLE marketing.cliente_beneficios (
  id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania     INT         NOT NULL,
  id_sucursal     INT         NOT NULL,
  id_cliente      INT         NOT NULL REFERENCES core.clientes(id),
  id_regla        INT         NOT NULL REFERENCES marketing.reglas_beneficios(id),
  fecha_otorgado  DATE        NOT NULL DEFAULT CURRENT_DATE,
  estado          VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                    CHECK (estado IN ('pendiente','aplicado','expirado'))
);
--rollback DROP TABLE IF EXISTS marketing.cliente_beneficios;
