# Notas de Crédito API — billing-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `NotaCreditoController`, `NotaCreditoService`).
>
> **G4 · Fase 2 — Notas de crédito electrónicas SRI (tipo `04`).** Requerido para corregir facturas ya autorizadas fuera de la ventana de anulación fiscal (día 7 del mes siguiente).

Todos los endpoints requieren `Authorization: Bearer {token}` (JWT de staff). El `id_compania` sale del token y se enforza en todas las consultas.

---

## Endpoints

### POST /api/v1/notas-credito
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Emitir nota de crédito electrónica sobre una factura ya autorizada **y transmitirla inmediatamente al SRI** dentro del mismo request.

> **Reutiliza el pipeline síncrono G2:** después de persistir la NC en estado `GENERADO`, el servidor dispara síncronamente **firmar XML NC v1.1.0 → enviar al SRI RECEPCION → consultar AUTORIZACION**, todo bajo el mismo timeout configurable (`sri.timeout.envio-seconds`, default 15 s). Los estados posibles en el body son idénticos a los de factura: `AUTORIZADO` (happy path), `DEVUELTO`, `NO_AUTORIZADO`, `ERROR`.
>
> **Secuencial automático:** el servidor reserva el siguiente secuencial de tipo `04` atómicamente contra `facturacion.secuenciales`. El campo `secuencial` **no** entra en el request.

**Request body:**
```json
{
  "id_sucursal": 1,
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "codigo_numerico": "123456789",
  "id_factura_original": 100,
  "codigo_motivo": "DEVOLUCION",
  "razon": "Devolución mensual por error de cargo",
  "valor_modificacion": 30.00,
  "detalles": [
    {
      "codigo_principal": "MEM-MENSUAL",
      "descripcion": "Membresía mensual (ajuste)",
      "cantidad": 1.000000,
      "precio_unitario": 30.00,
      "descuento": 0.00
    }
  ]
}
```

**Campo obligatorio/opcional:**
- `id_sucursal`, `cod_establecimiento`, `cod_punto_emision`, `codigo_numerico` — obligatorios (mismo formato que factura).
- `id_factura_original` — obligatorio. ID en `facturacion.comprobantes` de la factura tipo `01` que la NC corrige.
- `codigo_motivo` — obligatorio. Debe existir en `sri.motivos_anulacion_nc.codigo` (seeds actuales: `DEVOLUCION`, `DESCUENTO`, `ANULACION`, `ERROR_PRECIO`, `ERROR_CALIDAD`).
- `razon` — obligatorio. Descripción libre visible en el XML SRI campo `<motivo>`.
- `valor_modificacion` — obligatorio. Debe ser positivo y no exceder el total de la factura original.
- `detalles` — array no vacío. Mismo shape que en factura: `codigo_principal`, `descripcion`, `cantidad`, `precio_unitario` obligatorios; `codigo_auxiliar`, `descuento` opcionales.

**Validaciones semánticas:**
- La factura original debe existir, pertenecer a la misma compañía (multi-tenant), ser tipo `01` y estar en estado `AUTORIZADO`.
- El motivo debe existir en `sri.motivos_anulacion_nc`.
- El receptor de la NC se **copia** de la factura original — no se acepta override desde el request.

**Response 201 (happy path):**
```json
{
  "id": 300,
  "id_compania": 2,
  "id_sucursal": 1,
  "tipo_comprobante": "04",
  "clave_acceso": "1507202604179001234500110010010000000420123456789X",
  "numero_autorizacion": "1507202604179001234500110010010000000420123456789",
  "cod_establecimiento": "001",
  "cod_punto_emision": "001",
  "secuencial": "000000042",
  "fecha_emision": "2026-07-15",
  "ambiente": "1",
  "tipo_id_receptor": "05",
  "id_receptor": "1712345678",
  "razon_social_receptor": "Juan Carlos Pérez",
  "email_receptor": "juan@email.com",
  "subtotal_sin_impuesto": 30.00,
  "total_descuento": 0.00,
  "total_iva": 0.00,
  "total": 30.00,
  "moneda": "DOLAR",
  "estado": "AUTORIZADO",
  "fecha_autorizacion": "2026-07-15T14:30:12Z",
  "xml_firmado_path": null,
  "xml_autorizado_path": null,
  "ride_pdf_path": null,
  "created_at": "2026-07-15T14:30:00Z"
}
```

