# Roadmap billing-service — Fases de implementación SRI 2025-2026

> **ESTADO:** 📋 **División en fases — pendiente de arrancar desarrollo**
> **Fecha:** 2026-07-11
> **Base:** [gap-analysis-sri-2026.md](gap-analysis-sri-2026.md) (detalle de los 13 GAPs) y [anulacion-sri.md](anulacion-sri.md) (diseño de G3).
> **Decisión de producto:** primero corregir los GAPs de backend; el rediseño de pantallas va después.

Cuando arranquemos el desarrollo de una fase concreta, se abre la checklist detallada por GAP en el gap-analysis (o en el doc específico si existe).

---

## Mapa de dependencias

```
Fase 0 (Fundamentos)              Fase 1 (Pipeline SRI)
  G5 Secuencial atómico            G2 Transmisión inmediata
  G6 Catálogos consultables ──┐    G1 XML v2.2.0 ◄──── G6
                              │           │
                              ▼           ▼
                        Fase 2 (Ciclo de vida)
                          G4 Notas de crédito
                          G3 Anulación fiscal ◄── G4
                                    │
                                    ▼
                        Fase 3 (Cumplimiento)
                          G10 Bancarización ◄── G6
                          G9 ATS completo ◄── G4
                                    │
                                    ▼
                        Fase 4 (Complementarios opcionales)
                          G7 Notas de débito
                          G8 Retenciones
                          G13 Guías de remisión
                                    │
                                    ▼
                        Fase 5 (Frontend)
                          Rediseño auth-service-frond-end
                                    │
                                    ▼
                        Fase 6 (Diferibles / externos)
                          G11 QR en RIDE
                          G12 Sync finanzas.ingresos
```

---

## Fase 0 · Fundamentos

**Objetivo:** eliminar bugs latentes y quitar hardcodes **antes** de agregar código nuevo encima.
**GAPs:** G5 (secuencial atómico), G6 (catálogos SRI consultables).
**Dependencias:** ninguna. Los dos GAPs son paralelizables (dos personas).
**Habilita:** todas las fases posteriores.

---

## Fase 1 · Pipeline SRI moderno

**Objetivo:** cumplir con la transmisión inmediata desde 2026-01-01 y modernizar la versión del XML antes de emitir nuevos tipos de comprobante.
**GAPs:** G2 (transmisión inmediata), G1 (XML v2.1.0 → v2.2.0).
**Dependencias:** G1 depende de G6 (Fase 0) para catálogos consistentes.
**Habilita:** que NC, ND y retención hereden el pipeline correcto en las fases siguientes.

---

## Fase 2 · Ciclo de vida del comprobante

**Objetivo:** cerrar el ciclo emisión → corrección → anulación. Condición legal para operar con RUC real.
**GAPs:** G4 (notas de crédito tipo 04), G3 (anulación fiscal).
**Dependencias:** G3 depende de G4 (la anulación fuera del día 7 se hace vía NC). G4 depende de Fase 1.
**Habilita:** Fase 3 (ATS completo requiere NC).

---

## Fase 3 · Cumplimiento tributario — ✅ Completada 2026-07-13

**Objetivo:** cerrar validaciones de negocio y reportes que auditará el SRI en cruces automáticos.
**GAPs:** G10 (bancarización sobre USD 500), G9 (ATS completo).
**Dependencias:** G10 depende de G6 (Fase 0). G9 depende de G4 (Fase 2).
**Habilita:** contrato REST estable para arrancar Fase 5.

**G10 — Bancarización.** El catálogo `sri.formas_pago` no tenía forma de saber qué códigos usan el sistema financiero (el gap-analysis asumía que G6 lo había dejado, pero no). Se agregó el flag `bancarizada` (story Liquibase `202607_GYM-002`), marcando 16, 17, 18, 19 y 20. La regla vive en `ComprobanteService.validarBancarizacion()`: si el total supera USD 500, las formas bancarizadas deben cubrir **el excedente** sobre ese umbral (no el total), lo que permite pagos mixtos legítimos —en una factura de 600 se pueden pagar 100 en efectivo si 500 van por tarjeta—. Devuelve `422` si no se cumple.

> ⚠️ **Ojo con los códigos:** el gap-analysis §G10 los lista mal (dice 16=transferencia, 17=giro, 18=tarjeta débito). El catálogo real del SRI —y el seed de la BD— dice **16=tarjeta débito, 17=dinero electrónico, 18=tarjeta prepago, 19=tarjeta crédito, 20=otros con utilización del sistema financiero**. El código **01 es literalmente `SIN_UTILIZACION_SISTEMA_FINANCIERO`**.

