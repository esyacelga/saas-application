# GAP-analysis SRI Ecuador 2026 — billing-service

> **ESTADO:** 📋 **Análisis de brechas — hallazgos por atender**
> **Prioridad:** 🔴 Alta (varios GAPs bloquean cumplimiento tributario 2026)
> **Fecha del análisis:** 2026-07-11
> **Alcance:** Cruce de normativa SRI 2025-2026 vs. código actual del `billing-service` y la BD `facturacion`+`sri`.
> **Complementa** a [anulacion-sri.md](anulacion-sri.md); no la reemplaza.

Este documento **no rediseña** la solución. Solo lista los GAPs cruzados contra la normativa vigente, para que se prioricen y planifiquen por separado.

---

## 1. Resumen ejecutivo

| # | GAP | Severidad | Bloquea producción |
|---|-----|-----------|:------------------:|
| G1 | Ficha técnica del XML es v2.1.0; SRI publicó v2.32 (nov-2025) — ✅ resuelto: subido a v2.24 (ver [ADR 001](adr/001-version-xml-sri.md)) | 🔴 Alta | Sí |
| G2 | Transmisión inmediata obligatoria desde 2026-01-01 — ✅ resuelto: pipeline síncrono con timeout de 15 s dentro del POST; la cola queda como fallback (ver [flows/sri-submission-retry.md](../flows/sri-submission-retry.md)) | 🔴 Alta | Sí |
| G3 | Anulación fiscal — ✅ resuelto: `AnulacionService` con Flujo A (portal SRI) y Flujo B (dispara G4 NC dentro del pipeline síncrono), autorización por rol, validaciones fiscales (ventana día 7, consumidor final, motivo), ver [api/anulaciones.md](../api/anulaciones.md) · [flows/anulacion-nc.md](../flows/anulacion-nc.md) | 🔴 Alta | Sí |
| G4 | Notas de crédito (tipo 04) — ✅ resuelto: `NotaCreditoService`, `NotaCreditoXmlBuilder` v1.1.0, `POST /api/v1/notas-credito` reutiliza el pipeline síncrono G2 (ver [api/notas-credito.md](../api/notas-credito.md)) | 🔴 Alta | Sí (dependencia de G3) |
| G5 | Secuencial NO se reserva atómicamente contra BD; lo provee el cliente en el request | 🔴 Alta | Sí (riesgo de duplicados) |
| G6 | Catálogos SRI en BD sin usar; código tiene tarifa IVA, tipo comprobante, forma de pago hardcoded | 🟡 Media | No |
| G7 | Notas de débito (tipo 05) — sin tabla, sin código; el gym la necesita para cobrar mora | 🟡 Media | No hoy, sí a futuro |
| G8 | Comprobantes de retención (tipo 07) — sin tabla, sin código | 🟡 Media | Depende del tipo de contribuyente |
| G9 | ATS mensual reporta solo tipo 01; ignora NC, ND, retenciones | 🟡 Media | Sí (declaración incompleta) |
| G10 | Bancarización obligatoria sobre USD 500 — sin validación en código | 🟡 Media | No (riesgo tributario) |
| G11 | RIDE PDF no incluye código QR con la clave de acceso | 🟢 Baja | No |
| G12 | Sincronización `facturacion.comprobantes` → `finanzas.ingresos` no existe | 🟢 Baja | No (dependencia de finance-service) |
| G13 | Guías de remisión (tipo 06) — sin tabla, sin código | 🟢 Baja | Solo si venden mercancía física |

---

## 2. Cambios normativos SRI 2025-2026 relevantes

