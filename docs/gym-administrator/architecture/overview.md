# Gym Administrator — Documento de Proyecto

> **ESTADO:** 🟡 Arquitectura y modelo de negocio. Mezcla lo implementado con lo planeado; para detalle de implementación verificar contra el código. Ver [../../STATUS.md](../../STATUS.md).

> **Estado actual:** Diseño de base de datos completado · Migraciones Liquibase implementadas · Aplicación en desarrollo
> **Versión:** 1.0.0 · **Fecha:** Mayo 2026

---

## Tabla de Contenidos

1. [Qué es este proyecto](#1-qué-es-este-proyecto)
2. [Qué hace actualmente](#2-qué-hace-actualmente)
3. [Qué pretende hacer](#3-qué-pretende-hacer)
4. [Arquitectura técnica](#4-arquitectura-técnica)
5. [Módulos funcionales](#5-módulos-funcionales)
6. [Base de datos — Organización por esquemas](#6-base-de-datos--organización-por-esquemas)
7. [Reglas de negocio implementadas](#7-reglas-de-negocio-implementadas)
8. [Flujo general del sistema](#8-flujo-general-del-sistema)
9. [Pipeline CI/CD](#9-pipeline-cicd)
10. [Resultados esperados](#10-resultados-esperados)

---

## 1. Qué es este proyecto

**Gym Administrator** es una plataforma SaaS (Software as a Service) de gestión integral para gimnasios, diseñada con arquitectura **multicompañía y multisucursal**. Permite que múltiples gimnasios independientes contraten el servicio y administren sus operaciones desde un único sistema, con datos completamente aislados entre compañías.

### Modelo de negocio

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PLATAFORMA SAAS                                  │
│                  (Operador del sistema)                              │
│                                                                      │
│   Planes: Básico · Premium · Enterprise                              │
│   Características habilitadas por plan                               │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ suscripción mensual
          ┌────────────────┼─────────────────────┐
          ▼                ▼                     ▼
    ┌───────────┐   ┌───────────┐        ┌───────────┐
    │ Gimnasio A│   │ Gimnasio B│  ...   │ Gimnasio N│
    │ (tenant)  │   │ (tenant)  │        │ (tenant)  │
    └─────┬─────┘   └─────┬─────┘        └───────────┘
          │               │
    ┌─────┴──────┐   ┌────┴───────┐
    │ Quito      │   │ Sede única │
    │ Ibarra     │   └────────────┘
    │ Otavalo    │
    └────────────┘
```

Cada gimnasio contratante es un **tenant** independiente. Sus datos nunca se mezclan con los de otros gimnasios. La plataforma los distingue mediante `id_compania` presente en todas las tablas operativas.

---

## 2. Qué hace actualmente

### Capa de base de datos (implementada)

El proyecto tiene completamente diseñada e implementada la capa de persistencia:

- **69 tablas** organizadas en **12 esquemas PostgreSQL** (saas, identidad, tenant, core, asistencia, config, seguridad, finanzas, marketing, inventario, sri, facturacion)
- **Migraciones Liquibase** versionadas, consolidadas en una única story `202605_GYM-001` (subcarpetas `ddl/`, `ddl-facturacion/`, `ddl-freemium/`) y ejecutables en múltiples ambientes
- **Pipeline CI/CD** en Azure DevOps para despliegue automático
- **Validaciones a nivel de motor** (constraints, checks, unique) que garantizan integridad sin depender de la capa de aplicación

#### Lo que la base de datos ya soporta:

| Capacidad | Cómo está implementada |
|---|---|
| Múltiples gimnasios aislados | `id_compania` en todas las tablas tenant, sin FK |
| Múltiples sucursales por gym | `tenant.sucursales` con FK a `tenant.companias` y `qr_token` por puerta |
| Planes SaaS con características | `saas.planes` → `saas.plan_caracteristicas` → `saas.caracteristicas` |
| Ciclo de vida de suscripción | `tenant.compania_planes.estado` con 6 estados posibles |
| Período de gracia al no pagar | `tenant.compania_planes.dias_gracia` + estado `en_gracia` |
| Notificaciones de vencimiento configurables | `tenant.config_notif_suscripcion` (PK compuesta por umbral) |
| Upgrade / downgrade de plan | Filas inmutables con `tipo_cambio` + self-FK `id_compania_plan_orig` |
| Identidad global de personas | `identidad.personas` único por CI — compartido entre gyms |
| App móvil para clientes | `identidad.usuarios_app` con `id_persona + id_compania` |
| Membresías de todo tipo | `core.tipos_membresia` configurable: días / semanas / meses / años |
| Congelamientos con retroactivo | `core.congelamientos` + `CONSTRAINT chk_retroactivo` a nivel de motor |
| Acceso entre sucursales | `asistencia.asistencias.id_sucursal` libre o restringido por `gym_config` |
| Inventario y ventas | 7 tablas en esquema `inventario` con audit log completo |
| Roles y permisos por rol | `seguridad.roles` → `seguridad.rol_permisos` → `seguridad.permisos` |
| Bitácora de acciones | `seguridad.bitacora_accesos` con snapshot JSONB antes/después |
| Configuración por sucursal | `config.gym_config` clave-valor genérico (PK compuesta) |

---

## 3. Qué pretende hacer

### Visión completa del producto

El sistema apunta a ser la herramienta principal de operación de un gimnasio: desde que un cliente entra por primera vez hasta que decide no renovar, todo el ciclo está automatizado, medido y notificado.

#### Panel web administrativo (en desarrollo)

Interfaz para el personal del gimnasio con:
- Dashboard en tiempo real con KPIs, alertas y gráficas
- Gestión completa de clientes y membresías
- Registro de asistencia manual o por QR
- Módulo de finanzas con reportes de ingresos vs egresos
- Control de inventario y punto de venta
- Configuración de todos los parámetros del sistema

#### App móvil para clientes (planificada)

Los clientes del gimnasio podrán:
- Acceder con credenciales provistas por su gimnasio
- Ver su membresía activa, fecha de vencimiento y días restantes
- Ver su historial de asistencia
- Mostrar su QR para el registro de entrada
- Recibir notificaciones sobre su membresía

#### Automatizaciones planeadas

| Disparador | Acción automática |
|---|---|
| 3 días antes del vencimiento de membresía | WhatsApp + notificación app |
| Día de vencimiento | WhatsApp al cliente |
| 2 días sin asistir | Mensaje motivacional por WhatsApp |
| 5 días sin asistir | Mensaje de recuperación |
| 10 días sin asistir | Alerta al admin → llamada |
| 15 días sin asistir | Promoción especial para el siguiente mes |
| 1 mes sin faltas | Descuento automático 10% próxima membresía |
| 3 meses sin faltas | Sesión con nutricionista (1 mes) |
| 6 meses sin faltas | Trofeo + foto de progreso |
| Stock por debajo del mínimo | Alerta al admin |
| Suscripción SaaS próxima a vencer | WhatsApp + email al dueño del gym |

---

## 4. Arquitectura técnica

### Stack tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Base de datos | PostgreSQL | 14+ |
| Gestión de migraciones | Liquibase | 4.25.0 |
| Sistema de construcción | Gradle | 8.x |
| JVM | Java | 17 |
| Driver JDBC | PostgreSQL JDBC | 42.6.0 |
| CI/CD | Azure DevOps Pipelines | — |
| Gestión de secretos | Azure Key Vault | — |
| Backend (planificado) | Spring Boot / Node.js | — |
| Frontend (planificado) | React / Next.js | — |
| App móvil (planificada) | React Native / Flutter | — |

### Estructura del repositorio

```
gym-administrator/
├── build.gradle                         # Gradle + plugin Liquibase
├── settings.gradle
├── gradle.properties                    # Conexión BD (NO en git)
├── azure-pipelines.yml                  # Pipeline CI/CD
├── DATABASE_SCHEMA.md                   # Diseño completo con diagramas ASCII
├── PROYECTO.md                          # Definición funcional de módulos
├── PROYECTO-TEMPLATE.md                 # Plantilla técnica del proyecto DB
├── OVERVIEW.md                          # Este documento
└── db/
    └── scripts/
        ├── main-changelog.yml           # Changelog maestro (solo incluye la story)
        └── 202605_GYM-001/              # Story consolidada (única baseline)
            ├── partial-changelog.yml    # 96 changesets ordenados por dependencia
            ├── logical_diagram/
            │   └── schema.dbml
            ├── ddl/                     # 65 scripts (10 schemas base + 46 tablas + índices + seeds)
            ├── ddl-facturacion/         # 28 scripts (schemas sri + facturacion, 23 tablas)
            └── ddl-freemium/            # 3 scripts (REQ-SAAS-001: 2 tablas + seed)
```

### Principios de arquitectura

**Multitenancy por columna:** No hay bases de datos separadas por cliente. El aislamiento se logra mediante `id_compania` presente en todas las tablas operativas. El filtrado es responsabilidad de la capa de aplicación y el middleware de autenticación.

**Tablas globales vs. tablas tenant:**
- `saas.*` e `identidad.*` son globales — no tienen `id_compania`
- Todas las demás tablas son tenant — llevan `id_compania` + `id_sucursal`

**Inmutabilidad del historial:** Nunca se hace UPDATE para cambiar el estado de una suscripción o membresía cancelada. Se inserta una nueva fila, conservando el historial completo.

**Validación en capas:** Las reglas de negocio críticas (ej: congelamiento retroactivo requiere documento) se validan tanto en la app como a nivel de motor mediante `CONSTRAINT CHECK`, garantizando integridad aun si la app falla.

---

## 5. Módulos funcionales

### Plan Básico

#### Dashboard
Pantalla principal del administrador con:
- Contador de clientes activos, próximos a vencer, vencidos y en riesgo
- Ingresos del mes actual
- Asistencia del día de hoy
- Alertas y notificaciones pendientes
- Acciones rápidas: Ver · Editar · Mensaje · Historial

#### Clientes
Gestión completa del ciclo de vida de un cliente:
- Registro con datos personales (CI, nombre, teléfono, correo, foto)
- Medidas físicas (peso, altura) y objetivos
- Lesiones registradas
- Estado del cliente: `activo` · `proximo_vencer` · `vencido` · `congelado` · `riesgo_abandono`
- Historial de asistencia, faltas y promociones

#### Membresías
Control del acceso al gimnasio con dos modelos de cobro:

**Modelo Calendario** (mensual, semanal, N días):
- El cliente paga por un período de tiempo fijo
- Viene o no viene — la fecha de vencimiento no cambia
- La asistencia se registra para seguimiento y beneficios, pero no es obligatoria para ingresar

**Modelo Tarjeta de Accesos** (N entradas configurables, ej: 22 días):
- El cliente compra un número determinado de entradas
- Cada vez que escanea el QR consume un acceso
- Sin registro de entrada = no descuenta ningún día
- Tiene un plazo máximo de vencimiento (ej: usar 22 días dentro de 3 meses)
- Alerta automática cuando quedan pocos accesos

Acciones disponibles para ambos modelos: Nueva · Renovar · Anular · Congelar
- Congelamiento con pausa real del tiempo (días compensados al reactivar)
- Congelamiento retroactivo con certificado médico + aprobación de admin

#### Asistencia y Seguimiento
- El cliente escanea el QR del gimnasio (puerta de entrada) con su app móvil
- El QR pertenece a la sucursal y rota periódicamente para evitar suplantación
- Registro manual por el recepcionista como respaldo
- Soporte futuro para biometría (huella digital, reconocimiento facial, iris)
- Control de 30 días de asistencia por membresía
- Mensajes automáticos por WhatsApp en distintos intervalos
- 10 mensajes motivacionales aleatorios
- Escalado progresivo: 2d → 5d → 10d → 15d

### Plan Premium (adicional al básico)

#### Finanzas
- Ingresos: membresías, ventas, entrenamiento personalizado, otros
- Egresos: sueldos, servicios, insumos, otros
- Gráficas de ingresos vs egresos por período
- Proyección de ingresos del mes siguiente

#### Promociones y Beneficios
- Promo 2x1: dos personas con un precio
- Beneficios automáticos por asistencia perfecta (1, 3, 6 meses)
- Seguimiento de promociones activas por cliente
- Configuración de condiciones y reglas por promo

#### Inventario y Ventas
- Catálogo de productos (suplementos, implementos, accesorios)
- Control de stock por sucursal con alertas de mínimo
- Registro de proveedores
- Punto de venta con detalle de líneas
- Integración automática con módulo de finanzas
- Historial completo de movimientos (entradas, ventas, ajustes, devoluciones)

### Siempre disponibles

#### Usuarios y Permisos
La plataforma maneja **tres niveles de acceso** independientes:

| Nivel | Tabla | Scope |
|---|---|---|
| `super_admin` / `soporte` / `viewer` | `saas.usuarios_plataforma` | Toda la plataforma — sin límite de compañía |
| `admin_compania` / roles personalizados | `seguridad.usuarios` + `seguridad.roles` | Solo su compañía y sucursales |
| `cliente` | `identidad.usuarios_app` | Solo sus propios datos en la app móvil |

Roles predefinidos por compañía (personalizables):
| Rol | Restricciones |
|---|---|
| Dueño | Acceso total dentro de su compañía |
| Recepción | No puede borrar pagos |
| Entrenador | No ve el módulo de finanzas |
| Contador | Solo acceso a reportes |
| Personalizado | El dueño define los permisos |

Bitácora de todas las acciones: quién hizo qué, cuándo y en qué módulo.

#### Configuración del Sistema
- Datos del gimnasio (nombre, logo, teléfono, WhatsApp)
- Configuración de mensajes automáticos
- Métodos de pago aceptados
- Reglas de asistencia y recuperación
- Parámetros de congelamiento
- Umbrales de alertas de suscripción SaaS

---

## 6. Base de datos — Organización por esquemas

### Los 12 esquemas PostgreSQL

```
┌─────────────────────────────────────────────────────────────────────┐
│  saas (6 tablas)                                                     │
│  planes · caracteristicas · plan_caracteristicas ·                  │
│  usuarios_plataforma · actividad_plataforma · config_plataforma     │
│  → Catálogo global. Sin id_compania. Gestionado por el operador.    │
├─────────────────────────────────────────────────────────────────────┤
│  identidad (3 tablas)                                                │
│  personas · usuarios_app · biometria                                 │
│  → Identidad global. CI único por persona. Una credencial por gym.  │
├─────────────────────────────────────────────────────────────────────┤
│  tenant (7 tablas)                                                   │
│  companias · sucursales · compania_planes · pagos_suscripcion ·     │
│  config_notif_suscripcion · notificaciones_suscripcion ·            │
│  pagos_pendientes_validacion                                         │
│  → Ciclo de vida SaaS. Quién contrata, qué plan, cuánto debe.       │
├─────────────────────────────────────────────────────────────────────┤
│  core (4 tablas)                                                     │
│  clientes · tipos_membresia · membresias · congelamientos           │
│  → Operaciones principales. Todo lo que hace el gym día a día.      │
├─────────────────────────────────────────────────────────────────────┤
│  asistencia (3 tablas)                                               │
│  asistencias · plantillas_mensajes · mensajes_log                   │
│  → Registro de entradas y automatización de comunicación.           │
├─────────────────────────────────────────────────────────────────────┤
│  finanzas (4 tablas)                                [Plan Premium]   │
│  categorias_ingreso · ingresos · categorias_egreso · egresos        │
│  → Control financiero. Conectado a membresías y ventas.             │
├─────────────────────────────────────────────────────────────────────┤
│  marketing (4 tablas)                               [Plan Premium]   │
│  promociones · cliente_promociones · reglas_beneficios ·           │
│  cliente_beneficios                                                  │
│  → Fidelización y retención de clientes.                            │
├─────────────────────────────────────────────────────────────────────┤
│  seguridad (6 tablas)                                                │
│  roles · permisos · rol_permisos · usuarios · bitacora_accesos ·    │
│  refresh_tokens                                                      │
│  → Control de acceso y trazabilidad completa.                       │
├─────────────────────────────────────────────────────────────────────┤
│  config (2 tablas)                                                   │
│  gym_config · metodos_pago                                           │
│  → Parámetros operativos. Clave-valor por sucursal.                 │
├─────────────────────────────────────────────────────────────────────┤
│  inventario (7 tablas)                              [Plan Premium]   │
│  categorias_producto · proveedores · productos · stock ·            │
│  ventas · detalle_ventas · movimientos_inventario                   │
│  → Stock, punto de venta y trazabilidad de movimientos.             │
├─────────────────────────────────────────────────────────────────────┤
│  sri (6 tablas)                             [billing — sin servicio] │
│  tipos_comprobante · tipos_identificacion_comprador · formas_pago · │
│  tipos_impuesto · tarifas_iva · motivos_anulacion_nc                │
│  → Catálogos oficiales SRI Ecuador. Solo lectura, precargados.      │
├─────────────────────────────────────────────────────────────────────┤
│  facturacion (17 tablas)                    [billing — sin servicio] │
│  config_sri · certificados · puntos_emision · secuenciales ·        │
│  comprobantes (+ detalle · detalle_impuestos · impuestos_totales ·  │
│  pagos · info_adicional · nc_referencias) ·                          │
│  envios_sri · cola_envio · notificaciones_receptor ·                │
│  anulaciones · reportes_ats                                          │
│  → Facturación electrónica SRI completa: emisión, envío, anulación. │
└─────────────────────────────────────────────────────────────────────┘
                              Total: 69 tablas
```

### Cómo se controla el acceso a módulos

```
Request del usuario
       │
       ▼
Middleware de autenticación
       │
       ├─ Lee tenant.compania_planes WHERE estado IN ('activo','en_gracia')
       │
       ├─ JOIN saas.plan_caracteristicas → saas.caracteristicas
       │
       └─ caracteristicas.codigo == módulo_solicitado
              │
              ├─ Sí → acceso permitido
              └─ No → HTTP 403 — plan no incluye este módulo
```

---

## 7. Reglas de negocio implementadas

### Ciclo de vida de la suscripción SaaS

```
nuevo ──> activo ──(vence fecha_fin)──> en_gracia ──(vence gracia)──> vencido
                │                                                          │
                ├──(pago)──────────────────────────────────────────> activo (nueva fila)
                │
                ├──(upgrade)──> cancelado + nueva fila activa (tipo=upgrade)
                │
                └──(downgrade)──> nueva fila programado (activa en fecha_inicio)
```

| Estado | Acceso a módulos | Qué ve el usuario |
|---|---|---|
| `activo` | Completo según plan | Normal |
| `en_gracia` | Completo según plan | Banner: "Renueva en X días" |
| `vencido` | Ninguno | Pantalla de renovación |
| `programado` | Ninguno (plan futuro) | Invisible hasta su `fecha_inicio` |
| `cancelado` | Ninguno | Reemplazado por upgrade |
| `suspendido` | Ninguno | Contactar soporte |

### Ciclo de vida de membresías de clientes

- **Nueva:** Cliente compra tipo de membresía → INSERT en `core.membresias`
- **Activa:** `fecha_inicio <= HOY <= fecha_fin` y `estado = 'activa'`
- **Congelada:** `core.congelamientos` pausa el tiempo — días recuperados al reactivar
- **Congelamiento retroactivo:** Requiere `documento_respaldo` + `aprobado_por` — validado por `CONSTRAINT chk_retroactivo` en el motor de base de datos
- **Vencida:** `fecha_fin < HOY` → el estado del cliente escala a `riesgo_abandono`
- **Renovada:** Nueva fila en `core.membresias`, la anterior queda como historial

### Estados del cliente y su origen

| Estado | Condición |
|---|---|
| `activo` | Membresía activa sin alertas |
| `proximo_vencer` | `fecha_fin - HOY <= 3 días` |
| `vencido` | `fecha_fin < HOY` |
| `congelado` | Congelamiento activo en `core.congelamientos` |
| `riesgo_abandono` | Sin asistir 15+ días o membresía vencida sin renovar |

### Acceso entre sucursales

Por defecto un cliente puede entrar a cualquier sucursal del mismo gimnasio. La query de validación filtra por `id_compania`, no por `id_sucursal`, lo que permite la movilidad entre sedes de forma natural.

Comportamiento configurable mediante `gym_config`:
- `acceso_solo_sucursal_propia = false` (default) → libre entre sedes de la misma compañía
- `acceso_solo_sucursal_propia = true` → solo puede entrar en su sucursal de registro

La barrera entre compañías es automática: un cliente simplemente no tiene membresía en otra compañía, por lo que la query retorna cero filas y el acceso es denegado sin lógica adicional.

---

## 8. Flujo general del sistema

### Registro de un gimnasio nuevo

```
1. Operador registra el gym en tenant.companias
2. App crea automáticamente "Sede Principal" en tenant.sucursales
3. Gym selecciona un plan → INSERT en tenant.compania_planes (tipo='nuevo')
4. Gym configura parámetros en config.gym_config
5. Gym crea tipos de membresía en core.tipos_membresia
6. Sistema queda listo para operar
```

### Ciclo de vida de un cliente

```
1. Recepcionista busca CI del cliente en identidad.personas
   → No existe: INSERT personas (datos personales)
   → Existe: reutiliza el registro (persona ya en otro gym)

2. INSERT core.clientes (datos gym-específicos: peso, objetivos, etc.)

3. INSERT identidad.usuarios_app (credenciales app móvil, si el plan lo incluye)

4. Cliente elige plan → INSERT core.membresias
   → INSERT finanzas.ingresos con id_membresia

5. Cliente escanea QR al entrar → INSERT asistencia.asistencias

6. Job diario monitorea:
   - Membresías próximas a vencer → mensajes WhatsApp
   - Clientes sin asistir → escalado de mensajes
   - Beneficios por asistencia perfecta → INSERT marketing.cliente_beneficios

7. Cliente no renueva → estado evoluciona a riesgo_abandono → campaña de recuperación
```

### Flujo de una venta de inventario

```
Cajero registra venta
       │
       ├─ INSERT inventario.ventas (cabecera)
       ├─ INSERT inventario.detalle_ventas (líneas)
       ├─ UPDATE inventario.stock (reduce stock_actual)
       ├─ INSERT inventario.movimientos_inventario (tipo='venta', audit log)
       └─ INSERT finanzas.ingresos (id_venta enlazado)
```

---

## 9. Pipeline CI/CD

Las migraciones de base de datos se despliegan automáticamente mediante Azure DevOps:

```
Git Push (rama feature/*, develop, release/*, master)
       │
       ▼
Azure DevOps detecta cambios en db/ o build.gradle
       │
       ▼
Determina ambiente por rama:
  feature/* o develop → liq-dev-gym-administrator  (PostgreSQL DEV)
  release/*           → liq-test-gym-administrator (PostgreSQL TEST)
  master              → liq-prod-gym-administrator (PostgreSQL PROD)
       │
       ▼
Lee credenciales desde Azure Key Vault
       │
       ▼
Ejecuta: gradle update (Liquibase)
       │
       ▼
Liquibase compara DATABASECHANGELOG vs scripts nuevos
       │
       ├─ Script ya ejecutado → SKIP (idempotente)
       └─ Script nuevo → ejecuta SQL → registra en DATABASECHANGELOG
```

### Agregar una nueva historia de usuario a la BD

```
1. Crear carpeta: db/scripts/YYYYMM_GYM-XXX/
2. Agregar scripts DDL numerados en ddl/
3. Agregar datos de prueba en dml/ (si aplica)
4. Crear partial-changelog.yml con los ChangeSets
5. Agregar include al main-changelog.yml (siempre AL FINAL)
6. IDs únicos por ChangeSet: GYM-XXX-1, GYM-XXX-2, ...
7. Push → Pipeline se ejecuta automáticamente
```

---

## 10. Resultados esperados

### Para el dueño del gimnasio

| Problema actual | Solución con Gym Administrator |
|---|---|
| Registro manual en cuadernos o Excel | Base de datos centralizada con todo automatizado |
| No saber cuántos clientes están por vencer | Dashboard con alertas en tiempo real |
| Clientes que abandonan sin aviso previo | Sistema de recuperación automático por WhatsApp |
| No saber cuánto se gana y gasta | Módulo de finanzas con gráficas y reportes |
| Dificultad para premiar a clientes fieles | Beneficios automáticos por asistencia |
| Descontrol de inventario | Control de stock con alertas de mínimo |
| Empleados con acceso a todo | Roles y permisos configurables |
| Sin evidencia de quién hizo qué | Bitácora completa de acciones |

### Métricas objetivo del negocio

- **Reducción del abandono:** El sistema automatiza el seguimiento y la recuperación, apuntando a reducir la tasa de abandono en al menos 30%
- **Aumento de retención:** Los beneficios automáticos por fidelidad motivan a los clientes a no faltar
- **Incremento de ingresos:** La visibilidad de finanzas + el módulo de ventas permite identificar oportunidades
- **Ahorro de tiempo administrativo:** La automatización de mensajes y el seguimiento eliminan tareas manuales repetitivas
- **Decisiones basadas en datos:** Los reportes y estadísticas reemplazan las intuiciones con números reales

---

## Documentos relacionados

| Archivo | Contenido |
|---|---|
| [database-schema.md](database-schema.md) | Diagramas ASCII, definición de tablas y casos de negocio |
| [../infra/liquibase-azure-template.md](../infra/liquibase-azure-template.md) | Guía técnica de Liquibase + Azure DevOps |
| [../../../gym-administrator/db/scripts/](../../../gym-administrator/db/scripts/) | Migraciones versionadas por historia de usuario |

---

*Gym Administrator — Plataforma SaaS de gestión de gimnasios · v1.0.0 · Mayo 2026*
