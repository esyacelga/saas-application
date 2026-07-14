# REQ — Wizard de configuración inicial de Facturación Electrónica

> **ESTADO:** 📋 **Planeado — sin implementar.** Guía de construcción; nada de lo aquí descrito existe hoy.
> **Fecha:** 2026-07-14
> **Motivación:** hoy **ningún gimnasio puede activar la facturación electrónica por sí mismo.** Es un bloqueante de onboarding para un SaaS.
> **Relacionado:** [facturacion-diseno.md](../../auth-service-frond-end/facturacion-diseno.md) (el módulo que este wizard desbloquea) · [roadmap-sri-2026.md](roadmap-sri-2026.md)

---

## 1. El problema

`billing-service` está completo (23 endpoints, Fases SRI 0–3) pero **es inalcanzable para un gym real**. Para emitir una sola factura, la base de datos necesita tres filas que **hoy solo se pueden crear con SQL a mano**:

| Tabla | Qué contiene | ¿Cómo se llena hoy? |
|---|---|---|
| `facturacion.config_sri` | RUC, razón social, dirección, ambiente (pruebas/producción), obligado a contabilidad | ❌ **INSERT manual** |
| `facturacion.certificados` | El archivo `.p12` de firma electrónica + su contraseña (ambos cifrados) | ❌ **INSERT manual** |
| `facturacion.puntos_emision` | `cod_establecimiento` + `cod_punto_emision` (ej. `001` / `001`) | ❌ **INSERT manual** |

**Verificado en el código (2026-07-14):** el `AdminController` de `billing-service` expone **solo 3 endpoints, todos de lectura** (`GET /sri/ping`, `GET /certificado/estado`, `GET /auditoria`). No existe `ConfigSriUseCase` ni ningún `POST`/`PUT` de configuración. **El backend para este wizard no existe** — hay que construirlo.

Sin esas tres filas, `POST /comprobantes/facturas` responde `404 — configuración SRI no encontrada`.

### Por qué esto es más grave que un "falta una pantalla"

Es un **SaaS multi-tenant**. Cada gym que se registra necesita configurar su propia facturación con **su** RUC y **su** certificado. Si eso requiere que alguien del equipo corra un `INSERT`, el producto no escala: no se puede vender autoservicio. **El wizard no es una comodidad, es la condición para que la facturación exista como feature vendible.**

---

## 2. Dónde viven los datos — y la duplicación que hay que eliminar

### 2.1 El hallazgo

`tenant.companias` tiene **cuatro columnas fiscales duplicadas** con `facturacion.config_sri`: `nombre_comercial`, `dir_matriz`, `obligado_contabilidad`, `contribuyente_especial`.

**Origen (verificado en git):** nacieron en el **mismo commit** que creó la facturación (`92f0c69`), vía un `ALTER TABLE tenant.companias` que agregaba los campos "requeridos para facturación electrónica SRI". La consolidación posterior de migraciones (`e5ff46f`) fusionó ese `ALTER` dentro del `CREATE TABLE`, y por eso hoy parecen parte del diseño original de `tenant` cuando en realidad fueron un arrastre de facturación.

### 2.2 Están muertas

**Nadie las lee. Verificado por búsqueda exhaustiva en todo el repo** (Java, TS, SQL, YAML):

- `platform-service` — dueño del schema `tenant` — **no las usa**.
- `core-service` — **no las usa**.
- Los frontends — **no las usan**.
- `billing-service` lee **exclusivamente `facturacion.config_sri`**. Incluso el `dirMatriz` del XML sale de `configSri.getDirEstablecimiento()` ([`FacturaXmlBuilder.java:105`](../../../billing-service/src/main/java/com/gymadmin/billing/infrastructure/adapter/out/xml/FacturaXmlBuilder.java)), **no** de `tenant.companias.dir_matriz`.

O sea: **no hay dos fuentes de verdad compitiendo. Hay una fuente de verdad (`config_sri`) y cuatro columnas muertas.**

### 2.3 Decisión: fuente única en `config_sri`, borrar las columnas muertas

**El wizard NO pre-llena desde `tenant`.** Pide el RUC y los datos fiscales una vez, y los guarda en `config_sri`, que es donde el emisor los lee.

**Por qué** (y por qué esto es contraintuitivo pero correcto):

