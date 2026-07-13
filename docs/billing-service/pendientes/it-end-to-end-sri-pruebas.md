# Test de integración end-to-end contra SRI de pruebas

> **ESTADO:** 📋 **Pendiente — para próxima sesión**
> **Fecha:** 2026-07-13
> **Objetivo:** validar el pipeline `emitir → firmar → enviar → autorizar` con un envío real al ambiente de pruebas del SRI (`celcer.sri.gob.ec`), no con mocks.

Este doc lista todo lo que hace falta antes de escribir el test — es un requisito para reanudar el trabajo sin volver a descubrir las mismas piezas.

---

## 1. Alcance del test propuesto

Un IT único que ejecute end-to-end el camino feliz:

```
setUp
  ├─ Inserta config_sri (RUC, ambiente=1, establecimiento, punto emisión)
  ├─ Inserta certificado_info (P12 + passphrase encriptada)
  └─ Cliente WebTestClient autenticado con JWT staff válido

test
  └─ POST /api/v1/comprobantes/facturas con receptor real
       └─ espera HTTP 201 con estado ∈ {AUTORIZADO, RECIBIDO, DEVUELTO}
       └─ verifica que se hizo commit en facturacion.envios_sri (registro auditoría)
       └─ verifica que la clave de acceso quedó guardada

tearDown
  └─ delete de las filas insertadas (config_sri, certificado, comprobante)
```

Idealmente el test **espera `AUTORIZADO`**. Cualquier otro estado indica un problema en el pipeline o en los datos de prueba.

Segundo IT sugerido (nice-to-have): caso NC — emitir una factura, esperar autorizada, emitir NC contra ella, esperar NC autorizada.

---

## 2. Prerrequisitos técnicos — checklist

Sin al menos las primeras 5 piezas el test no puede correr.

### 2.1 · Certificado P12 real de firma electrónica

- [ ] Certificado emitido por CA reconocida por el SRI Ecuador (Banco Central, Security Data, Uanataca, ANFAC, etc.).
- [ ] **Vigente** en la fecha del test.
- [ ] De **firma electrónica**, no de sello ni de servidor SSL.
- [ ] Perteneciente al **mismo RUC** que se usa en `config_sri`.
- [ ] Archivo `.p12` accesible (por ejemplo en `test/resources/` o via variable de entorno `TEST_CERT_P12_PATH`).
- [ ] Contraseña del P12 conocida.

> ⚠️ **No sirve un P12 auto-firmado.** El SRI valida la cadena de confianza contra sus CAs reconocidas.

### 2.2 · RUC de contribuyente válido

- [ ] 13 dígitos con el **dígito verificador SRI** correcto (algoritmo módulo 10 posicional; el SRI rechaza el XML si no cuadra).
- [ ] Idealmente un RUC ya usado en el ambiente de pruebas de Certificación SRI para no arrastrar deuda de datos previos.
- [ ] Datos consistentes con el certificado: si el certificado dice "1712345678001", el RUC en `config_sri` debe ser el mismo.

### 2.3 · Fila en `facturacion.config_sri`

Ejemplo mínimo:

```sql
INSERT INTO facturacion.config_sri (
  id_compania, id_sucursal, ruc, razon_social, nombre_comercial,
  dir_establecimiento, ambiente, obligado_contabilidad, id_establecimiento,
  cod_establecimiento, cod_punto_emision
) VALUES (
  99999, 1, '<RUC_PRUEBA>', '<RAZÓN SOCIAL>', '<NOMBRE COMERCIAL>',
  '<DIRECCIÓN COMPLETA>', '1', TRUE, 1,
  '001', '001'
);
```

- `ambiente = '1'` — obligatorio para apuntar a `celcer.sri.gob.ec`.
- `id_compania` y `id_sucursal` deben coincidir con los del JWT del test.

### 2.4 · Fila en `facturacion.certificados_info`

- [ ] El P12 cargado como `contenido_p12` (bytes).
- [ ] Passphrase encriptada con **AES-256-GCM** usando la clave `CERT_ENCRYPTION_KEY` del `.env`.
- [ ] `fecha_vencimiento` en el futuro (para pasar el filtro de "certificado activo").
- [ ] Vinculado a la misma `id_compania` de `config_sri`.

Hay un helper `CertificadoDecryptionService` que hace la desencriptación; el proceso inverso (encriptar antes de insertar) debe hacerlo el `setUp` del test usando la misma clave.

### 2.5 · Base de datos local operacional

Bug conocido: los IT existentes fallan con `password authentication failed for user "administrador"`.

- [ ] Resolver la auth de PostgreSQL local. Opciones:
  - Ajustar el usuario en `.env` para que coincida con el que corre Postgres localmente.
  - Crear el usuario `administrador` en Postgres con la password del `.env`.
  - Cambiar `application-integration.yml` para usar credenciales existentes.
- [ ] Verificar que los schemas `facturacion` + `sri` estén creados y con seeds cargados (Liquibase debería hacerlo automáticamente al arrancar el servicio).

### 2.6 · Receptor válido

