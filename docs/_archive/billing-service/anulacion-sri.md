# Anulación fiscal SRI — pendiente por implementar

> **ESTADO:** ✅ **IMPLEMENTADO 2026-07-13** — ver [api/anulaciones.md](../../billing-service/api/anulaciones.md), [flows/anulacion-nc.md](../../billing-service/flows/anulacion-nc.md), y [gap-analysis §G3](../../billing-service/pendientes/gap-analysis-sri-2026.md#g3--anulación-fiscal-sri).
> **Prioridad:** 🔴 Alta (bloqueo legal para uso productivo con RUC real)
> **Fecha del análisis:** 2026-07-11

---

## 1. Problema actual

El endpoint `POST /api/v1/comprobantes/{id}/anular` es una **anulación lógica local**:

- Solo cambia `estado` a `ANULADO` en `facturacion.comprobantes` (ver `ComprobanteService.java:150-159`).
- **No** solicita anulación al SRI.
- **No** requiere motivo.
- **No** valida ventana temporal.
- **No** valida si el comprobante es anulable según normativa fiscal.
- **No** genera nota de crédito.
- **No** notifica al receptor.
- **No** registra la solicitud en `facturacion.anulaciones`, tabla que **ya existe** en el DDL (ver §3).

**Consecuencia:** si un usuario usa este endpoint con un comprobante ya `AUTORIZADO` por el SRI, se crea divergencia irrecuperable entre lo que el gimnasio ve y lo que el SRI ve. El ATS mensual (`/api/v1/reportes/ats`) reportará incorrectamente y el contribuyente enfrenta **riesgo de sanción tributaria**.

---

## 2. Reglas de la normativa SRI Ecuador (verificadas 2026-07)

Base normativa: resolución vigente desde **2025-08-01** (nuevas reglas más restrictivas).

### 2.1 Plazo de anulación
- La anulación en línea es hasta el **día 7 del mes siguiente** al de la emisión (plazo reducido; era día 10 hasta mediados 2025).
- Ejemplo: factura emitida el 2026-07-15 → anulable hasta el 2026-08-07.
- Fuera de plazo: no procede anulación; alternativas más complejas (rectificación tributaria) quedan fuera del alcance de este servicio.

### 2.2 Comprobantes NO anulables
- **Facturas emitidas a "consumidor final"** (identificación `9999999999999`) — **no se pueden anular en línea ni corregir con nota de crédito**. Es una restricción común en gimnasios: mucha facturación es a consumidor final.
- Comprobantes ya utilizados por el receptor en su contabilidad (fuera del alcance del sistema; queda a nivel de proceso).

### 2.3 Mecanismos disponibles
El SRI distingue dos flujos que **este servicio debe soportar por separado**:

| Mecanismo | Cuándo aplica | Requiere aceptación del receptor |
|-----------|---------------|----------------------------------|
| **Anulación directa** (en portal SRI) | Facturas con receptor identificado, dentro de plazo, sin efectos económicos ya realizados | No |
| **Nota de crédito** (comprobante tipo `04`) | Devoluciones, descuentos comerciales, errores parciales | **Sí** — el receptor tiene 5 días hábiles para aceptar |

Motivos oficiales del catálogo SRI (ya seedeados en `sri.motivos_anulacion_nc`):
- `DEVOLUCION` — Devolución de mercadería
- `DESCUENTO` — Descuento comercial
- `ANULACION` — Anulación de factura
- `ERROR_PRECIO` — Error en precio
- `ERROR_CALIDAD` — Diferencia de calidad

### 2.4 Canal técnico
> ⚠️ **Importante:** el SRI **no expone un webservice SOAP público** para anulación en el esquema offline. La anulación se registra:
>
> - **Manualmente en el portal SRI** (`srienlinea.sri.gob.ec`) — el usuario ingresa clave de acceso y motivo, y ejecuta.
> - Vía la aplicación oficial **Facturador SRI** (para contribuyentes que la usan).
> - **Notas de crédito sí se envían por el mismo webservice** de recepción/autorización que ya usa el sistema (tipo comprobante `04`), y siguen todo el flujo de firma + envío + autorización que ya existe.
>
> Este servicio **no puede** ejecutar la anulación directamente contra el SRI vía API. Puede sí: registrar la solicitud, generar nota de crédito, y proveer un flujo de "confirmación manual" para reflejar que el trámite en portal ya fue realizado.

---

## 3. Diseño ya presente en base de datos (no usado por el código)

El equipo modeló la anulación en el DDL desde el diseño de BD. **El código Java no aprovecha estas tablas**. Hay que implementar los adapters R2DBC y la lógica de dominio.

### 3.1 `facturacion.anulaciones`
Ver `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/24_create_table_facturacion_anulaciones.sql`.

```sql
CREATE TABLE facturacion.anulaciones (
  id                     BIGINT PRIMARY KEY,
  id_compania            INT NOT NULL,
  id_sucursal            INT NOT NULL,
  id_comprobante         BIGINT NOT NULL,           -- FK comprobantes
  motivo                 TEXT NOT NULL,
  estado                 VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA',
  id_comprobante_nc      BIGINT,                    -- FK a la NC generada (opcional)
  id_usuario_solicita    INT NOT NULL,
  id_usuario_aprueba     INT,
  fecha_solicitud        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  fecha_resolucion       TIMESTAMPTZ,
  observacion_resolucion TEXT,
  CHECK (estado IN ('SOLICITADA','APROBADA','RECHAZADA','EJECUTADA'))
);
```

**Máquina de estados diseñada:**

```
SOLICITADA ──aprobar──→ APROBADA ──ejecutar──→ EJECUTADA
     │
     └──rechazar──→ RECHAZADA
```

### 3.2 `sri.motivos_anulacion_nc`
Catálogo cerrado con 5 motivos oficiales SRI (ver §2.3). Ya viene con seed.

### 3.3 `facturacion.notas_credito_referencias`
Ver `20_create_table_facturacion_nc_referencias.sql`. Vincula una nota de crédito con el comprobante que modifica:

```sql
CREATE TABLE facturacion.notas_credito_referencias (
  id_comprobante       BIGINT NOT NULL,   -- la NC misma
  cod_doc_modificado   CHAR(2) NOT NULL DEFAULT '01',  -- factura
  num_doc_modificado   VARCHAR(17) NOT NULL,           -- 001-001-000000001
  fecha_emision_modif  DATE NOT NULL,
  id_motivo_anulacion  INT REFERENCES sri.motivos_anulacion_nc(id),
  razon                TEXT NOT NULL,
  valor_modificado     DECIMAL(14,2) NOT NULL
);
```

### 3.4 `facturacion.comprobantes.id_comprobante_ref`
Campo ya presente en `14_create_table_facturacion_comprobantes.sql:34`. Permite que una NC (tipo `04`) apunte al comprobante original.

---

## 4. Diseño propuesto — endpoints

Todos requieren `Authorization: Bearer` (JWT staff con `id_compania` de la empresa dueña del comprobante).

### 4.1 Reemplazar el endpoint actual
```
POST /api/v1/comprobantes/{id}/anular
```

**Cambios respecto a la implementación actual:**

- Requiere body con motivo:
  ```json
  {
    "motivo": "Cliente devolvió pago con reversa bancaria",
    "id_motivo_anulacion": 3,
    "generar_nota_credito": false
  }
  ```
- Validaciones nuevas (todas devuelven `422` `BusinessException`):
  1. Comprobante en estado `AUTORIZADO` o `GENERADO`.
  2. `id_receptor != '9999999999999'` (consumidor final).
  3. `fecha_emision` está dentro de ventana (≤ día 7 del mes siguiente).
  4. `motivo` no vacío, `id_motivo_anulacion` existe en `sri.motivos_anulacion_nc`.
- Efecto:
  - Crea fila en `facturacion.anulaciones` estado `SOLICITADA`.
  - **No** cambia el estado del comprobante todavía.
  - Retorna id de la solicitud + instrucciones para completar en portal SRI.

### 4.2 Nuevos endpoints
```
GET    /api/v1/comprobantes/{id}/anulaciones              # historial de solicitudes
POST   /api/v1/anulaciones/{idAnulacion}/aprobar          # rol admin_compania+
POST   /api/v1/anulaciones/{idAnulacion}/rechazar         # rol admin_compania+
POST   /api/v1/anulaciones/{idAnulacion}/confirmar-sri    # marcar EJECUTADA tras confirmar en portal
GET    /api/v1/anulaciones                                # listado filtrable
GET    /api/v1/sri/motivos-anulacion                      # catálogo público (staff)
```

### 4.3 Emisión de nota de crédito
```
POST /api/v1/comprobantes/notas-credito
```

Body:
```json
{
  "id_comprobante_original": 152,
  "id_motivo_anulacion": 1,
  "razon": "Devolución completa del cargo mensual",
  "valor_modificado": 38.00,
  "lineas": [ { "descripcion": "...", "cantidad": 1, "precio": 38.00 } ]
}
```

Comportamiento:
- Crea comprobante tipo `04` (nota de crédito) en `facturacion.comprobantes`.
- Crea fila en `facturacion.notas_credito_referencias`.
- **Reutiliza** todo el flujo async existente: firma XAdES-BES → cola de envío → SRI recepción → SRI autorización → retry.
- Al autorizarse, si la NC vino de una anulación, actualiza `facturacion.anulaciones.id_comprobante_nc` y transiciona `APROBADA → EJECUTADA`.
- **Notifica al receptor por email** para pedir aceptación (según normativa 5 días hábiles).

---

## 5. Flujos

### 5.1 Flujo A — Anulación directa (sin NC)
```
Usuario → POST /comprobantes/{id}/anular (motivo)
   ├─ Validar plazo, no consumidor final, motivo
   └─ INSERT anulaciones (estado=SOLICITADA)

Admin → POST /anulaciones/{id}/aprobar
   └─ UPDATE anulaciones SET estado=APROBADA, id_usuario_aprueba=..., fecha_resolucion=NOW()

Admin (fuera del sistema) → Portal SRI → anula manualmente

Admin → POST /anulaciones/{id}/confirmar-sri
   └─ UPDATE anulaciones SET estado=EJECUTADA
   └─ UPDATE comprobantes SET estado=ANULADO
```

### 5.2 Flujo B — Anulación con nota de crédito
```
Usuario → POST /comprobantes/{id}/anular (motivo, generar_nota_credito=true)
   └─ INSERT anulaciones (estado=SOLICITADA)

Admin → POST /anulaciones/{id}/aprobar
   └─ UPDATE anulaciones SET estado=APROBADA
   └─ Trigger interno: POST /comprobantes/notas-credito  (asíncrono)
        ├─ Crea comprobante tipo 04
        ├─ Firma + envía SRI (flujo actual)
        └─ Al autorizarse:
              ├─ UPDATE anulaciones SET id_comprobante_nc=..., estado=EJECUTADA
              └─ UPDATE comprobantes(original) SET estado=ANULADO (opcional según reglas)
              └─ Envío email al receptor
```

---

## 6. Checklist de implementación

### 6.1 Dominio
- [ ] Modelo `Anulacion` (`domain/model/`)
- [ ] Modelo `MotivoAnulacion` (catálogo)
- [ ] Modelo `NotaCreditoReferencia`
- [ ] Enum `EstadoAnulacion` (`SOLICITADA`, `APROBADA`, `RECHAZADA`, `EJECUTADA`)
- [ ] Puerto entrada `AnulacionUseCase`
- [ ] Puerto entrada `NotaCreditoUseCase`
- [ ] Puerto salida `AnulacionRepository`
- [ ] Puerto salida `MotivoAnulacionRepository`

### 6.2 Aplicación
- [ ] `AnulacionService` con reglas de negocio:
  - Ventana temporal (día 7 mes siguiente)
  - No consumidor final
  - Estado del comprobante válido
  - Motivo obligatorio y del catálogo
- [ ] `NotaCreditoService` que reutiliza `EnvioSriService` existente
- [ ] Modificar `ComprobanteService.anularComprobante` para delegar a `AnulacionService`

### 6.3 Infraestructura
- [ ] Entity + Repository R2DBC para `facturacion.anulaciones`
- [ ] Entity + Repository R2DBC para `sri.motivos_anulacion_nc`
- [ ] Entity + Repository R2DBC para `facturacion.notas_credito_referencias`
- [ ] Adapter que implementa los puertos
- [ ] Constructor XML de nota de crédito (extendiendo el generador de factura)

### 6.4 REST
- [ ] Modificar `ComprobanteController.anularComprobante` (body con motivo)
- [ ] `AnulacionController` (nuevo)
- [ ] `NotaCreditoController` (o integrar en `ComprobanteController`)
- [ ] DTOs: `SolicitarAnulacionRequest`, `AprobarAnulacionRequest`, `EmitirNotaCreditoRequest`, `AnulacionResponse`

### 6.5 Autorización (inline en controllers, siguiendo convención actual del servicio)
- [ ] Solicitar anulación: cualquier staff con `id_compania` coincidente
- [ ] Aprobar/rechazar: solo `admin_compania`, `super_admin`, `Dueño`
- [ ] Confirmar-sri: solo `admin_compania`, `super_admin`

### 6.6 Notificaciones
- [ ] Email al receptor cuando se emite NC (aceptación pendiente)
- [ ] Email al usuario solicitante cuando su solicitud es aprobada/rechazada

### 6.7 Reportes
- [ ] Actualizar `ReporteService.generarAts` para incluir NC (cod_documento `04`) además de facturas
- [ ] Actualizar `ReporteService.resumen` para desglosar comprobantes anulados

### 6.8 Tests
- [ ] Unit test: ventana temporal (borde: día 7, día 8)
- [ ] Unit test: rechazo por consumidor final
- [ ] Unit test: rechazo por estado no anulable
- [ ] Unit test: transiciones de máquina de estados
- [ ] Integration test: solicitud → aprobación → NC autorizada → EJECUTADA
- [ ] Integration test: solicitud → rechazo
- [ ] Integration test: anulación sin NC → confirmación manual portal

### 6.9 Documentación
- [ ] Nuevo `docs/billing-service/api/anulaciones.md`
- [ ] Nuevo `docs/billing-service/api/notas-credito.md`
- [ ] Nuevo `docs/billing-service/flows/anulacion-nc.md` (diagrama estados)
- [ ] Actualizar `comprobantes.md` (endpoint anular con body)
- [ ] Actualizar `INDEX.md` y `STATUS.md`
- [ ] Actualizar `billing-service/CLAUDE.md` (máquina de estados nueva)

---

## 7. Riesgo si no se implementa

| Riesgo | Severidad | Descripción |
|--------|-----------|-------------|
| Divergencia con SRI en ATS mensual | 🔴 Alta | El reporte ATS muestra un estado local que no coincide con el estado oficial en el SRI, disparando observaciones tributarias. |
| Facturas emitidas a "consumidor final" marcadas como anuladas | 🔴 Alta | Legalmente no son anulables; el estado local se contradice con la normativa. |
| Anulación fuera de plazo aceptada por el sistema | 🟡 Media | Usuario cree que anuló y no puede rectificar en portal SRI. |
| Ausencia de motivo obligatorio | 🟡 Media | Imposibilita auditoría contable interna. |
| Falta de workflow de aprobación | 🟡 Media | Cualquier recepción con acceso puede "anular" facturas grandes sin trazabilidad de quién autorizó. |
| Cliente afectado no notificado | 🟢 Baja | Ruptura de relación comercial; no es directamente fiscal. |

**Decisión de producto pendiente:** hasta implementar esto, se recomienda **bloquear el uso productivo del billing-service con RUC real** o al menos **deshabilitar el endpoint de anulación** en el frontend.

---

## 8. Estimación gruesa
- Dominio + adapters + endpoints: 2-3 días
- Emisión y firma de nota de crédito: 2 días (aprovecha infra existente)
- Notificaciones + reportes actualizados: 1 día
- Tests + docs: 2 días
- **Total: ~7-8 días de desarrollo.**

---

## 9. Referencias oficiales

- [Guía para contribuyentes: Anulación de comprobantes electrónicos (SRI)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/c97242e6-c271-4eb8-8f6a-f687313118ba/Guia%20para%20contribuyentes%20de%20anulaci%C3%B3n%20de%20comprobantes%20electr%C3%B3nicos.pdf) — documento oficial actualizado marzo 2025.
- [Ficha técnica de comprobantes electrónicos — esquema offline v2.26 (SRI)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/ed555352-46c7-4917-9f61-011b6a9f4600/FICHA%20TE%CC%81CNICA%20COMPROBANTES%20ELECTRO%CC%81NICOS%20ESQUEMA%20OFFLINE%20Versio%CC%81n%202.26.pdf) — formatos XML de nota de crédito.
- [Boletín 033 — SRI establece nuevas reglas para la anulación de comprobantes electrónicos](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/142630d3-569f-4cd2-a5a5-58557b7fc342/BOLET%C3%8DN%20033%20-%20SRI%20ESTABLECE%20NUEVAS%20REGLAS%20PARA%20LA%20ANULACI%C3%93N%20DE%20COMPROBANTES%20ELECTR%C3%93NICOS%20COMO%20PARTE%20DE%20SU%20ESTRATEGIA%20DE%20CONTROL.pdf) — normativa vigente desde 2025-08-01.
- [Anulación de comprobantes electrónicos — trámite oficial en gob.ec](https://www.gob.ec/sri/tramites/anulacion-comprobantes-electronicos)
- [Cómo anular una factura electrónica del SRI en 2026 (interpretación práctica)](https://laprensa.com.ec/como-anular-factura-electronica-sri-2026/)
- [Plazos SRI 2026 — Group Seres](https://blog.groupseres.com/latam/plazos-del-sri-para-los-comprobantes-electronicos-en-ecuador)

## 10. Archivos del repo relevantes

| Archivo | Rol |
|---------|-----|
| `billing-service/src/main/java/.../application/service/ComprobanteService.java:150-159` | Implementación actual de `anularComprobante` — a reemplazar |
| `billing-service/src/main/java/.../infrastructure/adapter/in/web/ComprobanteController.java:196-208` | Endpoint actual — a modificar |
| `billing-service/src/main/java/.../domain/port/out/SriSoapPort.java` | Cliente SOAP SRI — reutilizable para NC, sin cambios |
| `billing-service/src/main/java/.../application/service/EnvioSriService.java` | Flujo firma+envío+autorización — reutilizable para NC |
| `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/24_create_table_facturacion_anulaciones.sql` | Tabla `anulaciones` ya modelada |
| `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/20_create_table_facturacion_nc_referencias.sql` | Tabla `notas_credito_referencias` ya modelada |
| `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/08_create_table_sri_motivos_anulacion_nc.sql` | Catálogo `motivos_anulacion_nc` con seed |
| `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/14_create_table_facturacion_comprobantes.sql:34,48` | Campo `id_comprobante_ref` y estado `ANULADO` |