En posición 9-10 de `clave_acceso` viene el tipo comprobante (`04`). La fila `facturacion.notas_credito_referencias` se crea junto con la NC y guarda:

| Columna | Valor |
|---------|-------|
| `id_comprobante` | ID de la NC recién creada |
| `cod_doc_modificado` | `01` |
| `num_doc_modificado` | Formato `{cod_establecimiento}-{cod_punto_emision}-{secuencial}` de la factura original (ej. `001-001-000000123`) |
| `fecha_emision_modif` | `fecha_emision` de la factura original |
| `id_motivo_anulacion` | PK del motivo en `sri.motivos_anulacion_nc` |
| `razon` | Copiado del request |
| `valor_modificado` | Copiado del request |

**Errores:**
- `400` — campos requeridos faltantes o formato inválido.
- `401` — no autenticado.
- `404` — factura original no existe, o no pertenece a la compañía autenticada (multi-tenant), o motivo no existe en el catálogo.
- `422` — regla de negocio:
  - `Solo facturas AUTORIZADO admiten NC; la factura {id} está en estado {estado}`
  - `Solo se puede emitir NC sobre facturas (tipo 01); el comprobante {id} es tipo {tipo}`
  - `valor_modificacion debe ser positivo`
  - `valor_modificacion ({v}) no puede exceder el total de la factura original ({total})`

> **Los rechazos del SRI (DEVUELTO / NO_AUTORIZADO / ERROR por timeout) NO se traducen a 4xx/5xx.** La respuesta sigue siendo 201 con el `estado` en el body — la NC quedó persistida y entrará al ciclo de reintentos del scheduler.

---

### GET /api/v1/notas-credito/{id}
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Obtener detalles de una NC por ID.

**Path param:** `id` — ID en `facturacion.comprobantes` de una fila con `tipo_comprobante = '04'`.

**Response 200:** mismo shape que `POST` (`ComprobanteResponse`).

**Errores:**
- `401` — no autenticado.
- `404` — NC no encontrada, o pertenece a otra compañía, o el ID corresponde a un comprobante que no es NC.

---

### GET /api/v1/notas-credito
**Auth:** Bearer JWT (`tipo: staff`)  
**Description:** Listar NC de la empresa con paginación y filtros. Solo devuelve comprobantes con `tipo_comprobante = '04'`.

**Query params:**
- `estado` — filtrar por estado (opcional): `GENERADO`, `FIRMADO`, `RECIBIDO`, `AUTORIZADO`, `NO_AUTORIZADO`, `DEVUELTO`, `ANULADO`, `ERROR`.
- `id_sucursal` — filtrar por sucursal (opcional).
- `id_factura_original` — filtrar por factura de origen (opcional).
- `page` — número de página (default: 1).
- `limit` — resultados por página (default: 20).

**Response 200:**
```json
{
  "total": 3,
  "pagina": 1,
  "datos": [
    { "id": 300, "tipo_comprobante": "04", "estado": "AUTORIZADO", "...": "..." }
  ]
}
```

**Errores:**
- `401` — no autenticado.

---

## Modelo de datos

**Tabla `facturacion.comprobantes`** — misma tabla que las facturas; una NC se distingue por `tipo_comprobante = '04'` e `id_comprobante_ref` apuntando a la factura original.

