# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

Database-first SaaS gym management platform. The database layer (42 tables, 10 PostgreSQL schemas, Liquibase migrations) is complete and production-ready. Backend microservices and frontend are **not yet implemented** — only SQL migration scripts exist in `db/`.

## Commands

### Run database migrations locally

```powershell
# Apply all pending migrations
./gradlew update

# Check migration status
./gradlew status

# Rollback last N changesets
./gradlew rollbackCount -PliquibaseCommandValue=1
```

### Start local PostgreSQL via Docker

```powershell
docker-compose up -d postgres
# Then run migrations:
./gradlew update
```

### Start full stack (PostgreSQL + auto-run migrations)

```powershell
docker-compose up
```

### Configure local connection

Edit `gradle.properties` (never commit real credentials):
```properties
dbUrl=jdbc:postgresql://localhost:5432/gym_administrator
dbUser=postgres
dbPassword=your_password
```

The Docker Compose database is `gym-app-saas` with user `administrador`.

## Architecture

### Multi-tenant model

All tenant-scoped tables carry an `id_compania` column (no FK to `tenant.companias`). Data isolation is enforced by the application layer — every query must filter by `id_compania`. The `saas.*` and `identidad.*` schemas are global (no `id_compania`).

### Schema organization

| Schema | Scope | Purpose |
|---|---|---|
| `saas` | Global | Plans, features, platform operator users |
| `identidad` | Global | Persons (unique by CI), mobile app users, biometrics |
| `tenant` | Platform | Gym companies, branches, subscriptions, SaaS payments |
| `core` | Tenant | Clients, membership types, memberships, freezes |
| `asistencia` | Tenant | Attendance records, message templates, message log |
| `seguridad` | Tenant | Roles, permissions, staff users, access audit log |
| `config` | Tenant | Key-value gym config, payment methods |
| `finanzas` | Tenant (Premium) | Income/expense categories and records |
| `marketing` | Tenant (Premium) | Promotions, loyalty benefit rules |
| `inventario` | Tenant (Premium) | Products, stock, sales, movements |

### Three user tiers

- **Platform operators** → `saas.usuarios_plataforma` (global scope)
- **Gym staff** → `seguridad.usuarios` + `seguridad.roles` (per-company scope)
- **Gym clients** → `identidad.usuarios_app` (own data only via mobile app)

### Immutability rule

Never UPDATE to change the state of a subscription or membership. Always INSERT a new row. The previous row is preserved as history. This applies to `tenant.compania_planes` and `core.membresias`.

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

The existing `202605_GYM-001` story uses a single-digit prefix with a wider range (01–61), creating schemas first, then tables grouped by schema, then indexes, then seed data.

## CI/CD

Azure DevOps Pipeline (`azure-pipelines.yml`) runs `./gradlew update` automatically:
- `feature/*` or `develop` → DEV environment
- `release/*` → TEST environment
- `master` → PROD environment

Credentials are pulled from Azure Key Vault — never hardcoded.

## Key documentation

| File | Content |
|---|---|
| `OVERVIEW.md` | Full architecture, business rules, and data flows |
| `DATABASE_SCHEMA.md` | ASCII diagrams and table definitions |
| `DEVELOPMENT_ROADMAP.md` | Microservice build order and dependencies |
| `AUTH_SERVICE_SPEC.md` … `INVENTORY_SERVICE_SPEC.md` | Per-service API specs |
| `FRONTEND_AUTH_SPEC.md` | Frontend auth module spec |
| `db/scripts/202605_GYM-001/logical_diagram/schema.dbml` | Full DBML diagram |
