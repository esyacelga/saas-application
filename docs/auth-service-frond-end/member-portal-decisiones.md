# Portal del Miembro — Decisiones previas a la implementación

> Responde cada pregunta directamente debajo de la línea `**R:**`.
> Cuando estés listo, comparte el documento y comenzamos a implementar.

---

## Contexto detectado (lo que ya existe)

| Elemento | Estado |
|----------|--------|
| `POST /auth/app/login` | ✅ Endpoint de backend implementado (`login`, `password`, `id_compania`) |
| `JwtPayloadCliente` | ✅ Tipo definido (`id_compania`, `id_persona`, `nombre`) |
| QR de sucursal | ✅ `PrintQrModal.tsx` genera QR con `sucursal.qrToken` |
| API de membresías | ✅ Completa (listar, vender, congelar, anular) |
| Password reset | ✅ Implementado en auth-service |
| Google OAuth | ❌ No implementado en backend ni frontend |
| Endpoint de check-in (POST) | ❌ No existe — attendance service solo tiene GETs |
| Rutas `/app/*` para miembros | ❌ No existen — todo es staff o plataforma |

---

## Sección 1 — El código QR

### Pregunta 1.1 — ¿Qué contenido lleva el QR?

El QR actual de la sucursal guarda un `qrToken` (token opaco). Cuando el miembro lo escanee,
debe abrirse el portal. Hay dos opciones:

- **Opción A:** El QR codifica una URL con el token opaco → `https://app.dominio.com/gym/<qrToken>` y el backend resuelve el `id_compania` a partir del token
- **Opción B:** El QR codifica una URL con el ID directo → `https://app.dominio.com/gym/<id_compania>`

La opción A es más segura (el ID no queda expuesto) y permite revocar el QR.
La opción B es más simple de implementar.

**R:**
Opcion A me parece la mejor
---

### Pregunta 1.2 — ¿Cada cuánto se renueva el QR?

Ya existe `renovarQrToken()` para regenerarlo. Si el QR impreso en papel apunta a un token que expira,
el papel queda inutilizable.

- **Opción A:** El QR nunca expira (token permanente) — papel siempre válido, menos seguro
- **Opción B:** El QR expira cada X tiempo y el staff lo regenera e imprime de nuevo
- **Opción C:** El QR no expira pero el staff puede revocarlo manualmente cuando quiera

**R:**
Opcion C
---

### Pregunta 1.3 — ¿Qué ve el miembro si escanea el QR con una membresía vencida?

- **Opción A:** Puede ingresar al portal pero ve un aviso de membresía vencida y no puede hacer check-in
- **Opción B:** Se le bloquea el acceso al portal directamente

**R:**
Opcion A
---

## Sección 2 — Registro de asistencia (check-in)

> **Importante:** Actualmente no existe un endpoint `POST /asistencias`. Esto es el mayor gap de backend.

### Pregunta 2.1 — ¿Quién registra la asistencia?

| Flujo | Descripción |
|-------|-------------|
| **A — El miembro** | Abre la app → pulsa "Registrar entrada". El backend registra la asistencia con el JWT del miembro. |
| **B — El staff** | El staff escanea el QR del miembro en recepción. El miembro es pasivo. |
| **C — Ambos** | El miembro puede registrarse solo, y el staff también puede hacerlo. |

**R:**
opcion C

---

### Pregunta 2.2 — ¿Cuántas entradas por día se permiten?

- **Opción A:** Solo una entrada por día (si ya registró, el botón se deshabilita)
- **Opción B:** Múltiples entradas por día (ej. mañana y tarde)

**R:**

Opcion A
---

### Pregunta 2.3 — ¿Qué pasa si el miembro intenta hacer check-in con membresía congelada?

- **Opción A:** Se bloquea el check-in y se muestra mensaje de membresía congelada
- **Opción B:** Se permite de igual modo (el congelamiento solo pausa el conteo de días, no el acceso)

**R:**
Opcion A

---

## Sección 3 — Autenticación