| Cambio | Fecha vigencia | Fuente |
|--------|:--------------:|--------|
| Nueva ficha técnica **v2.32** (tarifas IVA tabla 17, catálogo ATS de retenciones, formas de pago tabla 24, Anexo 25 transporte comercial) | 2025-11-01 | Ficha técnica v2.32 |
| Transmisión **inmediata / en tiempo real** de comprobantes al SRI (elimina los 4 días hábiles previos) | 2026-01-01 | Res. NAC-DGERCGC25-00000014, Boletín 072 |
| Guía de remisión electrónica debe transmitirse **antes** del traslado físico | 2026-01-01 | Res. NAC-DGERCGC25-00000017 |
| Ventana de anulación reducida a **día 7 del mes siguiente** (antes día 10) | 2025-08-01 | Boletín 033 |
| IVA 15% (código 4 tabla 17) vigente para 2026; facturar al 12% es contravención | 2025-12-26 | Circular NAC-DGECCGC25-00000006 |
| Bancarización obligatoria sobre USD 500 (formas de pago códigos 16-20) | Vigente | Ficha técnica |
| Clave temporal de contingencia **eliminada** | Vigente | Ficha técnica |
| Multas por incumplimiento: USD 50–482 por evento (base SBU 2026 = 482) | Vigente | Res. NAC-DGERCGC24-00000022 |

---

## 3. GAPs detallados

### G1 — Versión de la ficha técnica (XML v2.1.0 vs v2.32)

> **Estado:** ✅ Resuelto en 2026-07-11 subiendo a **v2.24** (mínima que oficializa el código de tarifa `4` IVA 15%). Ver [ADR 001](adr/001-version-xml-sri.md).

**Situación:** `FacturaXmlBuilder.java:67` genera `<factura ... version="2.1.0">`. El SRI publicó **v2.32 en noviembre de 2025**.

**Impacto de las diferencias más relevantes:**
- Tabla 17 (tarifas IVA) — códigos 4, 5 y 10 pueden no estar reflejados en el mapeo del código.
- Tabla 24 (formas de pago) — códigos actualizados.
- Sección 8.15 (retenciones ATS) — catálogo actualizado.
- Anexo 25 (transporte comercial) — no aplica al gym.

**Riesgo:** el SRI aún acepta v2.1.0 para facturas simples, pero mensajes de error genéricos si algún código catalogado ha cambiado. Rechazos silenciosos posibles.

**Qué hacer:**
- Auditar diferencias entre v2.1.0 y v2.32 para el subset que emite el gym (factura tipo 01).
- Subir a **v2.1.0 → v2.2.0** como mínimo (versión estable aceptada por el SRI para facturas de servicios; menor riesgo).
- Documentar en un ADR qué versión seguimos y por qué.

**Archivos afectados:** `billing-service/.../infrastructure/adapter/out/xml/FacturaXmlBuilder.java`, `db/scripts/202605_GYM-001/ddl-facturacion/*` (seeds de `sri.tarifas_iva`, `sri.formas_pago` — verificar coherencia con tabla 17 y 24 vigentes).

---

### G2 — Transmisión inmediata obligatoria desde 2026-01-01

> **Estado:** ✅ **Resuelto en 2026-07-13 (Fase 1).** Ver [flows/sri-submission-retry.md](../flows/sri-submission-retry.md) para el flujo actualizado.

**Situación previa:** El flujo era 100% asíncrono vía cola:
1. `POST /comprobantes/facturas` → guardaba `GENERADO`, respondía HTTP 201.
2. `RetrySchedulerService` procesaba cada **60 segundos**. Primer intento del backoff comenzaba al minuto 1.

**Normativa 2026:** el comprobante debe transmitirse al SRI **en tiempo real** (mismo día, tolerancia de minutos). Fuente: Boletín 072, Res. NAC-DGERCGC25-00000014.

