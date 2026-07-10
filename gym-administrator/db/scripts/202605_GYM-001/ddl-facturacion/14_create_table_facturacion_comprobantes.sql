CREATE TABLE facturacion.comprobantes (
  id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania               INT           NOT NULL,
  id_sucursal               INT           NOT NULL,
  tipo_comprobante          CHAR(2)       NOT NULL REFERENCES sri.tipos_comprobante(codigo),
  clave_acceso              CHAR(49)      NOT NULL,
  numero_autorizacion       CHAR(49),
  cod_establecimiento       CHAR(3)       NOT NULL,
  cod_punto_emision         CHAR(3)       NOT NULL,
  secuencial                CHAR(9)       NOT NULL,
  fecha_emision             DATE          NOT NULL,
  ambiente                  CHAR(1)       NOT NULL,
  -- Receptor
  tipo_id_receptor          CHAR(2)       NOT NULL REFERENCES sri.tipos_identificacion_comprador(codigo),
  id_receptor               VARCHAR(20)   NOT NULL,
  razon_social_receptor     VARCHAR(300)  NOT NULL,
  email_receptor            VARCHAR(150),
  direccion_receptor        VARCHAR(300),
  telefono_receptor         VARCHAR(20),
  -- Totales denormalizados
  subtotal_sin_impuesto     DECIMAL(14,2) NOT NULL DEFAULT 0,
  subtotal_iva_0            DECIMAL(14,2) NOT NULL DEFAULT 0,
  subtotal_no_objeto_iva    DECIMAL(14,2) NOT NULL DEFAULT 0,
  subtotal_exento_iva       DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_descuento           DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_ice                 DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_iva                 DECIMAL(14,2) NOT NULL DEFAULT 0,
  propina                   DECIMAL(14,2) NOT NULL DEFAULT 0,
  total                     DECIMAL(14,2) NOT NULL,
  moneda                    VARCHAR(10)   NOT NULL DEFAULT 'DOLAR',
  -- Origen
  id_membresia              INT,
  id_venta                  INT,
  id_comprobante_ref        BIGINT        REFERENCES facturacion.comprobantes(id),
  -- Estado ciclo de vida
  estado                    VARCHAR(20)   NOT NULL DEFAULT 'GENERADO',
  fecha_autorizacion        TIMESTAMPTZ,
  -- Almacenamiento
  xml_firmado_path          VARCHAR(500),
  xml_autorizado_path       VARCHAR(500),
  ride_pdf_path             VARCHAR(500),
  -- Auditoría
  id_usuario_registro       INT,
  created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_facturacion_comprobantes_clave     UNIQUE (clave_acceso),
  CONSTRAINT uq_facturacion_comprobantes_numero    UNIQUE (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, secuencial, tipo_comprobante),
  CONSTRAINT chk_comprobantes_estado CHECK (estado IN ('GENERADO','FIRMADO','ENVIADO','ERROR','RECIBIDO','AUTORIZADO','NO_AUTORIZADO','DEVUELTO','ANULADO')),
  CONSTRAINT chk_comprobantes_ambiente CHECK (ambiente IN ('1','2'))
);

COMMENT ON COLUMN facturacion.comprobantes.secuencial          IS 'Formato de 9 dígitos: 000000001';
COMMENT ON COLUMN facturacion.comprobantes.id_comprobante_ref  IS 'Referencia al comprobante origen para notas de crédito/débito';
COMMENT ON COLUMN facturacion.comprobantes.estado              IS 'GENERADO|FIRMADO|ENVIADO|ERROR|RECIBIDO|AUTORIZADO|NO_AUTORIZADO|DEVUELTO|ANULADO';
COMMENT ON COLUMN facturacion.comprobantes.ambiente            IS '1=pruebas, 2=produccion';
