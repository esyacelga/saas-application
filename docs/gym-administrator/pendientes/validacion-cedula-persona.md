# Pendiente — Validación de cédula de `identidad.personas` (`ci_validada`)

> **Estado:** 🟡 **Parcial** — escritura al crear persona **ya implementada** en ruta específica
> (platform-service auto-register wizard); falta el **backfill** de personas existentes,
> recálculo al editar `ci`, exposición REST, e implementación en otras rutas de creación.
> **Fecha:** 2026-07-14 (creado) · 2026-07-14 (escritura implementada) · 2026-07-16 (verificado scope).
> **Área:** identidad / core (personas) — la escritura vive en **platform-service**.

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

## Lo que falta (implementación) 📋

1. **Extensión a otras rutas de creación de persona** (auth-service `POST /personas`,
   admin-created vía core-service, etc.). Hoy solo platform-service calcula el flag.

2. **UPDATE path — recálculo al editar `ci`.** Si se permite **editar** el `ci` de una
   persona existente (no hay endpoint hoy), hay que recalcular `ci_validada` en esa ruta.

3. **Backfill de personas existentes.** Recorrer personas ya guardadas (antes de 2026-07-14)
   y recalcular `ci_validada` (migración de datos, no de esquema). Pendiente porque la
   escritura nueva solo aplica a personas creadas de aquí en adelante.

4. **Exposición REST del flag.** Hoy `ci_validada` no se retorna en:
   - `core-service` → `ClienteDetalleResponse` (no incluye el campo)
   - `auth-service` → ningún endpoint de personas lo expone
   - `platform-service` → idem
   - Frontend no puede leer el valor para reflejar estado de validación al usuario.

### Notas de implementación

- El algoritmo canónico vive ahora en **dos** lugares que deben permanecer idénticos:
  frontend `src/lib/sri/validarCedula.ts` y backend `domain/validation/CedulaEcuatoriana.java`.
  Cualquier cambio debe aplicarse a ambos.
- El flag se calcula **en el servidor**; no se confía en el cliente.

## Relacionado

- Validación de cédula en el registro público:
  [../../auth-service-frond-end/registro-mejoras-implementadas.md](../../auth-service-frond-end/registro-mejoras-implementadas.md) §5.
- Frontera de validación pura `src/lib/sri/` (replicable a otros SaaS ecuatorianos).