**Solución implementada (Fase 1 · G2):**
- **`ComprobanteService.emitirFactura`** ahora dispara síncronamente `EnvioSriService.procesarEmisionInmediata` después de persistir el `Comprobante` en `GENERADO`.
- Pipeline síncrono: firmar XML v2.24 → RECEPCION SOAP → AUTORIZACION SOAP → generar RIDE, todo bajo un timeout configurable (`sri.timeout.envio-seconds`, default 15 s).
- **HTTP 201 siempre** cuando la factura queda persistida; el `estado` en el body refleja el resultado: `AUTORIZADO` / `DEVUELTO` / `NO_AUTORIZADO` / `ERROR`.
- **Cola como fallback**: solo se crea fila en `facturacion.cola_envio` cuando el pipeline síncrono falla. Si es timeout / error de red, `proxima_ejecucion = now()` para reintento inmediato del scheduler (dentro de 60 s). Si es DEVUELTO / NO_AUTORIZADO, se usa el backoff `{1, 5, 15, 60, 240}` min existente.
- **Métricas nuevas:** `sri.emision.duracion` (Timer, p50/p95/p99 del pipeline síncrono) y `sri.emision.timeouts` (Counter). SLO: p95 < 30 s en primer envío, p99 < 5 min contando fallback.
- **Bug corregido de paso:** `EnvioSriService.buildXmlFromComprobante` era un placeholder inválido `<factura ... version="2.24"><placeholder ... /></factura>`. Ahora reconstruye el XML real llamando a `FacturaXmlBuilder.buildXml(...)`.

**Archivos afectados:**
- `billing-service/src/main/java/.../application/service/ComprobanteService.java` — nuevo path síncrono.
- `billing-service/src/main/java/.../application/service/EnvioSriService.java` — extraído `procesarEmisionInmediata`; corregido `buildXmlFromComprobante`.
- `billing-service/src/main/java/.../infrastructure/config/SriTimeoutProperties.java` — nueva.
- `billing-service/src/main/java/.../infrastructure/config/BillingMetricsConfig.java` — nuevas métricas.
- `billing-service/src/main/resources/application.yml` — nueva property `sri.timeout.envio-seconds`.
- `billing-service/src/test/java/.../unit/EnvioSriServiceTest.java` — nuevo.
- `billing-service/src/test/java/.../infrastructure/adapter/in/web/EmisionInmediataTimeoutIT.java` — nuevo.

---

### G3 — Anulación fiscal SRI

> **Estado:** ✅ **Resuelto en 2026-07-13 (Fase 2).** Ver [api/anulaciones.md](../api/anulaciones.md) para el contrato REST y [flows/anulacion-nc.md](../flows/anulacion-nc.md) para la máquina de estados y flujos.

**Solución implementada:**
- **Dominio:** `Anulacion` (Lombok `@Data @Builder`), enum `EstadoAnulacion` (`SOLICITADA`/`APROBADA`/`RECHAZADA`/`EJECUTADA`), port entrada `AnulacionUseCase`, port salida `AnulacionRepository`.
- **Persistencia:** `AnulacionPersistenceAdapter` con `DatabaseClient` sobre `facturacion.anulaciones` (mismo patrón que `NotaCreditoReferenciaPersistenceAdapter`).
- **Caso de uso:** `AnulacionService` valida (motivo obligatorio, estado ∈ {AUTORIZADO, GENERADO}, `id_receptor != '9999999999999'`, ventana `hoy ≤ día 7 mes+1`, motivo del catálogo `sri.motivos_anulacion_nc` si viene), persiste `SOLICITADA`, y expone `aprobar / rechazar / confirmarSri / buscarPorId / historialPorComprobante / listar`.
- **Flujo B con G4:** al aprobar con `generar_nota_credito=true` dispara `NotaCreditoService.emitirNotaCredito` copiando detalles y total de la factura original. Si la NC llega a AUTORIZADO en el mismo request → anulación `EJECUTADA` + comprobante original `ANULADO`. Si queda transitoria, la anulación permanece `APROBADA` con `id_comprobante_nc` poblado.
- **Flag Flujo B sin DDL nuevo:** el campo `generar_nota_credito` no existe en la tabla; se persiste embebido en `observacion_resolucion` con el prefijo interno `[FLUJO_B][MOTIVO=<codigo>]`. El servicio strippea la metadata antes de mapear a DTO. Documentado en `AnulacionService`.
- **REST:** `POST /api/v1/comprobantes/{id}/anular` cambia a request con body; `AnulacionController` expone `aprobar/rechazar/confirmar-sri/buscarPorId/listar`; `MotivosAnulacionController` expone `GET /api/v1/sri/motivos-anulacion` para el catálogo.
- **Autorización inline:** solicitar admite cualquier staff con `id_compania` coincidente; aprobar/rechazar/confirmar requieren rol `admin_compania`/`super_admin`/`Dueño` (403 si no).
- **Notificaciones:** `EmailNotificationPort` extendido con `enviarSolicitudAprobada`, `enviarSolicitudRechazada`, `enviarNotaCreditoAceptacion` — todas best-effort (`onErrorResume → empty`) para no romper el flujo funcional.
- **`Clock` inyectable:** `ClockConfig` provee un `Clock` en zona `America/Guayaquil` para la validación de ventana temporal; los tests unitarios lo fijan a fechas deterministas.