**G9 — ATS.** Resultó ser una **reescritura**, no el parche que suponía el gap-analysis. Al contrastar con el [XSD oficial](https://descargas.sri.gob.ec/download/anexos/ats/ats.xsd) se descubrió que el XML que generábamos **no validaba contra el esquema en absoluto**: raíz `<ats>` en vez de `<iva>`, y nombres de campo inventados (`tipoComp`, `numComp`, `denoComp`, `tipoPago`, `valRetBien10`…) que no existen en el esquema. `AtsXmlBuilder` se rehízo entero contra el XSD; ver [api/reportes.md](../api/reportes.md) para la estructura. El XSD quedó versionado en `billing-service/src/test/resources/sri/ats.xsd` y `AtsXmlBuilderTest` **valida el XML generado contra él** — sin ese test el error habría vuelto a pasar inadvertido.
>
> Correcciones a suposiciones previas: las notas de crédito **no tienen nodo propio**, van en `detalleVentas` distinguidas por `tipoComprobante = 04` y con importes **en positivo** (`monedaType` no admite signo; solo el `totalVentas` global netea). Y `detalleVentas` **agrupa** por cliente+tipo: `numeroComprobantes` es un conteo, no el número de una factura.

---

## Fase 4 · Documentos complementarios opcionales

**Objetivo:** habilitar tipos de comprobante que no todo gym necesita. Cada GAP se implementa **solo si aplica al modelo de negocio**.
**GAPs:** G7 (notas de débito — si cobran mora), G8 (retenciones — si son agente de retención), G13 (guías de remisión — si mueven mercancía física).
**Dependencias:** Fase 1.
**Habilita:** completar G9 (retenciones en ATS) cuando aplique.

---

## Fase 5 · Frontend de facturación — 📋 Spec de diseño lista (2026-07-14)

> 🔴 **BLOQUEANTE DE ONBOARDING descubierto 2026-07-14 — va ANTES que las pantallas de emisión.**
> **Hoy ningún gimnasio puede activar la facturación por sí mismo.** `billing-service` necesita tres filas (`config_sri`, `certificados`, `puntos_emision`) que **solo se pueden crear con SQL a mano**: el `AdminController` expone únicamente 3 endpoints de **lectura**, no existe `ConfigSriUseCase` ni ningún `POST`/`PUT` de configuración. Sin esas filas, `POST /facturas` responde `404`.
> En un SaaS multi-tenant esto no es una comodidad ausente: **es la condición para que la facturación sea vendible.** Requerimiento completo (wizard de 4 pasos + los 7 endpoints que faltan): [wizard-configuracion-sri.md](wizard-configuracion-sri.md).

> ⚠️ **Corrección al plan original.** Esta fase decía "rediseñar las pantallas existentes (emisión, anulación, listado)". **Esa premisa era falsa:** se verificó contra el código que **no existe ninguna pantalla de facturación** en `auth-service-frond-end`. No hay nada que rediseñar — se construye el módulo entero desde cero, y es bastante más trabajo del que la fase sugería. Hoy los **23 endpoints de billing-service no tienen ningún consumidor** (ni el frontend, ni core-service).

**Objetivo:** construir el módulo de facturación en `auth-service-frond-end` sobre el contrato REST ya estable.
**Spec de diseño:** ✅ [docs/auth-service-frond-end/facturacion-diseno.md](../../auth-service-frond-end/facturacion-diseno.md) — 6 pantallas, flujos paso a paso, microcopy y traducción de errores SRI a lenguaje humano.
**Dependencias:** Fase 3 completa (contrato REST congelado); opcionalmente Fase 4.
**Siguiente paso:** resolver las **10 preguntas abiertas** de la sección 15 de la spec (varias son bloqueantes y dependen del backend: los permisos `facturacion:*` **no existen** en la BD, y no está claro de dónde saca la UI `cod_establecimiento`/`cod_punto_emision`/`codigo_numerico`). Recién después, `frontend-developer`.

**Decisiones de producto tomadas (2026-07-14):**
- **Usuario objetivo: recepcionista poco adaptativo a la tecnología.** Es la restricción #1 y subordina todo el diseño. El SRI **nunca** debe bloquear al que atiende el mostrador: si falla, la venta se completa igual, la factura se reintenta sola y el mensaje es *"procesándose"*, nunca un código tributario crudo.
- **Solo facturación manual.** La automática (emitir al vender una membresía) **es imposible hoy**: `core.membresias` guarda `precioPagado` pero **no guarda la forma de pago**, y el SRI la exige. Habilitarla es un requerimiento que arranca en `core-service`, no en el frontend.
- **Anulación en un solo paso** para quien tenga permiso de aprobar (el dueño de un gym chico es el mismo que atiende). El workflow `SOLICITADA→APROBADA→EJECUTADA` se conserva en el backend para la auditoría, pero la UI no obliga a recorrerlo de a uno.
- **Consumidor final soportado**, con aviso claro de que esa factura no podrá anularse con NC.
- **Reutilización futura (otros SaaS ecuatorianos): no se empaqueta como librería ahora.** Se traza la frontera `src/lib/sri/` (lógica pura: validación cédula/RUC, bancarización, IVA, ventana de anulación — **prohibido importar React/axios/PrimeReact**) vs `src/ui/features/facturacion/`. Extraer al segundo caso de uso, no al primero.

---

## Fase 6 · Diferibles y dependencias externas

**Objetivo:** cerrar UX y sincronización cross-service cuando el resto del sistema los requiera.
**GAPs:** G11 (código QR en RIDE), G12 (sync `facturacion.comprobantes` → `finanzas.ingresos` — bloqueado por la existencia de finance-service).

---

## Pendientes transversales (no ligados a una fase)

- **⚠️ Migración `202607_GYM-002` sin aplicar fuera de local** — el flag `sri.formas_pago.bancarizada` (G10) se aplicó **solo a la BD local** (`gym-postgres`, la que usan los IT). No se corrió `./gradlew update` porque `gym-administrator/gradle.properties` apunta a una **base Neon en la nube** (`neondb`), no a la local: aplicar ahí es una escritura sobre un ambiente posiblemente compartido y debe hacerlo/coordinarlo el equipo. Sin esta migración, `billing-service` falla al emitir (la query de `sri.formas_pago` selecciona una columna inexistente).

- **🔴 `core.membresias` no guarda la forma de pago** — el modelo tiene `precioPagado` pero **no** `forma_pago`. El SRI **exige** la forma de pago en el XML, y desde G10 se valida bancarización sobre USD 500. Consecuencia: **la facturación automática al vender una membresía es imposible hoy**, y por eso el módulo de frontend (Fase 5) se especificó como 100 % manual. Habilitarla requiere agregar el campo al formulario de venta de `core-service` — es un requerimiento propio, no una tarea de billing.

- **🔴 El XML del reenvío asíncrono miente sobre la forma de pago** — en `EnvioSriService.buildXmlDesdeBD()` (el camino de la cola de reintentos y del `/enviar` manual), el XML **no lee los pagos reales**: sintetiza uno solo con `formaPago="01"` (*sin utilización del sistema financiero* = efectivo) por el total. Una factura pagada con tarjeta se declara al SRI como efectivo si le toca ese camino. **Contradice al ATS** (que sí lee `facturacion.comprobante_pagos`) y **contradice la regla de bancarización de G10** — validamos en la entrada algo que el XML de salida desmiente. El `TODO(G6-follow)` que lo acompaña **está obsoleto**: dice *"cuando exista la tabla `facturacion.comprobante_pagos`"* y esa tabla **ya existe y está poblada** (el `AtsXmlBuilder` la consume). Arreglo corto: leer los pagos reales en vez de fabricarlos. El gap-analysis lo clasificó como 🟡 media / "no bloquea producción", pero esa evaluación es **anterior a G10** y hay que reconsiderarla.

- **G12 ya no está bloqueado** — el [gap-analysis §G12](gap-analysis-sri-2026.md#g12--sincronización-con-finanzasingresos-no-existe) dice *"esto es esperado porque `finance-service` aún no existe"*. **Sí existe** (implementado, 13 endpoints, ver [docs/finance-service/](../../finance-service/INDEX.md)). La dependencia externa que difería G12 a Fase 6 desapareció; queda decidir el contrato (outbox vs polling).

- **Test IT end-to-end contra SRI de pruebas** — enviar factura real a `celcer.sri.gob.ec` para validar el pipeline completo con certificado real. Ver [it-end-to-end-sri-pruebas.md](it-end-to-end-sri-pruebas.md) para el checklist de 7 prerrequisitos (certificado P12, RUC, BD operacional, receptor, red).
- ~~**Bug auth Postgres local**~~ — ✅ **Resuelto 2026-07-13.** Causa: `billing-service` tenía la dependencia `dotenv-java` en el `pom.xml` pero nunca escribió la clase `DotEnvInitializer` (los otros 4 servicios sí la tienen). Sin ella nadie cargaba el `.env`, `${DB_USER}`/`${DB_PASSWORD}` de `application-integration.yml` quedaban sin resolver y Postgres recibía credenciales vacías. Se creó `DotEnvInitializer` y se registró en `IntegrationTestBase` vía `@ContextConfiguration(initializers = ...)`. Los 115 tests pasan.
- **`AnulacionFlujoBIT` es flaky** — falla de forma intermitente al arrancar su contexto con `PostgresqlAuthenticationFailure` / `Failed to obtain R2DBC Connection`, y pasa al reintentar (verificado: 3/3 en verde tras un fallo, y la suite completa `fulltest` pasa 126/126). No es el bug de `DotEnvInitializer` —ese quedó resuelto— ni agotamiento de conexiones (Postgres tiene 100 y se usan ~13). Sospecha: carrera al levantar el pool durante el arranque del contexto. Reintentar si aparece en CI; investigar si se vuelve frecuente.
- **JDK mismatch `pom.xml`** — declara `<java.version>25</java.version>` pero el runner local es JDK 21. Workaround: pasar `-Djava.version=21` a todo `mvn`. **Importante:** el override requiere `mvn clean`; sin él, quedan `.class` de una corrida previa compilados a class file 69 y surefire aborta con "compiled by a more recent version of the Java Runtime". Decidir con el equipo si bajar el pom o subir el JDK.
- **`TODO(G6-follow)` tarifa IVA hardcoded** — `FacturaXmlBuilder` y `NotaCreditoXmlBuilder` hardcodean IVA 15% por línea. Se resuelve cuando los DTOs expongan `codigoTarifaIva` por detalle.
- ~~**`TODO(G9)` `tipoPago="20"` hardcoded en `AtsXmlBuilder`**~~ — ✅ **Resuelto 2026-07-13 (G9).** El campo `tipoPago` **no existe** en el esquema del SRI: las formas de pago van en un nodo `formasDePago` con N `formaPago`, que ahora se leen de `facturacion.comprobante_pagos`. El hardcode desapareció junto con el campo.
- **`TODO(G3-followup)` cierre APROBADA→EJECUTADA** — cuando una NC de Flujo B queda pendiente y autoriza después vía scheduler G2, la anulación no cierra automáticamente. Requiere un job o hook.

---

## Estado global

| GAP | Fase | Estado | Detalle |
|-----|:----:|--------|---------|
| G5 Secuencial atómico | 0 | ✅ Completado | [gap-analysis §G5](gap-analysis-sri-2026.md#g5--secuencial-no-se-reserva-atómicamente) |
| G6 Catálogos SRI | 0 | ✅ Completado | [gap-analysis §G6](gap-analysis-sri-2026.md#g6--catálogos-sri-no-consultables-desde-código) |
| G2 Transmisión inmediata | 1 | ✅ Completado | [gap-analysis §G2](gap-analysis-sri-2026.md#g2--transmisión-inmediata-obligatoria-desde-2026-01-01) · [flows/sri-submission-retry.md](../flows/sri-submission-retry.md) |
| G1 XML v2.1.0 → v2.24 | 1 | ✅ Completado | [gap-analysis §G1](gap-analysis-sri-2026.md#g1--versión-de-la-ficha-técnica-xml-v210-vs-v232) · [ADR 001](adr/001-version-xml-sri.md) |
| G4 Notas de crédito | 2 | ✅ Completado | [gap-analysis §G4](gap-analysis-sri-2026.md#g4--notas-de-crédito-tipo-04) · [api/notas-credito.md](../api/notas-credito.md) |
| G3 Anulación fiscal | 2 | ✅ Completado | [anulacion-sri.md](anulacion-sri.md) · [api/anulaciones.md](../api/anulaciones.md) · [flows/anulacion-nc.md](../flows/anulacion-nc.md) |
| G10 Bancarización USD 500 | 3 | ✅ Completado | [gap-analysis §G10](gap-analysis-sri-2026.md#g10--sin-validación-de-bancarización-sobre-usd-500) · flag `sri.formas_pago.bancarizada` (story `202607_GYM-002`) |
| G9 ATS completo | 3 | ✅ Completado | [gap-analysis §G9](gap-analysis-sri-2026.md#g9--ats-mensual-solo-incluye-tipo-01) · [api/reportes.md](../api/reportes.md) · XML validado contra el XSD oficial |
| G7 Notas de débito | 4 | 📋 Opcional | [gap-analysis §G7](gap-analysis-sri-2026.md#g7--notas-de-débito-tipo-05) |
| G8 Retenciones | 4 | 📋 Opcional | [gap-analysis §G8](gap-analysis-sri-2026.md#g8--retenciones-tipo-07) |
| G13 Guías de remisión | 4 | 📋 Opcional | [gap-analysis §G13](gap-analysis-sri-2026.md#g13--guías-de-remisión-tipo-06) |
| Rediseño de pantallas | 5 | 📋 Post-backend | Spec por `ui-ux-designer` |
| G11 QR en RIDE | 6 | 📋 Diferido | [gap-analysis §G11](gap-analysis-sri-2026.md#g11--ride-pdf-sin-código-qr) |
| G12 Sync finanzas.ingresos | 6 | 📋 Externa | [gap-analysis §G12](gap-analysis-sri-2026.md#g12--sincronización-con-finanzasingresos-no-existe) |
