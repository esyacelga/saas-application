# Pendiente — Avisos por WhatsApp de vencimiento (socios y dueños)

> **Estado:** 🟢 **Fases 1–6 cerradas (6 de 6)** — migración de consentimiento `202607_GYM-002` +
> adaptador Meta/normalizador E.164 + **cola WhatsApp del dueño** (`WhatsAppQueueService`, buckets {3,0},
> R2/R3/R4, cross-day día 0) + **endpoint interno `clientes-por-vencer`** en core (C3 + zona Guayaquil C4,
> IT 13/13) + **`MensajeriaJob` del socio cableado** en attendance (consume el endpoint, plantillas HSM
> `venc_membresia_*`/`venc_accesos_*`, opt-in R4, idempotencia C2, unit 29/29) + **FASE 6 BACKEND CERRADO:**
> tabla `saas.notif_buckets_globales` (story `202607_GYM-003`, seed `socio=3`/`dueno=3`, buckets configurables
> **R1 resuelto**) + endpoints `GET/PUT /api/v1/plataforma/notif-buckets` + endpoint interno
> `GET /internal/v1/notif-buckets/{destinatario}` (consumed by attendance) + jobs leyendo buckets dinámicos
> (**R2 resuelto definitivamente**) + endpoints PATCH opt-in en auth-service + platform-service (captura de
> consentimiento). **Único pendiente Fase 6:** frontend 6B (panel super_admin + 4 UIs de captura de opt-in).
> Plan de **6 fases backend testeables** + **issues de arquitectura** rastreados al final del documento.
> **Diferido** (Fases 3 y 5): ITs con WireMock+Postgres y canario manual, hasta disponibilidad de
> BD limpia/credenciales de Fase 0.
> **Fecha:** 2026-07-15 (revisión de arquitectura + plan de fases + Fases 1, 2, 3, 4, 5 el mismo día);
> **2026-07-16** (Fase 6 backend terminada: migraciones + endpoints + jobs).
> **Área:** attendance-service (socios/membresía) · core-service (lista de clientes por vencer) · platform-service (dueños/suscripción SaaS) · identidad/tenant (teléfono + consentimiento).
> **Prioridad:** media-alta — función de retención/cobranza que hoy queda a medias (se registra el mensaje pero nunca sale del sistema).

## Requerimiento

> Enviar mensajes por WhatsApp a las personas que están por vencer su suscripción.

Refinado, son **dos flujos distintos** que comparten mecánica pero viven en servicios separados:

1. **Socios (miembros del gym)** — avisar al cliente cuya **membresía** (modo `calendario`
   o `accesos`) está por vencer. Vive en **attendance-service** (+ un endpoint nuevo en core-service).
2. **Dueños (suscripción SaaS)** — avisar al owner cuya **suscripción a la plataforma**
   (plan Trial/Premium) está por vencer. Vive en **platform-service**.

**Decisiones de alcance tomadas al refinar:**

| Decisión | Elegido |
|---|---|
| Destinatarios | **Ambos** (socios y dueños), como dos flujos separados. |
| Dónde vive el envío | **Cada servicio envía lo suyo** (no hay servicio de mensajería central). |
| Proveedor | **Meta WhatsApp Cloud API** (plantillas HSM pre-aprobadas). |
| Consentimiento | **Opt-in explícito con registro** por persona/compañía. |
| Teléfono | **Existe pero falta normalizar** a E.164 antes de enviar. |
| Número remitente (WABA) | **Compartida** — un solo número de la plataforma para todos los gyms; el gym se distingue por el texto (`{{gym}}`), no por el número. |
| Quién configura | **Solo super_admin** (no el dueño). Se mantiene `NotifConfigController` super_admin only; sin ruta self-service. |
| Hora de envío | **Variable de entorno, única global**; la cambia super_admin editando config + reiniciando (no en caliente). |

## Mensajes y plantillas HSM del socio

Decisiones tomadas al definir el contenido:

| Decisión | Elegido |
|---|---|
| Variantes | **Calendario + accesos** (~4 plantillas HSM). |
| Tono / CTA | **Cercano** (como el actual `MensajeriaJob`) + **renovar en recepción** (sin pago en línea ni botón). |
| Personalización | **Texto único de plataforma** con `{{gym}}` como variable (no texto libre por gym). |

**Por qué texto único de plataforma:** Meta aprueba **cada texto** de plantilla por separado. Si
cada gym redactara el suyo (como hoy permite `asistencia.plantillas_mensajes`), cada variante
necesitaría su propia aprobación HSM — inviable a escala y contra las reglas de Meta. Con una
plantilla por `tipo` y el nombre del gym como variable `{{gym}}`, el socio igual percibe el
mensaje como de **su** gimnasio, y solo hay ~4 aprobaciones en total.

> **Nota de arquitectura:** el texto libre de `asistencia.plantillas_mensajes` sigue sirviendo
> para **otros canales** (email, futuro SMS/llamada). Para **WhatsApp proactivo** se ignora y se
> usa la plantilla HSM fija por `tipo`. Esto resuelve la tensión "texto libre vs. HSM" anotada en
> el bloque A (recomendación (a)).

### Lo que ve el socio (variante de display, con emoji)

Ejemplo con `nombre=María`, `gym=PowerGym`, `fecha_fin=18/07/2026`, `dias=3`, `accesos=3`:

1. **Calendario — aviso previo (N días antes):**
   > Hola **María**, tu membresía en **PowerGym** vence el **18/07/2026**, en **3 días**. Acércate a recepción para renovar y sigue sin parar. 💪
2. **Calendario — día del vencimiento:**
   > Hola **María**, tu membresía en **PowerGym** vence **hoy**. Pásate por recepción para renovarla y no perder tu ritmo. 🏋️
3. **Accesos — aviso previo (quedan pocas entradas):**
   > Hola **María**, te quedan **3 entradas** en tu membresía de **PowerGym**. Acércate a recepción para renovar antes de que se agoten. 💪
4. **Accesos — última entrada / agotadas:**
   > Hola **María**, usaste tu **última entrada** en **PowerGym**. Pásate por recepción para renovar y seguir entrenando. 🏋️

### Cómo se declaran ante Meta (lo que se sube a aprobación)

Las 4 se registran como **categoría `UTILITY`** (no `MARKETING`): informan sobre un servicio ya
contratado por el usuario → menor riesgo de rechazo y menor costo por conversación. Idioma `es`.

| Plantilla (`name`) | Body con placeholders | Variables en el envío |
|---|---|---|
| `venc_membresia_previo` | `Hola {{1}}, tu membresía en {{2}} vence el {{3}}, en {{4}} días. Acércate a recepción para renovar y sigue sin parar.` | 1=nombre · 2=gym · 3=fecha_fin · 4=días |
| `venc_membresia_hoy` | `Hola {{1}}, tu membresía en {{2}} vence hoy. Pásate por recepción para renovarla y no perder tu ritmo.` | 1=nombre · 2=gym |
| `venc_accesos_previo` | `Hola {{1}}, te quedan {{2}} entradas en tu membresía de {{3}}. Acércate a recepción para renovar antes de que se agoten.` | 1=nombre · 2=accesos · 3=gym |
| `venc_accesos_final` | `Hola {{1}}, usaste tu última entrada en {{2}}. Pásate por recepción para renovar y seguir entrenando.` | 1=nombre · 2=gym |

**Estado de subida a Meta** (identificador exacto = `templateName` en el código, no cambiar):

> **Decisión 2026-07-15 — solo aviso previo (WhatsApp):** al socio se le avisa **únicamente con
> antelación** (previo a N días o cuando quedan N entradas). **Se retiró el aviso del día del
> vencimiento (día/entrada 0)** por WhatsApp. En consecuencia, las plantillas `venc_membresia_hoy` y
> `venc_accesos_final` **ya no se usan** — no es necesario subirlas a Meta (créalas solo si en el
> futuro se reactiva el día 0). El código de `MensajeriaJob` ya no las referencia.

| Plantilla (`name`) | Header (texto fijo) | Estado en Meta |
|---|---|---|
| `venc_membresia_previo` | `Tu membresía está por vencer` | ✅ Creada (2026-07-15) — pendiente de aprobación |
| `venc_accesos_previo` | `Tus entradas están por agotarse` | ✅ Creada (2026-07-15) — pendiente de aprobación |
| ~~`venc_membresia_hoy`~~ | — | ❌ No se usa (día 0 retirado) |
| ~~`venc_accesos_final`~~ | — | ❌ No se usa (día 0 retirado) |

**Mapeo `tipo` → plantilla HSM** (el `tipo` ya existe en `MensajeriaJob`; se añaden los de accesos):

| `tipo` (job) | Plantilla HSM | Condición que lo dispara |
|---|---|---|
| `vencimiento_3d` (calendario) | `venc_membresia_previo` | `dias_para_vencer` ∈ buckets previos |
| `vencimiento_hoy` (calendario) | `venc_membresia_hoy` | `dias_para_vencer == 0` |
| `vencimiento_3d` (accesos) | `venc_accesos_previo` | `accesos_restantes == 3` (bucket previo) |
| `vencimiento_hoy` (accesos) | `venc_accesos_final` | `accesos_restantes == 0` |

### Detalles que estos textos exponen (a resolver en implementación)

- **Buckets del socio = `3` y `0` días (decidido).** `MensajeriaJob` ya dispara en
  `dias_para_vencer==3` y `==0` (y accesos `==3`/`==0`). Se **mantiene** así: aviso previo a **3
  días** + aviso el **día del vencimiento**; **no** se añade el de 7. El body
  `venc_membresia_previo` lleva los días como variable `{{4}}`, así que la misma plantilla serviría
  para otros valores si en el futuro se cambian los buckets desde el panel (ver siguiente punto).
- **El bucket previo NO debe quedar hardcodeado; el `0` sí es fijo.** Hoy los días de aviso (`3`/`0`
  en el socio; `{15,7,3,1,0}` en el dueño) están **fijos en el código del job**. El requisito es que
  **el aviso previo** (los N días de antelación) sea **configurable desde la pantalla del super_admin**
  (bloque E), con `3` como default del socio. El **aviso del día del vencimiento (`0`) queda fijo, no
  configurable y fuera de la pantalla** (decidido).