Pre-llenar desde `tenant` **resucitaría** las columnas muertas y crearía una segunda copia **viva** del RUC. A partir de ahí aparece el fallo clásico: alguien edita el RUC en el perfil de la empresa, `config_sri` no se entera, **el certificado deja de coincidir con el RUC declarado y el SRI rechaza todas las facturas.** Sincronizarlas exigiría una llamada cross-service (`billing` → `platform`) en cada edición, o un evento, más un mecanismo que detecte la desincronización — mucha maquinaria para ahorrarle al usuario **un campo**.

Además, el RUC **casi nunca cambia**, y si cambia hay que renovar el certificado de todos modos. No es un dato que valga la pena sincronizar entre dos schemas de dos servicios distintos.

**Costo:** el usuario tipea el RUC una vez más en el wizard.
**Beneficio:** se vuelve **imposible** que el sistema tenga dos RUC distintos.

**Migración requerida** (story Liquibase nueva):

```sql
ALTER TABLE tenant.companias
    DROP COLUMN IF EXISTS nombre_comercial,
    DROP COLUMN IF EXISTS dir_matriz,
    DROP COLUMN IF EXISTS obligado_contabilidad,
    DROP COLUMN IF EXISTS contribuyente_especial;
```

> ⚠️ Antes de ejecutarla, **volver a verificar que nadie las lee** (el código pudo cambiar). `tenant.companias.ruc` **se conserva** — es identidad del tenant, no dato fiscal de facturación.

### 2.4 Qué pide el wizard entonces

| Dato | Dónde se guarda | ¿El wizard lo pide? |
|---|---|---|
| RUC, razón social, nombre comercial, dirección | `facturacion.config_sri` | ✅ Sí — **fuente única** |
| `obligado_contabilidad`, `contribuyente_especial` | `facturacion.config_sri` | ✅ Sí |
| Certificado `.p12` + contraseña | `facturacion.certificados` | ✅ Sí |
| `cod_establecimiento`, `cod_punto_emision` | `facturacion.puntos_emision` | ✅ Sí (con defaults `001`/`001`) |
| Ambiente (pruebas/producción) | `facturacion.config_sri.ambiente` | ✅ Sí (default: pruebas) |

Sigue siendo un wizard corto: **son tres cosas reales** — los datos fiscales (que el dueño sabe de memoria o tiene en su RUC), el certificado, y la caja.

---

## 2-bis. Cómo el sistema sabe qué fila de `config_sri` le toca a cada tenant

`facturacion.config_sri` es una tabla **compartida entre todos los gimnasios** (PK compuesta `(id_compania, id_sucursal)` — una fila por sucursal de cada gym). El aislamiento **no se apoya en el wizard**, sino en un mecanismo que ya está en producción y que el wizard simplemente hereda:

**La compañía sale del JWT, nunca del request.**

1. El usuario hace login → `auth-service` emite un **JWT firmado** que lleva su `id_compania` adentro.
2. [`JwtAuthenticationFilter`](../../../billing-service/src/main/java/com/gymadmin/billing/infrastructure/config/JwtAuthenticationFilter.java) valida la firma y deja el principal en el contexto reactivo de seguridad.
3. El controller lo toma **del principal, no del body** — [`ComprobanteController.java:59`](../../../billing-service/src/main/java/com/gymadmin/billing/infrastructure/adapter/in/web/ComprobanteController.java): `Integer idCompania = toIntegerSafe(principal.getIdCompania());`
4. Ese valor llega a `ConfigSriRepository.findByEmpresa(idCompania, idSucursal)`, que es la query contra `config_sri`.

**La clave está en dónde NO viene ese dato:** `EmitirFacturaRequest` trae `idSucursal`, `codEstablecimiento`, detalles y pagos — pero **no trae `idCompania`**. No existe ningún campo del body donde un atacante pueda escribir "quiero facturar como el gym de al lado". El único origen posible es un token firmado criptográficamente que no puede falsificar.

**La sucursal sí viene del body** (un usuario puede operar varias sucursales *de su propio gym*), y por eso el backend valida que la sucursal pertenezca a la compañía del JWT. Si no coincide, no encuentra `config_sri` → el mismo `404`.

