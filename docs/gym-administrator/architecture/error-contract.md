# Contrato de Errores Estandarizado — Global Exception Handler

**Última actualización:** 2026-07-19
**Estado:** ✅ Implementado en los **6 microservicios** (core, billing, finance, attendance, platform, auth) + frontend transversal
**Autores:** Team SaaS Platform

> **v2 (2026-07-18):** refinado tras auditar el código real. Se añadieron 8 hallazgos que el diseño inicial no cubría (ver §"Análisis adversarial"). Los más críticos: (a) los 401/403 de Spring Security **no pasan** por el `GlobalExceptionHandler`; (b) `ProblemDetail` **no respeta** `SNAKE_CASE` de Jackson por defecto; (c) el **PWA ya depende de `codigo`** — es contrato intocable; (d) hay **2 frontends**, no 1.
>
> **v3 (2026-07-18) — piloto implementado en `core-service`:** paquete completo (`ErrorCode`, `ProblemDetailFactory`, `DataIntegrityMapper`, `GlobalExceptionHandler` reescrito, `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler` cableados en `SecurityConfig`, `JwtAuthenticationFilter` delega su 401 al entrypoint). Tests nuevos: `GlobalExceptionHandlerTest` + `SecurityErrorContractTest` (9/9 verdes). Frontend transversal hecho: **ambos** `api-error.ts` alineados (`detail` primero + `getApiErrorCode` en el panel admin) y el interceptor `axios-core.instance.ts` migrado a `plan_actual` (snake_case). **Hallazgo #2 confirmado en la práctica:** un `ObjectMapper` plano serializa las extensiones de `ProblemDetail` anidadas bajo `properties` — se resolvió aplanándolas en `ProblemDetailFactory.toMap()` (verificado por test). Pendiente: replicar a `platform`, `billing`, `attendance`, `finance`.
>
> **v4 (2026-07-19) — replicado en `auth-service`:** paquete completo adaptado (`ErrorCode` sin `LIMITE_PLAN_ALCANZADO`, `ProblemDetailFactory`, `DataIntegrityMapper` — cuya lógica *originó* aquí). `GlobalExceptionHandler` reescrito (mantiene `@Order(-2)` + Lombok `@Slf4j`) emitiendo el sobre vía `ProblemDetailFactory.toMap`, **preservando** la traducción de `DataIntegrityViolationException`. El `JwtAuthenticationEntryPoint` existente se actualizó (no se duplicó) para emitir el sobre `no_autenticado`; nuevo `ApiAccessDeniedHandler` (403) cableado junto al entrypoint en `SecurityConfig.exceptionHandling(...)`. Tests: `GlobalExceptionHandlerTest` (11) + `SecurityErrorContractTest` (2) — 13/13 verdes. El record `ApiError` queda obsoleto (ya nadie lo emite en backend).
>
> **v5 (2026-07-19) — replicación COMPLETA (6/6):** además de auth, se replicó a `billing` (8/8 tests), `finance` (9/9, se **eliminó** el `@RestControllerAdvice`), `attendance` (11/11, se eliminó el `@RestControllerAdvice`) y `platform` (17/17). Verificación de integración: los 6 servicios **compilan main+test con Zulu 25** (exit 0) y los **18 puntos de serialización** (handler + entrypoint + accessDenied de cada servicio) usan `ProblemDetailFactory.toMap(pd)` — ninguno serializa el `ProblemDetail` crudo, así que el sobre plano snake_case es idéntico en todo el monorepo.
> - **Congelados verificados (attendance):** tests blindan que `ConflictException.getCodigo()` preserva EXACTO `ya_registrado_hoy`, `sin_membresia`, `ultima_plantilla` (contrato del PWA, hallazgo #3). `GoneException`→410 `recurso_no_disponible`.
> - **platform:** `LimiteAlcanzado` emite `plan_actual` en **snake_case** (antes `planActual`) — el interceptor del panel admin ya se migró (v3); `RateLimitExcedido`→429 con `ventana`/`max`; `ConflictException.getConflicto()` se conserva como extra `conflicto` (lo consume `AutoRegistroPage`, valores `idPlan`/`correo`/`ci`).
> - **finance/attendance:** su `ConflictException` con `codigo` propio se preserva (fallback `conflicto` si null); su `IllegalArgumentException` custom→422 `regla_negocio`.
> - Pendiente (fuera de este alcance, siguiente iteración): retirar el alias `mensaje` tras migrar los ~47 accesos inline del panel admin; mapa opcional `codigo → i18n` en frontends.

---

## Resumen ejecutivo

Cada uno de los **6 microservicios** (`auth`, `platform`, `core`, `attendance`, `billing`, `finance`) ya tiene un `GlobalExceptionHandler`, pero **cada uno devuelve un JSON de error con un shape distinto**. El frontend no puede leer los errores de forma uniforme y hoy hay un bug directo por esto: `getApiErrorMessage()` solo lee `data.mensaje`, así que los errores de `core`/`billing`/`platform` (que usan `message`) muestran *"Error desconocido"*.

Este requerimiento define **un contrato de error único** que todos los servicios deben emitir, con un campo `codigo` legible por máquina para que el frontend genere mensajes descriptivos (i18n, UI contextual) en lugar de depender del texto crudo del backend.

**Decisiones tomadas:**

1. **Formato:** RFC 7807 (`ProblemDetail`) extendido con un campo `codigo` propio y `errores[]` de validación.
2. **Naming:** español + `snake_case` (coincide con el `SNAKE_CASE` global de Jackson ya configurado en todos los servicios).
3. **Ejecución:** plan detallado primero (este documento); implementación posterior.

---

## Diagnóstico — estado actual (2026-07-18)

Al menos **4 formatos incompatibles** conviven hoy:

| Servicio | Mecanismo | Shape del JSON de error |
|---|---|---|
| **auth** | `ErrorWebExceptionHandler` + record `ApiError` | `{ status, error, mensaje, errores[], timestamp }` |
| **core** | `ErrorWebExceptionHandler` | `{ timestamp, status, error, message, path }` |
| **billing** | `ErrorWebExceptionHandler` | `{ timestamp, status, error, message, path }` |
| **platform** | `ErrorWebExceptionHandler` (mezcla) | a veces `{timestamp,status,error,message,path}`, a veces `{codigo, mensaje}`, a veces `{codigo, recurso, actual, maximo, planActual}` |
| **attendance** | `@RestControllerAdvice` (`ResponseEntity`) | `{ mensaje }` \| `{ codigo, mensaje }` \| `{ mensaje, errores{} }` |
| **finance** | `@RestControllerAdvice` (`ResponseEntity`) | `{ mensaje }` \| `{ codigo, mensaje }` \| `{ mensaje, errores{} }` |

### Inconsistencias que rompen al frontend

1. **Nombre del campo de mensaje:** `mensaje` (auth/attendance/finance) vs `message` (core/billing/platform).
2. **Errores de validación:** lista de strings (auth) vs string aplanado `campo: msg; ...` (core/billing/platform) vs objeto `{campo: msg}` (attendance/finance).
3. **Código de negocio legible por máquina (`codigo`):** existe en attendance/finance/platform, **no existe** en auth/core/billing.
4. **Metadata contextual** (`path`, `timestamp`, `conflicto`, `recurso/maximo`): presente de forma irregular.
5. **Dos mecanismos técnicos distintos** (`ErrorWebExceptionHandler` vs `@RestControllerAdvice`). El segundo **ni siquiera captura** errores que ocurren fuera del controller (filtros, routing).

---

## Contrato objetivo — el "sobre" (envelope)

Basado en **RFC 7807 (`ProblemDetail`)** extendido, campos en español + `snake_case`. Todos los servicios devolverán exactamente este shape:

```jsonc
{
  "type": "about:blank",                      // RFC 7807 — URI del tipo de error (por ahora genérico)
  "title": "Conflict",                        // razón HTTP legible (status reason phrase)
  "status": 409,                              // código HTTP
  "detail": "El correo ya está registrado",   // mensaje humano (hoy: mensaje/message)
  "instance": "/api/v1/clientes",             // path de la request (hoy: path)
  "codigo": "correo_duplicado",               // ⭐ código máquina — clave para i18n/UI del frontend
  "mensaje": "El correo ya está registrado",  // alias de `detail` (período de gracia — ver hallazgo #5)
  "timestamp": "2026-07-18T10:00:00Z",        // ISO-8601 UTC
  "errores": [                                // solo en validación (400); ausente en el resto
    { "campo": "correo", "mensaje": "formato inválido" }
  ]
}
```

- **`codigo`** es el valor central: string estable `snake_case` que el frontend mapea a mensajes descriptivos/i18n, **sin depender del texto de `detail`**.
- **`mensaje`** duplica a `detail` durante la migración para no romper los ~47 accesos inline del panel admin y los `api-error.ts` que hoy leen `mensaje` (hallazgo #5). Se retira en una fase posterior.
- `errores[]` pasa a ser **lista de objetos `{campo, mensaje}`** (uniforme) — hoy son 3 formatos distintos. Además `detail` conserva un resumen legible para UIs que solo muestran `detail` (hallazgo #8).
- Se apoya en `org.springframework.http.ProblemDetail` (nativo de Spring 6) con propiedades extra (`codigo`, `mensaje`, `timestamp`, `errores`) vía `setProperty()`, **con claves ya en snake_case literal** porque `ProblemDetail` no aplica la estrategia `SNAKE_CASE` de Jackson (hallazgo #2).
- Los 5 campos RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) quedan en su forma estándar por diseño; solo las extensiones van en snake_case.

---

## Componentes compartidos (mismo diseño por servicio)

Los servicios son proyectos Maven independientes (sin módulo común), así que se replican las **mismas clases** en cada uno bajo `infrastructure/exception/`:

| Clase | Rol |
|---|---|
| `ErrorCode` (enum) | Catálogo de `codigo` + su HTTP status asociado. Ej.: `RECURSO_NO_ENCONTRADO(404,"recurso_no_encontrado")`, `CONFLICTO(409,"conflicto")`, `LIMITE_PLAN_ALCANZADO(403,"limite_plan_alcanzado")`. |
| `ApiException` (base) | Excepción de dominio que carga un `ErrorCode` + `detail` + metadata opcional (`Map<String,Object>`). |
| `ProblemDetailFactory` | Construye el `ProblemDetail` estándar desde `ErrorCode`, `exchange` y `detail`. Punto único de serialización. |
| `GlobalExceptionHandler` (reescrito) | **Un solo mecanismo:** `ErrorWebExceptionHandler` con `@Order(-2)` en todos (elimina los `@RestControllerAdvice` de attendance/finance, que no capturan errores de filtros/routing). |
| `ApiAuthenticationEntryPoint` + `ApiAccessDeniedHandler` | **Nuevos** (hallazgo #1). Registrados en `SecurityConfig.exceptionHandling(...)` para que los 401/403 de Spring Security emitan el mismo `ProblemDetail` (`no_autenticado` / `acceso_denegado`). Sin esto, el error más común (401 por token) escapa al contrato. |
| `DataIntegrityMapper` | **Nuevo** (hallazgo #6). Extrae la lógica de `DataIntegrityViolationException` hoy exclusiva de auth y la aplica en los 6 servicios. |

**Compatibilidad:** se mantienen las excepciones existentes (`NotFoundException`, `ConflictException`, `ForbiddenException`, `BusinessException`, etc.) para no reescribir los `throw` de toda la capa de servicio — solo cambia **cómo el handler las traduce** al nuevo sobre. Las excepciones de dominio que ya llevan `codigo` (attendance/finance/platform SaaS) conservan su código exacto.

---

## Catálogo de `codigo` inicial

**Comunes** (todos los servicios):

| `codigo` | HTTP | Significado |
|---|---|---|
| `no_autenticado` | 401 | Falta o es inválido el token |
| `acceso_denegado` | 403 | Sin permiso / compañía no coincide |
| `recurso_no_encontrado` | 404 | Entidad no existe |
| `conflicto` | 409 | Conflicto de estado genérico |
| `datos_duplicados` | 409 | Violación de unique constraint |
| `referencia_invalida` | 409 | Violación de foreign key |
| `regla_negocio` | 422 | Regla de negocio / transición inválida |
| `validacion` | 400 | Bean validation con `errores[]` |
| `demasiadas_solicitudes` | 429 | Rate limit |
| `error_interno` | 500 | No controlado (detail genérico, sin filtrar internos) |

**Preservados de platform/core** (no cambian su valor):
`limite_plan_alcanzado`, `trial_ya_usado`, `suscripcion_activa`, `sin_suscripcion_cancelable`, `pago_duplicado`, `pago_ya_procesado`, `transicion_invalida`, `rate_limit_excedido`.

**🔒 Congelados — el PWA ya depende de estos strings exactos** (hallazgo #3), `attendance-service`:
`ya_registrado_hoy`, `sin_membresia`, `membresia_expirada`, `accesos_agotados`, `congelado`, `ultima_plantilla`. Renombrar cualquiera **rompe el check-in del socio en producción**. Solo `ya_registrado_hoy` y `ultima_plantilla` se emiten hoy como `codigo` real; los otros 4 el PWA los infiere por substring de `mensaje` — mejora opcional: emitirlos como `codigo` real desde el backend.

Cada servicio extiende el enum con sus propios códigos de negocio según se necesite.

---

## Cambios por servicio

| Servicio | Mecanismo actual | Trabajo |
|---|---|---|
| **auth** | `ErrorWebExceptionHandler` + `ApiError` | Migrar `ApiError` → `ProblemDetail`; **preservar** la lógica de `DataIntegrityViolationException` (los mensajes de constraint son valiosos → van a `detail`, con `codigo=datos_duplicados`/`referencia_invalida`). |
| **core** | `ErrorWebExceptionHandler` | Reescribir handler; añadir `codigo`; **preservar** `LimiteAlcanzadoException`. |
| **billing** | `ErrorWebExceptionHandler` | Reescribir handler; añadir `codigo`. |
| **platform** | `ErrorWebExceptionHandler` (mezcla) | Reescribir; unificar los 3 sub-formatos SaaS al sobre estándar poniendo su metadata en propiedades extra + `codigo`. **El más delicado** (ver riesgos). |
| **attendance** | `@RestControllerAdvice` | **Reemplazar** por `ErrorWebExceptionHandler`; preservar mapeo 422 de su `IllegalArgumentException` custom y `GoneException` (410). **Congelar** los `codigo` que consume el PWA (hallazgo #3). |
| **finance** | `@RestControllerAdvice` | **Reemplazar** por `ErrorWebExceptionHandler`; preservar 422 de su `IllegalArgumentException` custom. |

**Transversal a los 6 (nuevo, hallazgo #1 y #6):** en cada `SecurityConfig` registrar `ApiAuthenticationEntryPoint` (401) y `ApiAccessDeniedHandler` (403) vía `http.exceptionHandling(...)`, y cablear el `DataIntegrityMapper` en el handler para capturar unique/FK violations (hoy solo auth lo hace).

---

## Impacto en el frontend (DOS frontends — hallazgo #4)

- **`auth-service-frond-end/src/lib/api-error.ts`** (panel admin — **atrasado**): hoy `getApiErrorMessage()` **solo** lee `data.mensaje` y **no** existe `getApiErrorCode()`. Actualizar la cadena de fallback a `detail ?? mensaje ?? message ?? error` y añadir `getApiErrorCode()`. Esto **arregla el bug actual** donde errores de core/billing/platform muestran *"Error desconocido"*.
- **`gym-member-pwa/src/lib/api-error.ts`** (PWA socios — **más avanzado**): ya lee `mensaje ?? message ?? error` y ya tiene `getApiErrorCode()`. Solo falta **anteponer `detail`** a la cadena de fallback. Su `CheckInPage` depende de `codigo` (hallazgo #3): no romper esos valores.
- **~47 archivos del panel admin** leen `.mensaje`/`.message` inline sin el helper (hallazgo #5). El alias `mensaje` en el sobre los cubre durante la migración; retirarlo solo tras migrarlos a `getApiErrorMessage`/`getApiErrorCode`.
- **Opcional (siguiente iteración):** un mapa `codigo → mensaje i18n` en cada frontend para mensajes descriptivos por código. Fuera del alcance base salvo que se decida incluir.
- **`useLimitPlanModalStore` / `UpgradeModal`** ya consumen `recurso, actual, maximo, plan_actual` de los errores 403 de límite de plan — deben seguir accesibles al nivel raíz del sobre (ver riesgo #1).

---

## Análisis adversarial — lo que se nos escapaba

Auditoría del código real (2026-07-18). Cada punto es un caso que el diseño inicial **no** cubría y que rompería el contrato si no se maneja explícitamente.

### 🔴 1. Los 401/403 de Spring Security NO pasan por el `GlobalExceptionHandler`

En los 6 servicios, `SecurityConfig` usa `.anyExchange().authenticated()` **sin** `authenticationEntryPoint` ni `accessDeniedHandler` personalizados. Cuando un request llega sin JWT válido, Spring Security escribe **directamente** una respuesta 401/403 vacía (o con su propio shape) que **nunca se propaga** por la cadena reactiva — por lo tanto el `ErrorWebExceptionHandler` (`@Order(-2)`) **no la ve**.

**Consecuencia:** el error más común que ve el frontend (token expirado/ausente → 401) **no tendría el sobre estándar** aunque implementemos todo lo demás. El interceptor de refresh del frontend depende del status 401, pero cualquier UI que quiera leer `codigo`/`detail` de un 401 recibiría un body distinto.

**Acción requerida (nueva):** en cada `SecurityConfig`, registrar un `ServerAuthenticationEntryPoint` (401 → `codigo=no_autenticado`) y un `ServerAccessDeniedHandler` (403 → `codigo=acceso_denegado`) que emitan el **mismo** `ProblemDetail`. Reutilizar el `ProblemDetailFactory`.

### 🔴 2. `ProblemDetail` NO respeta `SNAKE_CASE` de Jackson por defecto

`org.springframework.http.ProblemDetail` serializa sus propiedades estándar (`type`, `title`, `status`, `detail`, `instance`) con nombres **fijos** y las propiedades extra (`setProperty`) tal cual la clave que se pasa. La estrategia `property-naming-strategy: SNAKE_CASE` **no** las reescribe de forma garantizada, y los campos RFC 7807 son intencionalmente `camelCase`/una-palabra.

**Consecuencia:** si añadimos una propiedad extra como `setProperty("planActual", ...)` saldría `planActual` (no `plan_actual`), rompiendo la convención snake_case del resto del contrato.

**Acción requerida (nueva):** pasar las claves de propiedades extra **ya en snake_case** literal (`setProperty("plan_actual", ...)`, `setProperty("codigo", ...)`, `setProperty("errores", ...)`). Verificar en un test de serialización que el JSON final es snake_case. Documentar que los 5 campos RFC (`type/title/status/detail/instance`) quedan en su forma estándar por diseño.

### 🔴 3. El PWA (`gym-member-pwa`) YA depende de `codigo` — contrato intocable

`gym-member-pwa/src/ui/pages/attendance/CheckInPage.tsx` (`detectErrorKind`) hace branching de UI sobre valores **exactos** de `codigo`: `ya_registrado_hoy`, `sin_membresia`, `membresia_expirada`, `accesos_agotados`, `congelado`. Hoy solo `ya_registrado_hoy` se emite realmente como `codigo` desde el backend (`AsistenciaService` lanza `ConflictException("ya_registrado_hoy", ...)`); los otros 4 el PWA los infiere por **substring de `mensaje`** porque el `validar-acceso` de core devuelve `razon` en un body 200, no una excepción.

**Consecuencia:** cualquier renombre de estos `codigo` **rompe el check-in del socio** en producción. Además, el fallback por substring es frágil.

**Acción requerida (nueva):** estos 5 valores entran al catálogo `ErrorCode` **congelados con su string actual**. Oportunidad de mejora (opcional): que el backend emita los 4 restantes como `codigo` real en lugar de forzar al PWA a adivinar por texto.

### 🟠 4. Hay DOS frontends, no uno

El plan inicial solo mencionaba `auth-service-frond-end`. También existe **`gym-member-pwa`** con su **propio** `src/lib/api-error.ts` — y este ya está más avanzado: su `getApiErrorMessage()` lee `mensaje ?? message ?? error` y ya tiene `getApiErrorCode()`. El del panel admin (`auth-service-frond-end`) está **atrasado**: solo lee `mensaje` y no tiene `getApiErrorCode()`.

**Acción requerida (nueva):** el cambio de frontend aplica a **ambos** `api-error.ts`. Alinear el del panel admin al nivel del PWA + añadir `detail` a la cadena de fallback en ambos.

### 🟠 5. ~47 archivos del panel admin leen errores sin pasar por `getApiErrorMessage`

Grep encontró 131 usos de `.mensaje`/`.message`/`response.data`/`error.response` en 47 archivos del panel admin. No todos usan el helper central; muchos modales leen `err.response.data.mensaje` inline.

**Consecuencia:** centralizar en `api-error.ts` no basta — los accesos inline a `.mensaje` seguirán rotos con el nuevo `detail`. Durante la transición el sobre **debe** conservar un alias.

**Acción requerida (decisión):** el `ProblemDetail` incluirá **también** `mensaje` como propiedad extra (alias de `detail`) durante un período de gracia, para no romper los 47 accesos inline mientras se migran. Retirar el alias en una fase posterior.

### 🟠 6. `auth-service` tiene lógica de `DataIntegrityViolationException` que NO debe perderse

El handler de auth traduce constraints de PostgreSQL a mensajes legibles ("El campo 'x' es requerido", "Registro duplicado (restricción: ...)", "Referencia inválida..."). Es la **única** lógica de este tipo en el monorepo y aporta valor real.

**Acción requerida:** preservarla íntegra → `detail` recibe el mensaje resuelto, `codigo` recibe `datos_duplicados`/`referencia_invalida`/`campo_requerido`. **Propuesta:** extraer esta lógica a un helper reutilizable y aplicarla en **todos** los servicios (hoy solo auth captura `DataIntegrityViolationException`; los demás dejarían escapar un 500 genérico ante un unique/FK violation).

### 🟡 7. `platform-service` mapea `AccessDeniedException` a un shape propio `{codigo, mensaje}`

Además del entrypoint de Security (punto 1), platform ya intercepta `org.springframework.security.access.AccessDeniedException` dentro del handler y emite `{codigo:"acceso_denegado", mensaje}`. Hay que unificarlo al sobre estándar sin perder el `codigo`, y confirmar que ningún consumidor dependa del shape actual de 2 campos.

### 🟡 8. Errores de validación: la forma actual varía y algunos aplanan a string

core/billing/platform hoy **aplanan** los field errors a un solo string (`"campo: msg; campo2: msg2"`) en `detail`. auth los pone como lista de strings. attendance/finance como objeto `{campo:msg}`. El contrato nuevo (`errores: [{campo, mensaje}]`) es superior, pero **romperá** cualquier UI que hoy parsee el string aplanado.

**Acción requerida:** además de `errores[]`, mantener en `detail` un resumen legible (p.ej. el primer error o el string aplanado) para que las UIs que solo muestran `detail` sigan funcionando.

---

## Riesgos y decisiones abiertas

1. **`platform-service` tiene respuestas SaaS con shape propio** (`{codigo, recurso, actual, maximo, planActual}`) que el **frontend ya consume** (`UpgradeModal`). Al envolverlas en el sobre RFC 7807, esos campos deben seguir accesibles.
   **Recomendación:** ponerlos como **propiedades extra al nivel raíz** del `ProblemDetail` en snake_case (`data.recurso`, `data.actual`, `data.maximo`, `data.plan_actual`), así el frontend los sigue leyendo. ⚠️ Ojo: hoy el campo llega como `planActual` (camelCase) — al pasar por `ProblemDetail` con clave snake_case cambiaría a `plan_actual`; **verificar y alinear el `UpgradeModal`** en el mismo cambio.

2. **Breaking change para AMBOS frontends:** cambiar `mensaje`→`detail` rompería los helpers hasta actualizar el front. Mitigado por el alias `mensaje` en el sobre (hallazgo #5) + incluir ambos `api-error.ts` en el plan.

3. **Tests a actualizar (riesgo de CI):**
   - `platform-service/.../GlobalExceptionHandlerTest.java` assertea el shape viejo.
   - Revisar tests de integración que asserten sobre `.mensaje`/`.message`/`.errores` en los 6 servicios.
   - **Nuevos tests requeridos:** (a) serialización — el JSON final es snake_case en las extensiones (hallazgo #2); (b) un 401/403 de Security produce el sobre estándar (hallazgo #1); (c) unique/FK violation produce `datos_duplicados`/`referencia_invalida` en cada servicio (hallazgo #6); (d) los `codigo` congelados del PWA no cambian (hallazgo #3).

---

## Plan de ejecución (pendiente de decidir alcance)

**Recomendación:** piloto en **`core-service`** (el más limpio) implementando el paquete completo — incluidas las piezas transversales nuevas — y validar antes de replicar.

Por servicio, el paquete completo es:

1. `ErrorCode` (enum) + `ProblemDetailFactory` + `DataIntegrityMapper`.
2. `GlobalExceptionHandler` reescrito sobre `ErrorWebExceptionHandler` (attendance/finance: **eliminar** el `@RestControllerAdvice`).
3. `SecurityConfig`: registrar `ApiAuthenticationEntryPoint` (401) + `ApiAccessDeniedHandler` (403) — **hallazgo #1**.
4. Test de serialización snake_case (**#2**) + test de 401/403 via Security (**#1**) + test de constraint violation (**#6**).
5. Actualizar tests existentes que asserten el shape viejo.

**Transversal (una vez, no por servicio):**

- Ambos `api-error.ts` (panel admin + PWA) — **hallazgo #4**. Alinear cadena de fallback (`detail` primero) y `getApiErrorCode()`.
- Verificar `UpgradeModal` ante el posible `planActual` → `plan_actual` (**riesgo #1**).
- Congelar los `codigo` del PWA en el enum de attendance (**#3**).

Opciones de rollout tras el piloto:

- **Piloto en 1 servicio** (recomendado) → validar → replicar a los 5 restantes.
- **Los 6 de una vez** → mayor alcance y riesgo; requiere revisar tests de cada servicio en la misma tanda.

Al implementar, actualizar la sección "Error Handling" de los 6 `CLAUDE.md` y marcar este documento como ✅ implementado.
