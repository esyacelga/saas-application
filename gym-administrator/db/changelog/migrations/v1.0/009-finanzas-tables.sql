--liquibase formatted sql

--changeset gesyacelga:v1.0-027-finanzas-categorias-ingreso
--comment Categorías de ingresos configurables por sucursal
CREATE TABLE finanzas.categorias_ingreso (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS finanzas.categorias_ingreso;

--changeset gesyacelga:v1.0-028-finanzas-ingresos
--comment Registro de ingresos; puede venir de membresía o venta de inventario
CREATE TABLE finanzas.ingresos (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES finanzas.categorias_ingreso(id),
  id_membresia        INT,
  id_venta            INT,
  monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0),
  descripcion         TEXT,
  fecha               DATE          NOT NULL DEFAULT CURRENT_DATE,
  id_usuario_registro INT,
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS finanzas.ingresos;

--changeset gesyacelga:v1.0-029-finanzas-categorias-egreso
--comment Categorías de egresos configurables por sucursal
CREATE TABLE finanzas.categorias_egreso (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  nombre       VARCHAR(100) NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS finanzas.categorias_egreso;

--changeset gesyacelga:v1.0-030-finanzas-egresos
--comment Registro de egresos operativos del gym
CREATE TABLE finanzas.egresos (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_categoria        INT           NOT NULL REFERENCES finanzas.categorias_egreso(id),
  monto               DECIMAL(10,2) NOT NULL CHECK (monto > 0),
  descripcion         TEXT,
  fecha               DATE          NOT NULL DEFAULT CURRENT_DATE,
  id_usuario_registro INT,
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS finanzas.egresos;