**Para el wizard esto significa:** la fila que escribe lleva el `id_compania` **del JWT del dueño que lo está ejecutando**, no de un campo del formulario. Es estructuralmente imposible que el gym A configure —o lea— la facturación del gym B. Es el mismo patrón que ya usan `comprobantes`, `anulaciones` y todas las tablas de facturación; no hay nada especial que inventar aquí.

---

## 3. Principios de diseño (usuario poco adaptativo)

Los mismos que rigen el [módulo de facturación](../../auth-service-frond-end/facturacion-diseno.md), aplicados a un contexto más hostil: **el usuario está frente a trámites tributarios que no entiende y que, mal llenados, hacen que el SRI le rechace las facturas.**

1. **Nunca preguntar lo que ya sabemos.** Todo lo que está en `tenant` se muestra pre-llenado. El usuario confirma, no tipea.
2. **Un concepto por paso.** Nada de un formulario único con 12 campos.
3. **Traducir el vocabulario del SRI.** El usuario no sabe qué es un "punto de emisión". Sabe qué es "la caja donde cobro". El wizard habla su idioma y explica el término técnico entre paréntesis, no al revés.
4. **Defaults que funcionan.** `001` / `001` es lo correcto para el ~95 % de los gimnasios (un local, una caja). Viene pre-llenado; el que tiene varios locales lo cambia.
5. **Validar contra el SRI antes de terminar, no después.** El wizard **prueba la configuración de verdad** (ping al SRI + validación del certificado) antes de dar por activada la facturación. Es infinitamente preferible fallar aquí que descubrirlo con un cliente esperando en el mostrador.
6. **Se puede abandonar y retomar.** El certificado puede no estar a mano. El progreso se guarda; la facturación queda inactiva hasta completarlo.
7. **Sin jerga en los errores.** "El certificado no corresponde al RUC de tu empresa" — no `CertificateSubjectMismatchException`.

---

## 4. El wizard, paso a paso

**Disparador:** el usuario (dueño/admin) entra por primera vez a *Facturación* y no existe `config_sri` para su compañía → en lugar del módulo, ve el wizard. Es lo mismo que hoy devolvería `404`.

### Paso 0 — Bienvenida y expectativa