**Tabla `facturacion.notas_credito_referencias`** — una fila por NC:
```sql
id                   BIGINT PK
id_compania          INT NOT NULL
id_sucursal          INT NOT NULL
id_comprobante       BIGINT NOT NULL REFERENCES facturacion.comprobantes(id)  -- FK a la NC, NO a la factura
cod_doc_modificado   CHAR(2) NOT NULL DEFAULT '01'
num_doc_modificado   VARCHAR(17) NOT NULL   -- "001-001-000000001"
fecha_emision_modif  DATE NOT NULL
id_motivo_anulacion  INT REFERENCES sri.motivos_anulacion_nc(id)
razon                TEXT NOT NULL
valor_modificado     DECIMAL(14,2) NOT NULL
UNIQUE (id_comprobante)
```

**Secuencial:** independiente por combinación `(id_compania, id_sucursal, cod_establecimiento, cod_punto_emision, tipo_comprobante = '04')`, reservado atómicamente por `SecuencialRepository.reservarSiguiente(..., "04")`.

---

## XML SRI

Versión de la ficha técnica: **v1.1.0** (constante `NotaCreditoXmlBuilder.XML_VERSION_NC`).

Estructura simplificada:
```xml
<notaCredito id="comprobante" version="1.1.0">
  <infoTributaria>
    <ambiente/> <tipoEmision>1</tipoEmision>
    <razonSocial/> <ruc/> <claveAcceso/>
    <codDoc>04</codDoc>
    <estab/> <ptoEmi/> <secuencial/> <dirMatriz/>
  </infoTributaria>
  <infoNotaCredito>
    <fechaEmision>dd/MM/yyyy</fechaEmision>
    <dirEstablecimiento/>
    <tipoIdentificacionComprador/> <razonSocialComprador/> <identificacionComprador/>
    <obligadoContabilidad>SI|NO</obligadoContabilidad>
    <codDocModificado>01</codDocModificado>
    <numDocModificado>001-001-000000001</numDocModificado>
    <fechaEmisionDocSustento>dd/MM/yyyy</fechaEmisionDocSustento>
    <totalSinImpuestos/> <valorModificacion/> <moneda>DOLAR</moneda>
    <totalConImpuestos>
      <totalImpuesto>
        <codigo>2</codigo> <codigoPorcentaje>4</codigoPorcentaje>
        <baseImponible/> <valor/>
      </totalImpuesto>
    </totalConImpuestos>
    <motivo/>
  </infoNotaCredito>
  <detalles>
    <detalle>
      <codigoInterno/> <descripcion/> <cantidad/> <precioUnitario/>
      <descuento/> <precioTotalSinImpuesto/>
      <impuestos>
        <impuesto>
          <codigo>2</codigo> <codigoPorcentaje>4</codigoPorcentaje>
          <tarifa>15.00</tarifa> <baseImponible/> <valor/>
        </impuesto>
      </impuestos>
    </detalle>
  </detalles>
  <infoAdicional>
    <campoAdicional nombre="Email"/> <!-- opcional -->
  </infoAdicional>
</notaCredito>
```

> TODO(G6-follow): la tarifa IVA por línea (código `2`, porcentaje `4`, tarifa `15.00`) sigue hardcodeada — mismo tratamiento que en `FacturaXmlBuilder`. Se resolverá cuando el catálogo `sri.tarifas_iva` sea consultable por detalle.

---

## Flujo síncrono G2 reutilizado

`NotaCreditoService` invoca `EnvioSriService.procesarEmisionInmediataConXml(nc, xmlSinFirmar)` — variante añadida en G4 al servicio existente que ya sabe firmar → enviar → autorizar. Semántica idéntica a factura: mismo timeout, mismo encolado en fallo, mismo backoff `{1, 5, 15, 60, 240}` min. Ver [flows/sri-submission-retry.md](../flows/sri-submission-retry.md) para el diagrama completo.

**Métrica:** `billing.comprobantes.emitidos{tipo=NOTA_CREDITO}` se incrementa por cada NC firmada. El resto (`billing.comprobantes.autorizados`, `sri.emision.duracion`, `sri.emision.timeouts`) se comparten con facturas.
