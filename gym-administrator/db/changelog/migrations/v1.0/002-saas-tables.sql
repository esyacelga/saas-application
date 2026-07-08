--liquibase formatted sql

--changeset gesyacelga:v1.0-002-saas-planes
--comment Tabla de planes SaaS globales
CREATE TABLE saas.planes (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre           VARCHAR(100)  NOT NULL,
  descripcion      TEXT,
  precio_mensual   DECIMAL(10,2) NOT NULL,
  activo           BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS saas.planes;

--changeset gesyacelga:v1.0-003-saas-caracteristicas
--comment Características disponibles por módulo para feature gating
CREATE TABLE saas.caracteristicas (
  id      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo  VARCHAR(50)  NOT NULL UNIQUE,
  nombre  VARCHAR(100) NOT NULL,
  modulo  VARCHAR(50)  NOT NULL,
  activo  BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS saas.caracteristicas;

--changeset gesyacelga:v1.0-004-saas-plan-caracteristicas
--comment Relación N:M entre planes y características
CREATE TABLE saas.plan_caracteristicas (
  id_plan           INT NOT NULL REFERENCES saas.planes(id),
  id_caracteristica INT NOT NULL REFERENCES saas.caracteristicas(id),
  PRIMARY KEY (id_plan, id_caracteristica)
);
--rollback DROP TABLE IF EXISTS saas.plan_caracteristicas;
