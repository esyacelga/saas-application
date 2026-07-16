# Docker — gym-app-saas

> **ESTADO:** 🟡 Referencia de infraestructura. Verificar contra los archivos reales (docker-compose, pipelines) para el detalle actual. Ver [../../STATUS.md](../../STATUS.md).

Configuración Docker para levantar PostgreSQL y ejecutar las migraciones Liquibase del proyecto.

## Requisitos

- [Docker](https://www.docker.com/products/docker-desktop) instalado y corriendo
- [Docker Compose](https://docs.docker.com/compose/) (incluido en Docker Desktop)

## Servicios

| Servicio    | Imagen                        | Puerto | Descripción                          |
|-------------|-------------------------------|--------|--------------------------------------|
| `postgres`  | `postgres:16-alpine`          | 5432   | Base de datos principal              |
| `liquibase` | `liquibase/liquibase:4.25.0`  | —      | Ejecuta las migraciones y se detiene |

## Credenciales

| Parámetro | Valor          |
|-----------|----------------|
| Host      | `localhost`    |
| Puerto    | `5432`         |
| Base de datos | `gym-app-saas` |
| Usuario   | `administrador` |
| Contraseña | `seya1922`    |

## Comandos

### Levantar todo (postgres + migraciones)

```bash
docker-compose up
```

Espera a que PostgreSQL esté listo (healthcheck) antes de ejecutar Liquibase.

### Solo levantar PostgreSQL

```bash
docker-compose up postgres
```

Útil cuando ya tienes la BD corriendo y solo quieres el motor de base de datos.

### Correr migraciones sobre un postgres ya levantado

```bash
docker-compose run --rm liquibase
```

### Bajar los contenedores

```bash
docker-compose down
```

Los datos persisten en el volumen `gym-app-saas-data`.

### Bajar contenedores y eliminar datos

```bash
docker-compose down -v
```

Elimina también el volumen. Útil para empezar desde cero.

### Implementación limpia (eliminar todo y volver a levantar)

```bash
docker compose down -v --remove-orphans
docker compose up
```

Elimina contenedores, volúmenes y redes huérfanas antes de levantar desde cero. Garantiza que Liquibase aplique los 96 changesets de la story consolidada sobre una base de datos vacía y quede lista con las 69 tablas.

## Estructura de migraciones

Todas las migraciones viven en una única story consolidada (`202605_GYM-001`) con tres subcarpetas por dominio. Las migraciones se ejecutan en este orden:

```
db/scripts/
├── main-changelog.yml                    ← entrada principal (solo incluye la story)
└── 202605_GYM-001/
    ├── partial-changelog.yml             ← 96 changesets ordenados por dependencia
    ├── logical_diagram/
    │   └── schema.dbml                   ← diagrama lógico (core histórico)
    ├── ddl/                              ← 46 tablas base + schemas + índices + seeds
    │   ├── 01–10  creación de 10 schemas base
    │   ├── 11–13  tablas saas (planes, caracteristicas, plan_caracteristicas)
    │   ├── 14–15  tablas identidad (personas, usuarios_app)
    │   ├── 16–21  tablas tenant (companias, sucursales, compania_planes,
    │   │          pagos_suscripcion, config_notif, notificaciones_suscripcion)
    │   ├── 22–23  tablas config (gym_config, metodos_pago)
    │   ├── 24–28  tablas seguridad (roles, permisos, rol_permisos,
    │   │          usuarios, bitacora_accesos)
    │   ├── 29–32  tablas core (clientes, tipos_membresia, membresias, congelamientos)
    │   ├── 33–35  tablas asistencia (asistencias, plantillas_mensajes, mensajes_log)
    │   ├── 36–39  tablas finanzas (categorías + ingresos/egresos)
    │   ├── 40–43  tablas marketing (promociones + beneficios + asignaciones)
    │   ├── 44–50  tablas inventario (categorías, proveedores, productos,
    │   │          stock, ventas, detalle_ventas, movimientos)
    │   ├── 51–58  índices por schema
    │   ├── 54     identidad.biometria
    │   ├── 59–60  saas.usuarios_plataforma, seguridad.refresh_tokens
    │   ├── 61–63  seeds (usuario root, planes/caracteristicas, tipos_membresia)
    │   ├── 67     saas.actividad_plataforma
    │   └── 69     índices identidad
    ├── ddl-facturacion/                  ← 23 tablas + 2 schemas SRI/facturación
    │   ├── 01–02  schemas sri + facturacion
    │   ├── 03–08  catálogos oficiales SRI (6 tablas)
    │   ├── 09     seed catálogos SRI
    │   ├── 10–25  facturación electrónica (config, certificados, secuenciales,
    │   │          comprobantes y todo el flujo de emisión + envío + anulación)
    │   ├── 26     función next_secuencial
    │   ├── 27     índices facturacion
    │   └── 30     comprobantes_detalle (variante)
    └── ddl-freemium/                     ← 2 tablas nuevas + 1 seed (REQ-SAAS-001)
        ├── 01    tenant.pagos_pendientes_validacion
        ├── 02    saas.config_plataforma
        └── 03    seed saas.config_plataforma (7 claves pago.banco.*)
```

**Baseline invariant:** cada tabla se define una sola vez en su `CREATE TABLE`. No hay scripts `ALTER` posteriores en la story consolidada. Los cambios REQ-SAAS-001 (Sub-fase 1.1), antes en la story `202608_GYM-003`, están incorporados directamente al `CREATE` de las tablas afectadas (`saas.planes`, `tenant.companias`, `tenant.compania_planes`, `tenant.notificaciones_suscripcion`, `saas.actividad_plataforma`). Asimismo, los cambios de las stories WhatsApp `202607_GYM-002` (consentimiento) y `202607_GYM-003` (buckets globales) fueron consolidados en la baseline el 2026-07-16 al recrear la BD desde cero.

## Schemas creados (12)

| Schema       | Tablas | Descripción                                          |
|--------------|:------:|------------------------------------------------------|
| `saas`       | 6      | Plataforma SaaS: planes, caracteristicas, plan_caracteristicas, usuarios_plataforma, actividad_plataforma, config_plataforma |
| `identidad`  | 3      | Personas globales, credenciales de app móvil, biometría |
| `tenant`     | 7      | Compañías, sucursales, suscripciones, pagos, notificaciones, pagos pendientes |
| `core`       | 4      | Clientes, tipos_membresia, membresias, congelamientos |
| `asistencia` | 3      | Asistencias, plantillas_mensajes, mensajes_log       |
| `config`     | 2      | gym_config (clave-valor por gym), metodos_pago       |
| `seguridad`  | 6      | Roles, permisos, rol_permisos, usuarios staff, bitácora, refresh_tokens |
| `finanzas`   | 4      | Categorías e ingresos, categorías y egresos          |
| `marketing`  | 4      | Promociones y beneficios (regla + asignación por cliente) |
| `inventario` | 7      | Categorías, proveedores, productos, stock, ventas, detalle, movimientos |
| `sri`        | 6      | Catálogos oficiales SRI Ecuador (solo lectura, seeded) |
| `facturacion`| 17     | Facturación electrónica SRI: config, certificados, secuenciales, comprobantes + detalle + impuestos + envíos + anulaciones + ATS |
| **Total**    | **69** | |
