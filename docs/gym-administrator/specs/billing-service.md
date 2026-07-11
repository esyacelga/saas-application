# Módulo de Facturación Electrónica — Ecuador SRI

> **ESTADO:** 📋 Planeado — sin implementar. Este servicio NO existe en el código todavía; es una especificación de diseño futura. Ver [../../STATUS.md](../../STATUS.md).

> **Proyecto:** Gym Administrator — Plataforma SaaS de gestión de gimnasios
> **Regulador:** SRI (Servicio de Rentas Internas del Ecuador)
> **Servicio #8:** `billing-service` (Plan Premium o add-on)
> **Documento:** Análisis de factibilidad + Especificación técnica refinada
> **Julio 2026**

---

## 1. Resumen ejecutivo

### 1.1 Veredicto: Altamente factible ✓

La plataforma ya tiene los cimientos que exige el SRI:

| Requisito SRI | Dónde está en el proyecto |
|---|---|
| RUC del emisor | `tenant.companias.ruc` |
| Cédula/RUC del receptor | `identidad.personas.ci` |
| Monto de la transacción | `core.membresias.precio_pagado` / `inventario.ventas.total` |
| Multi-establecimiento | `tenant.sucursales` (1 sucursal → 1 establecimiento SRI) |
| Segregación multi-tenant | Patrón `(id_compania, id_sucursal)` ya adoptado en todo el proyecto |

### 1.2 Cambio normativo crítico

Desde el **1 de enero de 2026** los comprobantes deben enviarse al SRI **en tiempo real**. Ya no hay plazo de 4 días. El servicio debe emitir de forma síncrona (con timeout controlado) o con cola de reintentos muy rápida.

### 1.3 Alcance del MVP

Solo dos tipos de comprobante:
- **Factura (`01`)** — venta de membresía + venta de producto de tienda
- **Nota de Crédito (`04`)** — anulación/reembolso de membresía o venta

Excluidos del MVP (pueden implementarse en fase 2):
- Nota de Débito (`05`)
- Comprobante de Retención (`07`)
- Guía de Remisión (`06`) — no aplica (sin transporte)
- Liquidación de Compra (`03`) — no aplica

---

## 2. Convenciones de diseño

### 2.1 Regla dorada del proyecto

> **Toda tabla de negocio del tenant debe declarar `id_compania` e `id_sucursal` como primer par de columnas después del PK.**

Esto es la manera de administrar la SaaS: cada gimnasio (compañía) opera con sus sucursales, y el aislamiento se enforza a nivel de aplicación filtrando por ese par. Esta convención ya está aplicada en `config.*`, `seguridad.*`, `core.*`, `finanzas.*`, `marketing.*` e `inventario.*`.

**Excepciones válidas (esquemas globales):**
- `saas.*` — catálogo de planes y features (compartido por todos los tenants)
- `identidad.*` — personas globales (una persona puede ser cliente en varios gyms)
- **`sri.*`** (nuevo) — catálogos regulatorios del SRI, iguales para todos los emisores del Ecuador

### 2.2 Dos schemas nuevos

```
sri                → Catálogos oficiales del SRI (global, sin id_compania)
facturacion        → Datos operativos del tenant (id_compania + id_sucursal siempre)
```

Separar `sri.*` de `facturacion.*` evita duplicar datos regulatorios idénticos entre gimnasios y facilita actualizar códigos cuando el SRI cambia (por ejemplo, si aparece un nuevo `codigoPorcentaje` de IVA).

---

## 3. Schema `sri` — Catálogos globales del SRI

Estas tablas contienen códigos oficiales del SRI. Son idénticas para todos los emisores del país. **Se poblan una sola vez desde seed data** y solo cambian cuando el SRI actualiza su ficha técnica.

### 3.1 Tabla — `sri.tipos_comprobante`

```sql
CREATE TABLE sri.tipos_comprobante (
    codigo      CHAR(2)      NOT NULL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    version_xsd VARCHAR(10)  NOT NULL,          -- '2.1.0' factura, '1.1.0' NC, etc.
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed
INSERT INTO sri.tipos_comprobante VALUES
  ('01','FACTURA','2.1.0',TRUE),
  ('03','LIQUIDACION_COMPRA','1.1.0',TRUE),
  ('04','NOTA_CREDITO','1.1.0',TRUE),
  ('05','NOTA_DEBITO','1.0.0',TRUE),
  ('06','GUIA_REMISION','1.1.0',TRUE),
  ('07','COMPROBANTE_RETENCION','2.0.0',TRUE);
```

### 3.2 Tabla — `sri.tipos_identificacion_comprador`

```sql
CREATE TABLE sri.tipos_identificacion_comprador (
    codigo      CHAR(2)      NOT NULL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed
INSERT INTO sri.tipos_identificacion_comprador VALUES
  ('04','RUC',TRUE),
  ('05','CEDULA',TRUE),
  ('06','PASAPORTE',TRUE),
  ('07','CONSUMIDOR_FINAL',TRUE),
  ('08','ID_EXTERIOR',TRUE);
```

### 3.3 Tabla — `sri.formas_pago`

```sql
CREATE TABLE sri.formas_pago (
    codigo  VARCHAR(4)   NOT NULL PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL,
    activo  BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed (extracto de códigos SRI comunes)
INSERT INTO sri.formas_pago VALUES
  ('01','SIN_UTILIZACION_SISTEMA_FINANCIERO',TRUE),  -- efectivo
  ('15','COMPENSACION_DEUDAS',TRUE),
  ('16','TARJETA_DEBITO',TRUE),
  ('17','DINERO_ELECTRONICO',TRUE),
  ('18','TARJETA_PREPAGO',TRUE),
  ('19','TARJETA_CREDITO',TRUE),
  ('20','OTROS_SF',TRUE),
  ('21','ENDOSO_TITULOS',TRUE);
```

