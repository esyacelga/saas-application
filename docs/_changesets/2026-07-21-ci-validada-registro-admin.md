# Changeset 2026-07-21 — `ci_validada` en registro de cliente (panel admin) + recálculo al editar CI

> El registro de cliente desde el panel admin ahora calcula el dígito verificador ecuatoriano y
> puebla `identidad.personas.ci_validada`, igual que ya lo hacían el auto-registro (platform) y el
> registro OAuth PWA (auth). Además, editar el CI de un cliente recalcula el flag.
> Ámbitos tocados: `core-service`, `auth-service`, `db` (comentario baseline), `docs`.

---

## Motivación

`identidad.personas.ci_validada` es `TRUE` solo cuando el documento pasa el algoritmo del dígito
verificador ecuatoriano (módulo 10 del Registro Civil). Ya lo calculaban:
- **platform-service** — auto-register wizard.
- **auth-service** — registro OAuth PWA y `POST /personas` (solo al **crear**).

Pero el **registro de cliente desde el panel admin** (core-service) dejaba `ci_validada` en el default
de BD (`FALSE`) **incluso para cédulas ecuatorianas válidas**. Y editar el CI de un cliente no
recalculaba el flag. Este changeset cierra ambas brechas.

---

## Cambios

### 1. core-service — cálculo en el registro admin (ruta que faltaba)

- **Nuevo** `core-service/.../domain/validation/CedulaEcuatoriana.java` — cuarta copia idéntica del
  algoritmo (front `validarCedula.ts` + platform + auth + core). Las cuatro deben permanecer iguales.
- `PersonaPersistenceAdapter.create(...)`: el INSERT a `identidad.personas` ahora incluye la columna
  `ci_validada`, enlazada a `CedulaEcuatoriana.esValida(command.ci())`. → `TRUE` solo si la cédula es
  EC válida; `FALSE` para pasaporte/RUC/extranjero o cédula inválida. **Nunca rechaza el registro.**
- Cubre `ClienteService.registrar` y `registrarDesdePlataforma` (ambos pasan por `personaRepository.create`).

### 2. auth-service — recálculo al editar el CI (UPDATE)

- `PersonaMapper.toEntity(...)`: antes preservaba `ci_validada` en UPDATE (solo lo calculaba al crear).
  Ahora lo calcula **siempre** desde el `ci` actual: `ciValidada = CedulaEcuatoriana.esValida(d.getCi())`.
- Motivo: el panel admin permite editar el CI del cliente (`EditarClienteModal` → `PUT /personas` con
  `ci`). Si se corrige a una cédula EC válida, el flag debe reflejarlo. `esValida` es función pura del
  `ci`, así que recalcular en UPDATE es idempotente y deja el flag siempre consistente con el documento.

### 3. db — comentario baseline actualizado

- `202605_GYM-001/ddl/14_create_table_identidad_personas.sql`: el `COMMENT ON COLUMN ci_validada` decía
  "hoy solo lo puebla platform-service"; se actualizó para listar las tres rutas de escritura (platform,
  auth incl. recálculo en UPDATE, core) y los pendientes reales (backfill + exposición REST). Solo cambia
  el texto del comentario — sin `ALTER`, respeta el invariante de baseline.

---

## Lo que sigue pendiente (no tocado aquí)

- **Backfill** de personas creadas antes de 2026-07-14 (migración de datos).
- **Exposición REST** del flag (`ClienteDetalleResponse` y endpoints de personas no lo retornan).

Ver [../gym-administrator/pendientes/validacion-cedula-persona.md](../gym-administrator/pendientes/validacion-cedula-persona.md).

---

## Verificación

- `core-service`: `mvn -o compile` limpio + `mvn test` → **136 unit tests, 0 fallos** (Zulu 25).
- `auth-service`: `mvn -o clean test` (Zulu 25) → los **17 tests de Persona pasan**; el resto verde salvo
  **2 errores preexistentes en `RolApplicationServiceTest$ActualizarPermisos`** (mock `PermisoPort
  .findByIdInAndIdCompania` devuelve `null` — NPE ajeno a este cambio, no toca Persona/CI).
- El `PersonaPersistenceAdapter.create` (nivel BD) se valida con IT (`-P fulltest`), no con unit tests.

## Impacto operativo

- Requiere **desplegar/reiniciar** core-service (8083) y auth-service (8080).
- **No es retroactivo**: solo aplica a personas creadas/editadas de aquí en adelante. El backfill de
  personas previas queda pendiente.
