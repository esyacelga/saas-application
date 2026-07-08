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

Elimina contenedores, volúmenes y redes huérfanas antes de levantar desde cero. Garantiza que Liquibase aplique los 61 changesets sobre una base de datos vacía.

## Estructura de migraciones

Las migraciones se ejecutan en este orden:

```
db/scripts/
├── main-changelog.yml                    ← entrada principal
└── 202605_GYM-001/
    ├── partial-changelog.yml             ← 60 changesets
    └── ddl/                              ← scripts SQL
        ├── 01–10   creación de schemas
        ├── 11–13   tablas saas
        ├── 14–15   tablas identidad
        ├── 16–21   tablas tenant
        ├── 22–23   tablas config
        ├── 24–28   tablas seguridad
        ├── 29–32   tablas core
        ├── 33–35   tablas asistencia
        ├── 36–39   tablas finanzas
        ├── 40–43   tablas marketing
        ├── 44–50   tablas inventario
        └── 51–60   índices y tablas adicionales
```

## Schemas creados

| Schema       | Descripción                              |
|--------------|------------------------------------------|
| `saas`       | Gestión de la plataforma SaaS            |
| `identidad`  | Autenticación e identidad               |
| `tenant`     | Multi-tenant (empresas y sucursales)    |
| `config`     | Configuración del sistema               |
| `seguridad`  | Roles, permisos y bitácora de accesos   |
| `core`       | Datos principales del gimnasio          |
| `asistencia` | Control de asistencias                  |
| `finanzas`   | Ingresos y egresos                      |
| `marketing`  | Promociones y beneficios                |
| `inventario` | Productos, ventas e inventario          |