### 3.4 Tabla — `sri.tipos_impuesto`

```sql
CREATE TABLE sri.tipos_impuesto (
    codigo      CHAR(1)      NOT NULL PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed
INSERT INTO sri.tipos_impuesto VALUES
  ('2','IVA',TRUE),
  ('3','ICE',TRUE),
  ('5','IRBPNR',TRUE);
```

### 3.5 Tabla — `sri.tarifas_iva`

```sql
CREATE TABLE sri.tarifas_iva (
    codigo_porcentaje CHAR(1)      NOT NULL PRIMARY KEY,
    porcentaje        DECIMAL(5,2) NOT NULL,      -- valor real de la tarifa
    nombre            VARCHAR(50)  NOT NULL,
    vigente_desde     DATE         NOT NULL,
    vigente_hasta     DATE,                        -- NULL = vigente
    activo            BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed (relevantes al 2026)
INSERT INTO sri.tarifas_iva VALUES
  ('0', 0.00,  'IVA_0',            '2000-01-01', NULL,        TRUE),
  ('2', 12.00, 'IVA_12',           '2000-01-01', '2024-03-31',TRUE),
  ('3', 14.00, 'IVA_14',           '2016-06-01', '2017-05-31',TRUE),
  ('4', 15.00, 'IVA_15',           '2024-04-01', NULL,        TRUE),
  ('6', 0.00,  'NO_OBJETO_IMP',    '2000-01-01', NULL,        TRUE),
  ('7', 0.00,  'EXENTO_IVA',       '2000-01-01', NULL,        TRUE),
  ('8', 8.00,  'IVA_8_FERIADO',    '2024-01-01', NULL,        TRUE);  -- feriados turísticos
```

### 3.6 Tabla — `sri.motivos_anulacion_nc`

```sql
CREATE TABLE sri.motivos_anulacion_nc (
    id              SERIAL       PRIMARY KEY,
    motivo          VARCHAR(200) NOT NULL,        -- '01' devolución, '02' descuento, etc.
    codigo_interno  VARCHAR(20)  NOT NULL UNIQUE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);
```

---

## 4. Schema `facturacion` — Tenant (id_compania + id_sucursal en todas las tablas)

### 4.1 Tabla — `facturacion.config_sri`

Configuración de facturación electrónica por sucursal. Cada sucursal opera como un establecimiento SRI diferente.

```sql
CREATE TABLE facturacion.config_sri (
    id_compania             INT          NOT NULL,
    id_sucursal             INT          NOT NULL,
    -- Datos fiscales del emisor
    razon_social            VARCHAR(300) NOT NULL,
    nombre_comercial        VARCHAR(300),
    dir_matriz              VARCHAR(300) NOT NULL,
    dir_establecimiento     VARCHAR(300) NOT NULL,
    -- Régimen tributario
    obligado_contabilidad   BOOLEAN      NOT NULL DEFAULT FALSE,
    contribuyente_especial  VARCHAR(10),
    agente_retencion        VARCHAR(10),                        -- N° resolución agente retención
    regimen_rimpe           VARCHAR(30),                        -- 'NEGOCIO_POPULAR' | 'EMPRENDEDOR' | NULL
    -- Ambiente y activación
    ambiente                CHAR(1)      NOT NULL DEFAULT '1',  -- '1'=pruebas, '2'=producción
    tipo_emision            CHAR(1)      NOT NULL DEFAULT '1',  -- '1'=normal offline
    facturacion_activa      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Datos contacto para SRI
    correo_notificacion     VARCHAR(150),
    -- Auditoría
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by              INT,
    PRIMARY KEY (id_compania, id_sucursal),
    CONSTRAINT chk_ambiente CHECK (ambiente IN ('1','2')),
    CONSTRAINT chk_rimpe    CHECK (regimen_rimpe IS NULL
                                   OR regimen_rimpe IN ('NEGOCIO_POPULAR','EMPRENDEDOR'))
);
```

### 4.2 Tabla — `facturacion.certificados`

Certificado digital P12 por sucursal. El P12 se almacena cifrado (AES-256 con clave maestra en Azure Key Vault, mecanismo ya usado por el proyecto).

```sql
CREATE TABLE facturacion.certificados (
    id                  SERIAL       NOT NULL PRIMARY KEY,
    id_compania         INT          NOT NULL,
    id_sucursal         INT          NOT NULL,
    alias               VARCHAR(100) NOT NULL,
    entidad_emisora     VARCHAR(50)  NOT NULL,    -- 'BCE' | 'SECURITY_DATA' | 'ANF'
    -- P12 encapsulado (cifrado en reposo)
    p12_cifrado         BYTEA        NOT NULL,
    password_cifrado    BYTEA        NOT NULL,    -- password del P12 cifrado con la misma clave maestra
    kms_key_id          VARCHAR(100) NOT NULL,    -- referencia a la clave en Azure Key Vault
    -- Metadata del certificado
    subject_cn          VARCHAR(200) NOT NULL,    -- CN=... del certificado
    ruc_certificado     VARCHAR(13)  NOT NULL,    -- debe coincidir con tenant.companias.ruc
    fecha_emision       DATE         NOT NULL,
    fecha_vencimiento   DATE         NOT NULL,
    -- Estado
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    revocado            BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_revocacion   TEXT,
    -- Auditoría
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          INT
);
CREATE INDEX idx_certificados_compania_sucursal_activo
    ON facturacion.certificados (id_compania, id_sucursal, activo)
    WHERE activo = TRUE;
```

