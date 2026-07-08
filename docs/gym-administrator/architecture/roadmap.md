# Roadmap de Desarrollo — Gym Administrator

> **ESTADO:** 🟡 Arquitectura y modelo de negocio. Mezcla lo implementado con lo planeado; para detalle de implementación verificar contra el código. Ver [../../STATUS.md](../../STATUS.md).

> **Proyecto:** Gym Administrator — Plataforma SaaS de gestión de gimnasios  
> **Base de datos:** Completada (42 tablas, 10 esquemas PostgreSQL, 59 scripts Liquibase)  
> **Total de servicios:** 7 microservicios  
> **Mayo 2026**

---

## Orden de implementación

Los servicios deben desarrollarse en este orden. Cada uno depende de los anteriores — no se puede construir el siguiente sin tener operativo el que lo precede.

---

### 1. Auth Service
**Documento:** [auth-service.md](../specs/auth-service.md)  
**Esquemas BD:** `saas` · `identidad` · `seguridad`  
**Tablas:** 9  
**Plan requerido:** Todos los planes (es la base)

Gestiona los tres niveles de usuario de la plataforma: el operador SaaS (super_admin), el personal del gimnasio (staff con RBAC) y los clientes con app móvil. Emite y valida todos los JWT que los demás servicios consumen. **Ningún otro servicio puede funcionar sin este.**

**Entregables clave:**
- Login para los tres tipos de usuario con JWT diferenciado
- CRUD de roles y permisos por compañía
- Bitácora de todas las acciones de escritura

---

### 2. Platform Service
**Documento:** [platform-service.md](../specs/platform-service.md)  
**Esquemas BD:** `saas` · `tenant`  
**Tablas:** 9  
**Plan requerido:** Todos los planes (es la capa de onboarding)

Gestiona el ciclo de vida de cada gimnasio como cliente de la plataforma: registro, asignación de plan, upgrades, downgrades y vencimientos. Expone el endpoint `/modulos/check` que **todos los demás servicios llaman en cada request** para verificar que el gym tiene plan activo con el módulo solicitado habilitado.

**Entregables clave:**
- Registrar un gym nuevo (crea compañía + plan + sucursal principal + QR automáticamente)
- Gestión del ciclo activo → en_gracia → vencido
- Endpoint `/modulos/check` con caché (consumido por todos los servicios)
- Job diario de transición de estados y notificaciones de vencimiento SaaS

---

### 3. Core Service
**Documento:** [core-service.md](../specs/core-service.md)  
**Esquemas BD:** `core`  
**Tablas:** 4  
**Plan requerido:** Plan Básico y superiores

El corazón operativo del gimnasio: registra clientes, vende membresías y gestiona congelamientos. Soporta dos modelos de membresía (`calendario` por fecha y `accesos` por entradas consumidas). Expone el endpoint `/membresias/validar-acceso` que el Attendance Service consume en cada escaneo de QR.

**Entregables clave:**
- Registro de clientes (reutiliza persona global de `identidad.personas` si ya existe)
- Venta de membresía con cálculo automático de `fecha_fin` y copia de `dias_acceso_total`
- Congelamiento con compensación de días al reactivar
- Endpoint `/membresias/validar-acceso` (consumido por Attendance Service)
- Job diario de actualización de estados del cliente (activo → proximo_vencer → vencido → riesgo_abandono)

---

### 4. Attendance Service
**Documento:** [attendance-service.md](../specs/attendance-service.md)  
**Esquemas BD:** `asistencia`  
**Tablas:** 3  
**Plan requerido:** Plan Básico y superiores

Registra las entradas al gimnasio por tres métodos (QR del cliente, manual por recepcionista, biométrico futuro) y ejecuta la mensajería automática por WhatsApp según patrones de ausencia. La restricción `UNIQUE(id_membresia, fecha)` en BD impide consumir dos accesos el mismo día en el modo de tarjeta de accesos.

**Entregables clave:**
- Endpoint QR: valida el token de la sucursal + llama Core Service + registra asistencia
- Registro manual con opción de override para rol Dueño
- CRUD de plantillas de mensajes por tipo (múltiples por tipo → el job elige al azar)
- Job diario de mensajería: escalada 2d → 5d → 10d → 15d + avisos de vencimiento
- Endpoint `/clientes/{id}/asistencias/racha-perfecta` (consumido por Marketing Service)

---

### 5. Finance Service
**Documento:** [finance-service.md](../specs/finance-service.md)  
**Esquemas BD:** `finanzas`  
**Tablas:** 4  
**Plan requerido:** Plan Premium

Libro mayor del gimnasio: registra ingresos y egresos categorizados. Los ingresos por membresías llegan automáticamente desde Core Service y los de ventas desde Inventory Service. Genera reportes de rentabilidad por período y proyección del mes siguiente.

**Entregables clave:**
- Endpoint POST /ingresos (recibe llamadas de Core e Inventory Service)
- CRUD de categorías de ingreso y egreso
- Registro manual de egresos (sueldos, servicios, insumos)
- Reportes: resumen período, evolución mensual, proyección