- **Formato de fecha.** Hoy `fecha_fin` sale como `2026-07-18` (`fechaFin.toString()`). Para el
  socio conviene `dd/MM/yyyy` como ya hace platform-service (`FECHA_ES`). El valor de `{{3}}` debe
  ir ya formateado.
- **Emojis.** Los emojis del display cuentan como parte del texto aprobado por Meta. Dos opciones:
  (a) aprobar el body **sin** emoji y añadirlo solo en canales no-HSM; (b) incluirlo en el body HSM
  y aprobarlo tal cual. Bajo riesgo; decidir al redactar la solicitud a Meta.
- **`{{gym}}`** sale de `tenant.companias.nombre` (o `nombre_comercial` si se prefiere el comercial).

---

## Mensajes y plantillas HSM del dueño (suscripción SaaS)

Análogo al socio, pero **cambia el destinatario y el CTA**: aquí se avisa al **dueño del gym** de
que su **suscripción a la plataforma** (plan `TRIAL` o `PREMIUM`) está por vencer, y el llamado a
la acción es **renovar/reportar pago en línea**, no "acércate a recepción".

Decisiones tomadas (heredadas del socio + específicas del dueño):

| Decisión | Elegido |
|---|---|
| Variantes | **Previo** (faltan N días) + **día del vencimiento** + (opcional) **en gracia/vencido**. |
| Buckets | **`3` y `0` días** (aviso previo a 3 días + día del vencimiento). **Configurable desde el panel** (default `3`/`0`), no hardcodeado. *(Decidido 2026-07-15 en Fase 3: "el recordatorio son de tres días no más" — se reemplazó el `5` previo.)* |
| Tono / CTA | **Directo y de retención**, con enlace a **renovar / reportar pago** (`url_comprar_premium` / `url_reportar_pago`). |
| Personalización | **Texto único de plataforma** con `{{plan}}` y días como variables (una sola aprobación por plantilla, no una por bucket). |
| Cobertura de planes | `TRIAL` y `PREMIUM` (los que `NotificacionVencimientoJob` ya notifica). |

**Por qué una plantilla previa (no una por bucket):** el email de hoy usa **un template por
bucket** (`vencimiento_15d`, `_7d`, `_3d`, `_1d`, `_0d`). Replicar eso en HSM serían **5
aprobaciones de Meta** por texto casi idéntico. En su lugar, **una plantilla previa** con los días
como variable `{{}}` sirve para cualquier bucket (hoy `5`, o los que se configuren en el panel), y
**una** para el día 0 → solo **2 aprobaciones** (+ opcionales de gracia). El bucket sigue
decidiéndolo el job; solo cambia el valor de la variable.

### Lo que ve el dueño (variante de display, con emoji)

Ejemplo con `owner_nombre=Carlos`, `plan=Premium`, `fecha_vencimiento=30/07/2026`, `dias=5`:

1. **Aviso previo (N días antes):**
   > Hola **Carlos**, tu plan **Premium** de Gym Admin vence el **30/07/2026**, en **5 días**. Renueva antes de esa fecha para que tu gimnasio siga operando sin interrupciones. 👉 Reporta tu pago desde *Mi suscripción*.
2. **Día del vencimiento:**
   > Hola **Carlos**, tu plan **Premium** de Gym Admin vence **hoy**. Renueva ya para no perder el acceso a tu panel y a la app de tus socios. 👉 Reporta tu pago desde *Mi suscripción*.
3. *(Opcional)* **Periodo de gracia / vencido:**
   > Hola **Carlos**, tu plan **Premium** de Gym Admin venció y estás en periodo de gracia. Regulariza tu pago para no perder acceso. 👉 Reporta tu pago desde *Mi suscripción*.

> **Nota Trial vs Premium:** el body es el mismo; el plan entra como variable `{{plan}}`
> (`Trial`/`Premium`), igual que hoy `EmailQueueService.planActualDeTipo(...)` resuelve el nombre a
> partir de `VENCIMIENTO_TRIAL` / `VENCIMIENTO_PREMIUM`. No hacen falta plantillas separadas por plan.

### Cómo se declaran ante Meta (lo que se sube a aprobación)

Categoría **`UTILITY`** (avisan sobre un servicio ya contratado), idioma `es`:

| Plantilla (`name`) | Body con placeholders | Variables en el envío |
|---|---|---|
| `venc_suscripcion_previo` | `Hola {{1}}, tu plan {{2}} de Gym Admin vence el {{3}}, en {{4}} días. Renueva antes de esa fecha para que tu gimnasio siga operando sin interrupciones.` | 1=owner_nombre · 2=plan · 3=fecha_vencimiento · 4=días |
| `venc_suscripcion_hoy` | `Hola {{1}}, tu plan {{2}} de Gym Admin vence hoy. Renueva ya para no perder el acceso a tu panel y a la app de tus socios.` | 1=owner_nombre · 2=plan |
| `venc_suscripcion_gracia` *(opcional)* | `Hola {{1}}, tu plan {{2}} de Gym Admin venció y estás en periodo de gracia. Regulariza tu pago para no perder acceso.` | 1=owner_nombre · 2=plan |

**Estado de subida a Meta** (identificador exacto = `templateName` en el código, no cambiar):

| Plantilla (`name`) | Header (texto fijo) | Estado en Meta |
|---|---|---|
| `venc_suscripcion_previo` | `Tu suscripción está por vencer` | ✅ Creada (2026-07-15) — pendiente de aprobación |
| ~~`venc_suscripcion_hoy`~~ | — | ❌ No se usa (WhatsApp día 0 retirado; banner/email siguen) |
| `venc_suscripcion_gracia` *(opcional)* | — | ⏳ Opcional, no requerida |

> **Botón/URL:** Meta permite un botón de tipo *URL* en la plantilla. Si se quiere el "👉 Reporta tu
> pago", va como **componente botón** (no en el body) apuntando a `url_reportar_pago`. Decidir al
> redactar la solicitud: body limpio + botón URL es lo más pro y lo mejor visto por Meta.

**Mapeo `tipo`/bucket → plantilla HSM:**

> **Decisión 2026-07-15 — WhatsApp solo aviso previo (dueño):** el canal **WhatsApp** del dueño se
> envía **solo como aviso previo**; en el bucket `0` (día del vencimiento) se **omite WhatsApp**. El
> **banner y el email del día 0 se mantienen** (no cambian). Por eso `venc_suscripcion_hoy` **ya no
> se usa** por WhatsApp — no es necesario subirla a Meta.

| `tipo` (job) | Bucket | Plantilla HSM (WhatsApp) |
|---|---|---|
| `VENCIMIENTO_TRIAL` / `VENCIMIENTO_PREMIUM` | `3` (previo, configurable) | `venc_suscripcion_previo` (con `{{4}}=días`) |
| `VENCIMIENTO_TRIAL` / `VENCIMIENTO_PREMIUM` | `0` | ❌ WhatsApp omitido (banner/email siguen) — ~~`venc_suscripcion_hoy`~~ no se usa |
| *(estado `EN_GRACIA`, si se añade)* | — | `venc_suscripcion_gracia` (opcional) |

### Detalles que estos textos exponen (a resolver en implementación)

- **Buckets del dueño = `5` (previo, configurable) y `0` (fijo) días (decidido).** Hoy el job usa
  `{15,7,3,1,0}` **hardcodeados**; el requisito es cambiarlo a **aviso previo a `5` días + día `0`**.
  **Solo el `5` (aviso previo) es configurable desde el panel** (default `5`); el **`0` es fijo, no
  editable, fuera de la pantalla**. El body previo lleva los días en `{{4}}`, así que la misma
  plantilla sirve aunque el bucket previo cambie desde el panel, sin nueva aprobación de Meta.
- **Un solo aviso por ciclo.** El job usa `.next()` sobre los buckets → dispara **solo el primer
  bucket que cumple** `diasRestantes <= bucket`, no uno por cada umbral. Con `5`/`0` el dueño recibe
  a lo sumo el aviso previo (≤5 días) y el del día 0; el WhatsApp hereda ese comportamiento (1
  mensaje por ejecución cuando toca, no uno por bucket).
- **`{{plan}}`** sale de `plan_actual` (`Trial`/`Premium`), ya resuelto por `planActualDeTipo(...)`.
- **`fecha_vencimiento`** debe ir formateada `dd/MM/yyyy` (ya existe `FECHA_ES` en `EmailQueueService`).
  Ojo: en la ruta clásica de vencimiento el email hoy deja `fecha_vencimiento=""` — para WhatsApp hay
  que **poblarla** desde `cp.getFechaFin()` (el job tiene el `CompaniaPlan` a mano).
- **Emojis / botón URL:** misma decisión abierta que el socio (aprobar con o sin emoji; botón URL sí/no).

---

## Lo que ya está hecho (infraestructura parcial) ✅

Este requerimiento **no parte de cero**. Buena parte del andamiaje existe; el hueco real es
el **envío físico por WhatsApp**, exponer la lista de socios "por vencer", y abrir la config
al dueño.

### Lado dueño — platform-service (el más maduro)

- **`NotificacionVencimientoJob`** (cron `0 15 3 * * *`): job diario que detecta suscripciones
  `ACTIVO`/`EN_GRACIA` con `fecha_fin` dentro de 15 días y evalúa buckets `{15, 7, 3, 1, 0}`
  para planes `TRIAL` y `PREMIUM`.
  → `platform-service/.../infrastructure/scheduler/NotificacionVencimientoJob.java`
- **`EmailQueueService`** (implementa `EnviarNotificacionUseCase` + `ProcesarColaEmailsUseCase`):
  cola sobre Postgres con `FOR UPDATE SKIP LOCKED` (claim atómico), retry con backoff
  exponencial (`30s → 2m → 10m → 1h`, 4 intentos → `fallido`), ruteo de template por
  `tipo`/`dias_antes`.
  → `platform-service/.../application/service/EmailQueueService.java`