### 4.3 Tabla — `facturacion.puntos_emision`

Un establecimiento (sucursal) puede tener varios puntos de emisión (por ejemplo: recepción principal + cafetería + tienda).

```sql
CREATE TABLE facturacion.puntos_emision (
    id                  SERIAL       NOT NULL PRIMARY KEY,
    id_compania         INT          NOT NULL,
    id_sucursal         INT          NOT NULL,
    cod_establecimiento CHAR(3)      NOT NULL,       -- '001', '002', ...
    cod_punto_emision   CHAR(3)      NOT NULL,       -- '001', '002', ...
    descripcion         VARCHAR(100),                -- 'Caja Recepción', 'POS Tienda'
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision)
);
```

### 4.4 Tabla — `facturacion.secuenciales`

Contador atómico de secuenciales por (sucursal, punto de emisión, tipo de comprobante). Los secuenciales **nunca deben repetirse ni saltarse dentro del mismo tipo**.

```sql
CREATE TABLE facturacion.secuenciales (
    id_compania         INT     NOT NULL,
    id_sucursal         INT     NOT NULL,
    cod_establecimiento CHAR(3) NOT NULL,
    cod_punto_emision   CHAR(3) NOT NULL,
    tipo_comprobante    CHAR(2) NOT NULL REFERENCES sri.tipos_comprobante(codigo),
    ultimo_secuencial   INT     NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, tipo_comprobante)
);

-- Función atómica para reservar el próximo secuencial (evita race conditions)
CREATE OR REPLACE FUNCTION facturacion.next_secuencial(
    p_id_compania INT, p_id_sucursal INT,
    p_estab CHAR(3), p_pto CHAR(3), p_tipo CHAR(2)
) RETURNS INT AS $$
DECLARE v_next INT;
BEGIN
    UPDATE facturacion.secuenciales
       SET ultimo_secuencial = ultimo_secuencial + 1,
           updated_at = NOW()
     WHERE id_compania = p_id_compania
       AND id_sucursal = p_id_sucursal
       AND cod_establecimiento = p_estab
       AND cod_punto_emision = p_pto
       AND tipo_comprobante = p_tipo
    RETURNING ultimo_secuencial INTO v_next;
    RETURN v_next;
END; $$ LANGUAGE plpgsql;
```

### 4.5 Tabla — `facturacion.comprobantes` (cabecera)

Almacena el comprobante emitido. **No guarda impuestos ni pagos ni info adicional en columnas** — todo va a tablas hijas.

```sql
CREATE TABLE facturacion.comprobantes (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania             INT         NOT NULL,
    id_sucursal             INT         NOT NULL,
    -- Identificación del comprobante
    tipo_comprobante        CHAR(2)     NOT NULL REFERENCES sri.tipos_comprobante(codigo),
    clave_acceso            CHAR(49)    NOT NULL UNIQUE,
    numero_autorizacion     CHAR(49),                    -- igual a clave_acceso cuando autorizado
    cod_establecimiento     CHAR(3)     NOT NULL,
    cod_punto_emision       CHAR(3)     NOT NULL,
    secuencial              INT         NOT NULL,
    fecha_emision           DATE        NOT NULL,
    ambiente                CHAR(1)     NOT NULL,        -- snapshot del ambiente al emitir
    -- Receptor
    tipo_id_receptor        CHAR(2)     NOT NULL REFERENCES sri.tipos_identificacion_comprador(codigo),
    id_receptor             VARCHAR(20) NOT NULL,
    razon_social_receptor   VARCHAR(300) NOT NULL,
    email_receptor          VARCHAR(150),
    direccion_receptor      VARCHAR(300),
    telefono_receptor       VARCHAR(30),
    -- Totales (denormalizados para reportería rápida)
    subtotal_sin_impuesto   DECIMAL(14,2) NOT NULL,
    subtotal_iva_0          DECIMAL(14,2) NOT NULL DEFAULT 0,
    subtotal_no_objeto_iva  DECIMAL(14,2) NOT NULL DEFAULT 0,
    subtotal_exento_iva     DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_descuento         DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_ice               DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_iva               DECIMAL(14,2) NOT NULL DEFAULT 0,
    propina                 DECIMAL(14,2) NOT NULL DEFAULT 0,
    total                   DECIMAL(14,2) NOT NULL,
    moneda                  VARCHAR(15)   NOT NULL DEFAULT 'DOLAR',
    -- Origen (nullable — puede ser emisión manual)
    id_membresia            INT,
    id_venta                INT,
    id_comprobante_ref      BIGINT REFERENCES facturacion.comprobantes(id), -- para NC/ND
    -- Estado del ciclo de vida SRI
    estado                  VARCHAR(20) NOT NULL DEFAULT 'GENERADO',
    -- 'GENERADO' | 'FIRMADO' | 'ENVIADO' | 'RECIBIDO' | 'AUTORIZADO'
    -- | 'NO_AUTORIZADO' | 'DEVUELTO' | 'ANULADO' | 'ERROR'
    mensaje_sri             TEXT,
    fecha_envio             TIMESTAMPTZ,
    fecha_autorizacion      TIMESTAMPTZ,
    -- Documentos generados (URLs a blob storage o inline)
    xml_firmado_url         VARCHAR(500),
    xml_autorizado_url      VARCHAR(500),
    ride_pdf_url            VARCHAR(500),
    -- Auditoría
    id_usuario_registro     INT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_estado CHECK (estado IN (
        'GENERADO','FIRMADO','ENVIADO','RECIBIDO','AUTORIZADO',
        'NO_AUTORIZADO','DEVUELTO','ANULADO','ERROR'
    )),
    CONSTRAINT chk_totales_positivos CHECK (total >= 0 AND subtotal_sin_impuesto >= 0)
);

-- Índices
CREATE INDEX idx_comprobantes_compania_sucursal_fecha
    ON facturacion.comprobantes (id_compania, id_sucursal, fecha_emision DESC);
CREATE INDEX idx_comprobantes_pendientes
    ON facturacion.comprobantes (id_compania, id_sucursal, estado, created_at)
    WHERE estado NOT IN ('AUTORIZADO','ANULADO');
CREATE INDEX idx_comprobantes_membresia
    ON facturacion.comprobantes (id_membresia) WHERE id_membresia IS NOT NULL;
CREATE INDEX idx_comprobantes_venta
    ON facturacion.comprobantes (id_venta) WHERE id_venta IS NOT NULL;
```