### Pregunta 3.1 — ¿Login por cédula, email, o ambos?

El endpoint `/auth/app/login` recibe un campo genérico `login`. El backend decide cómo interpretarlo.

- **Opción A:** Solo email
- **Opción B:** Solo cédula/CI
- **Opción C:** Ambos — el usuario escribe lo que tiene y el backend discrimina

**R:**
Opcion C
---

### Pregunta 3.2 — ¿Se implementa Google OAuth?

- **Opción A:** Sí, desde el inicio — el miembro puede entrar con Google si su correo existe en el gimnasio
- **Opción B:** No por ahora — se deja para una fase 2
- **Opción C:** Sí, pero también permite auto-registro con Google (el sistema crea la cuenta automáticamente)

> Nota: si eliges A, el backend (auth-service) necesita implementar el flujo OAuth con Google.
> Si eliges C, considera que el staff pierde control sobre quién es miembro del gimnasio.

**R:**
Esta parte es que se debe usar google Auth o Faceboo auth, de tal manera que como estos servicios contiene en el payload el correo del usuario y la url de su profile
la aplicacion debera usar este correo para validar si existe en el gimnacio dueño del token caso contrario debera aparecer su mensaje de error correspondiente pero amigable 
---

### Pregunta 3.3 — ¿Cómo recibe el miembro su contraseña inicial?

El staff crea la cuenta del cliente desde el panel admin. Pero el miembro necesita saber cómo entrar.

- **Opción A:** El staff le dice la contraseña manualmente (o en papel)
- **Opción B:** El sistema envía un email automático de bienvenida con un link para que el miembro cree su propia contraseña
- **Opción C:** El miembro usa el flujo "olvidé mi contraseña" con su email para activar su cuenta

> La opción B o C es más profesional. El flujo de reset de contraseña ya está implementado en el backend.

**R:**

Si no usa el google auth o el face book auth, ahi si deberia ser el flujo de olvide mi contraseña
---

### Pregunta 3.4 — ¿Qué pasa si un miembro de Gym A escanea el QR de Gym B?

- **Opción A:** Se le fuerza logout y debe iniciar sesión en el Gym B
- **Opción B:** Se muestra un aviso: "Estás registrado en otro gimnasio"
- **Opción C:** Se permite ver la pantalla de login del Gym B pero no puede ingresar con su cuenta actual

**R:**

La idea des escanear el codigo qr es que le lleve a la url del dueño del token, digamos el peor de los casos yo estoy en dos gimnacios 
etonces la ur deberia llevarme al gimancio que yo estoy escaneando para registrar mi asistencia 
---

## Sección 4 — Membresías y datos del miembro

### Pregunta 4.1 — ¿Qué información principal ve el miembro en su dashboard?

Marca todo lo que quieres mostrar:

- [ ] Nombre y foto de perfil
- [ ] Estado de membresía (activa / vencida / congelada)
- [ ] Días restantes (para membresías tipo `calendario`)
- [ ] Pases/accesos restantes (para membresías tipo `accesos`)
- [ ] Fecha de vencimiento
- [ ] Historial de asistencias
- [ ] Información de la sede (nombre, dirección, teléfono)
- [ ] Otro: _______________

**R:**
Estado de membresía
Días restantes o  Pases/accesos restantes dependiendo que tipo de membresia poseo
Fecha de vencimiento
Historial de asistencias
Información de la sede
---

### Pregunta 4.2 — ¿Un miembro puede tener varias membresías activas al mismo tiempo?

Por ejemplo: una membresía mensual + un paquete de 10 clases simultáneamente.

- **Opción A:** Sí — mostrar todas las activas
- **Opción B:** No — siempre hay una sola activa, las demás están en historial

**R:**
B
---

### Pregunta 4.3 — ¿Qué ve el miembro si su membresía está congelada?

- **Opción A:** Ve la fecha estimada de reactivación y un mensaje explicativo
- **Opción B:** Ve la fecha de reactivación y puede solicitar descongelar desde la app
- **Opción C:** Solo ve "membresía suspendida, contacta a recepción"