**Archivos afectados:**
- `billing-service/src/main/java/.../domain/model/Anulacion.java` — nuevo.
- `billing-service/src/main/java/.../domain/model/EstadoAnulacion.java` — nuevo.
- `billing-service/src/main/java/.../domain/port/in/AnulacionUseCase.java` — nuevo.
- `billing-service/src/main/java/.../domain/port/out/AnulacionRepository.java` — nuevo.
- `billing-service/src/main/java/.../application/command/{SolicitarAnulacionCommand,AprobarAnulacionCommand,RechazarAnulacionCommand}.java` — nuevos.
- `billing-service/src/main/java/.../application/service/AnulacionService.java` — nuevo.
- `billing-service/src/main/java/.../application/service/ReporteService.java` — TODO(G9) para incluir NC/anulaciones en ATS.
- `billing-service/src/main/java/.../application/service/CatalogoSriService.java` — nuevo método `listarMotivosAnulacion`.
- `billing-service/src/main/java/.../application/service/ComprobanteService.java` — `anularComprobante` eliminado (delegado al nuevo controller).
- `billing-service/src/main/java/.../domain/port/in/ComprobanteUseCase.java` — `anularComprobante` eliminado.
- `billing-service/src/main/java/.../domain/port/out/CatalogoSriRepository.java` — `listMotivosAnulacionNc` agregado.
- `billing-service/src/main/java/.../domain/port/out/EmailNotificationPort.java` — 3 métodos nuevos G3.
- `billing-service/src/main/java/.../infrastructure/adapter/out/persistence/adapter/AnulacionPersistenceAdapter.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/out/persistence/adapter/CatalogoSriPersistenceAdapter.java` — `listMotivosAnulacionNc` agregado.
- `billing-service/src/main/java/.../infrastructure/adapter/out/email/EmailNotificationAdapter.java` — implementación de los 3 métodos G3.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/AnulacionController.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/MotivosAnulacionController.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/ComprobanteController.java` — `anularComprobante` con body + `GET /{id}/anulaciones` nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/dto/{SolicitarAnulacionRequest,AprobarAnulacionRequest,RechazarAnulacionRequest,AnulacionResponse,MotivoAnulacionResponse}.java` — nuevos.
- `billing-service/src/main/java/.../infrastructure/config/ClockConfig.java` — nuevo bean `Clock`.
- `billing-service/src/test/java/.../unit/AnulacionServiceTest.java` — 19 casos unitarios (ventana, consumidor final, estado, motivo, multi-tenant, Flujo A/B, transiciones, strip de metadata).
- `billing-service/src/test/java/.../infrastructure/adapter/in/web/AnulacionFlujoAIT.java` — Flujo A end-to-end + rol + multi-tenant + historial + motivos.
- `billing-service/src/test/java/.../infrastructure/adapter/in/web/AnulacionFlujoBIT.java` — Flujo B con NC mockeada AUTORIZADO.