Pantalla única, sin campos. Existe porque el usuario necesita saber **qué le vamos a pedir antes de empezar**, y sobre todo que **necesita un archivo que quizás no tiene a mano**.

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   🧾  Activemos tu facturación electrónica                  │
│                                                              │
│   Para emitir facturas al SRI necesitamos 3 cosas:          │
│                                                              │
│   ✅ Los datos de tu empresa   — ya los tenemos             │
│   📄 Tu certificado de firma   — necesitas el archivo .p12  │
│   🔢 El código de tu caja      — te sugerimos uno           │
│                                                              │
│   ⚠️  ¿No tienes el certificado de firma electrónica?       │
│       Se compra en el Banco Central, Security Data o ANF.   │
│       Puedes volver a este paso cuando lo tengas.           │
│                                                              │
│              [ Empezar ]        [ Lo hago después ]         │
└──────────────────────────────────────────────────────────────┘
```

> **Por qué este paso existe:** el certificado `.p12` es un trámite externo que cuesta dinero y toma días. Descubrirlo en el paso 3, después de llenar dos formularios, es la peor experiencia posible. Se avisa **antes**.

### Paso 1 — Los datos fiscales de la empresa

**Se piden aquí y se guardan en `config_sri` — fuente única** (ver [§2.3](#23-decisión-fuente-única-en-config_sri-borrar-las-columnas-muertas)). El único campo que puede pre-llenarse es la **razón social**, desde `tenant.companias.nombre`, que es identidad del tenant y no dato fiscal duplicado.

```
┌──────────────────────────────────────────────────────────────┐
│  Paso 1 de 4 · Datos de tu empresa            ●○○○           │
│                                                              │
│  Estos datos aparecerán en todas tus facturas, tal como los │
│  tiene registrados el SRI. Cópialos de tu RUC.               │
│                                                              │
│  RUC *                    [_____________]                    │
│   ℹ️ Los 13 dígitos de tu RUC, tal como salen en el         │
│      documento del SRI.                                      │
│                                                              │
│  Razón social *           [GYM POWER S.A.   ]               │
│   ℹ️ El nombre legal de tu empresa, no el comercial.        │
│                                                              │
│  Nombre comercial         [_____________]                    │
│   ℹ️ El nombre con el que te conocen tus clientes.          │
│      Si es el mismo, repítelo.                               │
│                                                              │
│  Dirección *              [_____________]                    │
│   ℹ️ La dirección registrada en el SRI.                     │
│                                                              │
│  ¿Tu empresa está obligada a llevar contabilidad?           │
│   ( ) Sí    (•) No                                          │
│   ℹ️ Sale en tu RUC. Si no sabes, revísalo en el portal     │
│      del SRI.                                                │
│                                                              │
│  Contribuyente especial (opcional)  [        ]              │
│   ℹ️ Solo si el SRI te designó como tal. Casi ningún gym lo │
│      es — si no sabes qué es, déjalo vacío.                 │
│                                                              │
│                          [ Atrás ]     [ Continuar ]        │
└──────────────────────────────────────────────────────────────┘
```

- El RUC se valida en cliente (13 dígitos + dígito verificador, `src/lib/sri/validarRuc.ts`).
- **Estos datos deben coincidir con los del certificado del paso 2.** Si no coinciden, el SRI rechaza todo. El paso 2 lo detecta y avisa.
- **No se escribe nada en `tenant.companias`.** Esas columnas se eliminan (ver §2.3).

> 💡 **Por qué se pide el RUC en vez de tomarlo de `tenant.companias.ruc`:** ese campo existe y es identidad del tenant, pero **copiarlo a `config_sri` crearía una segunda copia viva que puede desincronizarse**. Con la fuente única, si el RUC de facturación está mal, se corrige en un solo lugar. La alternativa nos obligaría a mantener dos copias sincronizadas entre dos servicios para ahorrar un campo de formulario que el dueño llena una vez en la vida.

### Paso 2 — El certificado de firma

El paso más difícil para el usuario y el más crítico. **Se valida en el momento de subirlo**, no al final.

```
┌──────────────────────────────────────────────────────────────┐
│  Paso 2 de 4 · Tu firma electrónica            ●●○○          │
│                                                              │
│  El SRI exige que cada factura vaya firmada digitalmente.    │
│  Sube el archivo que te entregó tu proveedor de firma.       │
│                                                              │
│   ┌────────────────────────────────────────────────┐        │
│   │   📄  Arrastra tu archivo .p12 aquí            │        │
│   │       o haz clic para buscarlo                  │        │
│   └────────────────────────────────────────────────┘        │
│                                                              │
│  Contraseña del certificado *  [••••••••]  👁              │
│   ℹ️ Es la clave que te dieron al comprar la firma.         │
│      No es tu contraseña de este sistema.                    │
│                                                              │
│  ─────────────────────────────────────────────────           │
│  ✅ Certificado válido                                       │
│     Titular:  GYM POWER S.A.                                │
│     RUC:      1790012345001  ✓ coincide con tu empresa      │
│     Vence:    14/03/2027  (en 8 meses)                       │
│                                                              │
│                          [ Atrás ]     [ Continuar ]        │
└──────────────────────────────────────────────────────────────┘
```

**Validaciones al subir (en el servidor, inmediatas):**

| Qué se valida | Mensaje si falla |
|---|---|
| El `.p12` abre con la contraseña | *"La contraseña no abre el certificado. Revisa que sea la que te dio tu proveedor de firma."* |
| No está vencido | *"Este certificado venció el {fecha}. Necesitas renovarlo con tu proveedor."* |
| El RUC del certificado == RUC de la empresa | *"Este certificado es de otra empresa (RUC {x}). Sube el certificado de {razón social}."* |
| No está revocado | *"Este certificado fue revocado y no sirve para firmar."* |

> 🔐 **Seguridad:** el `.p12` y su contraseña se guardan **cifrados en reposo** (`facturacion.certificados.p12_cifrado`, `password_cifrado` — ambos `BYTEA`). La contraseña **nunca** se devuelve en un `GET`, nunca se loguea, y no debe quedar en el store del frontend más allá del submit. El archivo viaja por HTTPS en un `multipart/form-data`.

**Alerta de vencimiento (fuera del wizard):** si el certificado vence en < 30 días, mostrar un banner persistente en el panel. Un certificado vencido **detiene toda la facturación** y renovarlo es un trámite de días. El sistema tiene que avisar con tiempo. *(Ver [§7](#7-preguntas-abiertas), pregunta 5.)*

### Paso 3 — Establecimiento y punto de emisión

Este es el paso donde **la traducción del vocabulario importa más**. El usuario no sabe qué es un "punto de emisión". El wizard **no le enseña tributación** — le pregunta por su local y su caja, y le pone valores que casi siempre son correctos.

```
┌──────────────────────────────────────────────────────────────┐
│  Paso 3 de 4 · Tu local y tu caja              ●●●○          │
│                                                              │
│  El SRI numera tus facturas según el local y la caja desde   │
│  la que se emiten. Si tienes un solo local, deja los         │
│  valores que te sugerimos.                                   │
│                                                              │
│  Local (establecimiento) *          [001]                    │
│   ℹ️ Es el número que el SRI asignó a tu local. Si tienes   │
│      un solo local, casi siempre es 001.                     │
│                                                              │
│  Caja (punto de emisión) *          [001]                    │
│   ℹ️ Si facturas desde una sola computadora, deja 001.      │
│                                                              │
│  Así se verán tus facturas:                                  │
│   ┌────────────────────────────────────┐                    │
│   │   001 - 001 - 000000001            │                    │
│   │   ↑local  ↑caja   ↑número          │                    │
│   └────────────────────────────────────┘                    │
│                                                              │
│  ⚠️ Estos números deben coincidir con los que el SRI te     │
│     autorizó. Si no coinciden, el SRI rechazará tus         │
│     facturas.                                                │
│                                                              │
│                          [ Atrás ]     [ Continuar ]        │
└──────────────────────────────────────────────────────────────┘
```

- **Defaults `001` / `001`.** Cubre al gimnasio de un local con una caja, que es la abrumadora mayoría.
- El preview en vivo del número de factura es lo que hace comprensible un concepto abstracto. Es el elemento más importante de este paso.
- Formato: exactamente 3 dígitos (`CHAR(3)` en BD). La UI rellena con ceros (`1` → `001`).

### Paso 4 — Ambiente y prueba real

**El paso que evita el desastre.** El wizard no se cierra con un "guardado ✓" — **prueba la configuración contra el SRI de verdad.**

```
┌──────────────────────────────────────────────────────────────┐
│  Paso 4 de 4 · Probemos que todo funcione      ●●●●          │
│                                                              │
│  ¿Vas a facturar de verdad, o quieres probar primero?        │
│                                                              │
│   (•) Modo pruebas                                           │
│       Las facturas NO tienen validez tributaria. Ideal para  │
│       familiarizarte. Puedes cambiar a producción cuando     │
│       quieras.                                               │
│                                                              │
│   ( ) Producción                                             │
│       Las facturas son reales y válidas ante el SRI.         │
│                                                              │
│  ─────────────────────────────────────────────────           │
│                                                              │
│   Verificando tu configuración...                            │
│    ✅ Conexión con el SRI                                    │
│    ✅ Certificado válido y vigente                           │
│    ✅ Firma de prueba correcta                               │
│                                                              │
│   🎉 ¡Todo listo! Ya puedes emitir facturas.                │
│                                                              │
│                       [ Atrás ]   [ Activar facturación ]   │
└──────────────────────────────────────────────────────────────┘
```

- **Default: modo pruebas** (`ambiente = '1'`). Un usuario poco adaptativo no debería estrenar su facturación con un comprobante fiscal real. Cambiar a producción es un toggle posterior, con su propia confirmación.
- La verificación usa el `GET /api/v1/admin/sri/ping` que **ya existe**, más una firma de prueba con el certificado recién cargado.
- **Solo al pasar la verificación** se hace `facturacion_activa = true`. Si falla, el wizard dice exactamente qué falló y deja volver al paso correspondiente.

### Cierre

```
┌──────────────────────────────────────────────────────────────┐
│   🎉  Tu facturación electrónica está activa                │
│                                                              │
│   Estás en MODO PRUEBAS. Las facturas que emitas ahora       │
│   no tienen validez tributaria — sirven para practicar.      │
│                                                              │
│   Cuando estés listo, cambia a producción desde              │
│   Configuración › Facturación.                               │
│                                                              │
│         [ Emitir mi primera factura ]   [ Ir al panel ]     │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. Qué hay que construir en el backend

