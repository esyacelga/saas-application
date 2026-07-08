--liquibase formatted sql

--changeset gesyacelga:v1.0-005-identidad-personas
--comment Identidad global de personas, compartida entre todos los gyms (sin tenant)
CREATE TABLE identidad.personas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ci               VARCHAR(20)  NOT NULL UNIQUE,
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  foto_url         VARCHAR(255),
  fecha_nacimiento DATE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS identidad.personas;

--changeset gesyacelga:v1.0-006-identidad-usuarios-app
--comment Credenciales de acceso a la app móvil, emitidas por cada gym
CREATE TABLE identidad.usuarios_app (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_persona          INT          NOT NULL REFERENCES identidad.personas(id),
  id_compania         INT          NOT NULL,
  login               VARCHAR(150) NOT NULL,
  password_hash       VARCHAR(255) NOT NULL,
  requiere_cambio_pwd BOOLEAN      NOT NULL DEFAULT TRUE,
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso       TIMESTAMPTZ,
  token_recuperacion  VARCHAR(100),
  token_expira        TIMESTAMPTZ,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (id_persona, id_compania),
  UNIQUE (id_compania, login)
);
--rollback DROP TABLE IF EXISTS identidad.usuarios_app;