**Pendientes/follow-ups:**
- **Reintento asíncrono para transiciones APROBADA→EJECUTADA en Flujo B** cuando la NC autorice más tarde (por retry del scheduler): documentado en `flows/anulacion-nc.md §9`. Hoy requiere re-aprobar manualmente.
- **G9 ATS**: incluir NC (`tipo=04`) y anulaciones en el ATS mensual — bloqueado por diseño de G9.

---

### G4 — Notas de crédito (tipo 04)

> **Estado:** ✅ **Resuelto en 2026-07-13 (Fase 2).** Ver [api/notas-credito.md](../api/notas-credito.md) para el contrato REST y ejemplos.

**Solución implementada:**
- **Dominio:** `NotaCreditoReferencia` (record de dominio) + `NotaCreditoReferenciaRepository` (port out).
- **Persistencia:** `NotaCreditoReferenciaPersistenceAdapter` con `DatabaseClient` sobre `facturacion.notas_credito_referencias` (mismo patrón que `SecuencialPersistenceAdapter`).
- **XML:** `NotaCreditoXmlBuilder` genera la ficha técnica NC **v1.1.0** con la API DOM (mismo patrón que `FacturaXmlBuilder`).
- **Caso de uso:** `NotaCreditoService.emitirNotaCredito` valida la factura original (existe, misma compañía, tipo `01`, estado `AUTORIZADO`, `valorModificacion` ≤ total), consulta el motivo contra `sri.motivos_anulacion_nc`, reserva secuencial `04` atómico, persiste la NC en `facturacion.comprobantes` con `id_comprobante_ref` apuntando a la factura y la fila de referencia denormalizada, y dispara el pipeline síncrono G2.
- **Pipeline G2:** se añadió `EnvioSriService.procesarEmisionInmediataConXml(comprobante, xmlSinFirmar)` — extrae la lógica común (firma → RECEPCION → AUTORIZACION) y acepta el XML ya construido. `procesarEmisionInmediata` (factura) ahora delega en ella. Cambio mínimo, retrocompatible con `ComprobanteService`.
- **API:** `POST /api/v1/notas-credito`, `GET /api/v1/notas-credito/{id}`, `GET /api/v1/notas-credito` — mismos JWT + multi-tenancy enforcement que factura.
- **Métrica:** `billing.comprobantes.emitidos{tipo=NOTA_CREDITO}` se incrementa por NC firmada (el bean ya existía en `BillingMetricsConfig`).

**Archivos afectados:**
- `billing-service/src/main/java/.../domain/model/NotaCreditoReferencia.java` — nuevo.
- `billing-service/src/main/java/.../domain/port/out/NotaCreditoReferenciaRepository.java` — nuevo.
- `billing-service/src/main/java/.../domain/port/in/NotaCreditoUseCase.java` — nuevo.
- `billing-service/src/main/java/.../application/command/EmitirNotaCreditoCommand.java` — nuevo.
- `billing-service/src/main/java/.../application/service/NotaCreditoService.java` — nuevo.
- `billing-service/src/main/java/.../application/service/EnvioSriService.java` — modificado: nueva variante `procesarEmisionInmediataConXml`, contador seleccionado por tipo de comprobante.
- `billing-service/src/main/java/.../infrastructure/adapter/out/persistence/adapter/NotaCreditoReferenciaPersistenceAdapter.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/out/xml/NotaCreditoXmlBuilder.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/NotaCreditoController.java` — nuevo.
- `billing-service/src/main/java/.../infrastructure/adapter/in/web/dto/EmitirNotaCreditoRequest.java` — nuevo.
- `billing-service/src/main/java/.../domain/port/out/ComprobanteRepository.java` — ampliado: `findByEmpresaAndTipo`, `countByEmpresaAndTipo`.
- `billing-service/src/main/java/.../infrastructure/adapter/out/persistence/repository/ComprobanteR2dbcRepository.java` — query nueva por tipo + `id_comprobante_ref`.
- `billing-service/src/test/java/.../unit/NotaCreditoServiceTest.java` — 12 casos unitarios.
- `billing-service/src/test/java/.../infrastructure/adapter/in/web/EmitirNotaCreditoIT.java` — 5 casos de integración.

