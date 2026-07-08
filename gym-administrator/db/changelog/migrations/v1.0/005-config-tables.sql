--liquibase formatted sql

--changeset gesyacelga:v1.0-013-config-gym-config
--comment Tabla clave-valor con parámetros operativos por sucursal
CREATE TABLE config.gym_config (
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  clave        VARCHAR(100) NOT NULL,
  valor        TEXT,
  descripcion  VARCHAR(255),
  tipo         VARCHAR(20)  NOT NULL DEFAULT 'texto'
                 CHECK (tipo IN ('texto','numero','booleano','json')),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  PRIMARY KEY (id_compania, id_sucursal, clave)
);
--rollback DROP TABLE IF EXISTS config.gym_config;

--changeset gesyacelga:v1.0-014-config-metodos-pago
--comment Métodos de pago habilitados por sucursal
CREATE TABLE config.metodos_pago (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS config.metodos_pago;