---

### 6. Marketing Service
**Documento:** [marketing-service.md](../specs/marketing-service.md)  
**Esquemas BD:** `marketing`  
**Tablas:** 4  
**Plan requerido:** Plan Premium

Gestiona dos mecanismos de retención: promociones comerciales (2x1, descuentos por porcentaje) y beneficios automáticos por fidelidad (descuento al 1 mes, sesión con nutricionista al 3er mes, trofeo al 6to mes). El job diario consulta el Attendance Service para evaluar rachas de asistencia perfecta.

**Entregables clave:**
- CRUD de promociones (2x1, porcentaje, servicio extra, regalo)
- CRUD de reglas de beneficios por meses sin faltas
- Job diario: consulta rachas en Attendance Service → otorga beneficios pendientes
- Endpoints GET consumidos por Core Service al vender membresía para aplicar descuentos

---

### 7. Inventory Service
**Documento:** [inventory-service.md](../specs/inventory-service.md)  
**Esquemas BD:** `inventario`  
**Tablas:** 7  
**Plan requerido:** Plan Premium

Punto de venta y control de stock: desde el catálogo de productos hasta la trazabilidad completa de cada movimiento. Cada venta actualiza el stock, genera un movimiento de auditoría y llama a Finance Service para registrar el ingreso. `subtotal` en detalle_ventas es calculado por la BD (`GENERATED ALWAYS AS`).

**Entregables clave:**
- CRUD de categorías, proveedores y productos (con inicialización automática de stock en 0)
- Registro de entradas de mercadería desde proveedor
- Punto de venta multi-línea con validación de stock y cálculo de total
- Alertas automáticas de stock mínimo
- Historial de movimientos inmutable (entrada, venta, ajuste, devolución)
- Llamada fire-and-forget a Finance Service al completar una venta

---

## Mapa de dependencias entre servicios

```
                    ┌─────────────────────────────────────────┐
                    │         auth-service                     │
                    │  JWT para todos · Personas globales      │
                    └────────────────┬────────────────────────┘
                                     │ JWT válido
                    ┌────────────────▼────────────────────────┐
                    │       platform-service                   │
                    │  /modulos/check (consumido por todos)    │
                    └──┬──────┬──────┬──────┬────────────────┘
                       │      │      │      │ módulo habilitado
              ┌────────▼──┐   │      │   ┌──▼─────────┐
              │   core    │   │      │   │  finance   │
              │  service  │   │      │   │  service   │
              └─────┬─────┘   │      │   └────────────┘
                    │         │      │         ▲
         validar    │    ┌────▼──┐   │    ingreso
         acceso     │    │attend.│   │    por venta
                    │    │service│   │         │
              ┌─────▼──┐ └───┬───┘   │   ┌────┴───────┐
              │asistenc│     │racha   │   │ inventory  │
              │(mismo) │     │        │   │  service   │
              └────────┘     │        │   └────────────┘
                        ┌────▼──────┐ │
                        │ marketing │ │
                        │  service  │◄┘
                        └───────────┘
```

---

## Resumen de documentos

| # | Servicio | Documento | Esquemas | Tablas | Plan |
|---|---|---|---|---|---|
| 1 | Auth Service | [auth-service.md](../specs/auth-service.md) | `saas` · `identidad` · `seguridad` | 9 | Todos |
| 2 | Platform Service | [platform-service.md](../specs/platform-service.md) | `saas` · `tenant` | 9 | Todos |
| 3 | Core Service | [core-service.md](../specs/core-service.md) | `core` | 4 | Básico+ |
| 4 | Attendance Service | [attendance-service.md](../specs/attendance-service.md) | `asistencia` | 3 | Básico+ |
| 5 | Finance Service | [finance-service.md](../specs/finance-service.md) | `finanzas` | 4 | Premium |
| 6 | Marketing Service | [marketing-service.md](../specs/marketing-service.md) | `marketing` | 4 | Premium |
| 7 | Inventory Service | [inventory-service.md](../specs/inventory-service.md) | `inventario` | 7 | Premium |

**Total: 40 tablas cubiertas** (las 2 restantes — `config.gym_config` y `config.metodos_pago` — son tablas de configuración que Platform Service y Core Service leen pero no gestionan; se exponen como parte del módulo de configuración del gym en una iteración posterior.)

---

## Documentos de referencia del proyecto

| Documento | Contenido |
|---|---|
| [overview.md](overview.md) | Visión general del producto, arquitectura y módulos |
| [database-schema.md](database-schema.md) | Diagramas de entidades y casos de negocio |
| [../infra/liquibase-azure-template.md](../infra/liquibase-azure-template.md) | Guía de Liquibase + Azure DevOps |
| [../../../gym-administrator/db/scripts/202605_GYM-001/](../../../gym-administrator/db/scripts/202605_GYM-001/) | 59 scripts DDL versionados con Liquibase |

---

*Gym Administrator — Development Roadmap v1.0 · Mayo 2026*
