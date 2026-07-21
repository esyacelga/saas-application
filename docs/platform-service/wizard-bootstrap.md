# Flujo de Bootstrap de Compañía — Wizard de Registro

> **ESTADO:** ✅ **Refleja el código actual** (verificado contra `CompaniaService.registrarGymWizard`, `MetodoPagoPersistenceAdapter` — implementado 2026-07-21).

## Descripción general

El endpoint `POST /api/v1/companias/wizard` ejecuta un flujo **orquestado de bootstrap completo** de una compañía nueva en la plataforma SaaS. Crea la compañía, su sucursal principal, configuraciones de notificación, **métodos de pago por defecto**, rol administrativo, permisos y usuarios del gimnasio en una sola transacción reactiva.

Base path: `/api/v1/companias`  
Service: platform-service (port 8081)

---

## Secuencia de ejecución (`ejecutarRegistroWizard`)

El flujo es **secuencial y atómico** — si falla cualquier paso, se revierte todo (transacción de BD). Los pasos son:

1. **Guardar compañía** → `tenant.companias`
2. **Crear suscripción plan** → `tenant.compania_planes` (relación compañía-plan, estado `ACTIVO`)
3. **Crear sucursal principal** → `tenant.sucursales` (genera `qr_token` válido por 1 año)
4. **Guardar configuración de notificaciones de vencimiento** → `config.config_notif_suscripcion` (3 registros: 7, 3, 1 días con canal WhatsApp)
5. **Crear métodos de pago por defecto** ← **NUEVO (2026-07-21)**
6. **Crear rol SUPER_ADMIN** → `seguridad.roles` con todos los permisos de la compañía
7. **Crear todos los usuarios** → `seguridad.usuarios` (usuario principal + adicionales)

---

## Métodos de Pago — Auto-bootstrap (2026-07-21)

### Qué se crea

En el paso **5**, se insertan automáticamente **3 métodos de pago por defecto** para la nueva compañía y su sucursal principal:

| Nombre | Tabla | Schema | Activo | Campos |
|--------|-------|--------|--------|--------|
| Efectivo | `config.metodos_pago` | `config` | `true` | `id_compania`, `id_sucursal`, `nombre`, `activo=true`, `eliminado=false` |
| Tarjeta | `config.metodos_pago` | `config` | `true` | (igual) |
| Transferencia | `config.metodos_pago` | `config` | `true` | (igual) |

### Implementación: idempotente por nombre

La operación **NO usa `INSERT ... ON CONFLICT`** (tabla `config.metodos_pago` sin UNIQUE sobre `(id_compania, nombre)`). En su lugar:

1. Consulta métodos existentes: `SELECT * FROM config.metodos_pago WHERE id_compania = ? AND eliminado = false`
2. Calcula cuáles faltan comparando los `nombre`s ya presentes contra la lista hardcodeada `["Efectivo", "Tarjeta", "Transferencia"]`
3. Inserta solo los faltantes

**Consecuencia:** Si se llama al wizard dos veces con la misma `id_compania` (ej. reintento), solo se insertan los métodos que no existan ya. Los existentes se dejan intactos.

### Consumidor: core-service

El endpoint `GET /api/v1/metodos-pago` de **core-service** (port 8083) **filtra por `id_compania` + `activo=true` + `eliminado=false`** y retorna la lista de métodos disponibles. Este endpoint lo consume el frontend admin (`/admin/ventas-pendientes`) en el modal `CompletarVentaClienteModal` para permitir que el operador seleccione un método al procesar una solicitud de membresía.

Antes de 2026-07-21, compañías registradas por el wizard carecían de métodos de pago, lo que provocaba que el selector apareciera vacío con el mensaje "No hay métodos de pago configurados".

### Arquitectura: schema cross-cutting

`config.metodos_pago` pertenece **conceptualmente al dominio de core-service** (configuración de operación del gimnasio), pero **platform-service lo siembra durante el bootstrap** de la compañía. Esto sigue el patrón ya establecido: platform-service es el **orquestador único del bootstrap completo** de una compañía, incluyendo:

- Compañía + sucursal principal
- Rol administrativo + permisos (`seguridad.roles`, `seguridad.permisos` — dominio de auth-service)
- Usuarios del gymnasi o (`seguridad.usuarios` — auth-service)
- Métodos de pago por defecto (`config.metodos_pago` — core-service)
- Configuración de notificaciones (`config.config_notif_suscripcion` — platform-service)

Esta consolidación evita que el frontend o servicios externos tengan que hacer llamadas POST adicionales después del registro — una sola llamada al wizard deja la compañía completamente funcional.

---

## Código relevante

### Puerto del dominio
**Archivo:** `platform-service/src/main/java/com/gymadmin/platform/domain/port/out/MetodoPagoRepository.java`

```java
public interface MetodoPagoRepository {
    /**
     * Crea los métodos de pago por defecto (Efectivo, Tarjeta, Transferencia) para la
     * compañía/sucursal recién registrada, si aún no existen. Idempotente por nombre.
     */
    Mono<Void> crearPorDefecto(Long idCompania, Long idSucursal);
}
```

