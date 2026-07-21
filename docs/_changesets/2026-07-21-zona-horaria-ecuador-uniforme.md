# Changeset 2026-07-21 — Uniformización de zona horaria (America/Guayaquil) en los 6 microservicios

> Documenta la uniformización de la zona horaria de operación (Ecuador, UTC-5) al registrar/actualizar
> y al serializar fechas. Ámbitos tocados: `auth-service`, `platform-service`, `core-service`,
> `billing-service` (y verificación de `attendance-service`/`finance-service`, ya conformes).

---

## Motivación

Auditoría del manejo de zona horaria reveló tres formas distintas de anclar (o no) la hora de Ecuador:

| Servicio | JVM `TimeZone.setDefault` (antes) | `Clock` de negocio (antes) | Jackson `time-zone` (antes) |
|---|---|---|---|
| attendance | ✅ Guayaquil | — | ✅ Guayaquil |
| finance | ✅ Guayaquil | — | ✅ Guayaquil |
| core | ❌ | ✅ Guayaquil | ❌ |
| billing | ❌ | ✅ Guayaquil | ❌ |
| platform | ❌ | ⚠️ **UTC** | ❌ |
| auth | ❌ | — | ❌ |

Consecuencias:
- **Bug real en `platform-service`**: su `Clock` de negocio usaba `Clock.systemUTC()`. Entre 19:00 y 24:00
  hora Ecuador, `LocalDate.now(clock)` ya estaba en el día siguiente (UTC), adelantando el cálculo de
  vencimientos, degradaciones de trial y buckets de aviso (jobs que corren de noche).
- **Serialización inconsistente**: solo attendance/finance fijaban `spring.jackson.time-zone`. Los demás
  serializaban `Instant`/`OffsetDateTime` con el offset del host/contenedor → el frontend recibía mezcla
  de `Z` (UTC) y `-05:00`.

> **Dato importante:** todas las columnas de auditoría (`creacion_fecha`, `modifica_fecha`) son
> `TIMESTAMPTZ`, que almacena un **instante absoluto**. Por eso el dato **nunca se corrompió** aunque la
> zona de la JVM variara — el problema era de *cálculo de fecha civil* (platform) y de *formato de
> serialización* (todos menos attendance/finance), no de integridad del instante guardado.

---

## Cambios

### 1. `platform-service` — Clock de negocio a hora Ecuador (corrige bug)

- `infrastructure/config/ClockConfig.java`: `Clock.systemUTC()` → `Clock.system(ZoneId.of("America/Guayaquil"))`.
- **Seguro**: los `Instant.now(clock)` / `clock.instant()` no cambian (un instante es absoluto). Solo cambian
  los `LocalDate.now(clock)` / `OffsetDateTime.now(clock)` / `LocalDateTime.now(clock)` — que es exactamente
  la corrección buscada (el "hoy" de negocio pasa a ser el día civil de Ecuador).
- **Tests**: los 224 unit tests siguen verdes. Cada test inyecta su propio `Clock.fixed(...UTC...)`, así que
  no dependen del bean real; la semántica de producción es la que se corrige.

### 2. JVM default timezone en `main()` (core, platform, billing, auth)

Se añadió `TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"))` al inicio de `main()`, replicando
el patrón que ya tenían `attendance` y `finance`. Así `LocalDate.now()` / `LocalTime.now()` reflejan hora
Ecuador de forma uniforme en los 6 servicios.

- `core-service/.../CoreServiceApplication.java`
- `platform-service/.../PlatformServiceApplication.java`
- `billing-service/.../BillingServiceApplication.java`
- `auth-service/.../AuthServiceApplication.java`

### 3. `spring.jackson.time-zone: America/Guayaquil` + `write-dates-as-timestamps: false`

- `core-service/src/main/resources/application.yml`
- `billing-service/src/main/resources/application.yml`
- `auth-service/src/main/resources/application.yml` (ya tenía `write-dates-as-timestamps: false`; se añadió `time-zone`)
- `platform-service/src/main/resources/application.yml` (no tenía bloque `jackson`; se creó **solo** con
  `time-zone` + `write-dates-as-timestamps`).

> ⚠️ **Excepción deliberada en platform-service:** sus DTOs serializan en **camelCase** (`logoUrl`,
> `planActivo`, `fechaFin`, `diasRestantes`) — NO usa `property-naming-strategy: SNAKE_CASE` como el resto.
> El frontend admin depende de esos nombres exactos. **No se añadió SNAKE_CASE** para no romper el contrato
> JSON; esa divergencia queda como deuda separada, fuera del alcance de zona horaria.

---

## Estado final (todos uniformes en zona Ecuador)

| Servicio | JVM default | Clock de negocio | Jackson time-zone |
|---|---|---|---|
| attendance | ✅ Guayaquil | — | ✅ Guayaquil |
| finance | ✅ Guayaquil | — | ✅ Guayaquil |
| core | ✅ Guayaquil | ✅ Guayaquil | ✅ Guayaquil |
| billing | ✅ Guayaquil | ✅ Guayaquil | ✅ Guayaquil |
| platform | ✅ Guayaquil | ✅ **Guayaquil (era UTC)** | ✅ Guayaquil |
| auth | ✅ Guayaquil | — | ✅ Guayaquil |

---

## Verificación

- `platform-service`: `mvn test` → **224 tests, 0 fallos** (BUILD SUCCESS) con Zulu 25.
- `core-service` / `billing-service`: `mvn -o compile` limpio con Zulu 25.
- `auth-service`: `mvn -o compile` limpio con JDK 21 (exit 0).
- `attendance-service` / `finance-service`: sin cambios (ya conformes).

## Impacto operativo

- Requiere **reiniciar** los servicios para tomar la nueva zona (`TimeZone.setDefault` y el `Clock` se fijan
  al arranque). No hay migración de datos: los `TIMESTAMPTZ` ya almacenados son instantes absolutos correctos.
- En despliegues donde el contenedor ya corría en UTC, tras el reinicio las respuestas JSON pasan a exponer
  offset `-05:00` de forma consistente (mismo instante, etiquetado en hora Ecuador).
