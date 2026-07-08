--liquibase formatted sql

--changeset gesyacelga:v1.0-007-tenant-companias
--comment Compañías (gimnasios) registrados en la plataforma
CREATE TABLE tenant.companias (
  id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre     VARCHAR(150) NOT NULL,
  ruc        VARCHAR(20)  NOT NULL UNIQUE,
  logo_url   VARCHAR(255),
  telefono   VARCHAR(20),
  whatsapp   VARCHAR(20),
  correo     VARCHAR(150),
  activo     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS tenant.companias;

--changeset gesyacelga:v1.0-008-tenant-sucursales
--comment Sedes físicas de cada compañía (FK real a companias)
CREATE TABLE tenant.sucursales (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL REFERENCES tenant.companias(id),
  nombre       VARCHAR(150) NOT NULL,
  direccion    VARCHAR(255),
  es_principal BOOLEAN      NOT NULL DEFAULT FALSE,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS tenant.sucursales;

--changeset gesyacelga:v1.0-009-tenant-compania-planes
--comment Suscripciones de cada compañía a un plan SaaS
CREATE TABLE tenant.compania_planes (
  id                    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania           INT           NOT NULL REFERENCES tenant.companias(id),
  id_plan               INT           NOT NULL REFERENCES saas.planes(id),
  fecha_inicio          DATE          NOT NULL,
  fecha_fin             DATE          NOT NULL,
  dias_gracia           INT           NOT NULL DEFAULT 5,
  fecha_ultimo_pago     DATE,
  motivo_suspension     TEXT,
  estado                VARCHAR(20)   NOT NULL
                          CHECK (estado IN (
                            'activo','en_gracia','vencido',
                            'suspendido','cancelado','programado'
                          )),
  tipo_cambio           VARCHAR(20)   NOT NULL
                          CHECK (tipo_cambio IN (
                            'nuevo','renovacion','upgrade','downgrade'
                          )),
  id_compania_plan_orig INT           REFERENCES tenant.compania_planes(id),
  credito_monto         DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS tenant.compania_planes;

--changeset gesyacelga:v1.0-010-tenant-pagos-suscripcion
--comment Registro de pagos de suscripción por cada período
CREATE TABLE tenant.pagos_suscripcion (
  id                INT           PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT           NOT NULL REFERENCES tenant.compania_planes(id),
  monto             DECIMAL(10,2) NOT NULL,
  fecha_pago        DATE          NOT NULL,
  periodo_desde     DATE,
  periodo_hasta     DATE,
  metodo_pago       VARCHAR(30)
                      CHECK (metodo_pago IN ('efectivo','transferencia','tarjeta')),
  tipo_pago         VARCHAR(30)
                      CHECK (tipo_pago IN (
                        'pago_completo','diferencia_upgrade',
                        'credito_downgrade','renovacion'
                      )),
  estado            VARCHAR(20)   NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pagado','fallido','pendiente')),
  referencia        VARCHAR(100),
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
--rollback DROP TABLE IF EXISTS tenant.pagos_suscripcion;

--changeset gesyacelga:v1.0-011-tenant-config-notif-suscripcion
--comment Umbrales de notificación de vencimiento configurables por compañía
CREATE TABLE tenant.config_notif_suscripcion (
  id_compania  INT         NOT NULL,
  dias_antes   INT         NOT NULL,
  canal        VARCHAR(20) NOT NULL
                 CHECK (canal IN ('email','whatsapp','ambos')),
  activo       BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_compania, dias_antes)
);
--rollback DROP TABLE IF EXISTS tenant.config_notif_suscripcion;

--changeset gesyacelga:v1.0-012-tenant-notificaciones-suscripcion
--comment Log de notificaciones de vencimiento enviadas
CREATE TABLE tenant.notificaciones_suscripcion (
  id                INT         PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  id_compania_plan  INT         NOT NULL REFERENCES tenant.compania_planes(id),
  dias_antes        INT         NOT NULL,
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('email','whatsapp')),
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('enviado','fallido')),
  fecha_envio       TIMESTAMPTZ
);
--rollback DROP TABLE IF EXISTS tenant.notificaciones_suscripcion;
