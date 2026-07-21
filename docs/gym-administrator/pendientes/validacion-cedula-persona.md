# Pendiente — Validación de cédula de `identidad.personas` (`ci_validada`)

> **Estado:** 🟡 **Parcial** — escritura al crear persona **ya implementada** en platform-service
> (auto-register wizard), auth-service (registro OAuth PWA + `POST /personas`, vía `PersonaMapper`)
> **y core-service** (registro de cliente desde el panel admin). El **recálculo al editar `ci`** ya
> está implementado en auth-service (UPDATE de `PUT /personas`). Falta el **backfill** de personas
> existentes y la **exposición REST** del flag.
> **Fecha:** 2026-07-14 (creado) · 2026-07-14 (escritura platform) · 2026-07-16 (verificado scope)
> · 2026-07-17 (escritura auth-service — registro OAuth PWA) · 2026-07-21 (core-service registro
> admin + recálculo en UPDATE de auth-service).
> **Área:** identidad / core (personas) — la escritura vive en **platform-service**, **auth-service**
> y **core-service**.

## Requerimiento

> Este valor va a ser `true` cuando la cédula esté validada por el algoritmo de dígito
> verificador perteneciente al Ecuador.

## Lo que ya está hecho (esquema)

La columna existe en la baseline `GYM-001`:

```sql
-- db/scripts/202605_GYM-001/ddl/14_create_table_identidad_personas.sql
ci_validada  BOOLEAN  NOT NULL  DEFAULT FALSE
```

- **`FALSE`** (default): aún no validada, o el documento no es una cédula ecuatoriana
  (pasaporte, RUC, documento extranjero) → esos nunca pasan el algoritmo del módulo 10.
- **`TRUE`**: la cédula pasó el algoritmo del dígito verificador ecuatoriano.

Se plegó en el `CREATE TABLE` de la baseline (no como `ALTER` en story aparte), coherente
con que la BD sigue en desarrollo y la Neon se recrea desde cero.

## Lo que YA está hecho (escritura al crear persona — ruta específica) ✅

El **2026-07-14** se implementó la población del campo al **crear** la persona en
**platform-service auto-register wizard solamente**:

- **Algoritmo replicado en el backend:**
  `platform-service/src/main/java/com/gymadmin/platform/domain/validation/CedulaEcuatoriana.java:28-59`
  — réplica exacta de `validarCedula.ts` (módulo 10 Registro Civil, mismos coeficientes
  `[2,1,2,1,2,1,2,1,2]`, provincia 01–24 o 30 → mismo veredicto front/back).
- **Se puebla al INSERT:** `PersonaPersistenceAdapter.resolverIdPersona(...)` (línea 32) hace
  `entity.setCiValidada(CedulaEcuatoriana.esValida(ci))` al **crear** la persona → `TRUE` solo
  si `ci` pasa el algoritmo; `FALSE` en cualquier otro caso (documento no-EC, cédula
  inválida, o dígito verificador incorrecto). Nunca rechaza el registro.
- **Mapeo R2DBC:** `PersonaEntity.ciValidada` mapea la columna `ci_validada`.
- **Alcance limitado:** El cálculo ocurre **solo** cuando platform-service crea una persona
  nueva vía `resolverIdPersona` (upsert por CI). Personas creadas por otras rutas
  (auth-service `POST /personas`, admin-created via core-service, importación desde otros
  servicios) **no** cálculan el flag hoy — quedan con el default de BD (`FALSE`).

## Lo que YA está hecho (escritura al crear persona — auth-service) ✅

El **2026-07-17** se extendió el cálculo a **auth-service**, que ahora lo puebla al **crear
cualquier** persona (registro OAuth de la PWA vía `completarRegistroOauth`, auto-registro por
correo, `POST /personas`):

- **Algoritmo replicado:** `auth-service/.../domain/validation/CedulaEcuatoriana.java` — tercera
  copia idéntica (front `validarCedula.ts` + platform + auth). Las tres deben permanecer iguales.
