# ADR 001 — Versión de la ficha técnica SRI para el XML de factura

> **Estado:** ✅ Aceptada
> **Fecha:** 2026-07-11
> **Fase / GAP:** Fase 1 · G1 del [roadmap SRI 2026](../roadmap-sri-2026.md)
> **Autor:** billing-service backend
> **Reemplaza a:** (ninguna)

## Contexto

`FacturaXmlBuilder` genera hoy comprobantes con `<factura ... version="2.1.0">`. Esa versión de la ficha técnica del SRI es previa a los cambios normativos de 2024 (IVA 15%) y 2024-2025 (IVA 8% feriado, ajustes de catálogos). Cronología relevante:

- **v2.1.0** — versión que emitía el gym. Predata la reforma del IVA 15%.
- **v2.2.0** — versión estable “intermedia” utilizada por buena parte del ecosistema entre 2023 y 2024. No incorpora oficialmente el código de tarifa `4` (IVA 15%).
- **v2.24** (2024-03) — primera versión de la ficha en publicar oficialmente el código de tarifa `4` (IVA 15%) en la Tabla 17. Sin cambios estructurales adicionales relevantes para servicios (que es lo que factura el gym).
- **v2.30** (nov-2024 aprox.) — añade el código `8` (IVA 8% feriado) y ajustes menores de catálogos.
- **v2.32** (nov-2025) — última versión conocida. Incorpora Anexo 25 (transporte comercial) — no aplica al modelo de negocio del gym — más ajustes ATS.

Además de la versión declarada, `FacturaXmlBuilder` **hardcodea** hoy la tarifa IVA 15% por línea (`codigo="2"`, `codigoPorcentaje="4"`, `tarifa="15.00"`, `valor = base * 0.15`), lo cual es incoherente con declararse en v2.1.0 (donde ese código formalmente no existía). Es una inconsistencia observable por el SRI que hasta ahora no ha producido rechazo porque el SRI valida laxamente la versión, pero con la **transmisión inmediata obligatoria desde 2026-01-01** (Boletín 072, Res. NAC-DGERCGC25-00000014) el margen de tolerancia se reduce y aumenta el riesgo de rechazo o glosa.

## Decisión

Se sube la versión del XML de `2.1.0` a **`2.24`**.

En código, la versión pasa a estar declarada como constante nombrada (`private static final String XML_VERSION = "2.24";` en `FacturaXmlBuilder`) para permitir tests y evitar duplicación.

## Alternativas consideradas

| Versión | Pros | Contras | Decisión |
|---------|------|---------|:--------:|
| **v2.1.0 (statu quo)** | Sin cambios en código, sin riesgo de regresión inmediato. | Incoherente con el hardcode IVA 15% del builder; expuesta a rechazo bajo transmisión inmediata; incumple el objetivo de G1. | ❌ |
| **v2.2.0** | Versión “intermedia” usada por comunidad; menor delta contra v2.1.0. | Tampoco incorpora oficialmente el código `4` (IVA 15%); resuelve poco frente al problema real. | ❌ |
| **v2.24** ✅ | Primera versión que oficializa el código de tarifa `4` (IVA 15%), que es el único código que emite el gym hoy. Muy documentada por la comunidad SRI. Mínimo cambio que resuelve la incoherencia. | No incorpora IVA 8% feriado (código `8`) — no lo emitimos hoy. | ✅ Elegida |
| v2.30 | Añade IVA 8% feriado (código `8`) — útil si el gym llega a facturar en un día feriado con IVA reducido. | Cambio más grande sin caso de uso inmediato; posponible. | ❌ diferida |
| v2.32 | Última publicada; incluye Anexo 25 (transporte comercial). | Anexo 25 no aplica al negocio; overhead de mantener paridad con la ficha más nueva sin beneficio funcional. | ❌ |

## Condiciones para revisitar

Se abrirá una nueva story de facturación (`YYYYMM_GYM-XXX`) para subir la versión más allá de v2.24 cuando ocurra **alguno** de los siguientes:

1. El gym empiece a emitir con IVA 8% feriado (código `8`) → subir a **v2.30** como mínimo.
2. El SRI publique una circular declarando obsoleta v2.24 con fecha límite → subir a la versión mínima aceptada.
3. Se implementen notas de crédito / débito / retenciones (Fases 2 y 4 del roadmap) y la versión del XML de esos comprobantes exija alinear también factura para consistencia de catálogos.
4. Se detecte rechazo del SRI con mensaje referenciando la versión.

## Impacto en catálogos SRI (schema `sri.*`)

Se revisaron los seeds actuales de `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/09_insert_seed_sri.sql` contra los códigos vigentes esperados en v2.24:

### `sri.tarifas_iva` — códigos vigentes en v2.24

