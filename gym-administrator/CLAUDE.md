# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

Database-first SaaS gym management platform. The database layer (70 tables across 12 PostgreSQL schemas, Liquibase migrations) is complete and production-ready. All DDL lives in the baseline `db/scripts/202605_GYM-001/` (subfolders `ddl/` core, `ddl-facturacion/` SRI+facturación, `ddl-freemium/` planes Free/Trial/Premium) plus incremental story `202607_GYM-003` (notif_buckets_globales for WhatsApp feature, Fase 6). Every table is defined once in its `CREATE TABLE` with no follow-up `ALTER`, so a fresh database builds in one pass. Backend microservices and frontend live in sibling monorepo folders (`auth-service/`, `platform-service/`, `core-service/`, `attendance-service/`, `auth-service-frond-end/`, `gym-member-pwa/`) — this folder only contains the `db/` migrations.

For architecture, schema organization, business rules, and the three user tiers, see [../docs/gym-administrator/architecture/overview.md](../docs/gym-administrator/architecture/overview.md) and [../docs/gym-administrator/architecture/database-schema.md](../docs/gym-administrator/architecture/database-schema.md) — this file only covers conventions not already documented there.

**When you add a new schema, table, or migration story, update [../docs/gym-administrator/architecture/database-schema.md](../docs/gym-administrator/architecture/database-schema.md) (and overview.md if it changes business rules or the data model) in the same task.**

## Fuentes autoritativas

Antes de confiar en un doc como referencia, verifica su estado en [../docs/STATUS.md](../docs/STATUS.md). Esta carpeta contiene solo las migraciones Liquibase; los docs "autoritativos" son la BD misma y el DBML:

| Área | Documento | Estado |
|------|-----------|--------|
| Esquema físico (fuente de verdad) | `db/scripts/202605_GYM-001/{ddl,ddl-facturacion,ddl-freemium}/*.sql` | ✅ Implementado |
| Diagrama lógico | `db/scripts/202605_GYM-001/logical_diagram/schema.dbml` | 🟡 Refleja el core histórico (42 tablas / 10 schemas); no incluye aún facturación/SRI |
| Visión de arquitectura y modelo de negocio | [../docs/gym-administrator/architecture/overview.md](../docs/gym-administrator/architecture/overview.md) · [database-schema.md](../docs/gym-administrator/architecture/database-schema.md) · [roadmap.md](../docs/gym-administrator/architecture/roadmap.md) | 🟡 Arquitectura mezcla implementado + planeado — verifica contra código para detalle de implementación |
| Specs de servicios ya implementados (auth, platform, core, attendance) | [../docs/gym-administrator/specs/](../docs/gym-administrator/INDEX.md) | 🟡 Spec de diseño — el código de cada servicio es la fuente de verdad |
| Specs de servicios planeados (billing, finance, marketing, inventory) | [../docs/gym-administrator/specs/](../docs/gym-administrator/INDEX.md) | 📋 Planeado — el código todavía no existe |

## Commands

```powershell
# Apply all pending migrations
./gradlew update

# Check migration status
./gradlew status

# Rollback last N changesets
./gradlew rollbackCount -PliquibaseCommandValue=1

# Start local PostgreSQL via Docker, then run migrations
docker-compose up -d postgres
./gradlew update

# Start full stack (PostgreSQL + auto-run migrations)
docker-compose up
```

Configure local connection in `gradle.properties` (never commit real credentials):
```properties
dbUrl=jdbc:postgresql://localhost:5432/gym_administrator
dbUser=postgres
dbPassword=your_password
```

The Docker Compose database is `gym-app-saas` with user `administrador`.

## Database Migration Conventions

### Adding a new user story

```
db/scripts/YYYYMM_GYM-XXX/
├── partial-changelog.yml       # ChangeSets for this story
└── ddl/
    ├── 01_description.sql
    └── 02_description.sql
```

Then append to `db/scripts/main-changelog.yml` (always at the end):
```yaml
- include:
    file: YYYYMM_GYM-XXX/partial-changelog.yml
    relativeToChangelogFile: true
```

### ChangeSet ID convention

`GYM-XXX-1`, `GYM-XXX-2`, ... (unique across the whole changelog)

### Script numbering

DDL scripts within a story are two-digit prefixed in execution order: `01_`, `02_`, etc.

The existing `202605_GYM-001` story is the single consolidated baseline. Its DDL is split across three subfolders — `ddl/` (core: 10 base schemas + tables + indexes + seeds), `ddl-facturacion/` (SRI + facturación electrónica), `ddl-freemium/` (planes Free/Trial/Premium extras) — and its `partial-changelog.yml` orders them so every FK dependency resolves in one pass (notably: facturación is created before `finanzas.ingresos`, which references `facturacion.comprobantes`). Baseline invariant: each table is defined **once** in its `CREATE TABLE`; there are no `ALTER TABLE` fix-up scripts. New changes go in a **new** `YYYYMM_GYM-XXX/` story appended to `main-changelog.yml`, never by editing the baseline in place.

## CI/CD

Azure DevOps Pipeline (`azure-pipelines.yml`) runs `./gradlew update` automatically:
- `feature/*` or `develop` → DEV environment
- `release/*` → TEST environment
- `master` → PROD environment

Credentials are pulled from Azure Key Vault — never hardcoded.

## Full documentation index

See [../docs/gym-administrator/INDEX.md](../docs/gym-administrator/INDEX.md) for architecture, per-service specs, frontend specs, and infra docs. Also: `db/scripts/202605_GYM-001/logical_diagram/schema.dbml` (full DBML diagram).