### 4.6 Tabla — `facturacion.comprobantes_detalle`

Cada línea del comprobante (`<detalle>` en el XML).

```sql
CREATE TABLE facturacion.comprobantes_detalle (
    id                          BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_comprobante              BIGINT         NOT NULL
                                               REFERENCES facturacion.comprobantes(id)
                                               ON DELETE CASCADE,
    id_compania                 INT            NOT NULL,
    id_sucursal                 INT            NOT NULL,
    codigo_principal             VARCHAR(25)   NOT NULL,
    codigo_auxiliar              VARCHAR(25),
    descripcion                  VARCHAR(300)  NOT NULL,
    cantidad                     DECIMAL(18,6) NOT NULL,
    precio_unitario               DECIMAL(18,6) NOT NULL,
    descuento                    DECIMAL(18,2) NOT NULL DEFAULT 0,
    precio_total_sin_impuesto    DECIMAL(18,2) NOT NULL,          -- (cantidad * precio_unitario) - descuento
    orden                         SMALLINT      NOT NULL DEFAULT 1, -- 1, 2, 3... orden en el XML
    creado_en                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_comprobantes_detalle_orden UNIQUE (id_comprobante, orden),
    CONSTRAINT chk_comprobantes_detalle_cantidad_positiva CHECK (cantidad > 0),
    CONSTRAINT chk_comprobantes_detalle_precio_no_negativo CHECK (precio_unitario >= 0)
);
CREATE INDEX idx_comp_det_comprobante
    ON facturacion.comprobantes_detalle (id_comprobante);
CREATE INDEX idx_comp_det_empresa
    ON facturacion.comprobantes_detalle (id_compania, id_sucursal);
```

### 4.7 Tabla — `facturacion.comprobante_detalle_impuestos`

Impuestos aplicados a cada línea de detalle. Una línea puede tener varios impuestos (IVA + ICE, por ejemplo). En la mayoría de casos de gimnasio será solo IVA 15%.

```sql
CREATE TABLE facturacion.comprobante_detalle_impuestos (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania             INT         NOT NULL,
    id_sucursal             INT         NOT NULL,
    id_comprobante          BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id) ON DELETE CASCADE,
    id_comprobante_detalle  BIGINT      NOT NULL REFERENCES facturacion.comprobantes_detalle(id) ON DELETE CASCADE,
    codigo_impuesto         CHAR(1)     NOT NULL REFERENCES sri.tipos_impuesto(codigo),
    codigo_porcentaje       CHAR(1)     NOT NULL,                 -- ref a sri.tarifas_iva.codigo_porcentaje si IVA
    tarifa                  DECIMAL(5,2) NOT NULL,
    base_imponible          DECIMAL(14,2) NOT NULL,
    valor                   DECIMAL(14,2) NOT NULL
);
CREATE INDEX idx_detalle_impuestos_comprobante
    ON facturacion.comprobante_detalle_impuestos (id_comprobante);
```

### 4.8 Tabla — `facturacion.comprobante_impuestos_totales`

Resumen agregado de impuestos por comprobante (equivale al bloque `<totalConImpuestos>` del XML). Es una desnormalización que evita recalcular en cada consulta.

```sql
CREATE TABLE facturacion.comprobante_impuestos_totales (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id) ON DELETE CASCADE,
    codigo_impuesto     CHAR(1)     NOT NULL REFERENCES sri.tipos_impuesto(codigo),
    codigo_porcentaje   CHAR(1)     NOT NULL,
    tarifa              DECIMAL(5,2) NOT NULL,
    base_imponible      DECIMAL(14,2) NOT NULL,
    valor               DECIMAL(14,2) NOT NULL,
    UNIQUE (id_comprobante, codigo_impuesto, codigo_porcentaje)
);
```

### 4.9 Tabla — `facturacion.comprobante_pagos`

Formas de pago del comprobante (`<pagos>` en el XML). Una factura puede pagarse con varias formas (ej: parte efectivo + parte tarjeta).

```sql
CREATE TABLE facturacion.comprobante_pagos (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id) ON DELETE CASCADE,
    forma_pago          VARCHAR(4)  NOT NULL REFERENCES sri.formas_pago(codigo),
    total               DECIMAL(14,2) NOT NULL,
    plazo               INT,                          -- días de plazo (para créditos)
    unidad_tiempo       VARCHAR(20),                  -- 'dias', 'meses'
    id_metodo_pago_gym  INT                           -- ref a config.metodos_pago (nuestra tabla)
);
CREATE INDEX idx_comprobante_pagos_comprobante
    ON facturacion.comprobante_pagos (id_comprobante);
```

