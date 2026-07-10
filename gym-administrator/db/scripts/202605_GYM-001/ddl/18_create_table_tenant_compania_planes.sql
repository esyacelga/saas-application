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
                            'suspendido','cancelado','programado','reemplazada'
                          )),
  tipo_cambio           VARCHAR(20)   NOT NULL
                          CHECK (tipo_cambio IN (
                            'nuevo','renovacion','upgrade','downgrade',
                            'degradacion_auto','cancelacion','suspension'
                          )),
  -- Modo SOBRE_LIMITE y trazabilidad de degradación (REQ-SAAS-001, RN-06/RN-03)
  sobre_limite          BOOLEAN       NOT NULL DEFAULT FALSE,
  sobre_limite_hasta    DATE,
  causa_degradacion     VARCHAR(30)
                          CHECK (
                            causa_degradacion IS NULL
                            OR causa_degradacion IN (
                              'vencimiento','pago_rechazado',
                              'cancelacion_manual','suspension_root'
                            )
                          ),
  id_compania_plan_orig INT           REFERENCES tenant.compania_planes(id),
  credito_monto         DECIMAL(10,2) NOT NULL DEFAULT 0,
  eliminado             BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario      VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha        TIMESTAMPTZ,
  modifica_usuario      VARCHAR(150)
);

COMMENT ON COLUMN tenant.compania_planes.sobre_limite       IS 'TRUE cuando la suscripción activa tiene más recursos que los que permite el plan actual (RN-06)';
COMMENT ON COLUMN tenant.compania_planes.sobre_limite_hasta IS 'Fecha límite de gracia (30 días) para reducir los recursos excedidos';
COMMENT ON COLUMN tenant.compania_planes.causa_degradacion  IS 'Causa de la última degradación automática: vencimiento / pago_rechazado / cancelacion_manual / suspension_root';
