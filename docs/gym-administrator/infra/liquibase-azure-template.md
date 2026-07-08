P# Plantilla de Proyecto: Gestión de Base de Datos con Liquibase + Azure DevOps

> Basado en el proyecto `tdd-dba-reconciliacion`. Sirve como guía para implementar proyectos de gestión de esquema de base de datos PostgreSQL con migraciones versionadas, encriptación de datos sensibles y pipeline CI/CD en Azure DevOps.

---

## Tabla de Contenidos

1. [Arquitectura General](#1-arquitectura-general)
2. [Estructura de Directorios](#2-estructura-de-directorios)
3. [Configuración del Proyecto](#3-configuración-del-proyecto)
4. [Esquema de Base de Datos](#4-esquema-de-base-de-datos)
5. [Convenciones de Nomenclatura](#5-convenciones-de-nomenclatura)
6. [Liquibase: Changelogs y ChangeSets](#6-liquibase-changelogs-y-changesets)
7. [Seguridad y Encriptación](#7-seguridad-y-encriptación)
8. [Auditoría y Trazabilidad](#8-auditoría-y-trazabilidad)
9. [Pipeline CI/CD con Azure DevOps](#9-pipeline-cicd-con-azure-devops)
10. [Variables y Secretos](#10-variables-y-secretos)
11. [Guía para Nuevo Proyecto](#11-guía-para-nuevo-proyecto)

---

## 1. Arquitectura General

Este tipo de proyecto gestiona **únicamente la capa de base de datos** mediante migraciones versionadas. No contiene lógica de aplicación.

```
┌─────────────────────────────────────────────────────────────────┐
│                     PIPELINE AZURE DEVOPS                       │
│                                                                 │
│  Git Push → Trigger → Key Vault → Gradle + Liquibase → Postgres │
└─────────────────────────────────────────────────────────────────┘
         │                                         │
         ▼                                         ▼
 db/scripts/                              PostgreSQL Database
 ├── main-changelog.yml                   ├── schema_1
 ├── YYYYMM_TICKET-XXX/                   │   ├── tablas
 │   ├── partial-changelog.yml            │   ├── funciones
 │   ├── ddl/                             │   └── triggers
 │   └── dml/                             └── schema_batch_metadata
 └── YYYYMM_TICKET-YYY/                       └── (Spring Batch tables)
     └── ...
```

**Stack tecnológico:**

| Componente | Tecnología | Versión referencia |
|---|---|---|
| Base de datos | PostgreSQL | 14+ |
| Gestión de migraciones | Liquibase | 4.25.0 |
| Sistema de construcción | Gradle | 8.x |
| JVM | Java | 17 |
| Driver JDBC | postgresql | 42.6.0 |
| Encriptación BD | pgcrypto (extensión PostgreSQL) | nativa |
| CI/CD | Azure DevOps Pipelines | — |
| Gestión de secretos | Azure Key Vault | — |

---

## 2. Estructura de Directorios

```
<nombre-proyecto>/
├── build.gradle                        # Configuración Gradle + plugin Liquibase
├── settings.gradle                     # Nombre del proyecto raíz
├── gradle.properties                   # Variables de conexión (NO commitear)
├── .gitignore                          # Excluir gradle.properties y build/
├── azure-pipelines.yml                 # Pipeline CI/CD
├── README.md                           # Documentación operativa
└── db/
    └── scripts/
        ├── main-changelog.yml          # Changelog maestro (solo incluye parciales)
        ├── YYYYMM_<TICKET-ID>/         # Una carpeta por historia de usuario
        │   ├── partial-changelog.yml   # Lista ordenada de ChangeSets
        │   ├── ddl/                    # Scripts DDL numerados
        │   │   ├── 01_create_schema_*.sql
        │   │   ├── 02_create_table_*.sql
        │   │   ├── ...
        │   │   └── NN_<descripcion>.sql
        │   ├── dml/                    # Scripts DML (datos iniciales)
        │   │   └── 01_insert_data_*.sql
        │   └── logical_diagram/        # Diagrama del esquema (opcional)
        │       └── schema.dbml
        └── YYYYMM_<TICKET-ID-2>/       # Fix o nueva HU
            ├── partial-changelog.yml
            └── ddl/
                └── 01_update_*.sql
```

**Reglas de organización:**

- Cada carpeta `YYYYMM_<TICKET>` corresponde a una historia de usuario o fix.
- Los scripts DDL se numeran con dos dígitos (`01_`, `02_`, ...) en el orden de ejecución.
- Los scripts DML van separados de los DDL para facilitar el control por contextos.
- El `main-changelog.yml` **nunca** contiene ChangeSets directamente, solo referencias a parciales.

---

## 3. Configuración del Proyecto

### build.gradle

```groovy
plugins {
    id 'org.liquibase.gradle' version '2.2.0'
    id 'java'
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

group = 'com.<tu-organizacion>'
version = '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    liquibaseRuntime 'org.liquibase:liquibase-core:4.25.0'
    liquibaseRuntime 'org.liquibase:liquibase-groovy-dsl:3.0.3'
    liquibaseRuntime 'org.postgresql:postgresql:42.6.0'
    liquibaseRuntime 'org.yaml:snakeyaml:2.2'
    liquibaseRuntime 'info.picocli:picocli:4.7.5'
}

liquibase {
    activities {
        main {
            changelogFile 'db/scripts/main-changelog.yml'
            url userdb                     // Variable desde gradle.properties
            username userdb                // Variable desde gradle.properties
            password passdb                // Variable desde gradle.properties
            driver 'org.postgresql.Driver'
            logLevel logLevel
            contexts contexts
            defaultSchemaName defaultSchema
            liquibaseSchemaName defaultLiquibaseSchema
            // Parámetros adicionales (ej: clave de encriptación)
            changeLogParameters(['encryptKey': encryptKey])
        }
    }
    runList = 'main'
}
```

### settings.gradle

```groovy
rootProject.name = '<nombre-proyecto>'
```

### gradle.properties (NO commitear — agregar al .gitignore)

```properties
# Conexión a base de datos

> **ESTADO:** 🟡 Referencia de infraestructura. Verificar contra los archivos reales (docker-compose, pipelines) para el detalle actual. Ver [../../STATUS.md](../../STATUS.md).
userdb=<usuario_db>
passdb=<password_db>
url=jdbc:postgresql://<host>:<puerto>/<nombre_db>

# Configuración Liquibase
logLevel=info
contexts=dev,test,prod
defaultSchema=<schema_principal>
defaultLiquibaseSchema=<schema_batch_metadata>

# Secretos (inyectados por CI/CD desde Key Vault)
encryptKey=<clave_de_encriptacion>
```

### .gitignore

```
gradle.properties
build/
.gradle/
*.class
bk-build.gradle
```

---

## 4. Esquema de Base de Datos

### Schemas recomendados

| Schema | Propósito |
|---|---|
| `batch_metadata` | Tablas de control de Spring Batch (si aplica) |
| `<nombre_dominio>` | Tablas del dominio de negocio |

### Patrón de tabla principal del dominio

```sql
CREATE TABLE <schema>.<nombre_tabla> (
    -- Clave primaria
    <id_campo>          VARCHAR(25) NOT NULL,   -- o UUID, BIGSERIAL según caso

    -- Campos de identificación de negocio
    <campo_negocio_1>   VARCHAR(X)  NOT NULL,
    <campo_negocio_2>   DATE        NOT NULL,

    -- Campos de estado
    status              <estado_enum>   NOT NULL DEFAULT 'pending',

    -- Campos sensibles (encriptados como BYTEA)
    <campo_sensible>    BYTEA,

    -- Campos de auditoría (usar en TODAS las tablas)
    audit_created_at     TIMESTAMPTZ,
    audit_created_user   VARCHAR(100),
    audit_created_app    VARCHAR(100),
    audit_created_device VARCHAR(100),
    audit_updated_at     TIMESTAMPTZ,
    audit_updated_user   VARCHAR(100),
    audit_updated_app    VARCHAR(100),
    audit_updated_device VARCHAR(100),

    CONSTRAINT pk_<nombre_tabla> PRIMARY KEY (<id_campo>)
);
```

### Tipos ENUM recomendados

```sql
-- Estados del ciclo de vida de un registro
CREATE TYPE <schema>.<nombre>_estado_enum AS ENUM (
    'pending',
    'processing',
    'processed',
    'failed'
);

-- Estado de exportación/sincronización
CREATE TYPE <schema>.<nombre>_export_state_enum AS ENUM (
    'pending',
    'exported'
);
```

### Tabla de catálogo/reglas

```sql
CREATE TABLE <schema>.<nombre_reglas> (
    exception_type   VARCHAR(2)   NOT NULL,
    description      VARCHAR(200) NOT NULL,
    suggested_action <action_enum> NOT NULL,
    -- ... campos de regla de negocio ...

    -- Cuentas contables (encriptadas si son sensibles)
    primary_account_debit    BYTEA,
    primary_account_credit   BYTEA,

    -- Campos de auditoría
    audit_created_at  TIMESTAMPTZ,
    audit_updated_at  TIMESTAMPTZ,

    CONSTRAINT pk_<nombre_reglas> PRIMARY KEY (exception_type)
);
```

### Tabla de auditoría de cambios de estado

```sql
CREATE TABLE <schema>.<nombre_status_audit> (
    audit_id        BIGSERIAL PRIMARY KEY,
    -- FK a tabla principal (clave compuesta si aplica)
    <fk_campo>      VARCHAR(25)  NOT NULL,
    -- Campos de auditoría
    old_status      VARCHAR(50),
    new_status      VARCHAR(50),
    multiple_update BOOLEAN     DEFAULT FALSE,
    update_count    INTEGER     DEFAULT 1,
    first_update_at TIMESTAMPTZ DEFAULT NOW(),
    last_update_at  TIMESTAMPTZ DEFAULT NOW(),
    audit_user      VARCHAR(100),
    audit_app       VARCHAR(100),
    audit_device    VARCHAR(100)
);
```

### Tabla de sincronización/exportación

```sql
CREATE TABLE <schema>.sincronizacion_<nombre_tabla> (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    <fk_campo>          VARCHAR(25) NOT NULL REFERENCES <schema>.<tabla_principal>(<id>),
    export_state        <export_enum> NOT NULL DEFAULT 'pending',
    data_to_export      JSONB,
    sequential_number   VARCHAR(25),

    -- Campos de auditoría
    audit_created_at    TIMESTAMPTZ DEFAULT NOW(),
    audit_created_user  VARCHAR(100) DEFAULT 'SYSTEM',
    audit_created_app   VARCHAR(100) DEFAULT 'SYSTEM',
    audit_updated_at    TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 5. Convenciones de Nomenclatura

### Archivos SQL

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Crear schema | `NN_create_schema_<nombre>.sql` | `01_create_schema_batch_metadata.sql` |
| Crear tabla | `NN_create_table_<nombre>.sql` | `03_create_table_batch_job_instance.sql` |
| Crear trigger | `NN_create_trigger_<nombre>.sql` | `11_create_trigger_audit_created.sql` |
| Crear función | `NN_create_function_<nombre>.sql` | `14_create_function_generate_id.sql` |
| Crear índice | `NN_create_indexes_<nombre>.sql` | `16_create_indexes_status_audit.sql` |
| Agregar FK | `NN_add_fk_<tabla>_<referencia>.sql` | `13_add_fk_conciliacion_matriz_reglas.sql` |
| Actualizar | `NN_update_<objeto>_<nombre>.sql` | `01_update_trigger_encrypt_accounts.sql` |
| Insertar datos | `NN_insert_data_<tabla>.sql` | `01_insert_data_ren_matriz_reglas.sql` |

### Objetos de Base de Datos

| Objeto | Patrón | Ejemplo |
|---|---|---|
| Schema | `snake_case` | `batch_metadata`, `reconciliacion` |
| Tabla | `<prefijo>_<nombre_negocio>` | `ren_cuadre_conciliacion` |
| PK Constraint | `pk_<nombre_tabla>` | `pk_ren_cuadre_conciliacion` |
| FK Constraint | `fk_<tabla_origen>_<tabla_destino>` | `fk_cuadre_matriz_reglas` |
| Índice | `idx_<tabla>_<campo(s)>` | `idx_cuadre_status` |
| Trigger | `trg_<accion>_<tabla>` | `trg_audit_created_cuadre` |
| Función trigger | `fn_<accion>()` | `fn_audit_status_change()` |
| Secuencia | `<nombre>_seq` | `batch_job_seq` |
| Tipo ENUM | `<prefijo>_<nombre>_enum` | `ren_estado_enum` |

---

## 6. Liquibase: Changelogs y ChangeSets

### main-changelog.yml

```yaml
databaseChangeLog:
  - include:
      file: 202507_TICKET-001/partial-changelog.yml
      relativeToChangelogFile: true
  - include:
      file: 202603_TICKET-002/partial-changelog.yml
      relativeToChangelogFile: true
```

### partial-changelog.yml (patrón)

```yaml
databaseChangeLog:

  - changeSet:
      id: <TICKET-ID>-1
      author: <iniciales_autor>
      context: dev, test, prod
      changes:
        - sqlFile:
            path: ddl/01_create_schema_<nombre>.sql
            relativeToChangelogFile: true
            splitStatements: false

  - changeSet:
      id: <TICKET-ID>-2
      author: <iniciales_autor>
      context: dev, test, prod
      changes:
        - sqlFile:
            path: ddl/02_create_table_<nombre>.sql
            relativeToChangelogFile: true
            splitStatements: false

  # DML solo para dev y test (no prod si los datos se cargan por otro medio)
  - changeSet:
      id: <TICKET-ID>-14
      author: <iniciales_autor>
      context: dev, test
      changes:
        - sqlFile:
            path: dml/01_insert_data_<nombre>.sql
            relativeToChangelogFile: true
            splitStatements: false
```

**Reglas para ChangeSets:**

- El `id` debe ser único en todo el proyecto. Usar `<TICKET-ID>-<numero>`.
- Nunca modificar un ChangeSet ya ejecutado en producción; crear uno nuevo.
- Usar `context` para diferenciar ambientes (`dev`, `test`, `prod`).
- `splitStatements: false` para scripts con múltiples statements o funciones PL/pgSQL.
- Los archivos SQL se referencian como rutas relativas al YAML.

### Orden recomendado de ChangeSets DDL

```
1. Crear schemas
2. Crear secuencias (si aplica)
3. Crear tipos ENUM
4. Crear tablas sin FK (independientes primero)
5. Crear tablas con FK (dependientes después)
6. Agregar constraints FK (si se definieron por separado)
7. Crear funciones trigger de auditoría
8. Crear triggers de auditoría en cada tabla
9. Crear funciones trigger de encriptación
10. Crear triggers de encriptación
11. Crear funciones trigger de negocio
12. Crear triggers de negocio
13. Crear índices
14. Insertar datos iniciales (DML — contexto dev,test o todos)
```

---

## 7. Seguridad y Encriptación

### Encriptación simétrica con pgcrypto

Para campos sensibles (números de cuenta, datos personales):

```sql
-- El campo en la tabla es BYTEA
<campo_sensible> BYTEA,

-- Trigger BEFORE INSERT y BEFORE UPDATE para encriptar automáticamente
CREATE OR REPLACE FUNCTION fn_encrypt_sensitive_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.<campo_sensible> IS NOT NULL THEN
        -- Si el valor ya es BYTEA (ya encriptado), leerlo como texto primero
        BEGIN
            NEW.<campo_sensible> := pgp_sym_encrypt(
                convert_from(NEW.<campo_sensible>, 'UTF8'),
                '${encryptKey}',
                'cipher-algo=aes256'
            );
        EXCEPTION WHEN OTHERS THEN
            NEW.<campo_sensible> := pgp_sym_encrypt(
                NEW.<campo_sensible>::TEXT,
                '${encryptKey}',
                'cipher-algo=aes256'
            );
        END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_encrypt_<nombre>_before_insert
    BEFORE INSERT ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_encrypt_sensitive_fields();

CREATE TRIGGER trg_encrypt_<nombre>_before_update
    BEFORE UPDATE ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_encrypt_sensitive_fields();
```

**Notas importantes:**
- La clave `${encryptKey}` es un parámetro Liquibase inyectado en tiempo de migración.
- El valor real de la clave se obtiene de Azure Key Vault y nunca se almacena en el repositorio.
- Para prevenir modificación de campos encriptados, usar un trigger adicional que restaure el valor original.

### Protección contra modificación de campos sensibles

```sql
CREATE OR REPLACE FUNCTION fn_prevent_sensitive_modification()
RETURNS TRIGGER AS $$
BEGIN
    -- Restaurar silenciosamente los valores originales
    NEW.<campo_sensible> := OLD.<campo_sensible>;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_<nombre>_modification
    BEFORE UPDATE ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_sensitive_modification();
```

---

## 8. Auditoría y Trazabilidad

### Triggers de auditoría estándar

Aplicar en **todas** las tablas del dominio:

```sql
-- Función para INSERT
CREATE OR REPLACE FUNCTION fn_set_audit_created_fields()
RETURNS TRIGGER AS $$
BEGIN
    NEW.audit_created_at     := NOW();
    NEW.audit_created_user   := COALESCE(NEW.audit_created_user, current_user);
    NEW.audit_created_app    := COALESCE(NEW.audit_created_app, 'SYSTEM');
    NEW.audit_created_device := COALESCE(NEW.audit_created_device, 'SYSTEM');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_created_<tabla>
    BEFORE INSERT ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_set_audit_created_fields();

-- Función para UPDATE
CREATE OR REPLACE FUNCTION fn_set_audit_updated_fields()
RETURNS TRIGGER AS $$
BEGIN
    NEW.audit_updated_at     := NOW();
    NEW.audit_updated_user   := COALESCE(NEW.audit_updated_user, current_user);
    NEW.audit_updated_app    := COALESCE(NEW.audit_updated_app, 'SYSTEM');
    NEW.audit_updated_device := COALESCE(NEW.audit_updated_device, 'SYSTEM');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_updated_<tabla>
    BEFORE UPDATE ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_set_audit_updated_fields();
```

### Trigger de auditoría de cambio de estado

```sql
CREATE OR REPLACE FUNCTION fn_audit_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        -- Verificar si ya existe un registro de auditoría
        IF EXISTS (
            SELECT 1 FROM <schema>.<tabla_audit>
            WHERE <fk_campo> = NEW.<id_campo>
        ) THEN
            UPDATE <schema>.<tabla_audit>
            SET
                new_status      = NEW.status::TEXT,
                multiple_update = (audit_device IS DISTINCT FROM NEW.audit_updated_device),
                update_count    = update_count + 1,
                last_update_at  = NOW()
            WHERE <fk_campo> = NEW.<id_campo>;
        ELSE
            INSERT INTO <schema>.<tabla_audit> (
                <fk_campo>, old_status, new_status,
                audit_user, audit_app, audit_device
            ) VALUES (
                NEW.<id_campo>, OLD.status::TEXT, NEW.status::TEXT,
                NEW.audit_updated_user, NEW.audit_updated_app, NEW.audit_updated_device
            );
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_status_change_<tabla>
    AFTER UPDATE ON <schema>.<tabla>
    FOR EACH ROW EXECUTE FUNCTION fn_audit_status_change();
```

---

## 9. Pipeline CI/CD con Azure DevOps

### azure-pipelines.yml

```yaml
trigger:
  branches:
    include:
      - master
      - develop
      - feature/*
      - hotfix/*
      - release/*
  paths:
    include:
      - db/**
      - build.gradle

pool:
  name: $(AGENT_POOL_LIQUIBASE_GRADLE)

variables:
  # Key Vault groups comunes a todos los ambientes
  - group: liq-cross-<nombre-proyecto>

  # Key Vault según rama
  - ${{ if or(eq(variables['Build.SourceBranch'], 'refs/heads/develop'), startsWith(variables['Build.SourceBranch'], 'refs/heads/feature/')) }}:
    - group: liq-dev-<nombre-proyecto>
  - ${{ if startsWith(variables['Build.SourceBranch'], 'refs/heads/release/') }}:
    - group: liq-test-<nombre-proyecto>
  - ${{ if eq(variables['Build.SourceBranch'], 'refs/heads/master') }}:
    - group: liq-prod-<nombre-proyecto>

stages:
  - template: templates/liquibase-migrate.yml@common-templates
    parameters:
      image: $(IMAGE_LIQUIBASE_GRADLE)
      encryptKey: $(KEY)                   # Variable desde Key Vault
      contexts: $(LIQUIBASE_CONTEXTS)      # Variable desde Key Vault por ambiente
```

### Variable Groups en Azure DevOps (por ambiente)

| Group | Variables contenidas |
|---|---|
| `liq-cross-<proyecto>` | `IMAGE_LIQUIBASE_GRADLE`, `AGENT_POOL_LIQUIBASE_GRADLE` |
| `liq-dev-<proyecto>` | `KEY`, URL BD dev, credenciales BD dev, `LIQUIBASE_CONTEXTS=dev` |
| `liq-test-<proyecto>` | `KEY`, URL BD test, credenciales BD test, `LIQUIBASE_CONTEXTS=test` |
| `liq-prod-<proyecto>` | `KEY`, URL BD prod, credenciales BD prod, `LIQUIBASE_CONTEXTS=prod` |

---

## 10. Variables y Secretos

### Flujo de secretos

```
Azure Key Vault
      │
      ▼
Azure DevOps Variable Groups  (liq-<env>-<proyecto>)
      │
      ▼
azure-pipelines.yml  ($(KEY), $(DB_URL), etc.)
      │
      ▼
Gradle build  (-PencryptKey=$(KEY) -Purl=$(DB_URL) ...)
      │
      ▼
liquibase changeLogParameters  ({'encryptKey': encryptKey})
      │
      ▼
SQL Scripts  (${encryptKey} como placeholder)
```

### Variables requeridas por Gradle

| Variable | Descripción | Fuente |
|---|---|---|
| `userdb` | Usuario de BD | Key Vault → Variable Group |
| `passdb` | Password de BD | Key Vault → Variable Group |
| `url` | JDBC URL de conexión | Key Vault → Variable Group |
| `logLevel` | Nivel de log Liquibase | Variable Group o hardcode `info` |
| `contexts` | Contextos a ejecutar | Variable Group (`dev`, `test`, `prod`) |
| `defaultSchema` | Schema principal de la BD | Variable Group |
| `defaultLiquibaseSchema` | Schema para tablas de control Liquibase | Variable Group |
| `encryptKey` | Clave de encriptación AES256 | Key Vault (secreto) |

---

## 11. Guía para Nuevo Proyecto

### Paso 1: Inicializar el proyecto

```bash
# Crear estructura de directorios
mkdir -p <nombre-proyecto>/db/scripts/<YYYYMM_TICKET-001>/{ddl,dml,logical_diagram}

# Archivos de configuración raíz
touch <nombre-proyecto>/build.gradle
touch <nombre-proyecto>/settings.gradle
touch <nombre-proyecto>/.gitignore
touch <nombre-proyecto>/azure-pipelines.yml
touch <nombre-proyecto>/db/scripts/main-changelog.yml
touch <nombre-proyecto>/db/scripts/<YYYYMM_TICKET-001>/partial-changelog.yml
```

### Paso 2: Adaptar build.gradle

Reemplazar en el `build.gradle`:
- `com.<tu-organizacion>` → nombre del grupo Maven
- Ajustar versión de Liquibase, PostgreSQL driver y Java si es necesario

### Paso 3: Definir los schemas

Crear los primeros archivos DDL:
- `01_create_schema_<schema_batch>.sql`
- `02_create_schema_<schema_dominio>.sql`

### Paso 4: Definir tipos y enums del dominio

Antes de crear tablas, definir los ENUMs que usarán:
- Estados del ciclo de vida
- Tipos de acción
- Estados de exportación

### Paso 5: Crear tablas en orden de dependencia

1. Tablas sin FK primero (catálogos, matrices de reglas)
2. Tablas principales con FK a catálogos
3. Tablas de auditoría con FK a tablas principales
4. Tablas de sincronización/exportación

### Paso 6: Agregar triggers estándar

Para cada tabla del dominio:
1. Trigger `BEFORE INSERT` para `audit_created_*`
2. Trigger `BEFORE UPDATE` para `audit_updated_*`
3. Trigger `BEFORE INSERT/UPDATE` para encriptación (si aplica)
4. Trigger `AFTER UPDATE` para auditoría de cambio de estado (si aplica)

### Paso 7: Crear índices

Agregar índices en campos usados frecuentemente en `WHERE`:
- Campos de estado
- Campos de fecha
- Campos usados en joins

### Paso 8: Datos iniciales (DML)

Para catálogos o matrices de reglas con datos fijos:
- Crear en `dml/` separado de los DDL
- Usar contexto `dev, test` si los datos de producción se cargan por otro proceso
- Usar `dev, test, prod` si los datos son iguales en todos los ambientes

### Paso 9: Configurar partial-changelog.yml

Listar los ChangeSets en el orden correcto (ver sección 6).

### Paso 10: Agregar al main-changelog.yml

```yaml
databaseChangeLog:
  - include:
      file: <YYYYMM_TICKET-001>/partial-changelog.yml
      relativeToChangelogFile: true
```

### Paso 11: Configurar Azure DevOps

1. Crear Variable Groups en Azure DevOps Library (uno por ambiente)
2. Conectar cada Variable Group al Key Vault correspondiente
3. Adaptar `azure-pipelines.yml` con los nombres de los Variable Groups
4. Configurar el pipeline en Azure DevOps apuntando al repositorio

### Para agregar una nueva Historia de Usuario (futuras iteraciones)

```
1. Crear carpeta: db/scripts/<YYYYMM_NUEVO-TICKET>/
2. Crear subcarpetas: ddl/, dml/ (si aplica)
3. Escribir scripts SQL numerados
4. Crear partial-changelog.yml con los nuevos ChangeSets
5. Agregar el include al main-changelog.yml (AL FINAL, nunca intercalado)
6. Los IDs de ChangeSet deben ser únicos: <NUEVO-TICKET>-1, <NUEVO-TICKET>-2, ...
```

---

## Diagrama de Flujo de una Migración

```
Developer hace push
        │
        ▼
Azure DevOps detecta cambios en db/ o build.gradle
        │
        ▼
Pipeline determina ambiente por rama:
  feature/* → dev
  release/* → test
  master    → prod
        │
        ▼
Lee secretos de Azure Key Vault (KEY, DB credentials)
        │
        ▼
Ejecuta: gradle update
    -PencryptKey=$(KEY)
    -Purl=$(DB_URL)
    -Pcontexts=$(ENV)
        │
        ▼
Liquibase compara DATABASECHANGELOG vs changelogs
        │
        ├─ Si ya fue ejecutado: SKIP
        │
        └─ Si es nuevo:
              │
              ▼
           Ejecuta SQL (con ${encryptKey} reemplazado)
              │
              ▼
           Registra en DATABASECHANGELOG
              │
              ▼
           Migración exitosa ✓
```

---

*Plantilla generada el 18/05/2026 — Proyecto de referencia: `tdd-dba-reconciliacion`*
