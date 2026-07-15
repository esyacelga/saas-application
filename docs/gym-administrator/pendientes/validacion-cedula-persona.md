# Pendiente — Validación de cédula de `identidad.personas` (`ci_validada`)

> **Estado:** 📋 **Pendiente de implementación** (columna creada, lógica sin implementar).
> **Fecha:** 2026-07-14.
> **Área:** identidad / core (personas).

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

## Lo que falta (implementación)

Hoy **nadie puebla el campo** — la columna existe pero siempre queda en `FALSE`. Falta la
lógica que:

1. Al crear/editar una persona, corra el **algoritmo del dígito verificador ecuatoriano**
   (módulo 10 del Registro Civil: 10 dígitos, provincia 01–24 o 30, tercer dígito < 6,
   coeficientes `[2,1,2,1,2,1,2,1,2]`, verificador).
2. Setee `ci_validada = TRUE` **solo** si `ci` es una cédula ecuatoriana que pasa el algoritmo;
   la deje en `FALSE` en cualquier otro caso (documento no-EC, cédula inválida).
3. Recalcule el flag cuando `ci` cambie.

### Notas de implementación

- **Ya existe el algoritmo en el frontend:** `auth-service-frond-end/src/lib/sri/validarCedula.ts`
  (TypeScript puro). La validación de escritura del backend debe replicar exactamente esa lógica
  para que front y back coincidan (misma persona → mismo veredicto).
- **Dónde vive la escritura de personas:** confirmar el servicio dueño (core-service / platform)
  antes de implementar; el flag se calcula en el servidor, no se confía en el cliente.
- **Backfill:** al implementar, considerar un paso que recorra las personas existentes y
  recalcule `ci_validada` una vez (una migración de datos, ya no de esquema).

## Relacionado

- Validación de cédula en el registro público:
  [../../auth-service-frond-end/registro-mejoras-implementadas.md](../../auth-service-frond-end/registro-mejoras-implementadas.md) §5.
- Frontera de validación pura `src/lib/sri/` (replicable a otros SaaS ecuatorianos).
