# Registro de gimnasios — mejoras implementadas (2026-07-14)

> **ESTADO:** ✅ **Implementado** (código y migración). La migración a la Neon queda pendiente de que se recorra la base (ver §7).
> **Origen:** revisión UX del auto-registro público + Pieza 1 de la [restructuración de onboarding](../gym-administrator/requirements/restructuracion-onboarding-facturacion.md).
> **Alcance:** solo el **auto-registro público** (`AutoRegistroPage`). El registro por operador de plataforma (`RegistrarGymWizard`) **no se tocó** — conserva RUC obligatorio.
> **Diseño previo:** [registro-quitar-ruc.md](registro-quitar-ruc.md).

Este documento es el registro de qué se cambió y por qué, para consultas futuras. Ordenado de la mejora más pequeña a la más grande.

---

## Resumen de archivos tocados

| Capa | Archivo | Cambio |
|---|---|---|
| Frontend | `auth/pages/AutoRegistro/steps/Step4DatosPropios.tsx` | Accesibilidad del toggle de contraseña |
| Frontend | `auth/pages/AutoRegistro/steps/Step2Sucursal.tsx` | Renombrado + "(opcional)" en dirección |
| Frontend | `auth/pages/AutoRegistro/ResumenExito.tsx` | Pasos post-registro corregidos |
| Frontend | `lib/sri/validarCedula.ts` · `lib/sri/validarRuc.ts` | **Nuevos** — frontera de validación pura |
| Frontend | `auth/schemas/auto-registro-wizard.schema.ts` | Validación real de cédula + schemas propios pasos 1 y 2 |
| Frontend | `auth/pages/AutoRegistro/steps/Step1Empresa.tsx` | Solo nombre + correo (sin RUC/tel/WhatsApp) |
| Frontend | `auth/pages/AutoRegistroPage.tsx` | Pre-llenado Paso 2, payload sin RUC/tel/WhatsApp, 409-RUC eliminado, stepper "Sede"→"Local" |
| Frontend | `infrastructure/http/auth/auth.dto.ts` | `AutoRegistroRequest.ruc` pasa a opcional |
| Backend | `dto/AutoRegistroRequest.java` | **Nuevo** — DTO propio del auto-registro con `ruc` opcional |
| Backend | `web/CompaniaController.java` | `/auto-registro` usa el nuevo DTO |
| Backend | `application/service/CompaniaService.java` | Guard: solo valida duplicado de RUC si viene presente |
| BD | `202607_GYM-002/ddl/02_alter_table_tenant_companias_ruc_nullable.sql` | **Nuevo** — `ruc` DROP NOT NULL |
| BD | `202607_GYM-002/partial-changelog.yml` | changeset `GYM-002-2` |

---

## 1. Accesibilidad del "mostrar contraseña" (la más pequeña)

**Antes:** los dos botones de ojo (contraseña y confirmar) tenían `tabIndex={-1}` — inalcanzables por teclado, sin etiqueta accesible.
**Ahora:** se quitó `tabIndex={-1}` y se añadió `aria-label` dinámico ("Mostrar/Ocultar contraseña") + `aria-pressed`. Un usuario que navega por teclado o con lector de pantalla ya puede revelar la contraseña.

## 2. Etiquetas "(opcional)" explícitas

**Problema:** los campos opcionales solo se distinguían por la **ausencia** del asterisco rojo — algo que un recepcionista poco técnico no lee.
**Ahora:** los campos opcionales dicen la palabra "(opcional)" en gris junto al label. Aplicado a **dirección** (Paso 2) y **correo corporativo** (Paso 1).

## 3. Pantalla de éxito corregida

**Problema:** `ResumenExito` recomendaba como primer paso post-registro *"Configura nombre comercial, logo y horarios"* — pero **el nombre comercial es un dato fiscal que sacamos del onboarding** (va al wizard de facturación). Promocionaba algo que ya no vive ahí.
**Ahora:** los tres "próximos pasos" apuntan a acciones reales del panel, verificadas contra las rutas existentes (`/admin/dashboard`, `/admin/tipos-membresia`, `/admin/clientes`):
1. Inicia sesión y revisa tu dashboard
2. Crea tu primer tipo de membresía
3. Registra a tus primeros clientes