- **`EmailQueueProcessorJob`**: worker `fixedDelay` (30s) que dispara `procesarLote(batchSize=50)`.
  → `platform-service/.../infrastructure/scheduler/EmailQueueProcessorJob.java`
- **`EmailSender` (puerto) + `EmailAdapter` (SMTP JavaMailSender)**: el envío real de email ya
  existe y sirve de **molde exacto** para el `WhatsAppSender`.
  → `platform-service/.../domain/port/out/EmailSender.java` · `.../adapter/out/email/EmailAdapter.java`
- **Esquema ya preparado para WhatsApp:**
  - `tenant.config_notif_suscripcion.canal` acepta `('email','whatsapp','ambos')`.
  - `tenant.notificaciones_suscripcion.canal` acepta `('email','whatsapp','banner')`.
- **Campo de teléfono dedicado:** `tenant.companias.whatsapp VARCHAR(20)` (y `telefono` como respaldo).

> **El hueco (dueño):** el job solo encola **EMAIL** y **BANNER**
> (`encolarSiNoExiste` itera `List.of(CANAL_EMAIL, CANAL_BANNER)`). **El canal `whatsapp`
> nunca se encola ni se envía**, aunque el esquema y el `CHECK` ya lo contemplan.

### Lado socio — la lógica de "por vencer" ya existe… pero en **core-service**

Hallazgo importante: **quién está por vencer ya se calcula**, solo que en core, no en attendance.

- **`ClienteStatusJobService`** (core-service, cron `0 10 0 * * *`): barre **todos** los clientes
  activos (`clienteRepository.findActivosParaJob()`), evalúa vencimiento por `calendario` y
  `accesos`, y ya marca `Cliente.Estado.proximo_vencer` cuando faltan **≤ 3 días** (o ≤ 3
  accesos). Toda la lógica de detección ya está aquí.
  → `core-service/.../application/service/ClienteStatusJobService.java`
- **Patrón interno service-to-service ya establecido:** `InternalCoreController` expone
  `/internal/v1/...` protegido por header `X-Internal-Call: {INTERNAL_SECRET}` (no JWT). Ya lo
  consume platform-service para contar clientes activos.
  → `docs/core-service/api/internal.md`
- **El teléfono del socio ya se lee vía JOIN en core:** `core` cruza a `identidad.personas`
  (nombre, CI, **teléfono**, correo) en varias queries. attendance **no** lee `personas`.

### Lado socio — attendance-service (esqueleto de mensajería)

- **Plantillas** `vencimiento_3d` y `vencimiento_hoy` en `asistencia.plantillas_mensajes`.
- **`MensajeriaJob`** (cron `0 15 0 * * *`): calcula `dias_para_vencer`, elige plantilla
  aleatoria, sustituye variables (`{nombre}`, `{dias}`, `{fecha_vencimiento}`,
  `{accesos_restantes}`, `{gym_nombre}`) y aplica anti-spam vía `contarEnviadosDesde`.
  → `attendance-service/.../infrastructure/scheduler/MensajeriaJob.java`
- **`asistencia.mensajes_log.canal`** acepta `('whatsapp','email','llamada')`.

> **Los huecos (socio):**
> 1. **`procesarAusencias()` está vacío** — devuelve `Flux.empty()`. `procesarCliente(...)` tiene
>    toda la lógica de negocio, pero **nadie lo llama con clientes reales**.
> 2. **attendance no tiene forma de listar clientes por vencer.** Su `CoreServiceClient` solo
>    tiene `validarAcceso`, `buscarSucursalPorQr`, `buscarIdClientePropio` — **ninguna query
>    agregada**. Necesita un endpoint nuevo en core (ver bloque C).
> 3. **No hay envío físico** — el flujo llega hasta `INSERT mensajes_log` (implícito `pendiente`)
>    y termina. Nunca se llama al proveedor, nunca se actualiza a `enviado`/`fallido`.

## Lo que falta (implementación) 📋

Cinco bloques. A y B son compartidos; C es del socio; D cablea ambos jobs; E es el panel.

### A. Adaptador de salida WhatsApp (Meta Cloud API) — en cada servicio

Como se decidió **no** crear un servicio de mensajería central, cada servicio tendrá su propio
adaptador (misma forma; en platform-service, calcado del `EmailSender`/`EmailAdapter`):

- Puerto `WhatsAppSender` con método tipo
  `enviarPlantilla(destinatarioE164, templateName, idioma, params) → Mono<Void>`.
- Adaptador `MetaWhatsAppAdapter` que llama `POST /{phone-number-id}/messages` (`type: template`)
  vía `WebClient` reactivo (no bloquear el event loop).
- Fallback de dev/CI como el de `EmailAdapter`: si falta config, WARN + `Mono.empty()` (no
  romper tests ni el arranque).
