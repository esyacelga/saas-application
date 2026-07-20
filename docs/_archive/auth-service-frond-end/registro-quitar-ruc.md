# REQ — Adelgazar el registro de gimnasios (quitar RUC y contactos del Paso 1, suavizar el Paso 2)

> **ESTADO:** ✅ **Implementado (2026-07-14)** para el auto-registro público — código y migración. Ver el registro de cambios en [registro-mejoras-implementadas.md](registro-mejoras-implementadas.md). Falta aplicar la migración a la Neon (se borra y recorre). El **teléfono** y el **WhatsApp** se quitaron del Paso 1 junto con el RUC.
> **Fecha:** 2026-07-14
> **Decisión de producto (2026-07-14):**
> - El **RUC** sale del registro → se pide en el [wizard de facturación](../billing-service/pendientes/wizard-configuracion-sri.md), solo cuando el gym decide facturar.
> - El **WhatsApp** sale del registro → se pedirá al **activar notificaciones por WhatsApp** (feature futura que hoy no existe).
> - El **teléfono** sale del registro → queda editable en **"Mi Empresa"** (`EditarCompaniaModal`, que ya lo tiene) para completar cuando quieran.
> - El **Paso 2 (sucursal)** se **suaviza**: se renombra (deja de decir "sede/sucursal", que asusta a un gym de un solo local) y se **pre-llena** el nombre con el del gym del Paso 1, para que el caso mayoritario —un único local— solo dé "Siguiente". No se elimina el paso: el local físico sí es un dato real.
> **Principio aplicado:** disclosure progresivo — no pedir un dato hasta que haya una razón concreta para pedirlo.
> **Parte de:** [restructuración de onboarding y facturación](../gym-administrator/restructuracion-onboarding-facturacion.md) (documento paraguas). **Es la Pieza 1 — la de menor riesgo.**
> **Relacionado:** [wizard-configuracion-sri.md](../billing-service/pendientes/wizard-configuracion-sri.md) · [facturacion-diseno.md](facturacion-diseno.md)

---

## 1. El problema (desde la piel de un usuario nuevo)

Un dueño de gimnasio que llega a **conocer la app** —probar el trial, ver si le sirve— hoy se topa, en el **primer paso** del registro, con **cuatro campos que no necesita para probar nada**: el **RUC**, el **teléfono**, el **WhatsApp** y el correo corporativo. El RUC además es un dato tributario obligatorio.

Los daños:

1. **Fricción de conversión.** Cada campo de más en un formulario de registro es gente que abandona. Y un dato *fiscal* de entrada (el RUC) asusta especialmente. El usuario que solo quiere probar no tiene por qué pensar en tributación ni dar sus números de contacto todavía.
2. **Son datos que no se usan.** Verificado en el código (2026-07-14):
   - El **RUC** se guarda en `tenant.companias.ruc`, pero **el emisor de facturas no lo lee** — factura desde `facturacion.config_sri`, que llena el wizard de facturación (ver [decisión de fuente única](../billing-service/pendientes/wizard-configuracion-sri.md#23-decisión-fuente-única-en-config_sri-borrar-las-columnas-muertas)).
   - El **WhatsApp** se guarda, pero **ningún servicio lo usa**: existe la constante `NotificacionSuscripcion.CANAL_WHATSAPP` pero no hay ningún emisor que la consuma. Las notificaciones de vencimiento van por email o banner.
   - O sea: **se piden datos que intimidan o cansan, para después no usarlos.** Lo peor de los dos mundos.

## 2. Principio de diseño: disclosure progresivo

**"Pide cada dato en el momento en que hay una razón concreta para pedirlo — no antes."**

El registro mezcla intenciones que deben estar separadas:

| Intención | Qué necesita de verdad | Dónde va |
|---|---|---|
| *"Quiero entrar y probar"* | Nombre del gym · tu cuenta (nombre, cédula, correo, contraseña) · plan | **Registro** |
| *"Quiero facturar"* | RUC, razón social, certificado `.p12`, punto de emisión | **Wizard de facturación** |
| *"Quiero notificaciones por WhatsApp"* | Número de WhatsApp | **Al activar esa feature** (no existe aún) |
| *"Quiero completar el perfil de mi negocio"* | Teléfono de contacto | **"Mi Empresa"** (`EditarCompaniaModal`, ya existe) |

Hoy el registro arrastra pedazos de las otras tres intenciones hacia la primera.

> 💡 **La cédula y la contraseña del Paso 4 SÍ se quedan.** No son "datos personales de más": son la creación de la cuenta del administrador. Sin ellas no hay a quién dar acceso ni cómo iniciar sesión. Están bien ubicadas (al final, tras ver el plan). Los datos mal puestos están todos en el **Paso 1**: RUC, teléfono y WhatsApp.

> ⚖️ **Contrapeso — no dejar el Paso 1 tan pelado que se sienta incompleto.** Tras quitar RUC + teléfono + WhatsApp, el Paso 1 queda con **nombre del gym** (obligatorio) + **correo** (opcional). Se evaluó fusionarlo con el Paso 2; **se decidió mantenerlos separados** al suavizar y pre-llenar el Paso 2 (ver [§3.4](#34-frontend--suavizar-el-paso-2-sucursal-para-el-gym-de-un-solo-local) y [§6, pregunta 4](#6-preguntas-abiertas)).

## 3. Qué cambia

### 3.1 Frontend — quitar RUC, teléfono y WhatsApp del Paso 1

- **Pantalla:** [`Step1Empresa.tsx`](../../auth-service-frond-end/src/ui/features/auth/pages/AutoRegistro/steps/Step1Empresa.tsx) — eliminar los bloques de **`ruc`** (líneas ~42-53), **`telefono`** y **`whatsapp`** (el grid de dos columnas, ~70-94). El Paso 1 queda: **nombre del gimnasio** (obligatorio) + **correo corporativo** (opcional).
- **Schemas de validación** — quitar `ruc`, `telefono` y `whatsapp` de **ambos**, porque hay dos flujos de registro:
  - [`auto-registro-wizard.schema.ts`](../../auth-service-frond-end/src/ui/features/auth/schemas/auto-registro-wizard.schema.ts) — el auto-registro público (el que ve el usuario nuevo).
  - [`registrar-gym-wizard.schema.ts`](../../auth-service-frond-end/src/ui/features/platform/schemas/registrar-gym-wizard.schema.ts) — el registro que hace un operador de plataforma. Hoy tiene `ruc: z.string().min(10).max(20)`.
- **Use cases** — `AutoRegistro.usecase.ts` y `RegistrarGymWizard`: dejar de enviar `ruc`, `telefono` y `whatsapp` en el payload.

> ✅ **El teléfono ya tiene dónde reaparecer — no hay que construir nada.** [`EditarCompaniaModal.tsx`](../../auth-service-frond-end/src/ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/EditarCompaniaModal.tsx) (pantalla "Mi Empresa") **ya tiene los campos `telefono` y `whatsapp` editables** (opcionales). Quitarlos del registro no pierde la capacidad de cargarlos: solo la mueve al momento correcto.

> **El WhatsApp** no necesita pantalla nueva ahora: se pedirá el día que se implemente el canal de notificación por WhatsApp (`CANAL_WHATSAPP`, hoy declarado pero sin emisor). Ese día, la feature pedirá el número en su propio flujo de activación.

### 3.2 Backend — el obstáculo real: `ruc` es `NOT NULL UNIQUE`

> 🔴 **No se puede simplemente dejar de mandar el RUC.** La columna es:
> ```sql
> ruc  VARCHAR(20)  NOT NULL UNIQUE
> ```
> Un `INSERT` sin `ruc` **falla**. Hay que decidir qué hacer con la columna. Tres opciones:

| Opción | Qué implica | Recomendación |
|---|---|---|
| **A. Hacer `ruc` nullable** | `ALTER TABLE tenant.companias ALTER COLUMN ruc DROP NOT NULL`. El gym se registra sin RUC; se llena luego en el wizard de facturación (o queda null si nunca factura). | ✅ **Recomendada.** Refleja la realidad: no todo gym factura. |
| **B. Generar un placeholder** | Insertar algo tipo `PENDIENTE-{id}` para satisfacer el `NOT NULL`. | ❌ Ensucia los datos; el `UNIQUE` obliga a placeholders distintos; alguien va a confundir un placeholder con un RUC real. |
| **C. Mantener el RUC obligatorio** | No cambiar nada. | ❌ Contradice la decisión de producto. |

**Con la opción A**, ojo con el `UNIQUE`: un índice único sobre una columna nullable permite **múltiples NULL** en Postgres (dos gyms sin RUC no chocan), y sigue impidiendo dos gyms con el **mismo** RUC real. Es exactamente el comportamiento que queremos. **No hay que tocar el `UNIQUE`.**

> ⚠️ **Coherencia con la otra migración pendiente.** El [wizard de facturación §2.3](../billing-service/pendientes/wizard-configuracion-sri.md#23-decisión-fuente-única-en-config_sri-borrar-las-columnas-muertas) ya propone una migración que **borra 4 columnas fiscales muertas** de `tenant.companias` (`nombre_comercial`, `dir_matriz`, `obligado_contabilidad`, `contribuyente_especial`). **Conviene hacer ambos cambios en la misma story Liquibase:** dejar `ruc` nullable + borrar las 4 columnas muertas. Un solo `ALTER TABLE tenant.companias`, un solo cambio a coordinar con la Neon.
>
> **`ruc` se conserva** (solo pasa a nullable) — es identidad del tenant. Las otras 4 sí se borran.

> ℹ️ **Teléfono y WhatsApp NO necesitan migración.** `tenant.companias.telefono` y `whatsapp` **ya son nullable** — solo se dejan de enviar desde el registro. Siguen editables desde "Mi Empresa". No se tocan en la BD.

### 3.3 Validación del RUC — se muda, no se pierde

La validación de RUC ecuatoriano (13 dígitos + dígito verificador) **no desaparece**: se mueve al wizard de facturación, que es donde el RUC pasa a ser obligatorio y crítico. Vivirá en `src/lib/sri/validarRuc.ts` (la frontera ya definida en [facturacion-diseno.md §14](facturacion-diseno.md#14-sección-clave-qué-es-replicable-a-otros-rubros)).

> Nota: el registro actual valida el RUC **flojo** (`min(10).max(20)`, sin dígito verificador). O sea que hoy acepta RUCs inválidos de todas formas — otra razón por la que no aporta valor en el registro. En el wizard de facturación se valida **en serio**.

### 3.4 Frontend — suavizar el Paso 2 (sucursal) para el gym de un solo local

**El problema no es el campo, es la palabra.** El Paso 2 hoy pide **"Nombre de la sede"** (obligatorio) + **dirección** (opcional). Para un dueño que tiene **un solo gimnasio** —que va a ser la mayoría— la palabra *"sede" / "sucursal"* suena a empresa con varios locales y genera la duda *"¿tengo que tener sucursales? yo solo tengo mi gym"*. El dato físico es real (siempre hay un lugar donde entra la gente); lo que sobra es el **nombre técnico** y el **tener que escribirlo**.

> ⚠️ **No confundir dos conceptos que comparten palabra:**
> | Concepto | Qué es | Dónde se pide |
> |---|---|---|
> | **Sucursal (operativa)** | El local físico donde entrenan. Todo gym tiene mínimo uno. | **Este Paso 2** |
> | **Establecimiento (SRI, código 001)** | Punto de emisión fiscal para facturar. | **Wizard de facturación** (Pieza 2), NO aquí |
>
> La pregunta "¿y si no tengo sede?" nace de mezclar el segundo (fiscal, opcional) con el primero (físico, siempre existe). El registro solo necesita el primero.

**Decisión (2026-07-14): suavizar y pre-llenar** — mantener el paso, no fusionarlo. Cambios:

1. **Renombrar, no "sede/sucursal".** Encabezado del paso: de *"Sede principal / ¿Dónde está ubicado tu gimnasio?"* a algo como **"Tu gimnasio / ¿Dónde entrenan tus clientes?"**. El label del campo: de *"Nombre de la sede"* a **"Nombre del local"** (o simplemente reutilizar el nombre del gym, ver punto 2).
2. **Pre-llenar el nombre del local con el nombre del gym del Paso 1.** Así el gym de un solo local **no escribe nada** y solo avanza; el que tiene varios lo cambia. El campo sigue siendo obligatorio (Zod `min(2)` no cambia), pero llega lleno.
3. **Dirección: sigue opcional** (ya lo está) — coherente con disclosure progresivo.
4. **Texto de ayuda:** *"Puedes agregar más sedes desde el panel"* → *"Podrás agregar más **locales** desde el panel cuando quieras."* — comunica que el sistema soporta varios sin obligar a pensar en ello ahora.

**Punto exacto del pre-llenado (código actual):** en [`AutoRegistroPage.tsx`](../../auth-service-frond-end/src/ui/features/auth/pages/AutoRegistroPage.tsx), cada paso tiene su propio `useForm` (`form1`…`form4`) y el estado se pasa entre pasos al avanzar. En `handleStep1` (hoy líneas ~100-103):

```ts
const handleStep1 = form1.handleSubmit((data) => {
  setStep1Data(data)
  // Pre-llenar el nombre del local con el del gym, solo si el usuario no lo tocó.
  if (!form2.getValues('nombreSucursal')) {
    form2.setValue('nombreSucursal', data.nombre)
  }
  setCurrentStep(2)
})
```

- **`if (!...getValues)`** evita pisar lo que el usuario ya haya escrito si vuelve atrás y re-avanza. El default `''` de `form2` (línea ~66) hace que la primera vez el campo esté vacío y se pre-llene.
- **No requiere backend ni migración.** Es puramente de UI en el orquestador del wizard.
- **Aplica a ambos flujos** si se quiere consistencia: el auto-registro público ([`AutoRegistroPage.tsx`](../../auth-service-frond-end/src/ui/features/auth/pages/AutoRegistroPage.tsx)) y el registro por operador ([`RegistrarGymWizard`](../../auth-service-frond-end/src/ui/features/platform/pages/RegistrarGymWizard/)). Prioridad: el público, que es el que ve el usuario nuevo asustadizo.

> 💡 Este ajuste **cierra la pregunta abierta 4** (¿fusionar Paso 1 y Paso 2?): al quedar el Paso 2 pre-llenado y de un solo campo real, ya no se siente como un paso pesado, y mantenerlo separado conserva la posibilidad de multi-local sin costo de UX. **Se mantiene como paso propio.**

## 4. Estado del registro después del cambio

**Antes** — Paso 1 con 5 campos:
```
nombre* · RUC* · correo · teléfono · WhatsApp
```

**Después** — Paso 1 con 2 campos:
```
Paso 1 · Tu gimnasio    → nombre del gimnasio (oblig.) + correo (opc.)    ← se quitaron RUC, teléfono y WhatsApp
Paso 2 · Tu gimnasio    → nombre del local (pre-lleno con el del Paso 1) + dirección (opc.)  ← renombrado + pre-llenado
Paso 3 · Tu plan        → (sin cambios)
Paso 4 · Tus datos      → nombre + cédula + correo + contraseña           ← se queda (es tu cuenta)
```

Cada dato removido reaparece en el momento en que hay una razón para pedirlo:
| Dato | Reaparece en |
|---|---|
| RUC | Wizard de facturación (al activar facturación) |
| Teléfono | "Mi Empresa" (perfil, cuando quieran completarlo) |
| WhatsApp | Al activar notificaciones por WhatsApp (feature futura) |

Más corto, sin datos tributarios ni de contacto de entrada, y sin perder ninguna capacidad — solo se movió el *cuándo*.

## 5. Qué es replicable a otros rubros

Directamente aplicable a cualquier SaaS ecuatoriano:
- **El registro no debe pedir el RUC.** La facturación es opcional en casi todos los rubros (una peluquería, una mecánica pequeña pueden no facturar al inicio), así que el RUC pertenece al onboarding de facturación, nunca al de la cuenta.
- **El registro no debe pedir datos que no alimentan nada todavía** (como el WhatsApp sin canal que lo consuma). El disclosure progresivo es un principio de producto, no específico de gimnasios.

Este documento y sus decisiones se replican tal cual.

## 6. Preguntas abiertas

1. **¿Hay gyms ya registrados con RUC?** Si la tabla tiene datos, la migración a nullable es segura (no toca los existentes). Verificar que ningún reporte/pantalla actual asuma que `ruc` siempre está presente antes de mostrarlo. *(La búsqueda de 2026-07-14 no encontró lectores de `ruc` fuera del registro y `config_sri`, pero reconfirmar antes de migrar.)*

2. **¿El registro por plataforma (`registrar-gym-wizard`) quiere conservar el RUC?** Un operador que da de alta un gym "llave en mano" quizás sí tiene el RUC a mano y quiere cargarlo. *(Recomendación: aun así quitarlo del registro y dejarlo para el wizard de facturación — mantener un solo lugar donde vive el RUC evita las dos copias que ya decidimos evitar. Pero es una decisión de producto que conviene confirmar.)*

3. **¿El nombre del gym alcanza como identificador único del tenant** si el RUC pasa a ser nullable? Hoy el `UNIQUE` está en `ruc`. Si un gym se registra sin RUC, ¿qué impide dos gyms con el mismo nombre? *(Probablemente nada, y probablemente está bien — el identificador real es el `id`. Confirmar que no hay lógica que dependa de la unicidad del RUC.)*

4. ~~**¿El Paso 1 sobrevive como paso propio?**~~ ✅ **Resuelto (2026-07-14):** sí. Con el Paso 2 renombrado y pre-llenado (ver §3.4), ya no se siente pesado, y mantener los pasos separados conserva la posibilidad de multi-local sin costo de UX. No se fusionan.

---

## 7. Pendiente relacionado (NO es parte de este cambio)

> 📌 **Feature futura — activación de notificaciones por WhatsApp.**
> Cuando se implemente el canal `CANAL_WHATSAPP` (hoy declarado en `NotificacionSuscripcion` pero sin ningún emisor que lo consuma), **debe existir una opción para activarlo, y esa activación SÍ debe pedir el número de WhatsApp y hacerlo obligatorio** — porque ahí sí es un dato necesario para que la feature funcione. Es el otro lado del disclosure progresivo: el dato que quitamos del registro se vuelve **obligatorio** en el punto exacto donde se usa.
>
> Esto **no se implementa ahora** — se registra aquí para no perderlo. El foco actual son los dos wizards: el [de facturación](../billing-service/pendientes/wizard-configuracion-sri.md) y el adelgazamiento del registro (este documento).