### 4.10 Tabla — `facturacion.comprobante_info_adicional`

Campos adicionales del comprobante (`<infoAdicional>` en el XML). Ejemplo: email del receptor, teléfono, ID cliente interno.

```sql
CREATE TABLE facturacion.comprobante_info_adicional (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id) ON DELETE CASCADE,
    nombre              VARCHAR(100) NOT NULL,
    valor               VARCHAR(300) NOT NULL,
    orden               INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_info_adicional_comprobante
    ON facturacion.comprobante_info_adicional (id_comprobante);
```

### 4.11 Tabla — `facturacion.notas_credito_referencias`

Referencias explícitas al documento original que la nota de crédito modifica.

```sql
CREATE TABLE facturacion.notas_credito_referencias (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania             INT         NOT NULL,
    id_sucursal             INT         NOT NULL,
    id_comprobante_nc       BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id) ON DELETE CASCADE,
    -- Documento original modificado
    cod_doc_modificado      CHAR(2)     NOT NULL,          -- '01' factura
    num_doc_modificado      VARCHAR(17) NOT NULL,          -- '001-001-000000001'
    fecha_emision_modif     DATE        NOT NULL,
    id_motivo_anulacion     INT         REFERENCES sri.motivos_anulacion_nc(id),
    razon                   VARCHAR(300) NOT NULL,
    valor_modificado        DECIMAL(14,2) NOT NULL
);
CREATE INDEX idx_nc_ref_comprobante_nc
    ON facturacion.notas_credito_referencias (id_comprobante_nc);
```

### 4.12 Tabla — `facturacion.envios_sri` (auditoría de llamadas SOAP)

Cada intento de comunicación con el SRI queda registrado para auditoría, diagnóstico y reproceso.

```sql
CREATE TABLE facturacion.envios_sri (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id),
    operacion           VARCHAR(20) NOT NULL,          -- 'RECEPCION' | 'AUTORIZACION'
    endpoint_url        VARCHAR(300) NOT NULL,
    request_soap        TEXT,                          -- truncado a N caracteres para no explotar tabla
    response_soap       TEXT,
    http_status         INT,
    duracion_ms         INT,
    exitoso             BOOLEAN     NOT NULL,
    estado_sri          VARCHAR(30),                   -- 'RECIBIDA','DEVUELTA','AUTORIZADO','NO AUTORIZADO'
    codigo_error        VARCHAR(20),
    mensaje_error       TEXT,
    intento_numero      INT         NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_envios_sri_comprobante
    ON facturacion.envios_sri (id_comprobante, created_at DESC);
CREATE INDEX idx_envios_sri_compania_fecha
    ON facturacion.envios_sri (id_compania, id_sucursal, created_at DESC);
```

### 4.13 Tabla — `facturacion.cola_envio`

Cola persistente para reintentos. Un job la procesa periódicamente para reenviar comprobantes en estado `ERROR` o `DEVUELTO`.

```sql
CREATE TABLE facturacion.cola_envio (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id),
    proxima_ejecucion   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    intentos            INT         NOT NULL DEFAULT 0,
    max_intentos        INT         NOT NULL DEFAULT 5,
    ultimo_error        TEXT,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    -- 'PENDIENTE' | 'PROCESANDO' | 'COMPLETADO' | 'FALLIDO_DEFINITIVO'
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cola_estado CHECK (estado IN
        ('PENDIENTE','PROCESANDO','COMPLETADO','FALLIDO_DEFINITIVO'))
);
CREATE INDEX idx_cola_envio_pendientes
    ON facturacion.cola_envio (proxima_ejecucion)
    WHERE estado = 'PENDIENTE';
```

### 4.14 Tabla — `facturacion.notificaciones_receptor`

Trazabilidad del envío del comprobante al email del receptor.

```sql
CREATE TABLE facturacion.notificaciones_receptor (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    id_comprobante      BIGINT      NOT NULL REFERENCES facturacion.comprobantes(id),
    canal               VARCHAR(20) NOT NULL,      -- 'EMAIL' | 'WHATSAPP'
    destinatario        VARCHAR(200) NOT NULL,
    asunto              VARCHAR(300),
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    -- 'PENDIENTE' | 'ENVIADO' | 'FALLIDO' | 'REBOTADO'
    proveedor_msg_id    VARCHAR(200),
    error_mensaje       TEXT,
    intentos            INT         NOT NULL DEFAULT 0,
    fecha_envio         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_receptor_comprobante
    ON facturacion.notificaciones_receptor (id_comprobante);
```

### 4.15 Tabla — `facturacion.anulaciones`

Registro de solicitudes de anulación de comprobantes ya autorizados. Regula el proceso de anulación con auditoría interna.

```sql
CREATE TABLE facturacion.anulaciones (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania             INT         NOT NULL,
    id_sucursal             INT         NOT NULL,
    id_comprobante          BIGINT      NOT NULL UNIQUE REFERENCES facturacion.comprobantes(id),
    motivo                  TEXT        NOT NULL,
    -- Nota de crédito emitida como parte de la anulación (si aplica)
    id_comprobante_nc       BIGINT REFERENCES facturacion.comprobantes(id),
    estado                  VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA',
    -- 'SOLICITADA' | 'APROBADA' | 'RECHAZADA' | 'EJECUTADA'
    id_usuario_solicita     INT         NOT NULL,
    id_usuario_aprueba      INT,
    fecha_solicitud         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_aprobacion        TIMESTAMPTZ,
    observacion_aprobacion  TEXT
);
CREATE INDEX idx_anulaciones_compania
    ON facturacion.anulaciones (id_compania, id_sucursal, fecha_solicitud DESC);
```