---

### G5 — Secuencial NO se reserva atómicamente

**Situación:** el request `EmitirFacturaRequest` recibe el `secuencial` como campo obligatorio (9 dígitos), es decir, **el cliente lo provee**. La tabla `facturacion.secuenciales` está creada en BD con constraint unique por `(tipo, establecimiento, punto)` pero el código Java **no la consulta ni la incrementa**.

**Riesgo:** dos requests concurrentes con el mismo secuencial. El SRI rechazará el segundo con clave de acceso duplicada, pero el estado local puede quedar inconsistente (dos filas con misma clave o filas huérfanas). Peor caso: gap en la secuencia si un secuencial se reserva y no se emite.

**Qué hacer:**
- Migrar a **reserva del secuencial del lado del servidor**: cliente NO lo provee, el servicio hace `UPDATE ... RETURNING` sobre `facturacion.secuenciales` con lock optimista o `SELECT FOR UPDATE`.
- Documentar en `docs/billing-service/api/comprobantes.md` que el campo `secuencial` deja de ser input (breaking change para core-service).

**Archivos afectados:** `ComprobanteService.emitirFactura`, `SecuencialRepository` (nuevo), `EmitirFacturaRequest.java` (deprecar campo).

---

### G6 — Catálogos SRI no consultables desde código

**Situación:** los 6 catálogos SRI existen en BD con seed, pero **ninguno tiene entidad/repositorio Java**:

| Catálogo | Uso hardcoded en código |
|----------|-------------------------|
| `sri.tipos_comprobante` | `"01"` hardcoded en `ComprobanteService:66` |
| `sri.tipos_impuesto` | IVA implícito, sin lookup |
| `sri.tarifas_iva` | 15% hardcoded (código 4) |
| `sri.formas_pago` | Sin validación del código de forma de pago |
| `sri.tipos_identificacion_comprador` | Solo validación regex, sin lookup |
| `sri.motivos_anulacion_nc` | No usado |

**Riesgo:** si el SRI publica nuevas tarifas (5% servicios digitales, 10% zona franca, etc.) hay que hacer deploy de código, no solo migración de datos.

**Qué hacer:**
- Crear entities R2DBC read-only para los 6 catálogos.
- Cachear con TTL largo (Redis, 24h) por ser catálogo estático.
- Validar en la capa de aplicación que `formaPago`, `tipoIdReceptor`, `codigoTarifaIva` existan en el catálogo cargado.

---

### G7 — Notas de débito (tipo 05)

**Situación:** ningún artefacto en BD ni en código. `sri.tipos_comprobante` sí tiene el código `05` con versión `1.0.0`, pero no hay tabla `facturacion.notas_debito`.

**Uso concreto para el gym:** cobro de **mora** por membresía vencida. Hoy no hay forma legal de facturar el recargo por mora sin emitir ND o factura complementaria.

**Qué hacer (cuando aplique):**
- Diseñar tabla `facturacion.notas_debito` (referencia a factura original + monto adicional).
- Entity + repo + use case análogo a NC.
- XML builder para tipo 05 v1.0.0.
- Reutilizar SOAP adapter.

Bajar la prioridad si el gym no cobra mora electrónicamente.

---

### G8 — Retenciones (tipo 07)

**Situación:** ningún artefacto en BD ni en código. Catálogo `sri.tipos_comprobante` tiene código `07` con versión `2.0.0`.

**Aplicabilidad:**
- **RIMPE Negocio Popular** (ingresos < USD 20k): NO es agente de retención.
- **RIMPE Emprendedor** (USD 20k–300k): SÍ debe emitir retenciones si actúa como agente.
- **Régimen general**: SÍ.

