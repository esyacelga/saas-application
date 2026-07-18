CREATE TABLE core.membresias (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_cliente          INT           NOT NULL REFERENCES core.clientes(id),
  id_tipo_membresia   INT           NOT NULL REFERENCES core.tipos_membresia(id),
  id_metodo_pago      INT,
  id_usuario_registro INT,
  fecha_inicio        DATE,
  fecha_fin           DATE,
  estado_pago         VARCHAR(20)   NOT NULL DEFAULT 'PAGADO'
                        CHECK (estado_pago IN ('PENDIENTE','PAGADO')),
  dias_acceso_total   INT,
  precio_pagado       DECIMAL(10,2) NOT NULL,
  descuento_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0,
  estado              VARCHAR(20)   NOT NULL DEFAULT 'activa'
                        CHECK (estado IN ('activa','vencida','congelada','anulada')),
  asistencias_previas INT           NOT NULL DEFAULT 0,
  eliminado           BOOLEAN       NOT NULL DEFAULT FALSE,
  fecha_eliminacion   TIMESTAMPTZ,
  eliminado_por       INT,
  motivo_eliminacion  VARCHAR(30)
                        CHECK (motivo_eliminacion IN (
                          'SOCIO_CAMBIO_OPINION','ERROR_DE_VENTA','DUPLICADA','DATOS_INCORRECTOS','OTRO'
                        )),
  creacion_fecha      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario    VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha      TIMESTAMPTZ,
  modifica_usuario    VARCHAR(150),
  CONSTRAINT ck_membresias_fechas_por_estado_pago CHECK (
    (estado_pago = 'PENDIENTE' AND fecha_inicio IS NULL AND fecha_fin IS NULL)
    OR
    (estado_pago = 'PAGADO' AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL)
  ),
  CONSTRAINT ck_membresias_motivo_si_eliminado CHECK (
    (eliminado = FALSE AND motivo_eliminacion IS NULL AND fecha_eliminacion IS NULL AND eliminado_por IS NULL)
    OR
    (eliminado = TRUE AND motivo_eliminacion IS NOT NULL AND fecha_eliminacion IS NOT NULL AND eliminado_por IS NOT NULL)
  )
);