| Código | Tarifa | Seed actual | Notas |
|:------:|--------|:-----------:|-------|
| 0 | IVA 0% | ✅ | OK |
| 2 | IVA 12% | ✅ | OK (cerrado 2024-03-31) |
| 3 | IVA 14% | ✅ | OK histórico (2016-2017) |
| 4 | IVA 15% | ✅ | OK — código nuevo v2.24 (vigente 2024-04-01→) |
| 6 | No objeto de IVA | ✅ | OK |
| 7 | Exento IVA | ✅ | OK |
| 8 | IVA 8% feriado | ✅ (seed) | **Presente**, pero solo entra oficialmente en v2.30. Al declararnos v2.24 no lo usamos aún; se mantiene en BD sin consumidor. Aceptable. |

**Faltantes contra la Tabla 17 de v2.24:** ninguno bloqueante para el subset "servicios de membresía IVA 15%". No se requiere migración.

### `sri.formas_pago` — códigos vigentes en v2.24 (Tabla 24)

Seed actual: `01`, `15`, `16`, `17`, `18`, `19`, `20`, `21`.

La Tabla 24 histórica del SRI incluye adicionalmente `02` (cheque), `03` (débito de cuenta bancaria), `04` (recolección), `05` (recaudación por convenios), `06` (banca electrónica), `07` (medios electrónicos de pago), `08` (efectivo). Algunos de estos códigos aparecen en versiones más recientes de la ficha. El seed actual cubre las formas de pago **efectivamente en uso por el gym** (`01` sin sistema financiero + bancarizadas `16`-`20`) y responde también a `15` (compensación de deudas) y `21` (endoso de títulos).

**Faltantes bloqueantes:** ninguno para el flujo de venta de membresía del gym. **No se requiere migración inmediata.** Si a futuro se necesita facturar con `02` (cheque) o `08` (efectivo denominado explícitamente), abrir una story dedicada.

### `sri.tipos_comprobante` — campo `version` del código `01`

El seed `('01', 'FACTURA', '2.1.0', TRUE)` en `09_insert_seed_sri.sql` declara la versión como **informativa**. Se verificó (grep en `billing-service/src/main`) que **ningún componente consume `TipoComprobanteSri.version()`** — solo se materializa en el record de dominio y se asserta en tests (`CatalogoSriServiceTest`, `CatalogoSriIT`). La versión efectiva del XML se toma de la constante `FacturaXmlBuilder.XML_VERSION`, no del catálogo.

**Consecuencia:** el seed queda **temporalmente desactualizado** (`2.1.0` en BD, `2.24` en el XML). Es una discrepancia cosmética que **no afecta la emisión**. Actualizarla requiere una nueva story de migración (regla del repo: nunca se modifica el baseline `202605_GYM-001` en sitio).

## Propuesta de nueva story de migración (fuera del alcance de G1)

Título tentativo: **`YYYYMM_GYM-XXX` — Actualizar catálogo `sri.tipos_comprobante` a versiones de ficha vigentes**.

Alcance sugerido:

1. `UPDATE sri.tipos_comprobante SET version = '2.24' WHERE codigo = '01'`.
2. Evaluar si actualizar también `04` (NC), `05` (ND), `06` (guía), `07` (retención) cuando el código Java correspondiente empiece a emitirlas (Fases 2 y 4). Mientras no se emitan, mantener sus versiones históricas es inofensivo.
3. Añadir `partial-changelog.yml` con changeSet `GYM-XXX-1` y DDL `01_update_sri_tipos_comprobante_version.sql`.

**Bloquea:** nada urgente. Se puede diferir hasta la siguiente ventana de migración de la BD de facturación (junto con G2 o con la implementación de NC en Fase 2). Recomendado agrupar.

**No se crea aquí** — solo se documenta el gap por la regla del repo "el DBA agent crea los changesets".

## Alcance NO cubierto por esta ADR

- **IVA hardcoded por línea de detalle en `FacturaXmlBuilder` líneas 170-177.** Es un `TODO(G6-follow)` conocido: aplicará cuando el DTO `EmitirFacturaCommand.DetalleFacturaItem` exponga `codigoTarifaIva` y el builder consulte `CatalogoSriService` por detalle. Sigue siendo IVA 15% (`4`) hardcoded — coherente con v2.24, pero fuera del alcance de G1.
- **Placeholder XML en `EnvioSriService.buildXmlFromComprobante` (línea 317).** Es un fallback usado solo cuando no hay XML firmado disponible en el flujo de reintentos. Se actualiza a `version="2.24"` como consecuencia mecánica (misma constante), pero no vive un contrato con SRI real.
- **Transmisión inmediata (G2)** — abordada en tarea separada.

## Referencias

- [gap-analysis-sri-2026.md §G1](../gap-analysis-sri-2026.md#g1--versión-de-la-ficha-técnica-xml-v210-vs-v232)
- [roadmap-sri-2026.md — Fase 1](../roadmap-sri-2026.md#fase-1--pipeline-sri-moderno)
- Boletín 072 SRI — transmisión obligatoria 2026
- Circular NAC-DGECCGC25-00000006 — IVA 15% vigente 2026