**Riesgo si aplica:** desde 2026-01-01 las retenciones también van en tiempo real.

**Qué hacer:**
- Diseñar tablas `facturacion.retenciones` + `facturacion.retenciones_detalle` con FK a la factura del proveedor.
- Entity + repo + use case + builder XML v2.0.0 + controller.
- Determinar automáticamente cuándo la empresa emite retención (agente de retención vs. no).

---

### G9 — ATS mensual solo incluye tipo 01

**Situación:** `AtsXmlBuilder` genera el XML del ATS **hardcodeando `tipoComp='01'` y `tipoPago='20'`**. No incluye:
- Notas de crédito emitidas.
- Notas de débito emitidas.
- Retenciones (ni en compras ni en ventas).

**Riesgo:** si el gym emite ATS al SRI y hay diferencia con lo que el SRI ya recibió por transmisión inmediata, se abre un cruce automático y observación tributaria.

**Qué hacer (dependencia G4, G7):**
- Actualizar `AtsXmlBuilder` cuando NC y retenciones existan.
- Validar la ficha técnica **ATS julio-2025** (documento aparte de la ficha de comprobantes).
- Agregar tests de estructura contra un XSD oficial del ATS.

---

### G10 — Sin validación de bancarización sobre USD 500

**Situación:** el DTO `PagoItem` acepta cualquier código de `formaPago` sin validar contra el total. La regla SRI dice: **si `total > USD 500`, la forma de pago debe ser bancarizada** (códigos 16 transferencia, 17 giro, 18 tarjeta débito, 19 tarjeta crédito, 20 otros con utilización sistema financiero).

**Riesgo:** el SRI puede autorizar la factura pero luego observar el pago en cruces automáticos → glosa y multa.

**Qué hacer:**
- Regla de dominio en `ComprobanteService`: si `total > 500` → validar que al menos una `FormaPago` del array esté en `{16, 17, 18, 19, 20}` **y** cubra el total.
- Dependencia de G6 (catálogo `sri.formas_pago` con flag `bancarizada`).

---

### G11 — RIDE PDF sin código QR

**Situación:** `RidePdfBuilder` imprime la clave de acceso de 49 dígitos como **texto plano**. La ficha técnica recomienda incluir código de barras o QR para lectura en portal SRI.

**Impacto:** UX; no es incumplimiento estricto. Se puede posponer.

**Qué hacer:**
- Agregar `zxing-core` o `barbecue` a dependencies (LGPL o Apache — verificar compatibilidad).
- Renderizar QR con la clave de acceso al lado del texto.

---

### G12 — Sincronización con `finanzas.ingresos` no existe

**Situación:** cuando una factura llega a `AUTORIZADO` no se escribe fila alguna en `finanzas.ingresos`. Esto es esperado porque `finance-service` aún no existe.

**Impacto:** cuando `finance-service` arranque necesitará pollear la tabla o consumir un evento. Diseñar el contrato ahora ahorra retrabajo.

**Qué hacer (diferido):**
- Decidir: outbox pattern vs. polling con cursor.
- Documentar el contrato en `docs/gym-administrator/specs/finance-service.md`.

---

### G13 — Guías de remisión (tipo 06)

**Situación:** sin BD ni código. Solo el catálogo `sri.tipos_comprobante` lo tiene registrado.

**Aplicabilidad al gym:** solo si venden mercancía física (suplementos, ropa, equipos) y la mueven entre puntos. **No aplica al core del negocio de membresías.**

**Qué hacer:** dejarlo como "planned si aparece caso de uso". Prioridad 🟢.

---

## 4. Matriz de priorización sugerida

