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
  eliminado         BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150)
);