- [ ] Cédula o RUC de 10/13 dígitos con verificador correcto.
- [ ] `razon_social_receptor` no vacío.
- [ ] Recomendado: usar los identificadores oficiales SRI de prueba si existen (buscar en la guía "Manual de Facturador SRI").

### 2.7 · Conectividad de red

- [ ] La máquina donde corre el test debe alcanzar por HTTPS:
  - `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline`
  - `https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline`
- [ ] Verificar que no haya proxy corporativo bloqueando las salidas.
- [ ] Test rápido antes de escribir código: `curl -k https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl` debe retornar el WSDL.

---

## 3. Consideraciones de diseño del test

### 3.1 Condicionalidad

Marcar el test con **`@EnabledIfEnvironmentVariable`** (o assumption JUnit) sobre variables como `SRI_TEST_CERT_PATH`, `SRI_TEST_RUC`. Si no están definidas, el test se salta con `@Disabled` visible en el reporte. Esto evita romper el pipeline de CI cuando el certificado no está disponible.

### 3.2 Datos de prueba

Usar `id_compania = 99999` y `id_sucursal = 1` (convención de los IT del monorepo — ver `IntegrationTestBase`) para no chocar con datos productivos.

### 3.3 Cleanup

`@AfterEach` debe:
- Borrar el comprobante emitido (por `clave_acceso`).
- Borrar filas en `facturacion.envios_sri`, `facturacion.cola_envio` asociadas.
- Borrar `config_sri` y `certificado_info` de la compañía de prueba.

Sin cleanup el segundo run del test falla por unique constraints o secuenciales gastados.

### 3.4 Idempotencia del secuencial

Como G5 reserva secuencial atómico en BD, cada run del test consume uno nuevo. No hay problema salvo que el ambiente SRI empiece a rechazar secuenciales duplicados si el mismo RUC ya emitió esa combinación. Contramedida: usar `codEstablecimiento="999"` y `codPuntoEmision="999"` (reservados para pruebas) para no chocar con datos reales.

### 3.5 Tolerancia al SRI de pruebas

`celcer.sri.gob.ec` a veces devuelve `DEVUELTO` en pruebas por razones no obvias (mantenimientos, catálogos desactualizados). El test debe:
- Reintentar automáticamente hasta 2 veces con un backoff pequeño.
- Loguear el `mensaje` completo del SRI en caso de rechazo para diagnóstico.
- Fallar el test si tras los reintentos no llega a `AUTORIZADO`.

### 3.6 No commitear secretos

- El P12 y la passphrase van **fuera del repo** (variable de entorno o `test-secrets/` gitignorado).
- El `.env` real ya está en `.gitignore` — verificar antes de cualquier PR.

---

## 4. Estructura de archivos propuesta

```
billing-service/
  src/test/
    java/com/gymadmin/billing/integration/
      EndToEndSriCertificacionIT.java      ← nuevo IT
    resources/
      end-to-end-sri/
        README.md                          ← cómo obtener y colocar el P12
        # (el .p12 no se commitea)
```

Variables de entorno esperadas por el test:
- `SRI_TEST_CERT_PATH` — path absoluto al `.p12`.
- `SRI_TEST_CERT_PASSPHRASE` — passphrase en claro (se encripta en `setUp`).
- `SRI_TEST_RUC` — RUC del contribuyente.
- `SRI_TEST_RAZON_SOCIAL` — nombre.
- `SRI_TEST_RECEPTOR_CEDULA` — cédula receptor válida.

Si alguna falta: `@Disabled`.

---

## 5. Referencias

- Ficha técnica SRI v2.24 (ver [ADR 001](adr/001-version-xml-sri.md)).
- `docs/billing-service/flows/sri-submission-retry.md` — pipeline actual.
- `billing-service/src/main/resources/application.yml` — URLs y config actuales del SRI.
- Guía de contribuyentes SRI para pruebas de certificación electrónica (buscar "Ambiente de pruebas SRI Ecuador facturación electrónica").

---

## 6. Deuda técnica relacionada

Estos ítems no bloquean el test pero conviene saberlos al retomarlo:

| Tema | Nota |
|------|------|
| Bug auth Postgres local | Afecta a **todos** los IT del servicio, no solo el nuevo. Resolver primero. |
| JDK mismatch `pom.xml` v25 vs runner v21 | Pasar `-Djava.version=21` a `mvn`. Ver `memory/project_billing_jdk_mismatch.md`. |
| `TODO(G6-follow)` tarifa IVA hardcoded en `FacturaXmlBuilder` y `NotaCreditoXmlBuilder` | Bloquea reportar IVA distinto a 15% desde el request. Se resuelve cuando el DTO exponga `codigoTarifaIva` por detalle. |
| `TODO(G9)` `tipoPago="20"` hardcoded en `AtsXmlBuilder` | Bloquea ATS correcto con formas de pago reales. Se resuelve en Fase 3. |
| `TODO(G3-followup)` cierre APROBADA→EJECUTADA cuando NC autoriza en retry | Hoy requiere re-aprobación manual si la NC de Flujo B queda pendiente. |
