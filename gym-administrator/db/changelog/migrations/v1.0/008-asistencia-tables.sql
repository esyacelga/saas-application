--liquibase formatted sql

--changeset gesyacelga:v1.0-024-asistencia-asistencias
--comment Registro de entradas por QR o manual; índice para consultas por cliente/fecha
CREATE TABLE asistencia.asistencias (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT         NOT NULL,
  id_sucursal      INT         NOT NULL,
  id_cliente       INT         NOT NULL REFERENCES core.clientes(id),
  id_membresia     INT         NOT NULL REFERENCES core.membresias(id),
  fecha            DATE        NOT NULL,
  hora_entrada     TIME        NOT NULL,
  metodo_registro  VARCHAR(20) NOT NULL DEFAULT 'qr'
                     CHECK (metodo_registro IN ('qr','manual'))
);

CREATE INDEX idx_asistencias_cliente_fecha
  ON asistencia.asistencias(id_compania, id_cliente, fecha);
--rollback DROP INDEX IF EXISTS asistencia.idx_asistencias_cliente_fecha;
--rollback DROP TABLE IF EXISTS asistencia.asistencias;

--changeset gesyacelga:v1.0-025-asistencia-plantillas-mensajes
--comment Plantillas de mensajes automáticos por WhatsApp/email
CREATE TABLE asistencia.plantillas_mensajes (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania  INT          NOT NULL,
  id_sucursal  INT          NOT NULL,
  tipo         VARCHAR(50)  NOT NULL
                 CHECK (tipo IN (
                   'motivacional','ausencia_2d',
                   'recuperacion_5d','recuperacion_10d','recuperacion_15d',
                   'vencimiento_3d','vencimiento_hoy'
                 )),
  nombre       VARCHAR(100) NOT NULL,
  contenido    TEXT         NOT NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
--rollback DROP TABLE IF EXISTS asistencia.plantillas_mensajes;

--changeset gesyacelga:v1.0-026-asistencia-mensajes-log
--comment Log de todos los mensajes enviados o pendientes a clientes
CREATE TABLE asistencia.mensajes_log (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania       INT         NOT NULL,
  id_sucursal       INT         NOT NULL,
  id_cliente        INT         NOT NULL REFERENCES core.clientes(id),
  id_plantilla      INT         REFERENCES asistencia.plantillas_mensajes(id),
  tipo              VARCHAR(50) NOT NULL,
  canal             VARCHAR(20) NOT NULL
                      CHECK (canal IN ('whatsapp','email','llamada')),
  contenido         TEXT        NOT NULL,
  estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente','enviado','fallido')),
  fecha_programada  TIMESTAMPTZ,
  fecha_envio       TIMESTAMPTZ,
  id_usuario_envio  INT
);
--rollback DROP TABLE IF EXISTS asistencia.mensajes_log;