### 4.16 Tabla — `facturacion.reportes_ats`

Anexo Transaccional Simplificado (ATS) por período. Es una obligación mensual del contribuyente.

```sql
CREATE TABLE facturacion.reportes_ats (
    id                  BIGSERIAL   NOT NULL PRIMARY KEY,
    id_compania         INT         NOT NULL,
    id_sucursal         INT         NOT NULL,
    anio                INT         NOT NULL,
    mes                 INT         NOT NULL,
    fecha_generacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    xml_url             VARCHAR(500) NOT NULL,
    total_ventas        DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_iva           DECIMAL(14,2) NOT NULL DEFAULT 0,
    num_comprobantes    INT         NOT NULL DEFAULT 0,
    estado              VARCHAR(20) NOT NULL DEFAULT 'GENERADO',
    -- 'GENERADO' | 'PRESENTADO' | 'CORREGIDO'
    fecha_presentacion  DATE,
    id_usuario_genera   INT,
    UNIQUE (id_compania, id_sucursal, anio, mes),
    CONSTRAINT chk_mes CHECK (mes BETWEEN 1 AND 12)
);
```

### 4.17 Enlace con `finanzas.ingresos`

**Cambio en tabla existente:** agregar FK a `facturacion.comprobantes` para correlacionar el asiento contable con la factura emitida.

```sql
ALTER TABLE finanzas.ingresos
    ADD COLUMN id_comprobante BIGINT REFERENCES facturacion.comprobantes(id);

CREATE INDEX idx_ingresos_comprobante
    ON finanzas.ingresos (id_comprobante) WHERE id_comprobante IS NOT NULL;
```

Además, al `tenant.companias` conviene agregar campos fiscales que hoy no existen:

```sql
ALTER TABLE tenant.companias
    ADD COLUMN nombre_comercial      VARCHAR(300),
    ADD COLUMN dir_matriz            VARCHAR(300),
    ADD COLUMN obligado_contabilidad BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN contribuyente_especial VARCHAR(10);
```

---

## 5. Resumen de tablas del módulo

| Schema | Tabla | Alcance | id_compania | id_sucursal |
|---|---|---|:-:|:-:|
| `sri` | `tipos_comprobante` | Global | — | — |
| `sri` | `tipos_identificacion_comprador` | Global | — | — |
| `sri` | `formas_pago` | Global | — | — |
| `sri` | `tipos_impuesto` | Global | — | — |
| `sri` | `tarifas_iva` | Global | — | — |
| `sri` | `motivos_anulacion_nc` | Global | — | — |
| `facturacion` | `config_sri` | Tenant | ✓ | ✓ |
| `facturacion` | `certificados` | Tenant | ✓ | ✓ |
| `facturacion` | `puntos_emision` | Tenant | ✓ | ✓ |
| `facturacion` | `secuenciales` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobantes` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobantes_detalle` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobante_detalle_impuestos` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobante_impuestos_totales` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobante_pagos` | Tenant | ✓ | ✓ |
| `facturacion` | `comprobante_info_adicional` | Tenant | ✓ | ✓ |
| `facturacion` | `notas_credito_referencias` | Tenant | ✓ | ✓ |
| `facturacion` | `envios_sri` | Tenant | ✓ | ✓ |
| `facturacion` | `cola_envio` | Tenant | ✓ | ✓ |
| `facturacion` | `notificaciones_receptor` | Tenant | ✓ | ✓ |
| `facturacion` | `anulaciones` | Tenant | ✓ | ✓ |
| `facturacion` | `reportes_ats` | Tenant | ✓ | ✓ |

**Total:** 6 tablas globales `sri.*` + 16 tablas tenant `facturacion.*` = **22 tablas nuevas**.

---

## 6. Máquina de estados del comprobante

```
      [POST /facturas]
             │
             ▼
       ┌─────────────┐
       │  GENERADO   │  ← insert cabecera + detalles + impuestos
       └──────┬──────┘
              │ firma XAdES-BES OK
              ▼
       ┌─────────────┐
       │   FIRMADO   │  ← xml_firmado_url poblado
       └──────┬──────┘
              │ SOAP recepción
       ┌──────┴──────┐
       │             │
       ▼             ▼
 ┌───────────┐  ┌──────────┐
 │  ENVIADO  │  │  ERROR   │  ← retry en cola_envio
 └─────┬─────┘  └──────────┘
       │ RECIBIDA (SRI OK)
       ▼
 ┌───────────┐
 │ RECIBIDO  │  ← luego llamada AutorizacionComprobantes
 └─────┬─────┘
       │
   ┌───┴────┐
   ▼        ▼
┌──────┐ ┌──────────────┐
│ AUTO │ │NO_AUTORIZADO │  ← estado terminal (con errores fiscales)
│RIZADO│ └──────────────┘
└──┬───┘
   │
   │ (anulación manual)
   ▼
┌──────────┐
│ ANULADO  │  ← genera nota de crédito referenciando el original
└──────────┘
```

Estado terminal `DEVUELTO`: el SRI rechaza el sobre por errores estructurales del XML (no fiscales). Se corrige y reenvía como un nuevo intento.

---

## 7. Endpoints WSDL del SRI

| Ambiente | Operación | URL |
|---|---|---|
| Pruebas | Recepción | `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl` |
| Pruebas | Autorización | `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline?wsdl` |
| Producción | Recepción | `https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl` |
| Producción | Autorización | `https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline?wsdl` |

---

## 8. Estructura de la clave de acceso (49 dígitos)

```
ddmmaaaa | TC | RRRRRRRRRRRRR | A | EEEPPP | SSSSSSSSS | CCCCCCCC | V
   8     |  2 |      13       | 1 |   6    |     9     |    8     | 1
