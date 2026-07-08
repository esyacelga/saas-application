CREATE TABLE core.membresias (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania         INT           NOT NULL,
  id_sucursal         INT           NOT NULL,
  id_cliente          INT           NOT NULL REFERENCES core.clientes(id),
  id_tipo_membresia   INT           NOT NULL REFERENCES core.tipos_membresia(id),
  id_metodo_pago      INT,
  id_usuario_registro INT,
  fecha_inicio        DATE          NOT NULL,
  fecha_fin           DATE          NOT NULL,
  dias_acceso_total   INT,
  precio_pagado       DECIMAL(10,2) NOT NULL,
  descuento_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0,
  estado              VARCHAR(20)   NOT NULL DEFAULT 'activa'
                        CHECK (estado IN ('activa','vencida','congelada','anulada')),
  asistencias_previas INT           NOT NULL DEFAULT 0,
  eliminado           BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario    VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha      TIMESTAMPTZ,
  modifica_usuario    VARCHAR(150)
);
