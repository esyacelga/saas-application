--liquibase formatted sql

--changeset gesyacelga:v1.0-015-seguridad-roles
--comment Roles de usuarios internos del gym por sucursal
CREATE TABLE seguridad.roles (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(50)  NOT NULL,
  descripcion  VARCHAR(255),
  UNIQUE (id_compania, nombre)
);
--rollback DROP TABLE IF EXISTS seguridad.roles;

--changeset gesyacelga:v1.0-016-seguridad-permisos
--comment Permisos granulares por módulo
CREATE TABLE seguridad.permisos (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  descripcion  VARCHAR(255),
  modulo       VARCHAR(50)  NOT NULL,
  UNIQUE (id_compania, nombre)
);
--rollback DROP TABLE IF EXISTS seguridad.permisos;

--changeset gesyacelga:v1.0-017-seguridad-rol-permisos
--comment Relación N:M entre roles y permisos
CREATE TABLE seguridad.rol_permisos (
  id_compania   INT NOT NULL,
  id_sucursal   INT NOT NULL,
  id_rol        INT NOT NULL REFERENCES seguridad.roles(id),
  id_permiso    INT NOT NULL REFERENCES seguridad.permisos(id),
  PRIMARY KEY (id_compania, id_sucursal, id_rol, id_permiso)
);
--rollback DROP TABLE IF EXISTS seguridad.rol_permisos;

--changeset gesyacelga:v1.0-018-seguridad-usuarios
--comment Usuarios internos del gym (admin, recepción, entrenadores)
CREATE TABLE seguridad.usuarios (
  id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania   INT          NOT NULL,
  id_sucursal   INT          NOT NULL,
  id_rol        INT          NOT NULL REFERENCES seguridad.roles(id),
  nombre        VARCHAR(100) NOT NULL,
  correo        VARCHAR(150) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  activo        BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (id_compania, correo)
);
--rollback DROP TABLE IF EXISTS seguridad.usuarios;

--changeset gesyacelga:v1.0-019-seguridad-bitacora-accesos
--comment Bitácora de todas las acciones realizadas por usuarios internos
CREATE TABLE seguridad.bitacora_accesos (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania INT          NOT NULL,
  id_sucursal INT          NOT NULL,
  id_usuario  INT          NOT NULL REFERENCES seguridad.usuarios(id),
  modulo      VARCHAR(50)  NOT NULL,
  accion      VARCHAR(100) NOT NULL,
  entidad_id  INT,
  detalle     JSONB,
  ip          VARCHAR(45),
  fecha       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bitacora_compania_fecha
  ON seguridad.bitacora_accesos(id_compania, fecha);
--rollback DROP INDEX IF EXISTS seguridad.idx_bitacora_compania_fecha;
--rollback DROP TABLE IF EXISTS seguridad.bitacora_accesos;