```

- `A` = 1 (pruebas) / 2 (producción)
- `V` = dígito verificador módulo 11 sobre los 48 anteriores

---

## 9. API REST del Billing Service

```
POST   /facturacion/facturas                    Emitir factura
POST   /facturacion/notas-credito               Emitir nota de crédito
POST   /facturacion/comprobantes/{id}/anular    Anular comprobante autorizado

GET    /facturacion/comprobantes                Listar (filtros: desde,hasta,tipo,estado,receptor)
GET    /facturacion/comprobantes/{clave}        Detalle por clave de acceso
GET    /facturacion/comprobantes/{clave}/ride   Descargar PDF RIDE
GET    /facturacion/comprobantes/{clave}/xml    Descargar XML autorizado
POST   /facturacion/comprobantes/{clave}/reenviar   Forzar reintento
POST   /facturacion/comprobantes/{clave}/notificar  Reenviar email al receptor

GET    /facturacion/config                      Obtener config SRI de la sucursal
PUT    /facturacion/config                      Actualizar config SRI
POST   /facturacion/certificados                Subir certificado P12 (multipart)
GET    /facturacion/certificados                Listar certificados
DELETE /facturacion/certificados/{id}           Revocar certificado

POST   /facturacion/puntos-emision              Crear punto de emisión
GET    /facturacion/puntos-emision              Listar puntos de emisión

GET    /facturacion/reportes/ats?anio=X&mes=Y   Generar/descargar ATS
GET    /facturacion/reportes/resumen            Totales por período
```

### 9.1 Payload de emisión de factura

```json
POST /facturacion/facturas
{
  "id_sucursal": 1,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "origen": {
    "id_membresia": 123,
    "id_venta": null
  },
  "receptor": {
    "tipo_identificacion": "05",
    "identificacion": "1712345678",
    "razon_social": "Juan Carlos Pérez",
    "email": "juan@email.com",
    "direccion": "Av. Los Shyris N32-14",
    "telefono": "0991234567"
  },
  "detalles": [
    {
      "codigo_principal": "MEM-MENSUAL",
      "descripcion": "Membresía Mensual — Agosto 2026",
      "cantidad": 1,
      "precio_unitario": 33.04,
      "descuento": 0,
      "impuestos": [
        { "codigo": "2", "codigo_porcentaje": "4", "tarifa": 15.00 }
      ]
    }
  ],
  "pagos": [
    { "forma_pago": "19", "total": 38.00 }
  ],
  "info_adicional": [
    { "nombre": "email", "valor": "juan@email.com" }
  ]
}
```

---

## 10. Arquitectura del microservicio (hexagonal)

```
billing-service/
├── src/main/java/com/gymadmin/billing/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Comprobante.java
│   │   │   ├── ComprobanteDetalle.java
│   │   │   ├── ClaveAcceso.java          // value object (49 dígitos + validación)
│   │   │   ├── Ambiente.java             // enum PRUEBAS / PRODUCCION
│   │   │   ├── EstadoComprobante.java    // enum del ciclo de vida
│   │   │   └── TipoComprobante.java      // enum '01','04','05','07'
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── EmitirFacturaUseCase.java
│   │   │   │   ├── EmitirNotaCreditoUseCase.java
│   │   │   │   ├── AnularComprobanteUseCase.java
│   │   │   │   └── ReenviarComprobanteUseCase.java
│   │   │   └── out/
│   │   │       ├── ComprobanteRepository.java
│   │   │       ├── SecuencialGenerator.java
│   │   │       ├── SriGateway.java              // envío SOAP + autorización
│   │   │       ├── FirmaDigitalPort.java        // XAdES-BES
│   │   │       ├── CertificadoStore.java        // acceso al P12 cifrado
│   │   │       ├── XmlGenerator.java            // marshalling XML según XSD
│   │   │       ├── RideGenerator.java           // PDF
│   │   │       ├── NotificacionPort.java        // email
│   │   │       └── BlobStorage.java             // XML y RIDE
│   ├── application/service/
│   │   ├── FacturacionService.java
│   │   ├── AnulacionService.java
│   │   └── ReenvioService.java
│   └── infrastructure/adapter/
│       ├── in/web/
│       │   ├── FacturacionController.java
│       │   ├── ConfigController.java
│       │   └── ReporteController.java
│       └── out/
│           ├── persistence/jpa/    (entidades + adapters + mappers)
│           ├── sri/soap/SriSoapClient.java
│           ├── firma/XadesBesSigner.java
│           ├── pdf/OpenPdfRideGenerator.java
│           ├── blob/AzureBlobAdapter.java
│           ├── email/SmtpNotificacionAdapter.java
│           └── kms/AzureKeyVaultAdapter.java
```

---

## 11. Integración con servicios existentes

### 11.1 Core Service — al vender membresía

```
[POST /membresias] (Core Service)
    ├── Registrar membresía en core.membresias
    ├── Registrar ingreso en finanzas.ingresos (llamada existente)
    └── SI (config_sri.facturacion_activa = true)
         └── Llamada asíncrona → POST /facturacion/facturas
              └── Al autorizar: UPDATE finanzas.ingresos SET id_comprobante = X