### Adapter de persistencia
**Archivo:** `platform-service/src/main/java/com/gymadmin/platform/infrastructure/adapter/out/persistence/adapter/MetodoPagoPersistenceAdapter.java`

```java
@Component
public class MetodoPagoPersistenceAdapter implements MetodoPagoRepository {

    private static final List<String> POR_DEFECTO = List.of("Efectivo", "Tarjeta", "Transferencia");

    @Override
    public Mono<Void> crearPorDefecto(Long idCompania, Long idSucursal) {
        // Idempotente: la tabla no tiene UNIQUE (id_compania, nombre), así que evitamos
        // duplicados comparando contra los nombres ya existentes de la compañía.
        return repository.findByIdCompaniaAndEliminadoFalse(idCompania)
                .map(MetodoPagoEntity::getNombre)
                .collect(Collectors.toSet())
                .flatMapMany(existentes -> Flux.fromIterable(faltantes(existentes, idCompania, idSucursal)))
                .flatMap(repository::save)
                .then();
    }

    private List<MetodoPagoEntity> faltantes(Set<String> existentes, Long idCompania, Long idSucursal) {
        return POR_DEFECTO.stream()
                .filter(nombre -> !existentes.contains(nombre))
                .map(nombre -> {
                    MetodoPagoEntity e = new MetodoPagoEntity();
                    e.setIdCompania(idCompania);
                    e.setIdSucursal(idSucursal);
                    e.setNombre(nombre);
                    e.setActivo(true);
                    return e;
                })
                .toList();
    }
}
```

### Orquestación en `CompaniaService`
**Archivo:** `platform-service/src/main/java/com/gymadmin/platform/application/service/CompaniaService.java` (líneas 190-212)

```java
private Mono<RegistrarGymWizardResult> ejecutarRegistroWizard(
        RegistrarGymWizardCommand command, Plan plan, List<UsuarioWizardCommand> adicionales) {
    return companiaRepository.save(construirCompania(command))
            .flatMap(savedCompania -> companiaPlanRepository.save(construirCompaniaPlan(savedCompania, plan))
                    .flatMap(savedCp -> guardarSucursalConQr(savedCompania, command)
                            .flatMap(savedSucursal ->
                                    guardarConfiguracionesNotificacion(savedCompania.getId())
                                            .then(metodoPagoRepository.crearPorDefecto(savedCompania.getId(), savedSucursal.getId()))
                                            .then(crearRolConPermisos(savedCompania.getId(), savedSucursal.getId()))
                                            .flatMap(idRol -> crearTodosLosUsuarios(savedCompania, savedSucursal, idRol, command, adicionales)
                                                    .map(r -> new RegistrarGymWizardResult(...))
                                            )
                            )
                    )
            );
}
```

El `.then(metodoPagoRepository.crearPorDefecto(...))` ocurre **después** de guardar configuraciones de notificación (línea 196) y **antes** de crear el rol (línea 198).

---

## Impacto en el frontend

### Modal `CompletarVentaClienteModal` (auth-service-frond-end)

Este modal consume `GET /api/v1/metodos-pago` de core-service para renderizar un selector de método de pago. Antes del cambio, este selector aparecía vacío para gimnasios registrados por wizard, bloqueando el flujo de completar solicitudes de membresía.

**Comportamiento posterior (2026-07-21):**
- Al crear un gimnasio nuevo → wizard siembra 3 métodos de pago
- Inmediatamente después → `GET /metodos-pago` retorna la lista completa
- Modal renderiza selector funcional sin cambios de código frontend

---

## Consideraciones de operación

### Auto-seed de compañía base (seed SRI GYM-002)

La compañía base de la plataforma (RUC `0000000000001`, creada por SQL seed al inicializar la BD) tenía sus métodos de pago sembradorados manualmente en ese mismo seed. El wizard **no afecta esa compañía** — es específico para compañías nuevas registradas en tiempo de ejecución.

### Soft-delete

La convención del repositorio es `eliminado = false` en todas las queries. Si en el futuro se soft-deletean métodos de pago de una compañía, el wizard **no recreará automáticamente** los métodos si se intenta reregistrar la compañía (porque la idempotencia solo verifica `eliminado = false`). Esto es a propósito — si el operador softdeleteó un método, probablemente no quería que reapareciera.

Para **restaurar** métodos eliminados, el flujo futuro sería:
1. Operador crea el método manualmente en el admin de métodos de pago de core-service (si existe ese endpoint)
2. O, edita directamente BD (`UPDATE ... SET eliminado = false`)

---

## Relación con REQ-SAAS-001 (Freemium)

El bootstrap de métodos de pago **no es parte de REQ-SAAS-001** (planes Freemium). Sin embargo, ambos flujos fueron implementados en la misma ventana (Fases SRI 2026 + mejoras operacionales 2026-07-21), y ambos usan el mismo endpoint `POST /companias/wizard` como punto de entrada. Las fases de REQ-SAAS-001 completadas (1.1–1.5) añadieron endpoints de planes, pagos, notificaciones, y banners de vencimiento al wizard — la creación de métodos de pago es un **complemento natural** de la cadena de bootstrap, independiente del número de fase.