| Sprint | GAPs a atender | Justificación |
|--------|----------------|---------------|
| 1 (bloqueo legal) | G3 anulación · G4 NC · G5 secuencial · G2 tiempo real | Sin esto no se puede ir a producción con RUC real en 2026 |
| 2 (compliance) | G1 versión XML · G6 catálogos · G10 bancarización | Prevenir observaciones tributarias |
| 3 (completitud fiscal) | G9 ATS completo · G7 ND · G8 retenciones | Dependen del tipo de contribuyente del gym |
| 4 (UX / futuro) | G11 QR RIDE · G12 sync finanzas · G13 guías | Nice-to-have; no bloquean |

---

## 5. Fuentes oficiales SRI

1. [Ficha técnica de comprobantes electrónicos v2.32 (SRI, noviembre 2025)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/29562323-2e76-42f5-abb6-cb7ac542c3c6/FICHA%20TE%CC%81CNICA%20COMPROBANTES%20ELECTRO%CC%81NICOS%20ESQUEMA%20OFFLINE%20Versio%CC%81n%202.32.pdf)
2. [Boletín 072 — transmisión inmediata obligatoria 2026 (SRI)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/871db08d-cefa-467e-b418-4e4b24427334/BOLET%C3%8DN%20072%20-%20SRI%20FORTALECE%20LA%20MODERNIZACI%C3%93N%20TRIBUTARIA%20CON%20LA%20TRANSMISI%C3%93N%20OBLIGATORIA%20DE%20COMPROBANTES%20DE%20VENTA.pdf)
3. [Boletín 033 — nueva ventana de anulación día 7 (SRI)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/142630d3-569f-4cd2-a5a5-58557b7fc342/BOLET%C3%8DN%20033%20-%20SRI%20ESTABLECE%20NUEVAS%20REGLAS%20PARA%20LA%20ANULACI%C3%93N%20DE%20COMPROBANTES%20ELECTR%C3%93NICOS%20COMO%20PARTE%20DE%20SU%20ESTRATEGIA%20DE%20CONTROL.pdf)
4. [Facturación electrónica — página oficial SRI](https://www.sri.gob.ec/en/facturacion-electronica)
5. [Formularios e Instructivos — ATS y comprobantes](https://www.sri.gob.ec/en/formularios-e-instructivos)
6. [Ficha técnica ATS (SRI)](https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/72d717c2-88ed-47b7-baba-50b87b7198b7/Ficha%20Tecnica%20Transaccional%20Simplificado%20ATS.pdf)
7. [RIMPE — Régimen simplificado](https://www.sri.gob.ec/en/rimpe)

---

## 6. Archivos del repositorio referenciados

| GAP | Archivo | Nota |
|-----|---------|------|
| G1 | `billing-service/src/main/java/.../infrastructure/adapter/out/xml/FacturaXmlBuilder.java:67` | `version="2.1.0"` hardcoded |
| G2 | `billing-service/src/main/java/.../application/service/RetrySchedulerService.java` | Backoff `{1,5,15,60,240}` min |
| G2 | `billing-service/src/main/java/.../application/service/ComprobanteService.java:emitirFactura` | Flujo asíncrono actual |
| G4 | `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/20_create_table_facturacion_nc_referencias.sql` | Tabla ya modelada |
| G5 | `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/*_facturacion_secuenciales.sql` | Tabla creada pero no consultada |
| G5 | `billing-service/src/main/java/.../infrastructure/adapter/in/web/dto/EmitirFacturaRequest.java` | Campo `secuencial` como input |
| G6 | `gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/{07,08,09,10}_create_table_sri_*.sql` | 6 catálogos con seed |
| G9 | `billing-service/src/main/java/.../infrastructure/adapter/out/xml/AtsXmlBuilder.java` | `tipoComp='01'` hardcoded |
| G10 | `billing-service/src/main/java/.../infrastructure/adapter/in/web/dto/PagoItem.java` | Sin validación bancarización |
| G11 | `billing-service/src/main/java/.../infrastructure/adapter/out/pdf/RidePdfBuilder.java` | Sin QR |
