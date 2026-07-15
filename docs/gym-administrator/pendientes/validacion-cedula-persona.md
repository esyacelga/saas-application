# Pendiente — Validación de cédula de `identidad.personas` (`ci_validada`)

> **Estado:** 🟡 **Parcial** — escritura al crear persona **ya implementada**; falta el
> **backfill** de personas existentes y el recálculo al editar `ci`.
> **Fecha:** 2026-07-14 (creado) · 2026-07-14 (escritura implementada).
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

## Lo que YA está hecho (escritura al crear persona) ✅

El **2026-07-14** se implementó la población del campo al **crear** la persona en
platform-service:

- **Algoritmo replicado en el backend:**
  `platform-service/src/main/java/com/gymadmin/platform/domain/validation/CedulaEcuatoriana.java`
  — réplica exacta de `validarCedula.ts` (mismo módulo 10, mismos coeficientes → mismo
  veredicto front/back).
- **Se puebla el flag:** `PersonaPersistenceAdapter.resolverIdPersona(...)` hace
  `entity.setCiValidada(CedulaEcuatoriana.esValida(ci))` al crear la persona → `TRUE` solo
  si `ci` pasa el algoritmo; `FALSE` en cualquier otro caso (documento no-EC, cédula
  inválida). Nunca rechaza el registro.
- **Mapeo R2DBC:** `PersonaEntity.ciValidada` mapea la columna `ci_validada`.

## Lo que falta (implementación) 📋

1. **Recálculo al editar `ci`.** La escritura de hoy solo cubre la **creación** vía
   `resolverIdPersona` (upsert por CI). Si en el futuro se permite **editar** el `ci` de una
   persona existente, hay que recalcular el flag en esa ruta también.
2. **Backfill de personas existentes.** Recorrer una vez las personas ya guardadas y
   recalcular `ci_validada` (migración de datos, no de esquema). Pendiente porque la escritura
   nueva solo aplica a personas creadas de aquí en adelante.

### Notas de implementación

- El algoritmo canónico vive ahora en **dos** lugares que deben permanecer idénticos:
  frontend `src/lib/sri/validarCedula.ts` y backend `domain/validation/CedulaEcuatoriana.java`.
  Cualquier cambio debe aplicarse a ambos.
- El flag se calcula **en el servidor**; no se confía en el cliente.

## Relacionado

- Validación de cédula en el registro público:
  [../../auth-service-frond-end/registro-mejoras-implementadas.md](../../auth-service-frond-end/registro-mejoras-implementadas.md) §5.
- Frontera de validación pura `src/lib/sri/` (replicable a otros SaaS ecuatorianos).