```

Patrón: **fire-and-forget** con cola de mensajes. Si el Billing Service falla, la membresía ya está registrada; la factura queda pendiente para reintento automático.

### 11.2 Inventory Service — al vender producto

Similar al anterior: la venta queda registrada; la factura se emite de forma asíncrona y correlaciona con `id_venta`.

### 11.3 Platform Service — check de módulo

El Billing Service consulta `/modulos/check` con `modulo='facturacion'` en cada request para validar que el plan del gimnasio incluye facturación electrónica.

---

## 12. Seguridad y almacenamiento del certificado P12

El certificado digital es **el mayor riesgo del módulo**. Robar el P12 permite emitir facturas fraudulentas a nombre del gimnasio.

### 12.1 Cifrado en reposo

- El P12 se cifra con **AES-256-GCM** antes de guardarse en `facturacion.certificados.p12_cifrado`.
- La clave maestra (KEK) vive en **Azure Key Vault** (el proyecto ya usa Key Vault en CI/CD).
- La contraseña del P12 también va cifrada, con la misma clave maestra.

### 12.2 Rotación

- `facturacion.certificados.fecha_vencimiento` permite alertar 30 días antes de la expiración (job diario).
- El certificado del BCE dura 2 años; el de Security Data hasta 3 años.

### 12.3 Uso en memoria

- El P12 se carga en memoria solo dentro del método de firma.
- Se limpia con `Arrays.fill(passwordBytes, (byte) 0)` inmediatamente después de usar.
- Nunca se loguea ni se serializa.

---

## 13. Estrategia de reintentos

Job programado cada 60 segundos que procesa `facturacion.cola_envio`:

```
SELECT * FROM facturacion.cola_envio
 WHERE estado = 'PENDIENTE'
   AND proxima_ejecucion <= NOW()
 ORDER BY proxima_ejecucion
 LIMIT 50
 FOR UPDATE SKIP LOCKED;   -- concurrencia sin race conditions
```

Backoff exponencial: intentos a los 1, 5, 15, 60, 240 minutos. Después de 5 intentos, el comprobante queda en `FALLIDO_DEFINITIVO` y requiere intervención manual (registro en `anulaciones` con motivo administrativo).

---

## 14. Librerías Java recomendadas

```xml
<!-- Firma XAdES-BES -->
<dependency>
    <groupId>com.luisguilherme</groupId>
    <artifactId>xades4j</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.apache.santuario</groupId>
    <artifactId>xmlsec</artifactId>
    <version>4.0.3</version>
</dependency>

<!-- SOAP -->
<dependency>
    <groupId>org.springframework.ws</groupId>
    <artifactId>spring-ws-core</artifactId>
</dependency>

<!-- PDF RIDE -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- Validación XSD -->
<dependency>
    <groupId>xerces</groupId>
    <artifactId>xercesImpl</artifactId>
    <version>2.12.2</version>
</dependency>

<!-- Cifrado del P12 -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-security-keyvault-secrets</artifactId>
</dependency>
```

Referencias open-source:
- [xprl-gjf/sri-soap-client](https://github.com/xprl-gjf/sri-soap-client) — cliente Java SOAP para SRI
- [alfredo138923/xades-bes-sri-ec](https://github.com/alfredo138923/xades-bes-sri-ec) — implementación Python (útil como referencia de la firma)

---

## 15. Hoja de ruta de implementación

### Fase 1 — Fundamentos de datos (semana 1)
- Migrations Liquibase con los dos schemas nuevos y las 22 tablas.
- Seed de todas las tablas `sri.*`.
- ALTER a `tenant.companias`, `finanzas.ingresos`.

### Fase 2 — Generación y firma del XML (semanas 2–3)
- Generación de clave de acceso (49 dígitos) con tests unitarios exhaustivos del módulo 11.
- Marshalling de XML según los XSD del SRI (validar con Xerces).
- Firma XAdES-BES con xades4j.
- Almacenamiento cifrado del P12 en Azure Key Vault.

### Fase 3 — Integración SOAP (semana 4)
- Cliente SOAP para Recepción y Autorización.
- Máquina de estados del comprobante.
- Auditoría en `envios_sri`.
- Cola de reintentos.
- Pruebas end-to-end contra `celcer.sri.gob.ec`.

### Fase 4 — RIDE y notificaciones (semana 5)
- Generación de PDF RIDE con OpenPDF.
- Envío por email al receptor.
- Almacenamiento en Azure Blob Storage.

### Fase 5 — API + integración (semana 6)
- API REST completa.
- Integración con Core Service e Inventory Service (fire-and-forget).
- Correlación con `finanzas.ingresos`.

### Fase 6 — Reportería (semana 7)
- Reporte ATS mensual.
- Reporte resumen período.
- Alertas de vencimiento de certificado.

### Fase 7 — Producción (semana 8)
- Activación en `cel.sri.gob.ec` con gimnasio piloto.
- Monitoreo de tasa de autorización.
- Runbook de incidentes.

---

## 16. Recursos oficiales

| Recurso | URL |
|---|---|
| SRI — Facturación Electrónica | https://www.sri.gob.ec/facturacion-electronica |
| Ficha Técnica v2.32 (nov 2025) | Portal SRI, sección Comprobantes Electrónicos |
| Validar comprobantes en línea | https://srienlinea.sri.gob.ec/comprobantes-electronicos-internet/publico/validezComprobantes.jsf |
| Autorización ambiente pruebas | https://www.gob.ec/sri/tramites/autorizacion-ambientes-pruebas-comprobantes-electronicos |
| Autorización ambiente producción | https://www.gob.ec/sri/tramites/autorizacion-ambientes-produccion-comprobantes-electronicos |
| Cliente Java SOAP (open-source) | https://github.com/xprl-gjf/sri-soap-client |
| API terceros — AutorizadorEC | https://autorizadorec.com |
| API terceros — Factuplan | https://docs.factuplan.com.ec |