- **Se puebla en el mapper (punto central de escritura):** `PersonaMapper.toEntity(...)` hace
  `ciValidada = CedulaEcuatoriana.esValida(ci)` **solo cuando `id == null`** (INSERT). En UPDATE
  preserva el valor de dominio. → `TRUE` solo si `ci` pasa el módulo 10; `FALSE` para documentos
  no-EC (pasaporte, RUC, extranjero) o cédulas inválidas. Nunca rechaza el registro.
- **Mapeo R2DBC:** `PersonaEntity.ciValidada` ↔ columna `ci_validada`; campo añadido también al
  modelo de dominio `Persona`.
- **Frontend PWA:** `CompletarRegistroOAuth.tsx` ya **no** limita a 10/13 dígitos — acepta
  cualquier documento numérico (`\d+`, mín. 3) para soportar socios extranjeros. La validación del
  dígito verificador es del lado servidor; el flag refleja si el documento es una cédula EC válida.

## Lo que YA está hecho (escritura al crear persona — core-service, ruta admin) ✅

El **2026-07-21** se extendió el cálculo a **core-service**, cubriendo el **registro de cliente
desde el panel admin** (`ClienteService.registrar` → `PersonaPersistenceAdapter.create`), que antes
dejaba `ci_validada` en el default de BD (`FALSE`) incluso para cédulas EC válidas:

- **Algoritmo replicado:** `core-service/.../domain/validation/CedulaEcuatoriana.java` — **cuarta**
  copia idéntica (front `validarCedula.ts` + platform + auth + core). Las cuatro deben permanecer iguales.
- **Se puebla en el INSERT:** `PersonaPersistenceAdapter.create(...)` calcula
  `ciValidada = CedulaEcuatoriana.esValida(ci)` y lo enlaza a la columna `ci_validada`. → `TRUE` solo
  si `ci` pasa el módulo 10; `FALSE` para documentos no-EC o cédulas inválidas. Nunca rechaza el registro.

## Lo que YA está hecho (recálculo al editar `ci` — auth-service UPDATE) ✅

El **2026-07-21** se implementó el **recálculo en UPDATE** en `auth-service` `PersonaMapper.toEntity`:
el flag ahora se calcula **siempre** desde el `ci` actual (INSERT y UPDATE), no solo al crear. El panel
admin permite editar el `ci` del cliente (`EditarClienteModal` → `PUT /personas` con `ci`); si se corrige
a una cédula EC válida, `ci_validada` pasa a `TRUE` (y viceversa). `esValida` es función pura del `ci`,
así que recalcular en UPDATE es idempotente y siempre deja el flag consistente con el documento guardado.

## Lo que falta (implementación) 📋

1. **Backfill de personas existentes.** Recorrer personas ya guardadas (antes de 2026-07-14)
   y recalcular `ci_validada` (migración de datos, no de esquema). Pendiente porque la
   escritura nueva solo aplica a personas creadas/editadas de aquí en adelante.

2. **Extensión a rutas restantes** (importación desde otros servicios, si las hubiera).
   platform-service, auth-service y core-service ya calculan el flag en sus rutas de creación.

3. **Exposición REST del flag.** Hoy `ci_validada` no se retorna en:
   - `core-service` → `ClienteDetalleResponse` (no incluye el campo)
   - `auth-service` → ningún endpoint de personas lo expone
   - `platform-service` → idem
   - Frontend no puede leer el valor para reflejar estado de validación al usuario.

### Notas de implementación

- El algoritmo canónico vive ahora en **cuatro** lugares que deben permanecer idénticos:
  frontend `src/lib/sri/validarCedula.ts`, `platform-service/.../domain/validation/CedulaEcuatoriana.java`,
  `auth-service/.../domain/validation/CedulaEcuatoriana.java` y
  `core-service/.../domain/validation/CedulaEcuatoriana.java`. Cualquier cambio debe aplicarse a los cuatro.
- El flag se calcula **en el servidor**; no se confía en el cliente.

## Relacionado

- Validación de cédula en el registro público:
  [../../_archive/auth-service-frond-end/registro-mejoras-implementadas.md](../../_archive/auth-service-frond-end/registro-mejoras-implementadas.md) §5.
- Frontera de validación pura `src/lib/sri/` (replicable a otros SaaS ecuatorianos).
