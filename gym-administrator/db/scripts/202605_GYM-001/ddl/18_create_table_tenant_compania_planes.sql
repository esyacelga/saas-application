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
  eliminado             BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario      VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha        TIMESTAMPTZ,
  modifica_usuario      VARCHAR(150)
);