## 4. Frontera de validación pura `src/lib/sri/`

**Nuevo directorio** con TypeScript puro (sin React/axios/PrimeReact), replicable a otros SaaS ecuatorianos:
- **`validarCedula.ts`** — algoritmo oficial del Registro Civil (módulo 10): 10 dígitos, provincia 01–24 o 30, tercer dígito < 6, verificador.
- **`validarRuc.ts`** — RUC ecuatoriano SRI: persona natural (cédula + "001"), sociedad pública (módulo 11 / "0001"), sociedad privada (módulo 11 / "001"). Reutiliza `validarCedula`.

Esta es la frontera prevista en [facturacion-diseno.md §14](facturacion-diseno.md#14-sección-clave-qué-es-replicable-a-otros-rubros). `validarRuc` todavía no se consume desde ninguna pantalla — queda lista para el wizard de facturación.

## 5. Validación real de cédula en el Paso 4

**Antes:** `ci: min(1).max(20)` — aceptaba "abc" o "1". Era la cédula del administrador (con la que se crea la cuenta y que el backend rechaza por 409 si duplica).
**Ahora:** el schema usa `.refine(validarCedula, 'Cédula ecuatoriana no válida')`. Se rechaza una cédula obviamente inválida **antes** de viajar al backend.

## 6. Paso 2 suavizado + pre-llenado (mediana)

Ver el diseño en [registro-quitar-ruc.md §3.4](registro-quitar-ruc.md#34-frontend--suavizar-el-paso-2-sucursal-para-el-gym-de-un-solo-local).

- **Renombrado:** el encabezado pasó de *"Sede principal / ¿Dónde está ubicado tu gimnasio?"* a **"Tu gimnasio / ¿Dónde entrenan tus clientes?"**. El label de *"Nombre de la sede"* a **"Nombre del local"**. El texto de ayuda de "más sedes" a "más locales". El stepper: etiqueta "Sede" → **"Local"**.
- **Pre-llenado:** en `handleStep1` de `AutoRegistroPage`, tras guardar el Paso 1, se copia el nombre del gimnasio al nombre del local **solo si el usuario no lo tocó** (`if (!form2.getValues('nombreSucursal'))`). Un gym de un solo local avanza sin escribir nada.
- **Motivo:** la palabra "sede/sucursal" asustaba a un gimnasio de un solo local. El dato físico sí se necesita; lo que sobraba era el nombre técnico y tener que escribirlo.

> **No confundir:** la *sucursal operativa* de este paso (el local físico) NO es el *establecimiento SRI 001* (punto de emisión fiscal), que se configura en el wizard de facturación.

## 7. RUC opcional — la más grande (frontend + backend + BD)

Ver el diseño y la decisión de producto en [registro-quitar-ruc.md](registro-quitar-ruc.md). Un dueño que solo quiere **probar** la app no debería toparse con un dato tributario. El RUC se pide más tarde, en el wizard de facturación, cuando el gym decide facturar.

### 7.1 Frontend

- **`Step1Empresa` (auth/)** reescrito: solo **nombre del gimnasio** (obligatorio) + **correo corporativo** (opcional). Se quitaron RUC, teléfono y WhatsApp.
- **Schemas propios del auto-registro público:** se crearon `autoRegistroStep1Schema` (nombre + correo) y `autoRegistroStep2Schema` (nombreSucursal + dirección) en `auto-registro-wizard.schema.ts`. Antes el auto-registro reutilizaba el schema del operador (`registrar-gym-wizard.schema.ts`); ahora está **desacoplado**, para poder adelgazar el público sin afectar al operador.
- **`AutoRegistroPage`:** usa los nuevos schemas; el payload ya no envía `ruc`, `telefono` ni `whatsapp`.
- **Manejo de 409-RUC eliminado:** al no enviarse el RUC, el auto-registro público no puede generar un conflicto de RUC. Se quitó la rama `conflicto === 'ruc'` del catch, el `tipo: 'ruc'` del `ServerError` y su banner — quedaban huérfanos (mandarían al Paso 1 a corregir un campo inexistente).
- **DTO front:** `AutoRegistroRequest.ruc` pasó de obligatorio a opcional.

### 7.2 Backend (platform-service)

> El endpoint real de auto-registro vive en **platform-service** (`CompaniaController.autoRegistro` → `POST /api/v1/companias/auto-registro`), no en auth-service. El frontend lo llama vía `authRepository.autoRegistro`.

- **DTO propio `AutoRegistroRequest.java`:** el endpoint `/auto-registro` compartía `RegistrarGymWizardRequest` (con `@NotBlank ruc`) con el endpoint del operador `/wizard`. Para hacer el RUC opcional **solo en el público sin debilitar al operador**, se creó un DTO propio del auto-registro con `ruc` sin `@NotBlank`. El DTO del operador queda intacto.
- **Guard en `CompaniaService.registrarGymWizard`:** solo consulta `findByRuc` (validación de duplicado) **cuando el RUC viene presente**. Con RUC ausente o en blanco, salta directo al registro. Evita una query innecesaria y un posible falso conflicto.
- **Comportamiento con RUC null:** el `UNIQUE` de Postgres permite varios NULL, así que múltiples gyms sin RUC conviven; dos RUC reales iguales siguen prohibidos.

### 7.3 Base de datos (migración `GYM-002-2`)

- **Script:** `202607_GYM-002/ddl/02_alter_table_tenant_companias_ruc_nullable.sql` — `ALTER TABLE tenant.companias ALTER COLUMN ruc DROP NOT NULL`, envuelto en un bloque `DO $$` idempotente (solo ejecuta si aún es NOT NULL). El `UNIQUE` **no se toca**.
- **Changeset:** `GYM-002-2` en `202607_GYM-002/partial-changelog.yml`.
- **Validado contra una BD limpia local** (Postgres Docker, base desechable creada desde cero): el changelog completo (99 changesets) corrió sin errores, `ruc` quedó `is_nullable=YES`, el UNIQUE `companias_ruc_key` intacto, dos INSERT con RUC NULL convivieron y un RUC real duplicado fue rechazado.

> ⚠️ **La migración NO se aplicó a la Neon.** Al intentarlo se detectó que la Neon compartida tenía changesets `GYM-002-1..31` aplicados desde una versión del changelog que **no está en master** (el `partial-changelog.yml` de master solo define `GYM-002-1`). El repo local y la Neon estaban desincronizados. Decisión del usuario (2026-07-14): la Neon **no es definitiva**, se va a **borrar y recorrer** el changelog desde cero. Cuando eso ocurra, `GYM-002-2` se aplicará limpio (ya validado en base limpia). **Borrar/recorrer la Neon es una acción del usuario** — no se ejecutó automáticamente por ser destructiva sobre una base compartida.

---

## Qué NO se hizo (y por qué)

- **RUC en el registro por operador de plataforma:** se conserva. Decisión del usuario (2026-07-14): solo adelgaza el auto-registro público. Un operador "llave en mano" suele tener el RUC a mano.
- **Fusionar Paso 1 y Paso 2:** se evaluó y se descartó. Con el Paso 2 pre-llenado, mantenerlos separados conserva la capacidad multi-local sin costo de UX. Ver [registro-quitar-ruc.md §6 pregunta 4](registro-quitar-ruc.md#6-preguntas-abiertas).
- **Consumir `validarRuc` en el registro:** no aplica — el RUC ya no está en el registro. `validarRuc` queda listo para el wizard de facturación.
- **Barrera al salir por "Volver al login":** hallazgo menor no priorizado; el `beforeunload` ya protege el cierre de pestaña. Queda como posible mejora futura.
- **Paso 3 (plan) auto-avance cuando hay un solo plan gratuito:** hallazgo de UX identificado (el Paso 3 hoy no pide decisión real), pero es una decisión de producto no cerrada. Documentado, no implementado.
