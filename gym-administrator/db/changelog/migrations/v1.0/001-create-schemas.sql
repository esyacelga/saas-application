--liquibase formatted sql

--changeset gesyacelga:v1.0-001-create-schemas
--comment Crea los 10 esquemas del sistema gym-administrator
CREATE SCHEMA IF NOT EXISTS saas;
CREATE SCHEMA IF NOT EXISTS identidad;
CREATE SCHEMA IF NOT EXISTS tenant;
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS asistencia;
CREATE SCHEMA IF NOT EXISTS finanzas;
CREATE SCHEMA IF NOT EXISTS marketing;
CREATE SCHEMA IF NOT EXISTS seguridad;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS inventario;
--rollback DROP SCHEMA IF EXISTS inventario CASCADE;
--rollback DROP SCHEMA IF EXISTS config CASCADE;
--rollback DROP SCHEMA IF EXISTS seguridad CASCADE;
--rollback DROP SCHEMA IF EXISTS marketing CASCADE;
--rollback DROP SCHEMA IF EXISTS finanzas CASCADE;
--rollback DROP SCHEMA IF EXISTS asistencia CASCADE;
--rollback DROP SCHEMA IF EXISTS core CASCADE;
--rollback DROP SCHEMA IF EXISTS tenant CASCADE;
--rollback DROP SCHEMA IF EXISTS identidad CASCADE;
--rollback DROP SCHEMA IF EXISTS saas CASCADE;