> 🔴 **Nada de esto existe hoy.** Es el bloqueante real: sin estos endpoints, el wizard no se puede implementar. El `AdminController` actual solo lee.

### Endpoints nuevos (`billing-service`)

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/v1/admin/config-sri` | Devuelve la config actual (o `404` si no existe → dispara el wizard). **Nunca devuelve la contraseña del `.p12`.** |
| `POST` | `/api/v1/admin/config-sri` | Crea/actualiza `facturacion.config_sri`. Idempotente por `(id_compania, id_sucursal)`. |
| `POST` | `/api/v1/admin/certificado` | `multipart/form-data`: sube el `.p12` + contraseña. **Valida antes de guardar** (abre, RUC, vigencia, revocación) y responde con los metadatos (titular, RUC, vencimiento) **sin** la contraseña. Cifra en reposo. |
| `POST` | `/api/v1/admin/puntos-emision` | Crea la fila en `facturacion.puntos_emision`. |
| `POST` | `/api/v1/admin/config-sri/verificar` | **Prueba real:** ping al SRI + firma de prueba con el certificado guardado. Devuelve el checklist del paso 4. |
| `POST` | `/api/v1/admin/config-sri/activar` | Pone `facturacion_activa = true`. **Solo debe permitirse si `verificar` pasó.** |
| `GET` | `/api/v1/admin/puntos-emision` | Lista los puntos de emisión (para el gym multi-local). |

### Capa de dominio

- `ConfigSriUseCase` (puerto in) — **no existe**, hay que crearlo.
- `CertificadoUseCase` (puerto in) — validación + cifrado del `.p12`.
- Los adaptadores de persistencia sobre `config_sri`, `certificados` y `puntos_emision` — verificar cuáles ya existen para lectura y extenderlos a escritura.

### Permisos

Requiere el permiso nuevo **`facturacion:configurar`** (solo dueño/admin — un recepcionista **no** debe poder tocar el certificado ni el ambiente). Esto se suma a los `facturacion:{leer,emitir,anular,reportes}` que la [spec del módulo](../../auth-service-frond-end/facturacion-diseno.md) ya identificó como **inexistentes en la BD**. Todos hay que crearlos.

---

## 6. Qué es replicable a otros rubros

Idéntico criterio que en la [spec de facturación §14](../../auth-service-frond-end/facturacion-diseno.md#14-sección-clave-qué-es-replicable-a-otros-rubros): **este wizard es casi 100 % genérico de Ecuador.**

**Se replica tal cual** en un SaaS de mecánicas, peluquerías o restaurantes:
- Los 4 pasos completos, su orden y su copy.
- La validación del `.p12` (abre / vigente / RUC coincide / no revocado).
- El concepto establecimiento + punto de emisión, sus defaults `001`/`001` y el preview del número.
- La elección de ambiente con default en pruebas.
- La verificación contra el SRI antes de activar.
- Las tablas `config_sri`, `certificados`, `puntos_emision` — no tienen **nada** de gimnasio.

**Cambia entre rubros:**
- Prácticamente **nada**. Al ser `config_sri` la fuente única (§2.3), el wizard **no depende del modelo de tenant** del SaaS que lo hospeda: pide sus datos y los guarda en sus propias tablas.
- Lo único acoplado es de dónde sale el `id_compania` del JWT — y eso es un claim del token, no una tabla.

> Es la pieza **más portable de todo el sistema** — más aún que el módulo de facturación, que al menos tiene el catálogo de ítems atado al rubro. Refuerza la frontera `src/lib/sri/` ya definida: la validación de RUC y los catálogos que este wizard usa **son los mismos** que usa la pantalla de emisión.

---

## 7. Preguntas abiertas

1. **¿Un `config_sri` por sucursal o uno por compañía?** La PK es `(id_compania, id_sucursal)` → el modelo **soporta uno por sucursal**. ¿El wizard configura solo la sucursal principal y las demás heredan, o hay que correrlo por cada local? *(Recomendación: configurar la principal en el wizard; las demás desde una pantalla de gestión, copiando los datos fiscales y pidiendo solo su `cod_establecimiento`.)*

2. ~~**¿Se actualiza `tenant.companias` si el usuario corrige el RUC?**~~ — ✅ **Resuelto 2026-07-14: NO.** `facturacion.config_sri` es la **fuente única** de los datos fiscales. Las cuatro columnas duplicadas de `tenant.companias` (`nombre_comercial`, `dir_matriz`, `obligado_contabilidad`, `contribuyente_especial`) **están muertas —nadie las lee— y se eliminan** (ver [§2.3](#23-decisión-fuente-única-en-config_sri-borrar-las-columnas-muertas)). No hay llamada cross-service ni sincronización que mantener.

3. ~~**¿Qué pasa si cambian el RUC en `tenant` después de configurar?**~~ — ✅ **Disuelta por la decisión anterior.** Ya no existe una copia del RUC fiscal en `tenant` que pueda desincronizarse. `tenant.companias.ruc` se conserva como identidad del tenant, pero **el emisor no lo lee**: el XML se construye desde `config_sri`. Si el gym cambia de RUC, se corrige en el wizard **y hay que renovar el certificado igual** (el certificado está atado al RUC).

3-bis. **¿Qué pasa si el RUC de `config_sri` deja de coincidir con el del certificado?** El SRI rechaza **todas** las facturas. El paso 2 lo valida al subir el certificado, pero **nada impide editar el RUC después y romper la correspondencia.** *(Recomendación: revalidar la coincidencia RUC↔certificado en cada `POST /config-sri`, y bloquear el guardado si no coinciden. Es la misma validación del paso 2, reutilizada.)*

4. **¿Dónde vive el wizard?** ¿Es una ruta propia (`/admin/facturacion/configuracion`) o un estado de la pantalla de facturación cuando el backend devuelve `404`? *(Recomendación: lo segundo — el usuario no busca "configurar facturación", busca "facturar" y el sistema lo lleva de la mano.)*

5. **Alerta de vencimiento del certificado:** ¿banner en el panel, email, ambos? ¿Con cuántos días de anticipación? Un certificado vencido **detiene toda la facturación** y renovarlo toma días. *(Recomendación: banner desde 30 días + email desde 15. El `GET /admin/certificado/estado` que ya existe puede alimentarlo.)*

6. **¿Quién cifra el `.p12`?** ¿Qué mecanismo de cifrado en reposo se usa para `p12_cifrado` / `password_cifrado`? ¿La clave maestra vive en env var, en un KMS, en un vault? **Es la decisión de seguridad más importante de este requerimiento** y hoy no está documentada. Verificar qué hace el `CertificadoService` actual al leerlos.

7. **Gimnasio multi-local:** ¿el wizard soporta cargar varios puntos de emisión de una, o se configura uno y los demás se agregan después? *(Recomendación: uno en el wizard, el resto en una pantalla de gestión — no cargar de complejidad el 95 % de los casos por el 5 %.)*

8. **¿El módulo está gateado por plan?** La [spec de facturación §11](../../auth-service-frond-end/facturacion-diseno.md) asume un `402` si el plan no incluye facturación. Si es así, **el wizard no debe ni aparecer** en planes que no lo incluyen — debe verse la pantalla de upgrade. Confirmar con REQ-SAAS-001.

---

## 8. Dependencias y orden

```
1. Permisos facturacion:* en BD          ← bloquea todo
2. Decisión de cifrado del .p12 (§7.6)   ← bloquea el endpoint de certificado
3. Endpoints de config en billing        ← bloquea el wizard
4. Migración: DROP de las 4 columnas
   muertas de tenant.companias (§2.3)    ← independiente, puede ir en paralelo
        ↓
5. Wizard (frontend)
        ↓
6. Módulo de facturación (frontend)      ← inútil sin el wizard
```

**El aislamiento multi-tenant no requiere trabajo nuevo.** El wizard hereda el mecanismo que ya está en producción: el `id_compania` sale del JWT firmado, nunca del body (ver [§2-bis](#2-bis-cómo-el-sistema-sabe-qué-fila-de-config_sri-le-toca-a-cada-tenant)).

> **El wizard va antes que el módulo de facturación.** Sin él, las pantallas de emisión no le sirven a ningún gym: `POST /facturas` responde `404` hasta que exista la configuración.