- **Plantillas HSM pre-aprobadas** obligatorias para mensajes iniciados por el negocio (este caso).
  El `templateName` mapea a una plantilla aprobada en Meta, **no** al texto libre de
  `asistencia.plantillas_mensajes` (ver [Mensajes y plantillas HSM del socio](#mensajes-y-plantillas-hsm-del-socio)).
- **WABA compartida (decidido):** un solo número remitente de la plataforma para todos los gyms.
  Las credenciales Meta son **únicas por servicio** (no por tenant), así que el adaptador **no**
  necesita resolver credenciales por gym — lee las env vars y listo. El gym se identifica por el
  texto (`{{gym}}`), no por el número. Si en el futuro se migra a "número por gym", solo cambia esta
  parte (config por tenant); el resto del flujo no.
- Variables de entorno nuevas (por servicio):
  ```env
  WHATSAPP_PROVIDER=meta
  META_WABA_ID=***
  META_PHONE_NUMBER_ID=***
  META_ACCESS_TOKEN=***
  META_API_VERSION=v21.0
  ```

### B. Normalización de teléfono a E.164 — compartido

Ni `identidad.personas.telefono` ni `tenant.companias.whatsapp`/`telefono` garantizan formato
internacional. Antes de enviar hay que normalizar (contexto Ecuador → prefijo `+593`):

1. Utilidad de normalización (`0987654321` → `+593987654321`, tolerar espacios/guiones,
   descartar longitudes inválidas).
2. Si no es normalizable → **no** enviar WhatsApp; registrar motivo (`telefono_invalido`) y, si
   aplica, caer a email.
3. **Decisión abierta:** normalizar al vuelo (v1, más simple) vs. persistir `telefono_e164`
   (evita recomputar, sirve de flag de "número usable"). **Recomendación:** al vuelo en v1.

### C. Endpoint interno "clientes por vencer" (core-service) — el hueco más grande del socio

attendance no puede (ni debe) replicar la detección de vencimiento ni leer `identidad.personas`.
En lugar de duplicar `ClienteStatusJobService`, **exponer su resultado** desde core:

1. Nuevo endpoint en `InternalCoreController`, p. ej.
   `GET /internal/v1/companias/{id}/clientes-por-vencer?dias=3` (mismo header `X-Internal-Call`).
2. Devuelve por cada socio por vencer: `idCliente`, `idPersona`, `nombre`, **`telefono`**,
   `modoControl`, `fechaFin`, `diasParaVencer`/`accesosRestantes`, `idSucursal`. Todo eso core ya
   lo tiene (o lo obtiene con el JOIN a `personas` que ya hace).
3. Nuevo método en el `CoreServiceClient` de attendance que consuma ese endpoint y alimente
   `procesarAusencias()`.
4. **Decisión abierta:** ¿reutilizar el estado `proximo_vencer` que `ClienteStatusJobService` ya
   persiste (más barato, pero atado a su umbral fijo de 3 días) o recalcular con un `dias`
   parametrizable? **Recomendación:** endpoint parametrizable por `dias` para no acoplar los
   buckets de aviso al umbral de estado.

### D. Cablear cada job al envío

**Dueño (platform-service):**
- En `NotificacionVencimientoJob.encolarSiNoExiste`, encolar `CANAL_WHATSAPP` **según
  `config_notif_suscripcion.canal`** del tenant (`whatsapp`/`ambos`), no incondicionalmente.
- Rutear en el procesamiento de cola las notificaciones `canal='whatsapp'` al
  `MetaWhatsAppAdapter` en vez del `EmailSender`, reutilizando el mismo retry/backoff y las
  transiciones `pendiente → enviado/fallido/reintentar` que ya existen. (Elegir: extender
  `EmailQueueService` o un procesador hermano para WhatsApp.)

**Socio (attendance-service):**
- **Implementar `procesarAusencias()`** consumiendo el endpoint del bloque C y llamando al ya
  existente `procesarCliente(...)` por cada socio.
- Tras `INSERT mensajes_log`, llamar al `MetaWhatsAppAdapter` y actualizar `estado` a `enviado`
  (`fecha_envio`) o `fallido`. Respetar `RN-05` (no enviar a `congelado`) y el anti-spam ya presente.

### E. Consentimiento (opt-in) + Panel de configuración

**Consentimiento — nuevo modelo de datos** (Meta puede **bloquear el número** si se envía sin
opt-in). Hoy **no existe ningún campo de consentimiento** ni en `identidad.personas` ni en
`tenant.companias` — verificado. Hay que crearlo. Decisiones tomadas:

**1. Modelo = 2 columnas por tabla** (mismo molde que `ci_validada`, no tabla genérica en v1):

```sql
-- identidad.personas (socio)
acepta_whatsapp          BOOLEAN     NOT NULL DEFAULT FALSE
fecha_consentimiento_wa  TIMESTAMPTZ            -- NULL hasta que acepte

-- tenant.companias (dueño)
acepta_whatsapp          BOOLEAN     NOT NULL DEFAULT FALSE
fecha_consentimiento_wa  TIMESTAMPTZ
```

- `acepta_whatsapp = TRUE` solo cuando la persona/compañía dio opt-in **explícito**; `fecha_*`
  sella cuándo (prueba mínima ante Meta).
- **Descartado para v1:** tabla `consentimientos` genérica (historial + origen + multicanal). Queda
  como evolución futura si se añaden SMS/email-marketing o se necesita auditoría de opt-out. Con 2
  columnas basta para el caso actual.

**2. Backfill = asumir NO-consentido.** Todos los registros existentes quedan en `FALSE` (es el
`DEFAULT`, no requiere UPDATE). **Nadie recibe WhatsApp hasta dar opt-in explícito** → sin riesgo de
bloqueo del número por Meta. Los ya registrados irán aceptando cuando entren a la app, en recepción,
o desde su perfil. **No** se hace `UPDATE ... SET TRUE` masivo (no hay prueba de opt-in real).

**3. Captura del opt-in:**

- **Socio — en tres puntos** (para cubrir todos los caminos de alta y permitir opt-out):
  1. **Registro público del socio** (gym-member-pwa) — checkbox en el auto-registro. Punto natural,
     con prueba de origen (`fecha_*` = alta).
  2. **Alta/renovación en recepción** (panel staff) — recepción marca el opt-in que el socio
     autoriza. Cubre a los socios que **no** usan la PWA.
  3. **Perfil / config del socio** (PWA) — toggle ON/OFF. Permite **opt-out** y capturar a los ya
     registrados.
- **Dueño** — en el **onboarding del gym** o en la **pantalla de config de notificaciones** (bloque
  E, panel del dueño). Un solo punto basta; el dueño es uno por tenant.

**4. Regla de envío (el job):** solo envía WhatsApp si `acepta_whatsapp = TRUE`. Sin consentimiento
→ cae a email/banner, **nunca** WhatsApp, aunque el canal configurado sea `whatsapp`/`ambos`. (Ya
está anotado como caso borde de pruebas.)

> Columnas nuevas → **story nueva** `YYYYMM_GYM-XXX` con `partial-changelog.yml` + DDL, apendada al
> final de `main-changelog.yml`; **nunca** editando la baseline `GYM-001` (convención en
> `gym-administrator/CLAUDE.md`). Escribir con `COMMENT ON COLUMN` como hace `ci_validada`.

**Panel de configuración — SOLO super_admin (decidido):**

Decisión: **la configuración de avisos la parametriza únicamente el super_admin de la plataforma.**
El dueño del gym **NO** configura sus propios avisos. Se **mantiene** `NotifConfigController`
(`/api/v1/companias/{id}/notif-config`) tal como está hoy — **solo `super_admin`** — y **NO** se abre
ninguna ruta self-service para el dueño. Esto simplifica el alcance: no hay nueva ruta ni relajar el
guard con `requireAccessToCompania`.

Lo que la pantalla de super_admin debe controlar:

- **Canal** por tipo de aviso (`email` / `whatsapp` / `ambos` / desactivado).
- **Días de anticipación — SOLO el aviso previo es configurable (decidido):** lo que el super_admin
  edita es **únicamente los N días de antelación** del aviso previo (default **socio `3`**, **dueño
  `5`**). El **aviso del día del vencimiento (`0`) es fijo**: siempre existe, **no** es editable y
  **no** aparece en la pantalla. El job pasa a leer el bucket previo desde config (en vez del
  `if`/`switch` del socio o la lista fija `BUCKETS` del dueño) pero **mantiene el `0` como constante**.
  - **Por qué esto importa (elimina el cross-day):** al no ser el `0` configurable, no hay riesgo de
    que un aviso "día del vencimiento" se reprograme o corra a un día distinto. El `0` dispara siempre
    el mismo día; si su envío falla y el backoff cruza a mañana, el aviso ya no aplica (ver
    [Robustez del envío](#robustez-del-envío-decidido)).
- **Activar/desactivar** el aviso completo.
- **Hora de envío** — ver siguiente sub-sección.

**Hora de envío — variable de entorno, única global (decidido):**

Requisito: super_admin debe poder parametrizar **a qué hora salen los mensajes**. Hoy la hora está
**hardcodeada en el cron** de cada job:

- Socio (`MensajeriaJob`): `0 15 0 * * *` → **00:15**.
- Dueño (`NotificacionVencimientoJob`): `0 15 3 * * *` → **03:15**.

Decisión: la hora se mantiene como **variable de entorno** (el cron ya es sobreescribible —
`scheduling.messaging-job-cron` y `notificacion.vencimiento.cron`), **única para toda la plataforma**
(no por tenant). **Cambiarla la hace super_admin editando la config y reiniciando el servicio** — se
acepta el reinicio; **no** se edita en caliente desde la pantalla. La pantalla, a lo sumo, la
**muestra** (solo lectura) o la deja fuera. **Cero código nuevo en los jobs** — solo fijar el cron
por env var.

> **Por qué no en caliente:** `@Scheduled(cron=...)` de Spring se resuelve **al arrancar** la app; no
> se puede cambiar sin reiniciar solo con el cron. Editar la hora en vivo exigiría `TaskScheduler`
> programático o "chequeo cada hora + hora en BD" — **descartado** por complejidad; el reinicio es
> aceptable para un parámetro que casi nunca cambia.

**Buckets configurables — modelo de datos:**

- **Los días de aviso no existen aún como columna configurable** — hoy son constantes en el job.
  Guardarlos requiere ampliar el modelo de config (p. ej. `config_notif_suscripcion.dias_aviso` o
  tabla de buckets) → **story nueva** `YYYYMM_GYM-XXX`, no la baseline. Al ser config **global** (no
  por tenant), puede ser una tabla/fila de config de plataforma, más simple que por-tenant.
- El equivalente para socios (canal + buckets de avisos de membresía) hoy se controla por existencia
  de plantillas, sin tabla de config propia → definir su tabla/columnas en diseño.
- Trabajo de frontend en `auth-service-frond-end` (panel de super_admin), **una sola pantalla** (no
  hay pantalla del dueño).

## Robustez del envío (decidido)

Reglas de comportamiento del envío, aterrizadas al refinar:

- **El aviso del día `0` no cruza de día.** Como el `0` es **fijo, no configurable** (ver bloque E),
  no hay reprogramación posible. Si el envío del día del vencimiento falla y el backoff agotaría los
  intentos **al día siguiente**, el aviso ya no corresponde (el texto "vence hoy" sería falso): se
  marca **`fallido` sin reintentar cross-day**. El backoff sí opera dentro del **mismo día**; solo se
  corta al cambiar de fecha. (No aplica al aviso previo, cuyo texto no depende de "hoy".)
- **Canal `ambos` = email y WhatsApp en paralelo, sin esperar (decidido).** Se **mantiene** el
  comportamiento actual del job: con `ambos` se **encolan los dos canales por separado** y salen en
  paralelo; **no** se espera el veredicto de WhatsApp para decidir el email. El email así **actúa de
  respaldo natural**: si WhatsApp termina en `fallido`, el email ya salió por su cuenta → **no** hay
  que implementar un fallback secuencial. Cada canal se deduplica por separado (`existsIdempotente`
  incluye `canal`), así que no hay doble envío del mismo canal.
- **Anti-spam del socio por bucket/canal.** El anti-spam actual del socio
  (`contarEnviadosDesde(idCliente, tipo, desdeUltimaAsistencia)`) está pensado para avisos de
  **ausencia**, atado a la última asistencia — **no** garantiza "un solo aviso de vencimiento por
  bucket/canal". Para vencimiento hay que asegurar la **idempotencia por `(idCliente, tipo, canal,
  bucket)`** como ya hace el dueño con `existsIdempotente`. **Decisión abierta menor:** reutilizar
  `mensajes_log` con esa clave lógica o añadir un `exists` equivalente en attendance. **Recomendación:**
  chequear en `mensajes_log` por `(idCliente, tipo, canal, día)` antes de encolar.

## Orden sugerido para empezar

Ruta de menor riesgo, entregando valor incremental:

0. **Trámite Meta en paralelo (lo gestiona el dueño del producto).** Crear/verificar la cuenta WABA,
   el número y el `phone_number_id`, y **subir las plantillas HSM a aprobación** (socio + dueño) —
   puede tardar días. Corre **en paralelo** al desarrollo de los bloques siguientes; no lo bloquea
   (los adaptadores funcionan con mock/WARN hasta tener credenciales).
1. **Bloque A + D (dueño)** primero — es donde casi todo existe: añadir `WhatsAppSender`/adaptador,
   encolar `whatsapp` según config y rutear en la cola. Reutiliza cola, retry e idempotencia
   ya probados. **Entrega el primer WhatsApp real con el menor código nuevo.**
2. **Bloque E (consentimiento)** — necesario antes de enviar a volumen real sin arriesgar el número.
3. **Bloque C** (endpoint core) + **A/D (socio)** — el lado socio, que requiere más código nuevo.
4. **Bloque E (panel)** — pantalla de configuración de **super_admin** (canal + bucket previo) +
   frontend. Ni el `0` ni la hora de envío entran aquí (el `0` es fijo; la hora queda en env var).

## Qué falta decidir antes de codificar (checklist para arrancar)

- [~] **Cuenta Meta / WABA + aprobación de plantillas:** trámite externo **en curso, gestionado por
      el dueño del producto en paralelo** al desarrollo (no bloquea: los adaptadores usan mock/WARN
      hasta tener credenciales). Sin esto no hay envío real, solo mocks.
- [x] **Plantillas HSM del socio redactadas** — 4 plantillas `UTILITY` definidas (ver
      [Mensajes y plantillas HSM del socio](#mensajes-y-plantillas-hsm-del-socio)).
- [x] **Plantillas HSM del dueño redactadas** — 2 (+1 opcional de gracia) `UTILITY` definidas (ver
      [Mensajes y plantillas HSM del dueño](#mensajes-y-plantillas-hsm-del-dueño-suscripción-saas)).
      **Falta enviarlas a aprobación** en Meta (socio + dueño; puede tardar días → arrancar **ya**).
- [x] **Texto libre vs. HSM:** decidido — WhatsApp usa plantilla HSM fija por `tipo`; el texto
      libre del gym queda para email/otros canales.
- [x] **Quién personaliza:** decidido — texto único de plataforma con `{{gym}}` variable.
- [ ] **Emojis en el body HSM:** decidir aprobar con o sin emoji (ver detalles de la sección).
- [x] **Buckets del socio:** decidido — **`3` y `0` días** (aviso previo a 3 días + día del
      vencimiento). **No** se añade el de 7. **Solo el aviso previo (`3`) es editable desde el panel**;
      el **día `0` es fijo, no configurable y no aparece en pantalla** (ver bloque E).
- [x] **Robustez del envío:** decidido — el aviso día `0` **no cruza de día** (falla → `fallido`, sin
      reintento cross-day); canal `ambos` = **email y WhatsApp en paralelo, sin esperar** (email como
      respaldo natural, sin fallback secuencial). Ver [Robustez del envío](#robustez-del-envío-decidido).
- [x] **Consentimiento:** decidido — **2 columnas** (`acepta_whatsapp` + `fecha_consentimiento_wa`)
      en `personas` y `companias`; **backfill = asumir NO-consentido** (`DEFAULT FALSE`); captura del
      socio en **registro público + recepción + perfil PWA**, del dueño en onboarding/config. Falta
      **implementarlo** (story nueva + UI en los 3 puntos). Ver bloque E.
- [x] **Config del dueño:** decidido — **NO**. Solo `super_admin` configura; se mantiene
      `NotifConfigController` (super_admin only), sin ruta self-service. Ver bloque E.
- [x] **Hora de envío:** decidido — **variable de entorno, única global**; la cambia super_admin
      editando config + **reiniciando** (no en caliente). Cero código nuevo en los jobs. Ver bloque E.
- [x] **¿Una WABA compartida o una por gym?** Decidido — **compartida**: un solo número de la
      plataforma para todos los gyms (credenciales únicas por servicio, sin config por tenant). El
      gym se distingue por el texto `{{gym}}`. "Por gym" queda como evolución futura (solo añadiría
      config por tenant al adaptador, sin rehacer el flujo).

## Notas de implementación

- **Idempotencia / anti-spam ya resuelto**, no reinventarlo:
  - Dueño: `NotificacionRepository.existsIdempotente(idCompaniaPlan, tipo, canal, bucket)` — el
    `canal` es parte de la clave, así que WhatsApp y Email se deduplican **por separado** (bien).
  - Socio: `MensajeLogService.contarEnviadosDesde(idCliente, tipo, desdeUltimaAsistencia)`.
- **Reutilizar la cola del dueño** (`notificaciones_suscripcion`: `estado`, `intentos`,
  `proximo_intento`, `ultimo_error`) para WhatsApp con el mismo `procesarLote`/backoff.
- **No duplicar la detección de vencimiento** entre `MensajeriaJob` (attendance) y
  `ClienteStatusJobService` (core). El bloque C existe precisamente para evitar esa duplicación.
- **Zona horaria:** attendance corre en `America/Guayaquil`; platform usa `Clock` inyectable; core hoy
  usa `LocalDate.now()` sin `Clock`. Coherencia al calcular `dias_para_vencer` → formalizado como
  **issue C4** (ver *Issues de arquitectura*, fase 4).
- **Costo/tarifa:** Meta cobra por conversación iniciada por el negocio. Considerar tope/métrica
  si el volumen escala (no bloqueante para la v1).

## Casos borde a cubrir en pruebas

- Destinatario sin teléfono / no normalizable → sin WhatsApp, sin excepción, motivo registrado.
- Destinatario sin consentimiento → nunca WhatsApp aunque el canal esté en `ambos`.
- Fallo del proveedor Meta → `fallido`/`reintentar` según backoff; el job no se rompe.
- Anti-spam: no reenviar el mismo `tipo`/bucket por el mismo canal en el mismo ciclo.
- Socio `congelado` → no recibe avisos de ausencia (RN-05); confirmar comportamiento en vencimiento.
- Config del tenant en `email` → no debe salir ningún WhatsApp aunque haya consentimiento.

## Issues de arquitectura a resolver durante la implementación

> Detectadas en la **revisión de arquitectura (2026-07-15)**. Son decisiones/fixes que **cambian el
> shape del código** y deben resolverse en la fase indicada, **antes** de escribir ese código. Se
> marcan `[x]` conforme se resuelven.

### Críticas

- [x] **C1 — Procesador de cola WhatsApp: hermano, no extensión (fase 3).** ✅ **Resuelto en Fase 3.**
      `WhatsAppQueueService` (implementa `ProcesarColaWhatsAppUseCase`) + `WhatsAppQueueProcessorJob` leen
      la misma tabla vía `claimLoteWhatsapp(max)` (`WHERE canal='whatsapp'`, mismo `FOR UPDATE SKIP LOCKED`);
      no se tocó `EmailQueueService`. Texto original abajo. No meter WhatsApp dentro
      de `EmailQueueService` (rompería la responsabilidad única del `EmailSender`). Crear
      `WhatsAppQueueService` + `WhatsAppQueueProcessorJob` que leen la **misma** tabla
      `tenant.notificaciones_suscripcion` filtrando `canal='whatsapp'`. Requiere `claimLoteWhatsapp(max)`
      (o parametrizar el `claimLote` existente por canal) con el mismo `FOR UPDATE SKIP LOCKED`; sin ese
      filtro, email y whatsapp compiten por los mismos lotes con el sender equivocado. El
      `EnviarNotificacionUseCase.encolar(...)` puede seguir compartido (solo cambia el `canal`).
- [x] **C2 — Idempotencia del socio por `(idCliente, tipo, canal, día)` (fase 5).** ✅ **Resuelto en Fase 5.**
      Añadido `MensajeLogRepository.existsEnviadoHoy(idCliente, tipo, canal, dia)` (query
      `existsEnviadoEnRango` sobre `mensajes_log` con rango [inicio, inicio+1d) del día Guayaquil) y el
      `MensajeriaJob` lo checkea **antes** de enviar. `contarEnviadosDesde` se queda solo para avisos de
      **ausencia** (semántica distinta, intacto). Verificado por `MensajeriaJobTest` (caso "ya enviado hoy
      → no reenvía"). Texto original abajo. El anti-spam actual
      (`contarEnviadosDesde` atado a la última asistencia) **no** garantiza un solo aviso de vencimiento
      por bucket/canal: un reinicio del job el mismo día genera duplicados → Meta bloquea el número.
- [x] **C3 — Contrato formal del endpoint interno de core (fase 4).** ✅ **Resuelto en Fase 4.**
      Implementado `GET /internal/v1/companias/{id}/clientes-por-vencer?dias=&modo=` con la proyección
      de abajo (`ClientePorVencer`): `telefono` sin normalizar, `estadoCliente`, `aceptaWhatsapp`/
      `fechaConsentimientoWa` (evita 2º JOIN en attendance), `accesosRestantes` calculado por core.
      403 sin secreto; 400 si `dias`∉[0,30] o `modo` inválido. Contrato original abajo. Formalizar antes de que attendance
      lo consuma:
      ```
      GET /internal/v1/companias/{id}/clientes-por-vencer?dias={0..30}&modo={calendario|accesos|todos}
      Header: X-Internal-Call: {INTERNAL_SECRET}
      200 → { companiaId, fechaCorte, clientes: [ { idCliente, idPersona, idSucursal, nombre,
             telefono (SIN normalizar), correo, modoControl, fechaFin, diasParaVencer, accesosRestantes,
             estadoCliente, aceptaWhatsapp, fechaConsentimientoWa } ] }
      403 → X-Internal-Call ausente/ inválido · 400 → dias fuera de rango o modo inválido
      ```
      Claves: `telefono` va **sin** normalizar (E.164 es responsabilidad de attendance, no acopla core);
      incluir `estadoCliente` (para saltar `congelado`, RN-05) y `aceptaWhatsapp`/`fechaConsentimientoWa`
      (evita un segundo JOIN en attendance); `accesosRestantes` la calcula core (no attendance).
- [x] **C4 — Zona horaria coherente al resolver "hoy" (fase 4).** ✅ **Resuelto en Fase 4** para el
      endpoint interno. `ClockConfig` expone un `Clock` anclado a `America/Guayaquil`
      (`@ConditionalOnMissingBean` para override en tests); el `InternalCoreController` calcula
      `fechaCorte = LocalDate.now(clock)` y la query usa `:fechaCorte` en vez de `CURRENT_DATE`. La
      `fechaCorte` **no** se delega al cliente. Verificado por IT con `Clock.fixed` a
      `2026-07-15T04:00Z` → `fechaCorte = 2026-07-14` (día Guayaquil, no UTC). **Nota:** el
      `ClienteStatusJobService` (job de estados, distinto de este endpoint) sigue usando
      `LocalDate.now()` sin `Clock`; su refactor a `Clock` queda como mejora futura si se quiere
      alinear también ese cron (no lo consume attendance para los avisos).
- [x] **C5 — Orden de despliegue de la migración de consentimiento (fase 1).** ✅ **Resuelto en Fase 1.**
      Migración `202607_GYM-002` (una sola story, 2 columnas en ambas tablas, `ALTER TABLE ADD COLUMN`
      idempotente + `COMMENT ON COLUMN`). `acepta_whatsapp BOOLEAN NOT NULL DEFAULT FALSE` +
      `fecha_consentimiento_wa TIMESTAMPTZ` NULL. Los mapeos son **pasivos** (default `FALSE`), así que
      auth-service y platform-service toleran `NULL`/`FALSE` en la ventana de despliegue sin fallar. Falta
      (fase 4) que core **añada la columna a la proyección** del endpoint C3; attendance no lee `personas`
      (recibe el flag vía C3). Orden de deploy: migración → servicios.

### Recomendadas

- [x] **R1 — `config_notif_suscripcion` tiene PK `(id_compania, dias_antes)` (fase 6).** ✅ **Resuelto en Fase 6.**
      Los buckets son **globales**, no por tenant → no meterlos en esa tabla por-tenant. Creada
      `saas.notif_buckets_globales(destinatario PK VARCHAR(10) CHECK IN('socio','dueno'), dias_previo INT
      NOT NULL DEFAULT 3 CHECK BETWEEN 1 AND 30, activo BOOLEAN DEFAULT TRUE, auditoría)` con seed
      idempotente `socio=3`, `dueno=3` (fue `dueno=5` en Fase 3; mantiene `3` aquí). El `0` **no** va en la
      tabla (es constante fija del código, no editable). `config_notif_suscripcion` sigue rigiendo **canal
      por tenant**, sin cambios en su PK.
- [x] **R2 — Reevaluar el `.next()` de buckets al reducir a `{previo, 0}` (fases 3 y 6).** ✅ **Resuelto
      DEFINITIVAMENTE en Fase 6.** Fase 3 resolvió dueño: `resolverBucket(diasRestantes)` mapea
      `==0`→bucket 0; `1..dias_previo`→bucket previo; resto→null. Fase 6 idem para socio: buckets
      dinámicos `{dias_previo, 0}` (ley `dias_previo` de la tabla), mismo `resolverBucket`. El día 0 ya no
      cae al template previo con "en 0 días" (templates son `venc_*_hoy` para día 0, `venc_*_previo` para
      el aviso). No hay posibilidad de cross-day: el `0` es fijo, no reprogramable.
- [x] **R3 — Poblar `fecha_vencimiento` (fase 3).** ✅ **Resuelto en Fase 3** para el canal WhatsApp del
      dueño: `WhatsAppQueueService` carga el `CompaniaPlan` y pobla `{{3}}` con
      `cp.getFechaFin().format(FECHA_ES)` (`dd/MM/yyyy`) para `venc_suscripcion_previo`. **Nota:** el bug de
      `vars.put("fecha_vencimiento","")` en `EmailQueueService` para la ruta de **email** clásica sigue
      presente (WhatsApp usa su propio service, no `EmailQueueService`); arreglar el email queda pendiente
      si se quiere `fecha_vencimiento` en el correo de vencimiento.
- [x] **R4 — `canal='whatsapp'` puro + sin consentimiento = NO enviar nada (fase 3 y 5).** ✅ **Resuelto en
      Fase 3** (dueño): el encolado en `NotificacionVencimientoJob` solo añade el canal `whatsapp` si hay
      opt-in + teléfono normalizable; si el tenant eligió solo `whatsapp` y no cumple, no se encola nada por
      ese canal (no cae a email). El `banner` se mantiene siempre. Falta aplicarlo al socio en Fase 5.
- [x] **R5 — Timeouts y clasificación de errores del `MetaWhatsAppAdapter` (fase 2).** ✅ **Resuelto en
      Fase 2.** `WebClient` sobre `HttpClient` de Reactor Netty con timeout de conexión (5s,
      `CONNECT_TIMEOUT_MILLIS`) y lectura (10s, `ReadTimeoutHandler`). `WhatsAppSendException(retryable,
      metaErrorCode)` clasifica: **retryable** = 429 + 5xx + errores de transporte/timeout; **no-retryable**
      = resto de 4xx de negocio (con `error.code` de Meta, p. ej. `131047`). El `error.code` queda en el
      mensaje para loggearlo en `ultimo_error`. Verificado por IT (429→retryable, 400 `131047`→no-retryable,
      5xx→retryable).

### Menores

- [ ] **M1 — Emojis HSM:** sugerencia — aprobar **sin** emoji (más flexible; se añade en canales no-HSM).
- [ ] **M3 — Observabilidad (fase 3, canario):** counters `whatsapp.sent`, `whatsapp.failed{codigo}`,
      `whatsapp.skipped{razon}` (`no_consentimiento`/`telefono_invalido`/`anti_spam`). Sin esto no hay
      señal temprana si Meta bloquea el número.

## Plan de fases (testeable e incremental)

> Cada fase se **testea de forma aislada** (IT con Postgres real — convención del repo — + **WireMock**
> simulando Meta y, donde aplique, el endpoint interno de core). Una fase se cierra solo cuando su
> **criterio de aceptación** está verde; recién entonces se pasa a la siguiente. El envío real a Meta
> queda mockeado en todas las fases (el `WhatsAppSender` se stubea) hasta el canario manual.

**Flujo de dependencias:**

```
Fase 0 (aprobación HSM en Meta) ── paralela, no bloquea ─────────────────┐
                                                                          │
Fase 1 (migración opt-in) ─┬─▶ Fase 3 (dueño WhatsApp) ──────────────────┼─▶ Fase 6 (panel + UI opt-in)
                            │                                             │
                            └─▶ Fase 4 (endpoint core) ─▶ Fase 5 ─────────┘
                                                        (socio WhatsApp)
Fase 2 (adaptador Meta) ─────▶ usada por Fase 3 y Fase 5
```

Paralelizables: **2 y 4** pueden ir junto con/ tras **1**. Secuenciales obligatorias: `1→3`, `1→4`,
`4→5`, `{3,5}→6`.

### Fase 0 — Trámite Meta (paralela, la gestiona el PO)

Crear/verificar WABA + `phone_number_id` y **subir las plantillas HSM a aprobación** (socio:
`venc_membresia_previo`, `venc_membresia_hoy`, `venc_accesos_previo`, `venc_accesos_final`; dueño:
`venc_suscripcion_previo`, `venc_suscripcion_hoy`, `venc_suscripcion_gracia` opcional). Todas `UTILITY`,
`es`. **No bloquea** el desarrollo (los adaptadores funcionan con mock/WARN hasta tener credenciales).

- [ ] Fase 0 completada (credenciales + plantillas aprobadas).

### Fase 1 — Migración de consentimiento (bloque E parcial) · *entrada*

- **Alcance:** story nueva `YYYYMM_GYM-XXX` (partial-changelog + DDL para las 2 columnas en
  `identidad.personas` y `tenant.companias`, `COMMENT ON COLUMN` como `ci_validada`), apendada a
  `main-changelog.yml`. Adaptar `PersonaEntity` (auth-service) y `CompaniaEntity` (platform-service) a
  mapeo pasivo (default `FALSE`). Sin endpoints ni validaciones aún.
- **Testeo:** Repository IT en auth-service y platform-service: persona/compañía nueva sin especificar →
  `FALSE`/`NULL`; registros existentes no se rompen.
- **Aceptación:** migración corre limpia contra BD limpia y con datos; ningún test existente se rompe;
  el flag aparece como campo en las respuestas actuales de personas y compañías.
- **Depende de:** nada. Resuelve **C5**.
- [x] **Fase 1 cerrada** (2026-07-15). Story `202607_GYM-002` (`ddl/01_add_consentimiento_whatsapp.sql`
      + `partial-changelog.yml` con rollback, apendada a `main-changelog.yml`). Entidades: `PersonaEntity`
      (auth + platform), `CompaniaEntity` (platform); dominio `Persona` (auth) + `Compania` (platform) +
      mapeos (`PersonaMapper`, `CompaniaPersistenceAdapter` — el opt-in **no** se toca en `update()`, como
      `trial_usado`). Flag mapeado **solo en dominio/persistencia**, no en los DTOs de respuesta (coherente
      con `ci_validada`/`trial_usado`). Verificado contra Postgres local `gym-app-saas`: 4 columnas con
      default correcto, 5 personas + 3 compañías existentes quedaron en `FALSE`/`NULL` (backfill
      no-consentido). IT verdes: `PersonaR2dbcRepositoryIT$ConsentimientoWhatsApp` (2/0/0) y
      `CompaniaR2dbcRepositoryIT$ConsentimientoWhatsApp` (2/0/0); ningún test preexistente se rompió.

### Fase 2 — Adaptador Meta + normalizador E.164 (bloques A y B en platform-service)

- **Alcance:** puerto `WhatsAppSender` + `MetaWhatsAppAdapter` (`WebClient` reactivo, timeouts,
  retryable/no-retryable — **R5**), fallback WARN si faltan env vars. Utilidad `PhoneNumberE164Normalizer`
  (pura, estática). Env vars `WHATSAPP_PROVIDER`/`META_*`. **No** se cablea al job todavía.
- **Testeo:** unit del normalizador (`0987654321→+593987654321`, `+593 987 654 321→…`, `123→empty`,
  `null→empty`); IT con **WireMock** de Meta: body `type=template`, `language.code=es`, params en orden;
  test 429→retryable; test 400 `131047`→no-retryable; test sin token → WARN + `Mono.empty()` (arranca).
- **Aceptación:** los 5 tests verdes; el servicio arranca sin env vars de Meta; el adaptador es
  inyectable como `WhatsAppSender`.
- **Depende de:** nada (paralela a Fase 1). Resuelve **R5**.
- [x] **Fase 2 cerrada** (2026-07-15). Archivos nuevos en platform-service: puerto
      `domain/port/out/WhatsAppSender`, `domain/exception/WhatsAppSendException` (retryable + metaErrorCode),
      `domain/validation/PhoneNumberE164Normalizer` (puro/estático, `Optional<String>`), adaptador
      `infrastructure/adapter/out/whatsapp/MetaWhatsAppAdapter` (WebClient + Reactor Netty timeouts, fallback
      WARN si faltan `META_PHONE_NUMBER_ID`/`META_ACCESS_TOKEN`). Env vars leídas: `META_API_BASE_URL`,
      `META_API_VERSION` (default `v21.0`), `META_PHONE_NUMBER_ID`, `META_ACCESS_TOKEN`. Dependencia test
      nueva: `okhttp3:mockwebserver` (test-scope). **No** se cablea al job todavía (eso es Fase 3). Tests
      verdes: `PhoneNumberE164NormalizerTest` (15/0/0) y `MetaWhatsAppAdapterTest` (5/0/0 — request bien
      formado, 429 retryable, 400 `131047` no-retryable, 5xx retryable, sin-credenciales→WARN+empty). El
      servicio arranca sin las env vars de Meta (bean inyectable, flag `configurado`).
      > ✅ **Deuda ajena resuelta:** `AprobarPagoServiceTest.aprobacionInmediataOk` fallaba con NPE porque el
      > mock `CompaniaPlanRepository.findActivoByIdCompania` no estaba stubeado. El servicio, en activación
      > **no** programada, llama `reemplazarActivoPrevio` → `findActivoByIdCompania` (lógica añadida por
      > `d48b283` billing/SRI) y el test no se había actualizado. Fix: stub `findActivoByIdCompania(5L) →
      > Mono.empty()` (sin plan previo que reemplazar). Suite completa ahora **238/0/0** verde.

### Fase 3 — Cola WhatsApp del dueño (bloque D-dueño) · *primer WhatsApp real*

- **Alcance:** `NotificacionVencimientoJob.encolarSiNoExiste` encola según `config_notif_suscripcion.canal`
  (`email`/`whatsapp`/`ambos`) **y** solo si `compania.aceptaWhatsapp=TRUE` + teléfono normalizable
  (**R4**). Procesador hermano `WhatsAppQueueService` + `WhatsAppQueueProcessorJob` sobre la misma tabla
  filtrando `canal='whatsapp'` (**C1**). Map `tipo`+`diasAntes`→template (`>0`→`venc_suscripcion_previo`,
  `=0`→`venc_suscripcion_hoy`). Regla cross-day del día 0 (falla y el retry cruza de fecha → `fallido`
  sin reintento). Fix `fecha_vencimiento` (**R3**). Buckets `{3,0}` hardcodeados aquí (configurables en
  fase 6); aplicar **R2** al `next()`.
- **Testeo:** IT (`WhatsAppQueueDuenoIntegrationTest`) con WireMock-Meta y `Clock.fixed`, 10 casos:
  (1) `whatsapp`+opt-in+tel válido→1 `enviado` whatsapp/0 email; (2) `ambos`+opt-in→1+1; (3)
  `whatsapp`+opt-out→0 whatsapp; (4) `ambos`+opt-out→0 whatsapp/1 email; (5) tel `null`→skip
  `telefono_invalido`; (6) 429→`reintentar` +30s; (7) 400 `131047`→`fallido` sin retry; (8) idempotencia
  (correr 2×→sigue 1 row); (9) cross-day día 0 (23:30 + 429→`fallido` inmediato); (10) `fecha_vencimiento`
  no vacío en el request a Meta.
- **Aceptación:** 10 casos verdes; canario manual a un número de prueba si Fase 0 lista (si no, se difiere).
- **Depende de:** Fase 1 (`companias.acepta_whatsapp`) + Fase 2. Resuelve **C1, R2, R3, R4** y parte de **M3**.
- [x] **Fase 3 cerrada** (2026-07-15). Archivos nuevos en platform-service: puerto in
      `domain/port/in/ProcesarColaWhatsAppUseCase`, servicio hermano
      `application/service/WhatsAppQueueService` (implementa el puerto; C1 — lee la misma tabla
      filtrando `canal='whatsapp'` vía `claimLoteWhatsapp`, sin extender `EmailQueueService`), worker
      `infrastructure/scheduler/WhatsAppQueueProcessorJob` (`fixedDelay` 30s, batch 50). Modificados:
      `NotificacionRepository` + `NotificacionPersistenceAdapter` (nuevo `claimLoteWhatsapp(max)` calcado
      de `claimLoteEmails` con `FOR UPDATE SKIP LOCKED`); `NotificacionVencimientoJob` (buckets **{3,0}**
      no `{5,0}` — decisión del usuario; **R2**: día 0 por igualdad vía `resolverBucket`; encola canal
      según `config_notif_suscripcion.canal` + `banner` siempre; **R4**: `whatsapp` solo con opt-in +
      teléfono normalizable); `application.yml` (`notificacion.whatsapp.queue.*`). Map template:
      `dias>0`→`venc_suscripcion_previo` (params [nombre, plan, fecha `dd/MM/yyyy`, dias] — **R3**),
      `dias=0`→`venc_suscripcion_hoy` (params [nombre, plan]). Regla cross-day del día 0 implementada en
      `manejarError`. **Testeo (unit Mockito, no IT-con-WireMock):** `NotificacionVencimientoJobTest`
      reescrito para {3,0}/canal/opt-in (6/0/0) y nuevo `WhatsAppQueueServiceTest` (7/0/0: previo OK +
      params en orden con fecha R3, hoy 2 params, telefono_invalido→skip, retryable→reintentar +30s,
      no-retryable→fallido con meta_code, cross-day día 0→fallido, compañía inexistente→fallido).
      `MetaWhatsAppAdapterTest` (5/0/0) intacto. Suite unit completa **197/0/0** verde. **Diferido:** el IT
      `WhatsAppQueueDuenoIntegrationTest` con WireMock-Meta + Postgres real y el canario manual quedan para
      cuando haya BD/credenciales (Fase 0). Los 39 errores de `mvn test` son `*IntegrationTest`
      preexistentes que requieren Postgres levantado, ajenos a esta fase.

### Fase 4 — Endpoint interno "clientes por vencer" (bloque C)

- **Alcance:** `GET /internal/v1/companias/{id}/clientes-por-vencer?dias=&modo=` en `InternalCoreController`
  con el contrato de **C3**; query con JOIN a `identidad.personas`, cálculo de `diasParaVencer`/
  `accesosRestantes`, excluye `congelado` y `vencido`, incluye campos de opt-in. `today` con
  `America/Guayaquil` (**C4**, refactor a `Clock`). Actualizar `docs/core-service/api/internal.md`.
- **Testeo:** IT (`ClientesPorVencerInternalIntegrationTest`): calendario 3d aparece; calendario 10d con
  `dias=3` no; accesos 7/10 → `accesosRestantes=3`; `congelado`/`vencido` nunca; opt-in reflejado;
  seguridad (sin/con secreto malo→403); zona horaria (`Clock.fixed` 04:00 UTC → `fechaCorte`=día
  Guayaquil, no UTC).
- **Aceptación:** casos verdes; smoke con servicios arrancados; `internal.md` actualizado.
- **Depende de:** Fase 1 (columnas en `personas`). Paralela a 2/3. Resuelve **C3, C4**.
- [x] **Fase 4 cerrada** (2026-07-15). Archivos nuevos en core-service: `domain/model/ClientePorVencer`
      (proyección C3: `telefono` sin normalizar, opt-in incluido, `estadoCliente`, `diasParaVencer`/
      `accesosRestantes`), `domain/model/ClientesPorVencerResult` (`companiaId` + `fechaCorte` +
      `clientes[]`), `infrastructure/config/ClockConfig` (**C4** — `Clock` anclado a
      `America/Guayaquil`, `@ConditionalOnMissingBean` para override en tests). Modificados:
      `ClienteRepository` + `ClientePersistenceAdapter` (nuevo `findClientesPorVencer(idCompania,
      fechaCorte, dias, modo)` — JOIN a `identidad.personas` con `acepta_whatsapp`/
      `fecha_consentimiento_wa`, `LEFT JOIN asistencia.asistencias` para `accesos_restantes`, filtro
      por modo vía `HAVING`, excluye `congelado`/`vencido`, usa `:fechaCorte` en vez de `CURRENT_DATE`
      para respetar C4); `InternalCoreController` (endpoint `GET /internal/v1/companias/{id}/
      clientes-por-vencer?dias=&modo=`, valida `dias∈[0,30]` y `modo∈{calendario,accesos,todos}` →
      400, header `X-Internal-Call` → 403, `fechaCorte = LocalDate.now(clock)`);
      `BaseIntegrationTest` (limpieza de `asistencia.asistencias` antes de `membresias` por FK).
      `docs/core-service/api/internal.md` actualizado con el contrato completo. **Testeo:** IT
      `ClientesPorVencerInternalIntegrationTest` **13/13 verde** (calendario 3d aparece/10d no; accesos
      restantes 3 aparece/8 no; congelado y vencido nunca; opt-in sí/no reflejado; **C4** fechaCorte =
      día Guayaquil con `Clock.fixed` a `2026-07-15T04:00Z`→`2026-07-14`; seguridad sin/mal secreto →
      403; validación `dias=99`→400 y `modo=raro`→400). **Nota:** los fallos preexistentes de
      `MembresiaIntegrationTest`, `AccessControlServiceTest`, `ClienteServiceTest` y
      `MembresiaServiceTest` (5 F + 9 E) son **ajenos a esta fase** — reproducidos en `master` sin estos
      cambios (deuda: `UnnecessaryStubbingException` de Mockito y `validar-acceso` devolviendo 400).

### Fase 5 — `MensajeriaJob` del socio cableado (bloques A, B, D-socio en attendance)

- **Alcance:** duplicar `WhatsAppSender`/`MetaWhatsAppAdapter` y `PhoneNumberE164Normalizer` en
  attendance (decisión pragmática v1). `CoreServiceClient.listarClientesPorVencer(...)` consume el
  endpoint de Fase 4. Implementar `procesarAusencias()` → itera compañías → llama `procesarCliente(...)`.
  Tras `mensajes_log` `pendiente`, llamar al `WhatsAppSender` con el template por `tipo`+`modoControl` y
  actualizar `enviado`/`fallido`. Chequeo de opt-in (sin fallback a email — attendance no manda emails) y
  de teléfono normalizable. Idempotencia `existsEnviadoHoy(idCliente, tipo, canal)` (**C2**). Fecha
  `dd/MM/yyyy`.
- **Testeo:** IT (`MensajeriaJobWhatsAppIntegrationTest`) con WireMock de core (3 clientes: calendario 3d,
  calendario 0d, accesos 0d) y de Meta: 3 `mensajes_log` `enviado` con templates correctos; opt-out→0
  requests; idempotencia (2×→3 rows, no 6); congelado→0; teléfono inválido→skip.
- **Aceptación:** tests verdes; canario manual a número de prueba (si Fase 0 lista).
- **Depende de:** Fase 1 + Fase 4 (+ Fase 2 como referencia del adaptador). Resuelve **C2**.
- [x] **Fase 5 cerrada** (2026-07-15). Archivos nuevos en attendance-service (duplicado pragmático v1 de
      Fase 2): `domain/port/out/WhatsAppSender`, `domain/exception/WhatsAppSendException`,
      `domain/validation/PhoneNumberE164Normalizer`, `infrastructure/adapter/out/whatsapp/MetaWhatsAppAdapter`.
      Modificados: `CoreServiceClient` (nuevo `listarClientesPorVencer(idCompania, dias, modo)` con header
      `X-Internal-Call` + DTOs `ClientesPorVencerResponse`/`ClientePorVencer`); `MensajeLogRepository` +
      `MensajeLogPersistenceAdapter` + `MensajeLogR2dbcRepository` (**C2**: `existsEnviadoHoy(idCliente,
      tipo, canal, dia)` por rango de día Guayaquil); `AsistenciaRepository` + adapter
      (`findCompaniasActivas`, `findNombreCompania`); `MensajeLogService` (inyecta `WhatsAppSender`;
      `existsEnviadoHoy` + `enviarWhatsAppJob` que inserta `pendiente`→envía HSM→`enviado`/`fallido`);
      `MensajeriaJob` **reescrito** (`procesarAusencias()` ya no vacío: itera compañías → consume core →
      buckets {3,0} → ruteo template HSM por `tipo`+`modoControl`, opt-in R4, teléfono normalizable, RN-05
      congelado, idempotencia C2, fecha `dd/MM/yyyy`); `application.yml` (`services.core-service.internal-secret`
      + `whatsapp.meta.*`); `pom.xml` (`okhttp3:mockwebserver` test). Map plantillas del socio:
      calendario→`venc_membresia_previo`[nombre,gym,fecha,dias]/`venc_membresia_hoy`[nombre,gym];
      accesos→`venc_accesos_previo`[nombre,accesos,gym]/`venc_accesos_final`[nombre,gym]. `README.md`
      actualizado. **Testeo (unit Mockito + MockWebServer, no IT-con-WireMock):** `MensajeriaJobTest`
      (9/0/0: calendario previo/hoy, accesos previo/final con params en orden, sin opt-in→no envía,
      telefono inválido→skip, congelado→no envía, idempotencia C2→no reenvía, fuera de bucket→no envía),
      `PhoneNumberE164NormalizerTest` (15/0/0), `MetaWhatsAppAdapterTest` (5/0/0: request bien formado,
      429/5xx retryable, 400 `131047` no-retryable, sin credenciales→WARN+empty). `MensajeLogServiceTest`
      (16/0/0) intacto tras añadir el mock `WhatsAppSender`. **Diferido:** el IT
      `MensajeriaJobWhatsAppIntegrationTest` con WireMock de core+Meta + Postgres y el canario manual quedan
      para disponibilidad de BD limpia/credenciales (Fase 0). **Nota:** los ~58 errores de `*IntegrationTest`
      de attendance son **preexistentes/ambientales** — `BaseIntegrationTest.cleanDatabase` falla con FK
      (`tipos_membresia`↔`membresias`) por datos residuales en la BD local compartida `gym-app-saas`;
      reproducido en HEAD sin estos cambios (mis clases nuevas no tocan `BaseIntegrationTest` ni los ITs).

### Fase 6 — Panel super_admin + buckets configurables + UIs de opt-in (bloque E completo) — BACKEND CERRADO

- **Alcance BACKEND (2026-07-16):** tabla `saas.notif_buckets_globales` (**R1** ✅ resuelto), story nueva
  `202607_GYM-003`. Seed idempotente: `(socio, 3, true)` + `(dueno, 3, true)` (ambos activos, día 0 fijo
  en código). DDL: `destinatario VARCHAR(10) PK CHECK IN('socio','dueno')`, `dias_previo INT DEFAULT 3
  CHECK BETWEEN 1 AND 30`, `activo BOOLEAN DEFAULT TRUE`, auditoría (`modificado_por`, `modificado_at`,
  `creacion_fecha`, `creacion_usuario`). Endpoint **GET/PUT** en platform-service:
  - `GET /api/v1/plataforma/notif-buckets` (super_admin only) → lista `[{destinatario, diasPrevio, activo,
    diaVencimiento:0}]`.
  - `PUT /api/v1/plataforma/notif-buckets/{destinatario}` (super_admin only) → body `{diasPrevio, activo}`;
    validación `diasPrevio ∈ [1,30]`; 400 si fuera de rango, 404 si `destinatario` inválido.
  - `GET /internal/v1/notif-buckets/{destinatario}` (header `X-Internal-Call`, NO JWT) → `{destinatario,
    dias_previo, activo}` (consume attendance-service para el socio). 403 sin secreto, 404 si fila ausente,
    400 si `destinatario` inválido.
  
  Endpoints PATCH opt-in (nuevo en Fase 6 backend):
  - **auth-service:** `PATCH /api/v1/personas/{id}/consentimiento-wa` (JWT válido: staff/cliente/plataforma)
    → body `{acepta: boolean}`. Respuesta: `{idPersona, aceptaWhatsapp, fechaConsentimientoWa}`. Si
    `acepta=true` sella `fecha_consentimiento_wa`, si `acepta=false` la limpia.
  - **platform-service:** `PATCH /api/v1/companias/{id}/consentimiento-wa` (gate `requireAccessToCompania`:
    super_admin o dueño del propio tenant) → body `{acepta: boolean}`. Respuesta:
    `{idCompania, aceptaWhatsapp, fechaConsentimientoWa}`. Misma semántica.
  
  Los dos jobs (dueño y socio) ahora leen `dias_previo` dinámicamente desde la tabla (en vez de constante
  {3} o {5}). **R2 resuelto definitivamente:** el `0` sigue fijo en código, no editable; buckets reales
  son siempre `{dias_previo, 0}`. Fallback a 3 si fila ausente.
  
  `PlatformServiceClient` nueva en attendance-service: consume `GET /internal/v1/notif-buckets/socio` para
  leer el bucket del socio. Config: `services.platform-service.url` (env var `PLATFORM_SERVICE_URL`, default
  `http://localhost:8081`) + `internal-secret`.

- **Testeo:** unit (Mockito) de los servicios / repositories; sin IT-con-Postgres por convención de v1
  (deduzco de Fase 3 y 5: diferido a canario manual + BD limpia).
- **Aceptación BACKEND (✅ completo):**
  - [x] Migración `202607_GYM-003` corre limpia, seed idempotente (socio=3/dueno=3 ambos activos).
  - [x] `GET /plataforma/notif-buckets` lista buckets con auditoría.
  - [x] `PUT /plataforma/notif-buckets/{destinatario}` actualiza con validación + auditoría.
  - [x] `GET /internal/v1/notif-buckets/{destinatario}` responde sin JWT (header `X-Internal-Call`).
  - [x] `PATCH /api/v1/personas/{id}/consentimiento-wa` sella/limpia `fecha_consentimiento_wa` del socio.
  - [x] `PATCH /api/v1/companias/{id}/consentimiento-wa` sella/limpia `fecha_consentimiento_wa` del dueño.
  - [x] `NotificacionVencimientoJob` (dueño) lee bucket dinámico de la tabla + fallback 3.
  - [x] `MensajeriaJob` (socio) lee bucket dinámico vía `PlatformServiceClient` + fallback 3.
  - [x] Tests verdes (unit); cambio de bucket → reinicio → jobs usan valor nuevo.
  
  **Pendiente FRONTEND (Fase 6B, no backend):**
  - [ ] Panel super_admin en `auth-service-frond-end`: pantalla de buckets + hora de envío (solo lectura) +
        botón salvar.
  - [ ] 4 UIs de captura de opt-in: (1) checkbox en auto-registro PWA (gym-member-pwa), (2) toggle en
        perfil PWA, (3) checkbox alta staff (panel admin), (4) toggle de compañía (panel dueño).

- **Depende de:** Fases 1, 3 y 5. Resuelve **R1, R2** (definitivo).
- [x] **Fase 6 BACKEND cerrada (2026-07-16).** Archivos nuevos en platform-service: `domain/model/NotifBucket`,
      `domain/port/out/NotifBucketsRepository`, `application/service/NotifBucketsService`, controlador
      `infrastructure/adapter/in/web/NotifBucketsController` (GET/PUT /plataforma/notif-buckets) +
      `InternalNotifBucketsController` (GET /internal/v1/notif-buckets/{destinatario}), adaptador
      `infrastructure/adapter/out/persistence/adapter/NotifBucketsPersistenceAdapter`. Modificados:
      `NotificacionVencimientoJob` (lee `dias_previo` dinámico, fallback 3); `application.yml` (env
      `PLATFORM_SERVICE_INTERNAL_SECRET`). Nuevos en attendance-service: `infrastructure/client/
      PlatformServiceClient` (GET /internal/v1/notif-buckets/socio); modificado `MensajeriaJob` (consume
      `PlatformServiceClient`, fallback 3). Nuevos en auth-service: controlador
      `infrastructure/adapter/in/web/ConsentimientoWhatsAppController` (PATCH /personas/{id}/
      consentimiento-wa). Storage: `db/scripts/202607_GYM-003/` (migración Liquibase con seed idempotente).
      **Tests verdes** (unit Mockito, no IT preexistentes rotos).
      
      **Archivos modificados (git status inicial):**
      - `platform-service/src/main/java/com/gymadmin/platform/domain/port/out/NotificacionRepository.java`
      - `platform-service/src/main/java/com/gymadmin/platform/infrastructure/adapter/out/persistence/adapter/NotificacionPersistenceAdapter.java`

## Relacionado

- Spec del lado socio (mensajería + plantillas + `mensajes_log`):
  [../specs/attendance-service.md](../specs/attendance-service.md) §5 y §11 (RN-05, RN-06, RN-09).
- Detección de vencimiento del socio: `core-service` → `ClienteStatusJobService`; patrón interno
  service-to-service: [../../core-service/api/internal.md](../../core-service/api/internal.md).
- Job y cola del lado dueño: `platform-service` → `NotificacionVencimientoJob` /
  `EmailQueueService` / `EmailQueueProcessorJob` (REQ-SAAS-001 Sub-fases 1.5 y 1.6).
- Config de notificaciones por tenant (hoy solo super_admin): `tenant.config_notif_suscripcion`
  · `NotifConfigController` → `/api/v1/companias/{id}/notif-config`.
- Convención de migraciones (story nueva, no editar baseline):
  [../../../gym-administrator/CLAUDE.md](../../../gym-administrator/CLAUDE.md) § *Database Migration Conventions*.