**R:**
Opción B 
---

### Pregunta 4.4 — ¿El miembro puede ver su historial de membresías pasadas?

- **Opción A:** Sí — una sección de historial con membresías anteriores
- **Opción B:** No — solo ve la membresía actual

**R:**

Opcion A
---

## Sección 5 — Branding y multi-gimnasio

### Pregunta 5.1 — ¿La pantalla de login debe mostrar el logo y nombre del gimnasio?

Cuando el miembro escanea el QR e ingresa al portal, ¿ve el branding de su gimnasio?

- **Opción A:** Sí — mostrar logo y nombre del gimnasio (necesita endpoint público que retorne datos básicos del gimnasio por ID, sin autenticación)
- **Opción B:** No — pantalla de login genérica de la plataforma

**R:**
Si ya que el token deberia traer esos datos para que carge en pantalla del cliente
---

### Pregunta 5.2 — ¿El portal aplica colores/temas por gimnasio?

- **Opción A:** Sí — cada gimnasio puede configurar su color primario y el portal lo usa
- **Opción B:** No — tema único de la plataforma para todos los gimnasios

**R:**

Opcion A
---

## Sección 6 — Despliegue y dominio

### Pregunta 6.1 — ¿El portal será una app separada o parte de este mismo repo?

- **Opción A:** Repo separado (recomendado) — mobile-first, PWA, deploy independiente
- **Opción B:** Rutas `/app/*` dentro de este mismo repo

> El QR impreso contendrá la URL definitiva. Cambiarla después invalida todos los QR ya impresos.

**R:**
Opcion A
---

### Pregunta 6.2 — ¿Cuál será la URL del portal del miembro?

Ejemplos posibles:
- `https://app.tudominio.com/gym/<token>`
- `https://migimnasio.tudominio.com`
- `https://tudominio.com/miembro`

> Esta URL irá dentro del QR impreso — debe ser definitiva antes de generar los primeros QR.

**R:**
https://app.tudominio.com/gym/<token>
---

### Pregunta 6.3 — ¿El portal debe funcionar sin conexión (offline)?

- **Opción A:** Sí — PWA con caché; muestra última membresía conocida sin internet
- **Opción B:** No — requiere conexión siempre

**R:**
A
---

## Sección 7 — Notificaciones

### Pregunta 7.1 — ¿Se envían avisos cuando la membresía está próxima a vencer?

- **Opción A:** Sí, por email (el backend ya tiene la infraestructura de email para reset de contraseña)
- **Opción B:** Sí, por push notification (requiere service worker en PWA)
- **Opción C:** Ambos
- **Opción D:** No por ahora

**R:**
Opcion D
---

### Pregunta 7.2 — ¿Con cuántos días de anticipación se envía el aviso?

**R:**
Por el momento no hay aviso 
---

## Sección 8 — Estado de la suscripción del gimnasio

### Pregunta 8.1 — ¿Qué pasa si el gimnasio no pagó su plan en la plataforma?

- **Opción A:** Los miembros siguen pudiendo usar el portal normalmente
- **Opción B:** El portal muestra un aviso genérico y bloquea el acceso
- **Opción C:** El portal solo muestra info pero bloquea el check-in

**R:**
Opción B:

---

---

## Sección 9 — Aclaraciones pendientes

> Hallazgos del análisis del código backend que requieren respuesta antes de implementar.

---

### Hallazgo A — El endpoint de check-in por QR ya existe

En `attendance-service` existe **`POST /api/v1/asistencias/qr`** que recibe `{ qrToken }`.
Esto significa que el miembro NO escanea un QR con la cámara para hacer check-in — la app ya conoce el `qrToken` del gimnasio (lo obtuvo al abrir el portal) y lo envía directamente al pulsar un botón.

**No hay pregunta aquí — solo confirmar que este es el flujo deseado:**

- El miembro abre el portal (URL ya contiene el `qrToken`)
- Se loguea
- Pulsa "Registrar entrada"
- La app llama a `POST /asistencias/qr` con el `qrToken` de esa sesión
- El backend registra la asistencia usando el JWT del miembro para identificarlo

