# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

Database-first SaaS gym management platform. The database layer (42 tables, 10 PostgreSQL schemas, Liquibase migrations) is complete and production-ready. Backend microservices and frontend live in sibling monorepo folders (`auth-service/`, `platform-service/`, `core-service/`, `attendance-service/`, `auth-service-frond-end/`, `gym-member-pwa/`) — this folder only contains the `db/` migrations.

For architecture, schema organization, business rules, and the three user tiers, see [../docs/gym-administrator/architecture/overview.md](../docs/gym-administrator/architecture/overview.md) and [../docs/gym-administrator/architecture/database-schema.md](../docs/gym-administrator/architecture/database-schema.md) — this file only covers conventions not already documented there.

**When you add a new schema, table, or migration story, update [../docs/gym-administrator/architecture/database-schema.md](../docs/gym-administrator/architecture/database-schema.md) (and overview.md if it changes business rules or the data model) in the same task.**

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

The existing `202605_GYM-001` story uses a single-digit prefix with a wider range (01–61), creating schemas first, then tables grouped by schema, then indexes, then seed data.

## CI/CD

Azure DevOps Pipeline (`azure-pipelines.yml`) runs `./gradlew update` automatically:
- `feature/*` or `develop` → DEV environment
- `release/*` → TEST environment
- `master` → PROD environment

Credentials are pulled from Azure Key Vault — never hardcoded.

## Full documentation index

See [../docs/gym-administrator/INDEX.md](../docs/gym-administrator/INDEX.md) for architecture, per-service specs, frontend specs, and infra docs. Also: `db/scripts/202605_GYM-001/logical_diagram/schema.dbml` (full DBML diagram).