**R:**
Si exactamente
---

### Pregunta 9.1 — ¿Google Auth solamente, o también Facebook?

Tu respuesta en 3.2 menciona "Google Auth o Facebook Auth". Son dos integraciones distintas con distinta complejidad (Facebook requiere revisión de app Meta y permisos adicionales). El backend (auth-service) no tiene ninguna implementada todavía.

- **Opción A:** Solo Google para v1 (más simple, mayor adopción)
- **Opción B:** Solo Facebook para v1
- **Opción C:** Ambos desde el inicio

**R:**
 Opcion C, mas el login manual como ya lo vimos anteriormente
---

### Pregunta 9.2 — Sesión activa al escanear QR de otro gimnasio

Describes bien el caso ideal: "la URL me lleva al gimnasio que estoy escaneando". 
El problema concreto: si el miembro ya tiene sesión iniciada en Gym A y abre la URL del Gym B (`/gym/<tokenGymB>`), ¿qué debe ocurrir?

- **Opción A:** Se cierra la sesión del Gym A automáticamente y se muestra el login del Gym B
- **Opción B:** La app detecta que el JWT actual pertenece a otro gimnasio → muestra aviso amigable y el botón "Iniciar sesión en este gimnasio" (sin cerrar sesión automáticamente)

**R:**
Opcion A
---

### Pregunta 9.3 — ¿El descongelamiento desde la app es automático o requiere aprobación del staff?

Elegiste Opción B en 4.3: el miembro puede solicitar descongelar desde la app. Necesito saber cómo fluye esa solicitud:

- **Opción A:** Es automático — la app llama al endpoint directamente y la membresía se descongela sin intervención del staff
- **Opción B:** Es una solicitud — el staff la ve como notificación en el panel admin y la aprueba o rechaza manualmente

**R:**
Opcion A pero en el caso q el usuario no pueda lo puede hacer tambien el STAFF
---

### Pregunta 9.4 — ¿Dónde se guarda el color y logo del gimnasio?

Para aplicar el tema visual por gimnasio (tu respuesta 5.2) necesito un campo en la base de datos. Actualmente los DTOs de `companias` y `sucursales` en auth-service solo tienen `id` y `nombre` — no existe `color_primario`, `logo_url` ni similar.

- **Opción A:** Agregar `color_primario` (hex) y `logo_url` a la tabla `companias` como parte de este proyecto
- **Opción B:** Agregar esos campos a `sucursales` (cada sede puede tener su propio branding)
- **Opción C:** Agregar a `companias` para v1 (una sola paleta por gimnasio, no por sede)

**R:**
Omite lo del color, pero si tra iformacion del gimnacio y su logo desde el token 

---

## Resumen de decisiones críticas

| # | Decisión | Impacto |
|---|----------|---------|
| 1.1 | Formato del QR (token vs ID) | Define el endpoint de backend necesario |
| 1.2 | Expiración del QR | Define flujo operativo del staff |
| 2.1 | Quién hace check-in | Define si se necesita endpoint POST en attendance-service |
| 3.1 | Login por cédula, email o ambos | Define cambio en auth-service |
| 3.2 | Google/Facebook OAuth | Define trabajo en auth-service |
| 3.3 | Contraseña inicial | Define flujo de onboarding del miembro |
| 5.1 | Branding en login | Define endpoint público sin auth |
| 5.2 | Color/logo del gimnasio | Define migración de BD en gym-administrator |
| 6.1 | Repo separado vs mismo repo | Define estructura del proyecto |
| 6.2 | URL definitiva del portal | Se incrusta en el QR permanentemente |
| 9.1 | Google vs Facebook vs ambos | Define providers OAuth a implementar en auth-service |
| 9.2 | Sesión multi-gimnasio | Define comportamiento de la app al cambiar de contexto |
| 9.3 | Descongelamiento automático vs aprobación | Define si se necesita flujo de notificación al staff |
