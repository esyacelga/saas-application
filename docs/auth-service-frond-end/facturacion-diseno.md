# Especificación de Diseño — Módulo de Facturación Electrónica SRI Ecuador

> **ESTADO:** 📋 **Planeado — sin implementar.** Es una guía de construcción, **no** describe nada que exista hoy.
> **Fecha:** 2026-07-14
> **Aplicación destino:** `auth-service-frond-end` (panel admin/staff, puerto 5173).
> **Backend:** `billing-service` (puerto 8086), ya completo (23 endpoints). Contratos en [docs/billing-service/api/](../billing-service/api/).
> **Parte de:** [restructuración de onboarding y facturación](../_archive/gym-administrator/restructuracion-onboarding-facturacion.md) (documento paraguas, 📜 archivado). **Es la Pieza 3 — depende de la Pieza 2 (el wizard de configuración).**

## Punto de partida (verificado en el código, 2026-07-14)

**Hoy no existe ninguna pantalla de facturación.** No hay nada que rediseñar: se construye el módulo entero desde cero. Los 23 endpoints de `billing-service` **no tienen ningún consumidor** — ni el frontend, ni `core-service`. Todo lo emitido en las Fases 0–3 del [roadmap SRI](../billing-service/pendientes/roadmap-sri-2026.md) solo es alcanzable por HTTP directo.

> ⚠️ El roadmap SRI describe la Fase 5 como *"modificar pantallas existentes (emisión, anulación, listado)"*. **Esa premisa es falsa** y este documento la corrige: no hay pantallas existentes.

## 🔴 Prerrequisito: el wizard de configuración

**Estas pantallas no le sirven a ningún gimnasio hasta que exista el [wizard de configuración SRI](../billing-service/pendientes/wizard-configuracion-sri.md).** Hoy `billing-service` necesita tres filas (`config_sri`, `certificados`, `puntos_emision`) que **solo se pueden crear con SQL a mano** — no hay endpoints de escritura. Sin ellas, `POST /facturas` responde `404 — configuración SRI no encontrada`.

**El wizard va primero.** De ahí salen, además, el `cod_establecimiento` y el `cod_punto_emision` que el formulario de emisión necesita y que el recepcionista no puede tipear.

## Por qué el módulo es 100 % manual

`core.membresias` guarda `precioPagado` pero **no guarda con qué se pagó** — no existe `forma_pago` en el modelo. Como el SRI **exige** la forma de pago en el XML (y desde G10 se valida bancarización sobre USD 500), **la facturación automática al vender una membresía es imposible hoy** sin antes agregar ese campo al formulario de venta.

Este documento especifica por tanto **solo el flujo manual**. La facturación automática es un requerimiento aparte que arranca por `core-service`, no por el frontend.

## Contexto de reutilización

Existe la intención futura de reusar este módulo en otros SaaS ecuatorianos (mecánicas, etc.). **No se empaqueta como librería ahora** — abstraer al primer caso de uso, con la pantalla aún sin construir, es adivinar. En su lugar, la [sección 14](#14-sección-clave-qué-es-replicable-a-otros-rubros) traza la **frontera** entre lo genérico de Ecuador/SRI y lo específico de gimnasio, de modo que la extracción futura sea un `git mv` y no un rediseño. Esa frontera (`src/lib/sri/` sin React ni axios) **debe respetarse desde el primer commit**: es todo el costo de mantener la opción abierta.

---

## 0. Contexto de negocio y restricciones de diseño

### Usuario objetivo primario

Recepcionista de gimnasio con poca adaptación tecnológica. Opera en mostrador con teclado y mouse. Sus prioridades son velocidad, claridad y no cometer errores que luego requieran gestionar. El diseño se subordina completamente a este perfil.

### Restricciones fiscales que impactan la UI

| Regla | Impacto en pantalla |
|---|---|
| Bancarización G10: si total > $500, el excedente sobre $500 debe pagarse con forma de pago bancarizada (códigos 16–20) | La UI calcula y advierte en tiempo real antes de enviar |
| Consumidor final (9999999999999): NO puede anularse vía nota de crédito | Advertencia prominente al autocompletearlo |
| Ventana de anulación: hasta el día 7 del mes siguiente a la emisión | La UI desactiva el botón si la ventana venció; muestra días restantes |
| Emisión síncrona G2: el POST puede tardar 3–10 s (p95), timeout 15 s | Spinner con mensaje humano, nunca bloquear al recepcionista |
| Estados transitorios DEVUELTO/NO_AUTORIZADO/ERROR: se reintentan solos | Mensaje tranquilizador, no error de pantalla roja |
| Módulo gateado: si el plan no incluye FACTURACION, el backend responde 402/403 | Pantalla de "módulo no disponible" en lugar de error técnico |

### Lo que NO hace este módulo

La integración automática `venta de membresía → factura` no existe hoy porque `core.membresias` no guarda forma de pago. Todo el flujo es 100 % manual. No se especifica ningún componente de "facturar al vender membresía".

---

## 1. Inventario de pantallas y jerarquía de navegación

### 1.1 Rutas en AdminLayout

```
/admin/facturacion                    → redirige a /admin/facturacion/facturas
/admin/facturacion/facturas           → FacturasPage (listado + tab detalle)
/admin/facturacion/nueva              → NuevaFacturaPage (formulario)
/admin/facturacion/notas-credito      → NotasCreditoPage (listado)
/admin/facturacion/anulaciones        → AnulacionesPage (solicitudes de anulación)
/admin/facturacion/reportes           → ReportesPage (ATS + resumen)
```

### 1.2 Entrada en el sidebar (AdminLayout)

Añadir al array `ALL_NAV_ITEMS` en `AdminLayout.tsx`:

```
{ to: '/admin/facturacion', labelKey: 'nav.facturacion', icon: <Receipt size={20} />, permiso: 'facturacion:leer' }
```

El ítem se agrupa lógicamente entre `tipos-membresia` y `usuarios` en el orden visual del sidebar. Usa el ícono `Receipt` de `lucide-react`.

### 1.3 Diagrama de navegación

```
Sidebar
  └── Facturación (/admin/facturacion)
        ├── Facturas (/admin/facturacion/facturas)      ← página por defecto
        │     └── [botón "+ Nueva factura"] ──► NuevaFacturaPage
        │     └── [fila de tabla] ──► Tab detalle (inline) o modal detalle
        │     └── [botón "Anular"] ──► AnularFacturaDialog (modal inline)
        │     └── [botón "Nota de crédito"] ──► NuevaNcDialog (modal inline)
        ├── Notas de Crédito (/admin/facturacion/notas-credito)
        ├── Anulaciones (/admin/facturacion/anulaciones)
        └── Reportes (/admin/facturacion/reportes)
```

La navegación entre las sub-secciones se implementa como **tabs horizontales** en la parte superior del contenido (debajo del PageHeader), exactamente igual al patrón `CompaniaDetallePage`. Las rutas son independientes para que el navegador back/forward funcione.

---

## 2. FacturasPage — Listado de facturas

### Propósito

Ver todos los comprobantes de tipo `01` de la empresa, filtrar por estado, consultar el detalle, descargar RIDE, reenviar email, iniciar anulación o emitir nota de crédito.

### Entrada

Clic en "Facturación" en el sidebar. Es la vista por defecto del módulo.

### Layout

```
┌─ PageHeader ─────────────────────────────────────────────────────────┐
│  Receipt  Facturación electrónica                [+ Nueva factura]   │
│           Comprobantes emitidos a clientes                           │
├─ Stats bar ───────────────────────────────────────────────────────────┤
│  152 Total  │  148 Autorizadas  │  3 Procesándose  │  1 Con error   │
├─ Tab nav ─────────────────────────────────────────────────────────────┤
│  [Facturas (152)]  [Notas de Crédito]  [Anulaciones]  [Reportes]    │
├─ Filter bar (px-4 py-2) ──────────────────────────────────────────────┤
│  [Estado ▾]  [Sucursal ▾]  [Desde──]  [Hasta──]  [🔍 Buscar...]    │
├─ DataTable ───────────────────────────────────────────────────────────┤
│  # │ Fecha │ Cliente │ Total │ Estado │ Acciones                     │
│  ──┼───────┼─────────┼───────┼────────┼──────────────────────────   │
│  1 │ ...   │ ...     │ ...   │ BADGE  │ [Ver] [RIDE] [Email] [▾]    │
└───────────────────────────────────────────────────────────────────────┘
```

### Componentes

- `PageHeader` — título `t('facturacion.facturas.title')`, description `t('facturacion.facturas.description')`, action = `<IfPermission permiso="facturacion:emitir"><Button "+ Nueva factura"/></IfPermission>`
- Stats bar: patrón idéntico a `TiposMembresiaPage`. Valores obtenidos de `GET /api/v1/reportes/resumen` con rango del mes actual.
- Tab nav: botones de navegación al estilo `CompaniaDetallePage` (no el componente shadcn Tabs), enlazados a rutas.
- Filter bar: select nativo `Estado` (opciones: Todos, AUTORIZADO, DEVUELTO, NO_AUTORIZADO, ERROR, ANULADO), select nativo `Sucursal`, dos `input[type=date]` (Desde/Hasta), input de búsqueda con icono `Search`.
- `DataTable` de PrimeReact con los props estándar del design-guide. Columnas:
  - Fecha (sortable, formato dd/MM/yyyy)
  - Secuencial (formato 001-001-000000001)
  - Cliente (razón social + cédula/RUC en `text-xs --page-muted`)
  - Total ($XX.XX, sortable)
  - Estado (badge de color, ver paleta abajo)
  - Acciones (ver más abajo)
- `ConfirmDialog` de PrimeReact (montar `<ConfirmDialog />` en el root del componente).

### Paleta de badges de estado

| Estado | Background | Color texto | Punto |
|---|---|---|---|
| AUTORIZADO | `rgba(34,197,94,0.15)` | `#22c55e` | verde |
| DEVUELTO | `rgba(234,179,8,0.15)` | `#eab308` | amarillo |
| NO_AUTORIZADO | `rgba(234,179,8,0.15)` | `#eab308` | amarillo |
| ERROR | `rgba(234,179,8,0.15)` | `#eab308` | amarillo |
| ANULADO | `rgba(100,116,139,0.15)` | `var(--page-muted)` | gris |
| GENERADO | `rgba(99,179,237,0.15)` | `#63b3ed` | azul (estado técnico, no debería aparecer) |

Los estados DEVUELTO, NO_AUTORIZADO y ERROR comparten el mismo badge visual porque para el recepcionista todos significan lo mismo: "procesándose". La diferencia técnica no le aporta nada.

### Columna de acciones

Botones en línea (tamaño `text` `size="small"`, fuente `!text-[0.6rem]`):

- **Ver** — abre modal de detalle del comprobante (siempre disponible)
- **RIDE** — descarga PDF `GET /comprobantes/{id}/ride`. Deshabilitado con tooltip "Disponible cuando se autorice" si `estado !== 'AUTORIZADO'`
- **Email** — ícono de sobre, dispara `POST /comprobantes/{id}/reenviar-email`. Deshabilitado si no hay `email_receptor`. `<IfPermission permiso="facturacion:emitir">`
- **▾** (menú desplegable `DropdownMenu` shadcn) con opciones adicionales según estado:
  - "Anular" — visible si `estado === 'AUTORIZADO'` y dentro de ventana. `<IfPermission permiso="facturacion:anular">`
  - "Nota de crédito" — visible si `estado === 'AUTORIZADO'`. `<IfPermission permiso="facturacion:emitir">`
  - "Reintentar envío" — visible si estado es ERROR/DEVUELTO/NO_AUTORIZADO. Llama `POST /comprobantes/{id}/enviar`
  - "Ver anulaciones" — visible si existen anulaciones previas

### Estados

- **Cargando:** DataTable con `loading={true}` (skeleton nativo de PrimeReact)
- **Vacío:** `EmptyState` con ícono `Receipt`, texto `t('facturacion.facturas.emptyTitle')`, botón "+ Nueva factura"
- **Error API:** toast.error con `t('facturacion.facturas.loadError')`. La tabla muestra el estado vacío sin bloquear.
- **Módulo no habilitado (402/403 del backend):** pantalla especial `ModuloNoDisponible` (ver sección 9)
- **Poblado:** tabla paginada con server-side pagination (los filtros disparan llamadas a `GET /api/v1/comprobantes` con los query params correspondientes)

### Interacciones

- Clic en fila → abre `DetalleFacturaModal` (modal, no tab detalle, porque el número de columnas de la tabla ya es suficiente densidad)
- Filtro de estado → `setPage(1)` + refetch
- Botón "+ Nueva factura" → navegar a `/admin/facturacion/nueva`
- "Anular" → abre `AnularFacturaDialog` (modal inline)
- "Nota de crédito" → abre `NuevaNcModal` (modal inline)

### Permissions

- Ver la página: `<PermissionGuard permiso="facturacion:leer">`
- Botón Nueva factura: `<IfPermission permiso="facturacion:emitir">`
- Acciones de anulación: `<IfPermission permiso="facturacion:anular">`

### i18n keys (sugeridas)

```
facturacion.facturas.title         → "Facturación Electrónica" / "Electronic Billing"
facturacion.facturas.description   → "Comprobantes emitidos a tus clientes" / "Invoices issued to your clients"
facturacion.facturas.nueva         → "Nueva factura" / "New invoice"
facturacion.facturas.emptyTitle    → "Aún no tienes facturas" / "No invoices yet"
facturacion.facturas.emptyHint     → "Emite tu primera factura electrónica" / "Issue your first electronic invoice"
facturacion.facturas.loadError     → "No se pudieron cargar las facturas" / "Could not load invoices"
facturacion.facturas.colFecha      → "Fecha" / "Date"
facturacion.facturas.colSecuencial → "No. Factura" / "Invoice No."
facturacion.facturas.colCliente    → "Cliente" / "Client"
facturacion.facturas.colTotal      → "Total" / "Total"
facturacion.facturas.colEstado     → "Estado" / "Status"
facturacion.facturas.colAcciones   → "Acciones" / "Actions"
facturacion.facturas.statTotal     → "Total" / "Total"
facturacion.facturas.statAutoriz   → "Autorizadas" / "Authorized"
facturacion.facturas.statProcesando → "Procesándose" / "Processing"
facturacion.facturas.statError     → "Con error" / "With error"
```

---

## 3. NuevaFacturaPage — Emitir factura

### Propósito

El recepcionista completa los datos del cliente, elige los ítems que compra, define las formas de pago y emite la factura al SRI. Es la pantalla más crítica del módulo.

### Entrada

Botón "+ Nueva factura" en `FacturasPage` o acceso directo a `/admin/facturacion/nueva`.

### Layout

```
┌─ PageHeader ──────────────────────────────────────────────────────────┐
│  ← Volver    Nueva factura electrónica                                │
├─ Contenido (dos columnas en pantallas >= 1280px, una columna en <1280)│
│                                                                        │
│  COLUMNA IZQUIERDA (col-span-7)                                       │
│  ┌─ Sección 1: Datos del cliente ─────────────────────────────────┐  │
│  │  [Consumidor Final — botón prominente naranja]                  │  │
│  │  ──── o ────                                                    │  │
│  │  Tipo ID [▾]  Nro. Cédula/RUC [          ] [Buscar]            │  │
│  │  Nombre completo [                                  ]           │  │
│  │  Email (opcional) [               ]                             │  │
│  │  Dirección (opcional) [                             ]           │  │
│  └──────────────────────────────────────────────────────────────── ┘  │
│  ┌─ Sección 2: Ítems ─────────────────────────────────────────────┐  │
│  │  [+ Agregar del catálogo]  [+ Línea libre]                      │  │
│  │  ┌──────────────────────────────────────────────────────────┐   │  │
│  │  │ Descripción │ Cant │ P. Unit │ Desc. │ Subtotal │ [x]    │   │  │
│  │  └──────────────────────────────────────────────────────────┘   │  │
│  └────────────────────────────────────────────────────────────────  ┘  │
│  ┌─ Sección 3: Formas de pago ────────────────────────────────────┐  │
│  │  [+ Agregar forma de pago]                                      │  │
│  │  Forma de pago [▾]    Monto [$      ]   [x]                    │  │
│  │  ⚠ Bancarización: si total > $500                              │  │
│  └────────────────────────────────────────────────────────────────  ┘  │
│                                                                        │
│  COLUMNA DERECHA (col-span-5)                                         │
│  ┌─ Resumen y totales ─────────────────────────────────────────────┐  │
│  │  Subtotal sin IVA    $ XX.XX                                    │  │
│  │  IVA 15 %            $ XX.XX                                    │  │
│  │  Total               $ XX.XX                                    │  │
│  │  ─────────────────────────────                                  │  │
│  │  Total pagos         $ XX.XX                                    │  │
│  │  ⚠ Diferencia: $X.XX  (si no cuadra)                          │  │
│  │                                                                 │  │
│  │  [ Emitir factura — botón grande naranja ]                      │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```

### Sección 1: Datos del cliente — detalle

**Botón "Consumidor Final"** (prominente, severidad `warning`, icono `UserX`):
- Al hacer clic: auto-rellena `tipo_id_receptor = '07'`, `id_receptor = '9999999999999'`, `razon_social_receptor = 'CONSUMIDOR FINAL'`. Deshabilita los inputs de identificación y nombre.
- Muestra inmediatamente un `Alert` visible (no toast): `t('facturacion.nueva.consumidorFinalWarning')` — texto sugerido: "Esta factura NO se puede anular con nota de crédito. Solo se puede anular directamente ante el SRI dentro de la ventana de 7 días."
- Para volver a identificar a un cliente, el recepcionista hace clic en "Cambiar cliente" que reaparece junto al aviso.

**Campo "Tipo de identificación"** (select):
- Opciones mapeadas desde los catálogos SRI:
  - `05` → Cédula ecuatoriana
  - `04` → RUC
  - `06` → Pasaporte
  - `08` → Identificación exterior
- Default: `05` (cédula), que es el 95 % de los casos en gimnasios.

**Campo "Número de identificación"**:
- Validación en cliente:
  - Cédula (`05`): exactamente 10 dígitos, algoritmo de dígito verificador ecuatoriano (lógica en `src/lib/sri/validarCedula.ts`).
  - RUC (`04`): exactamente 13 dígitos, validación básica (primeros 10 dígitos = cédula o especial, últimos 3 = `001`). Lógica en `src/lib/sri/validarRuc.ts`.
  - Pasaporte/exterior: mínimo 3 caracteres, sin validación de dígito verificador.
- Al completar una cédula/RUC válido: buscar en el sistema (`GET /api/v1/personas?cedula=…`) para auto-rellenar el nombre. Si no se encuentra, el recepcionista escribe el nombre manual.
- Mensaje de error humano si la cédula falla el verificador: `t('sri.cedulaInvalida')` → "Esa cédula no es válida — revisa que no tenga errores de tipeo."
- Mensaje de error humano si el RUC falla: `t('sri.rucInvalido')` → "Ese RUC no es válido — verifica que termina en 001."

**Comportamiento de auto-búsqueda de persona:**
- Se dispara con debounce de 400 ms cuando la identificación tiene la longitud correcta y pasa el verificador.
- Muestra un spinner small en el input mientras busca.
- Si encuentra persona: rellena nombre, email y dirección. Muestra badge "Cliente registrado" en verde junto al nombre.
- Si no encuentra: los campos quedan editables y en blanco. No es error — el recepcionista completa el dato.

### Sección 2: Ítems — detalle

**Botón "+ Agregar del catálogo"** (primario):
- Abre `CatalogoItemsDialog` (modal). Muestra los tipos de membresía activos del gimnasio (obtenidos de `GET /api/v1/tipos-membresia`) y cualquier otro producto configurable.
- El usuario hace clic en una fila del catálogo y el ítem se agrega a la tabla de detalles con sus datos prerellenados (código, descripción, precio).
- El `codigo_principal` del ítem de catálogo = el código del tipo de membresía (ej. `MEM-MENSUAL`).

**Botón "+ Línea libre"** (secundario, outline):
- Agrega una fila vacía donde el recepcionista puede tipear libremente descripción, cantidad y precio.
- El `codigo_principal` para línea libre = `SERV-LIBRE` (valor configurable o constante).
- Usar con moderación — el catálogo cubre el 95 % de los casos.

**Tabla de ítems:**
- Columnas: Descripción (editable inline), Cantidad (input numérico, min 0.001), Precio unitario (input numérico $), Descuento (input $, default 0), Subtotal (calculado, read-only), botón [x] eliminar.
- El subtotal por línea = (cantidad × precio_unitario) − descuento. Calculado en tiempo real en el cliente, sin esperar al servidor.
- Mínimo 1 ítem requerido para poder emitir.

### Sección 3: Formas de pago — detalle

**Catálogo de formas de pago** (etiquetas para el recepcionista, código SRI entre paréntesis solo en spec, nunca en UI):

| Código SRI | Nombre real en `sri.formas_pago` | Etiqueta UI (para el recepcionista) | ¿Bancarizada? |
|---|---|---|---|
| `01` | `SIN_UTILIZACION_SISTEMA_FINANCIERO` | Efectivo | No |
| `15` | `COMPENSACION_DEUDAS` | Compensación de deudas | No |
| `16` | `TARJETA_DEBITO` | Tarjeta de débito | **Sí** |
| `17` | `DINERO_ELECTRONICO` | Dinero electrónico | **Sí** |
| `18` | `TARJETA_PREPAGO` | Tarjeta prepago | **Sí** |
| `19` | `TARJETA_CREDITO` | Tarjeta de crédito | **Sí** |
| `20` | `OTROS_CON_UTILIZACION_SISTEMA_FINANCIERO` | Transferencia bancaria u otro medio bancario | **Sí** |
| `21` | `ENDOSO_TITULOS` | Endoso de títulos | No |

El select muestra solo la etiqueta UI, nunca el código SRI ni el nombre en `SCREAMING_SNAKE_CASE` del catálogo.

> ⚠️ **Ojo — el código 17 NO es "transferencia bancaria".** Es `DINERO_ELECTRONICO`. La transferencia bancaria cae en el `20` (*otros con utilización del sistema financiero*). Confundirlos hace que el XML declare al SRI un medio de pago distinto del real. Los códigos correctos están en el seed [`09_insert_seed_sri.sql`](../../gym-administrator/db/scripts/202605_GYM-001/ddl-facturacion/09_insert_seed_sri.sql), que es la única fuente de verdad — el `gap-analysis-sri-2026.md` §G10 los lista **mal**, no te guíes por él.

> 📌 **La columna "¿Bancarizada?" no se hardcodea en el frontend.** El backend expone el flag `bancarizada` en el catálogo (`sri.formas_pago`, consolidada en baseline `ddl-facturacion/05_create_table_sri_formas_pago.sql`, Fase 3 · G10). La UI debe **leerlo del endpoint de catálogos**, no replicar esta tabla en el código. Si mañana el SRI reclasifica un código, se cambia en la BD y el frontend se entera solo. La tabla de arriba es documentación, no una constante a copiar.

**Lógica de bancarización (G10) — validación en cliente:**

Esta validación se ejecuta en `src/lib/sri/validarBancarizacion.ts` y se evalúa en tiempo real:

```
// Pseudocode — no es código de implementación
si total_factura > 500:
  excedente = total_factura - 500
  total_pagos_bancarizados = sum(pagos donde forma_pago ∈ {16,17,18,19,20})
  si total_pagos_bancarizados < excedente:
    mostrar aviso: "Si el total supera $500, la parte que excede ese monto debe pagarse con tarjeta o transferencia. Faltan $X.XX en pago bancarizado."
    deshabilitar botón "Emitir factura"
```

El aviso se muestra como un `Alert` naranja (no rojo, para no asustar) en la sección de formas de pago, justo debajo de los inputs de pago. El botón de emitir queda deshabilitado mientras el aviso esté activo.

**Validación de cuadre total:**
- La suma de todas las formas de pago debe igualar el total de la factura (con tolerancia de ±$0.01 por redondeo).
- Mientras no cuadre, el botón "Emitir factura" está deshabilitado y el resumen lateral muestra en rojo la diferencia.

**Comportamiento al agregar forma de pago:**
- Al agregar la primera, el campo Monto se prellenará con el total de la factura.
- Al agregar una segunda, el campo Monto se prellenará con el saldo restante (total − lo que ya está asignado).

### Resumen lateral y botón de emisión

El panel lateral (sticky en desktop) muestra:
- Subtotal sin IVA (calculado del cliente)
- IVA 15 % (calculado del cliente: subtotal × 0.15)
- **Total** (subtotal + IVA) en `text-2xl font-bold`
- Separador visual
- Total formas de pago ingresadas
- Diferencia (si > ±$0.01, en rojo)

**Botón "Emitir factura":**
- Tamaño grande (no el size="small" de acciones de tabla), `severity="warning"`, ancho completo del panel lateral.
- Texto: `t('facturacion.nueva.emitir')` → "Emitir factura"
- Deshabilitado si: formulario inválido, ítems vacíos, suma de pagos no cuadra, o regla de bancarización violada.
- Cuando el usuario hace clic:
  1. Se deshabilita el botón para evitar doble clic.
  2. Aparece un estado de carga (ver sección 6).
  3. Se llama `POST /api/v1/comprobantes/facturas`.
  4. Se maneja la respuesta (ver sección 6).

### Estados de la página

- **Cargando catálogo:** spinner small en el área de ítems mientras se carga el catálogo de tipos de membresía.
- **Formulario vacío (inicial):** campos en blanco, botón "Emitir factura" deshabilitado.
- **En proceso de emisión:** estado de carga (ver sección 6).
- **Éxito:** modal `FacturaEmitidaModal` (ver sección 6).
- **Error de validación:** mensajes inline debajo de cada campo con `text-xs text-red-400`. Nunca en código SRI crudo.
- **Error de API (400/422):** toast con mensaje humano traducido (ver sección 7).

### Validaciones en cliente (zod schema sugerido como tipos, no como implementación)

```typescript
// Contrato de tipos — no es código TSX ni implementación

type TipoIdReceptor = '04' | '05' | '06' | '07' | '08'

interface ItemFactura {
  codigo_principal: string   // min 1 char
  descripcion: string        // min 1 char
  cantidad: number           // > 0
  precio_unitario: number    // >= 0
  descuento: number          // >= 0, <= precio_unitario * cantidad
}

interface PagoFactura {
  forma_pago: string         // código SRI válido
  total: number              // > 0
}

interface NuevaFacturaForm {
  tipo_id_receptor: TipoIdReceptor
  id_receptor: string        // validado por lib/sri según tipo
  razon_social_receptor: string  // min 1 char
  email_receptor?: string    // email si se provee
  direccion_receptor?: string
  id_sucursal: number        // viene del JWT, no lo ingresa el usuario
  cod_establecimiento: string
  cod_punto_emision: string
  detalles: ItemFactura[]    // min 1 elemento
  pagos: PagoFactura[]       // min 1 elemento, suma = total factura
}
```

Los campos `id_sucursal`, `cod_establecimiento`, `cod_punto_emision` se toman del JWT o de la configuración activa de la empresa — el recepcionista **no los ve ni los ingresa**.

### Permissions

- Acceso a la página: `<PermissionGuard permiso="facturacion:emitir">`
- El botón "Emitir factura" no necesita `<IfPermission>` adicional porque la página ya está guardada.

### i18n keys (sugeridas)

```
facturacion.nueva.title                 → "Nueva factura electrónica" / "New invoice"
facturacion.nueva.emitir                → "Emitir factura" / "Issue invoice"
facturacion.nueva.seccCliente           → "Datos del cliente" / "Client details"
facturacion.nueva.consumidorFinal       → "Consumidor Final" / "End Consumer"
facturacion.nueva.consumidorFinalWarning → "Esta factura NO se puede anular con nota de crédito..." / ...
facturacion.nueva.cambiarCliente        → "Cambiar cliente" / "Change client"
facturacion.nueva.tipoId                → "Tipo de identificación" / "ID type"
facturacion.nueva.nroId                 → "Número de cédula / RUC" / "ID number"
facturacion.nueva.nombre                → "Nombre completo" / "Full name"
facturacion.nueva.email                 → "Correo electrónico (opcional)" / "Email (optional)"
facturacion.nueva.direccion             → "Dirección (opcional)" / "Address (optional)"
facturacion.nueva.seccItems             → "Ítems" / "Line items"
facturacion.nueva.agregarCatalogo       → "Agregar del catálogo" / "Add from catalog"
facturacion.nueva.agregarLibre          → "Línea libre" / "Free line"
facturacion.nueva.colDescripcion        → "Descripción" / "Description"
facturacion.nueva.colCantidad           → "Cant." / "Qty"
facturacion.nueva.colPrecio             → "P. Unit." / "Unit price"
facturacion.nueva.colDescuento          → "Desc." / "Discount"
facturacion.nueva.colSubtotal           → "Subtotal" / "Subtotal"
facturacion.nueva.seccPagos             → "Formas de pago" / "Payment methods"
facturacion.nueva.agregarPago           → "Agregar forma de pago" / "Add payment method"
facturacion.nueva.formaPago             → "Forma de pago" / "Payment method"
facturacion.nueva.monto                 → "Monto" / "Amount"
facturacion.nueva.bancarizacionAviso    → "Si el total supera $500, la parte que excede ese monto debe pagarse con tarjeta o transferencia. Faltan ${faltante} en pago bancarizado." / ...
facturacion.nueva.subtotalSinIva        → "Subtotal sin IVA" / "Subtotal excl. VAT"
facturacion.nueva.iva                   → "IVA 15 %" / "VAT 15%"
facturacion.nueva.total                 → "Total" / "Total"
facturacion.nueva.totalPagos            → "Total pagado" / "Total paid"
facturacion.nueva.diferencia            → "Diferencia" / "Difference"
facturacion.nueva.clienteRegistrado     → "Cliente registrado" / "Registered client"
sri.cedulaInvalida                      → "Esa cédula no es válida — revisa que no tenga errores de tipeo" / "That ID number is not valid"
sri.rucInvalido                         → "Ese RUC no es válido — verifica que termina en 001" / "That RUC is not valid"
```

---

## 4. DetalleFacturaModal — Ver comprobante

### Propósito

Ver el detalle completo de una factura: datos del cliente, ítems, totales, estado SRI, número de autorización, y acceder a descargas.

### Layout (modal, max-width md)

```
┌─ DialogHeader ─────────────────────────────────────────────────────────┐
│  Factura No. 001-001-000000042          [BADGE ESTADO]                │
├─ DialogContent ────────────────────────────────────────────────────────┤
│  ┌─ Datos del cliente ───────────────────────────────────────────────┐ │
│  │  Juan Carlos Pérez · Cédula 1712345678                           │ │
│  │  correo@ejemplo.com · Av. Los Shyris                             │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
│  ┌─ SRI ─────────────────────────────────────────────────────────────┐ │
│  │  Autorización: 0207202601...789   Fecha: 02/07/2026 14:30        │ │
│  │  Emisión: 02/07/2026                                              │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
│  ┌─ Ítems ────────────────────────────────────────────────────────────┐ │
│  │  Membresía Mensual  ×1  $33.04                                    │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
│  ┌─ Totales ──────────────────────────────────────────────────────────┐ │
│  │  Subtotal $33.04 · IVA $4.96 · Total $38.00                      │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
│  ┌─ Formas de pago ───────────────────────────────────────────────────┐ │
│  │  Tarjeta de crédito  $38.00                                       │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
├─ DialogFooter ─────────────────────────────────────────────────────────┤
│  [Descargar RIDE PDF]  [Reenviar email]  [Cerrar]                     │
└────────────────────────────────────────────────────────────────────────┘
```

Si el estado es DEVUELTO/NO_AUTORIZADO/ERROR, se muestra un `Alert` informativo (no de error):
`t('facturacion.detalle.procesandoInfo')` → "Esta factura está siendo procesada por el SRI. Generalmente se autoriza en menos de una hora. No necesitas hacer nada."

Si el estado es ANULADO, el modal lo indica claramente con un badge gris y la fecha de anulación.

---

## 5. AnularFacturaDialog — Solicitar anulación

### Propósito

El recepcionista (o admin) solicita anular una factura. El diseño expone UN solo paso al usuario; el flujo interno (SOLICITADA → APROBADA → EJECUTADA) es transparente.

### Entrada

Opción "Anular" en el menú desplegable de la fila en FacturasPage, o botón en DetalleFacturaModal.

### Pre-condiciones validadas en cliente antes de abrir el modal

1. Estado de la factura es `AUTORIZADO` — si no, el botón no aparece.
2. Receptor NO es consumidor final (9999999999999) — si lo es, el botón "Anular" no aparece; en su lugar aparece un tooltip: "Las facturas a consumidor final se anulan directamente en el portal del SRI".
3. Fecha de emisión dentro de la ventana (hoy ≤ día 7 del mes siguiente) — si venció, mostrar mensaje: "La ventana de anulación venció el [fecha]. Para corregir esta factura, usa una Nota de Crédito."

### Layout (modal, max-width sm)

```
┌─ DialogHeader ─────────────────────────────────────────────────────────┐
│  Anular factura 001-001-000000042                                      │
├─ DialogContent ────────────────────────────────────────────────────────┤
│  📋 Factura del: Juan Pérez — $38.00 — 02/07/2026                    │
│  ⏳ Plazo para anular: quedan 4 días (vence el 07/08/2026)            │
│                                                                        │
│  Motivo de la anulación *                                              │
│  [textarea — min 5 chars]                                              │
│                                                                        │
│  [✓] Necesito generar una nota de crédito                             │
│      (para devolver el dinero al cliente o corregir el valor)          │
│                                                                        │
│  [Si checkbox marcado]                                                 │
│  Motivo SRI *                                                          │
│  [select: Devolución / Descuento / Anulación / Error en precio / ...]  │
├─ DialogFooter ─────────────────────────────────────────────────────────┤
│  [Cancelar]  [Solicitar anulación]                                    │
└────────────────────────────────────────────────────────────────────────┘
```

**Checkbox "Necesito generar una nota de crédito":**
- Default: unchecked. La mayoría de las anulaciones simples no requieren NC.
- Si se marca, aparece el select de Motivo SRI (cargado de `GET /api/v1/sri/motivos-anulacion`). El campo se vuelve obligatorio.
- Corresponde al campo `generar_nota_credito: true` en el request.

**Flujo desde el punto de vista del backend:**
- La UI siempre llama `POST /comprobantes/{id}/anular` con los datos.
- Si la empresa tiene rol `admin_compania`/`Dueño`: puede que el backend auto-apruebe en un paso o que requiera un segundo paso. La UI **no asume** ningún comportamiento — espera el response y muestra el estado resultante.
- Si el usuario es un recepcionista sin rol de aprobación: la solicitud queda en SOLICITADA. La UI muestra: "Tu solicitud fue enviada. Un administrador la revisará y ejecutará la anulación." No bloquea la pantalla.

**Nota de diseño sobre el doble control — regla única, sin ambigüedad:**

La decisión de producto es **un solo paso para el usuario**. Concretamente:

- **Si el usuario tiene permiso de aprobar** (dueño / admin, que en un gym chico es la misma persona que atiende el mostrador): al hacer clic en "Anular", el sistema **solicita y aprueba en la misma acción**. El usuario ve una sola confirmación y la factura queda anulada. **No se le pide que vaya a otra pantalla a aprobar su propia solicitud** — eso es la fricción exacta que este diseño existe para evitar.
- **Si el usuario NO tiene permiso de aprobar** (recepcionista en un gym con separación de funciones): la solicitud queda en `SOLICITADA` y un admin la resuelve desde `AnulacionesPage`.

El backend registra igual los estados `SOLICITADA → APROBADA → EJECUTADA` para la auditoría fiscal; eso es correcto y no cambia. Lo que cambia es que **la UI no obliga a recorrerlos de a uno cuando la misma persona tiene ambos permisos**.

`AnulacionesPage` (sección 9) sigue existiendo: es donde el admin ve el historial y resuelve las solicitudes ajenas. Simplemente no es un paso obligatorio en el camino feliz del gym pequeño.

### i18n keys (sugeridas)

```
facturacion.anular.title            → "Anular factura" / "Cancel invoice"
facturacion.anular.info             → "Factura de: {cliente} — ${total} — {fecha}" / ...
facturacion.anular.plazo            → "Quedan {n} días para anularla (vence el {fecha})" / ...
facturacion.anular.vencio           → "La ventana de anulación venció. Usa una Nota de Crédito para corregir." / ...
facturacion.anular.motivoLabel      → "Motivo de la anulación" / "Reason for cancellation"
facturacion.anular.motivoPlaceholder → "Explica brevemente por qué anulan esta factura..." / ...
facturacion.anular.generarNc        → "Necesito generar una nota de crédito" / "Generate a credit note"
facturacion.anular.generarNcHint    → "(para devolver el dinero o corregir el valor)" / ...
facturacion.anular.motivoSri        → "Motivo SRI" / "SRI reason"
facturacion.anular.btn              → "Solicitar anulación" / "Request cancellation"
facturacion.anular.enviada          → "Tu solicitud fue enviada. Un administrador la revisará." / ...
facturacion.anular.consumidorFinal  → "Las facturas a consumidor final se anulan en el portal del SRI" / ...
```

---

## 6. NuevaNcModal — Nota de Crédito

### Propósito

Emitir una nota de crédito electrónica sobre una factura ya autorizada, cuando la ventana de anulación venció o se quiere hacer un ajuste parcial.

### Entrada

Opción "Nota de crédito" en el menú de acciones de la factura. Solo disponible si `estado === 'AUTORIZADO'`.

### Layout (modal, max-width md)

```
┌─ DialogHeader ─────────────────────────────────────────────────────────┐
│  Nota de crédito — Factura 001-001-000000042                          │
├─ DialogContent ────────────────────────────────────────────────────────┤
│  📋 Factura original: Juan Pérez — $38.00 — 02/07/2026               │
│  ⚠ El receptor recibirá la NC y tendrá 5 días hábiles para aceptarla │
│                                                                        │
│  Motivo SRI *                                                          │
│  [select: Devolución / Descuento / Anulación / ...]                   │
│                                                                        │
│  Descripción del motivo *                                              │
│  [textarea: "Devolución mensual por error de cargo"]                  │
│                                                                        │
│  Valor a ajustar *                                                     │
│  $ [       ]   (máximo $38.00)                                        │
│                                                                        │
│  ┌─ Ítems de la nota de crédito ─────────────────────────────────────┐ │
│  │  [mismo editor de ítems que en NuevaFacturaPage]                  │ │
│  └─────────────────────────────────────────────────────────────────── ┘ │
├─ DialogFooter ─────────────────────────────────────────────────────────┤
│  [Cancelar]  [Emitir nota de crédito]                                 │
└────────────────────────────────────────────────────────────────────────┘
```

**Campo "Valor a ajustar":**
- Debe ser > 0 y ≤ total de la factura original.
- Validación en cliente antes de habilitar el botón.
- La suma de los ítems debe igualar el valor a ajustar (misma lógica de cuadre que en `NuevaFacturaPage`).

**Nota:** los datos del receptor se copian automáticamente de la factura original. No se muestran campos de cliente en este modal.

---

## 7. Estados asíncronos durante la emisión (G2)

Esta sección describe exactamente qué ve el recepcionista en cada momento del ciclo de vida de `POST /api/v1/comprobantes/facturas`.

### 7.1 Durante los 3–10 segundos del POST

**Estado visual:**
- El botón "Emitir factura" se reemplaza por un `Loader2` girando (de lucide-react) con el texto `t('facturacion.nueva.emitiendo')`.
- Todos los campos del formulario se deshabilitan para evitar edición.
- NO hay barra de progreso — no tenemos información de progreso real del servidor.
- No aparece ningún modal de espera: el formulario mismo muestra el estado.

**Copy sugerido:**
`t('facturacion.nueva.emitiendo')` → "Enviando al SRI..."
`t('facturacion.nueva.emitiendoHint')` → "Esto puede tomar hasta 15 segundos. No cierres la ventana."

### 7.2 Respuesta: estado AUTORIZADO (happy path)

- Se cierra el formulario de emisión.
- Aparece `FacturaEmitidaModal` (modal de éxito):

```
┌─────────────────────────────────────────────────────┐
│  ✅  ¡Factura autorizada!                           │
│                                                     │
│  Factura 001-001-000000042                          │
│  Juan Carlos Pérez — $38.00                         │
│  Autorización: 0207202601...789                     │
│                                                     │
│  [Descargar RIDE PDF]  [Enviar por email]           │
│  [Nueva factura]       [Ir a facturas]              │
└─────────────────────────────────────────────────────┘
```

El botón "Enviar por email" dispara `POST /comprobantes/{id}/reenviar-email` y muestra toast de éxito/error.
El botón "Nueva factura" resetea el formulario para emitir otra inmediatamente (flujo de caja rápida).

### 7.3 Respuesta: estado DEVUELTO / NO_AUTORIZADO / ERROR (transitorio)

**Principio fundamental:** estos no son errores para el recepcionista. La factura quedó registrada y el sistema la reintentará solo.

- Se cierra el formulario de emisión.
- Aparece `FacturaEmitidaModal` con tono informativo (no error):

```
┌─────────────────────────────────────────────────────┐
│  🕐  Factura registrada — procesándose               │
│                                                     │
│  Factura 001-001-000000042                          │
│  Juan Carlos Pérez — $38.00                         │
│                                                     │
│  La factura está siendo autorizada por el SRI.      │
│  Generalmente tarda menos de una hora.              │
│  No necesitas hacer nada más.                       │
│                                                     │
│  [Nueva factura]       [Ir a facturas]              │
└─────────────────────────────────────────────────────┘
```

**Lo que NO se hace:**
- No se muestra "DEVUELTO", "NO_AUTORIZADO" o "ERROR" en pantalla.
- No se muestran mensajes del SRI en crudo.
- No se pide al recepcionista que "reintente" — el sistema lo hace automáticamente.

### 7.4 Error real (400 / 404 / 422 del servidor)

Estos sí son errores que requieren corrección. Se muestran como toast.error + el formulario vuelve al estado editable:

| Código backend | Mensaje humano en UI |
|---|---|
| 400 (campo faltante) | "Hay un campo obligatorio sin completar. Revisa el formulario." |
| 404 (configuración SRI no encontrada) | "Tu empresa no tiene configurada la facturación electrónica. Contacta a soporte." |
| 422 "Tipo de identificación no reconocido" | "El tipo de identificación seleccionado no es válido para el SRI." |
| 422 "Forma de pago no reconocida" | "La forma de pago seleccionada no está habilitada. Selecciona otra." |
| 422 (bancarización — aunque la UI ya previene esto) | "La parte del pago que supera $500 debe ser con tarjeta o transferencia." |
| 402 (módulo no incluido en plan) | "Tu plan actual no incluye facturación electrónica. Actualiza tu suscripción." |
| 403 (módulo suspendido) | "El módulo de facturación está temporalmente suspendido. Contacta a soporte." |
| 503 (billing-service caído) | "El servicio de facturación no responde. Intenta en unos minutos." |

---

## 8. NotasCreditoPage — Listado de notas de crédito

### Propósito

Ver todas las notas de crédito emitidas por la empresa.

### Layout

Idéntica en estructura a `FacturasPage` pero con `tipo_comprobante = '04'`. Consume `GET /api/v1/notas-credito`.

**Columnas DataTable:**
- Fecha, No. NC (secuencial), No. Factura original (enlace al detalle de la factura), Cliente, Valor ajustado, Estado.

**Diferencias con FacturasPage:**
- No tiene botón "+ Nueva nota de crédito" (las NC se emiten desde una factura, no de forma independiente).
- La columna "No. Factura original" muestra el secuencial de la factura y al hacer clic abre el `DetalleFacturaModal` de esa factura.
- El filtro adicional `id_factura_original` permite buscar todas las NC de una factura específica (útil para soporte).

---

## 9. AnulacionesPage — Gestión de solicitudes de anulación

### Propósito

Los administradores ven las solicitudes de anulación pendientes, las aprueban o rechazan, y confirman la ejecución en el SRI (Flujo A).

### Entrada

Tab "Anulaciones" en el módulo de facturación, o enlace desde `FacturasPage` → "Ver anulaciones".

### Layout

```
┌─ PageHeader ──────────────────────────────────────────────────────────┐
│  Solicitudes de anulación                                             │
├─ Filter bar ──────────────────────────────────────────────────────────┤
│  [Estado ▾: SOLICITADA / APROBADA / RECHAZADA / EJECUTADA]  [Buscar] │
├─ DataTable ───────────────────────────────────────────────────────────┤
│  Factura │ Solicitado por │ Fecha │ Motivo │ Estado │ Acciones        │
│  ─────────────────────────────────────────────────────────────────── │
│  001-001-000000042 │ Juan (recepción) │ 13/07 │ "..." │ BADGE │ [▾]  │
└───────────────────────────────────────────────────────────────────────┘
```

### Columna Acciones según estado

| Estado | Acciones disponibles (solo para `admin_compania`/`Dueño`) |
|---|---|
| SOLICITADA | [Aprobar] [Rechazar] |
| APROBADA (Flujo A) | [Confirmar ejecución SRI] |
| APROBADA (Flujo B, NC pendiente) | [Ver NC asociada] (botón informativo, sin acción) |
| RECHAZADA | solo [Ver detalle] |
| EJECUTADA | solo [Ver factura] |

Las acciones de aprobación/rechazo/confirmación están envueltas en `<IfPermission permiso="facturacion:anular">` (o el permiso que se defina para roles admin_compania).

### AprobacionDialog (modal de aprobación)

```
┌─────────────────────────────────────────────────────┐
│  Aprobar anulación                                  │
│                                                     │
│  Factura: 001-001-000000042                         │
│  Motivo solicitado: "..."                           │
│                                                     │
│  Observación (opcional)                             │
│  [textarea]                                         │
│                                                     │
│  [Cancelar]  [Aprobar]                              │
└─────────────────────────────────────────────────────┘
```

Al aprobar Flujo B (NC), el backend puede tardar 3–15 s. Aplica el mismo patrón de carga de la sección 7.

### RechazoDialog (modal de rechazo)

```
┌─────────────────────────────────────────────────────┐
│  Rechazar solicitud de anulación                    │
│                                                     │
│  Motivo del rechazo *  (obligatorio)                │
│  [textarea]                                         │
│                                                     │
│  [Cancelar]  [Rechazar]  ← botón rojo               │
└─────────────────────────────────────────────────────┘
```

Usa `ConfirmDialog` de PrimeReact con `acceptClassName: 'p-button-danger'`.

### ConfirmarSriDialog (Flujo A)

```
┌─────────────────────────────────────────────────────┐
│  Confirmar ejecución en el SRI                      │
│                                                     │
│  ¿Ya ingresaste la clave de acceso de esta factura  │
│  en el portal del SRI y fue anulada?                │
│                                                     │
│  Una vez confirmado, la factura quedará marcada     │
│  como ANULADA en el sistema.                        │
│                                                     │
│  [Cancelar]  [Sí, ya anulé en el SRI]              │
└─────────────────────────────────────────────────────┘
```

Este dialog usa `ConfirmDialog` con `destructive` flag pero explicando al usuario que la confirmación es declarativa (él ya ejecutó el trámite en el portal SRI externo).

---

## 10. ReportesPage — ATS y resumen de ventas

### Propósito

Descargar el Anexo Transaccional Simplificado (ATS) mensual en XML y consultar el resumen de facturación por período.

### Layout

```
┌─ PageHeader ──────────────────────────────────────────────────────────┐
│  Reportes de facturación                                              │
├─ Contenido (dos columnas) ─────────────────────────────────────────── │
│                                                                        │
│  CARD 1: Resumen de ventas                                            │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  Desde [date]  Hasta [date]  [Consultar]                       │  │
│  │  ─────────────────────────────────────                         │  │
│  │  152 emitidas  148 autorizadas  4 con error                    │  │
│  │  Subtotal $12,450  IVA $1,494  Total $13,944                   │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  CARD 2: ATS mensual                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  Año [2026 ▾]   Mes [Julio ▾]                                   │  │
│  │  [Descargar ATS XML]                                            │  │
│  │  El ATS contiene todas las ventas del mes para declarar al SRI. │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```

**Descarga del ATS:** al hacer clic en "Descargar ATS XML", dispara `GET /api/v1/reportes/ats?anio=2026&mes=7`. El navegador recibe el archivo con `Content-Disposition: attachment` y lo descarga directamente. Mientras descarga: botón con spinner y deshabilitado.

**Estado vacío del resumen:** si el período no tiene comprobantes, mostrar `"No hay facturas en el período seleccionado."` en el área de métricas.

### Permissions

- Acceso: `<PermissionGuard permiso="facturacion:reportes">` (o permiso que se defina).

---

## 11. ModuloNoDisponiblePage — Error de gating

### Propósito

Mostrar una pantalla amigable cuando el módulo de facturación no está habilitado para el plan de la empresa (response 402 del `ModuloGatingFilter`).

### Layout

```
┌─ PageHeader ──────────────────────────────────────────────────────────┐
│  Facturación Electrónica                                              │
├─ Contenido centrado ──────────────────────────────────────────────────┤
│                                                                        │
│  [ícono Lock grande, color --page-border]                             │
│                                                                        │
│  "Tu plan no incluye facturación electrónica"                         │
│                                                                        │
│  "Para emitir facturas SRI, actualiza tu suscripción."                │
│                                                                        │
│  [Ver planes de suscripción] ──► /admin/mi-suscripcion                │
│                                                                        │
└───────────────────────────────────────────────────────────────────────┘
```

Esta pantalla se muestra como wrapper de todas las rutas del módulo cuando se detecta 402 en el primer request. No bloquea otras partes del panel.

---

## 12. Flujos de usuario paso a paso

### Flujo A: Emitir una factura normal

1. Recepcionista hace clic en "Facturación" en el sidebar → ve `FacturasPage`.
2. Clic en "+ Nueva factura" → navega a `/admin/facturacion/nueva`.
3. Selecciona tipo "Cédula", ingresa número de cédula → la UI valida el dígito verificador en tiempo real.
4. Si la cédula es válida y el cliente existe: nombre, email y dirección se auto-rellenan.
5. Clic en "+ Agregar del catálogo" → se abre `CatalogoItemsDialog` → elige "Membresía Mensual" → se cierra el modal, el ítem aparece en la tabla.
6. La sección de totales muestra $38.00. La sección de pagos está vacía.
7. Clic en "+ Agregar forma de pago" → selecciona "Tarjeta de crédito", el monto se prellenó con $38.00.
8. Los totales cuadran. El botón "Emitir factura" se habilita.
9. Clic en "Emitir factura" → botón muestra spinner "Enviando al SRI..." durante 5 segundos.
10. El servidor responde 201 con `estado: 'AUTORIZADO'`.
11. Aparece `FacturaEmitidaModal` con los datos de autorización y botones de descarga/email.
12. Recepcionista hace clic en "Nueva factura" → el formulario se resetea para el siguiente cliente.

**Duración total del flujo:** 60–90 segundos para un recepcionista habituado.

### Flujo B: Emitir factura a consumidor final

1. Recepcionista abre `NuevaFacturaPage`.
2. Clic en el botón naranja "Consumidor Final" (prominente).
3. Los campos de identificación se rellenan y bloquean automáticamente.
4. Aparece el aviso en amarillo sobre la imposibilidad de nota de crédito.
5. Continúa con ítems y formas de pago normalmente.
6. El formulario puede emitirse. La lógica de anulación/NC para este receptor es diferente (ver restricciones de backend).

### Flujo C1: Anular una factura — usuario CON permiso de aprobar (caso más común: gym pequeño)

Este es el camino feliz y el que hay que optimizar. **Un solo paso.**

1. En `FacturasPage`, el dueño/admin abre el menú "▾" de la factura y elige "Anular".
2. Se verifica en cliente: estado AUTORIZADO, no es consumidor final, dentro de la ventana temporal.
3. Se abre `AnularFacturaDialog`. Escribe el motivo. Si necesita devolver el dinero, marca "Nota de crédito" y elige el motivo SRI.
4. Clic en **"Anular factura"** (no "Solicitar anulación" — este usuario no le pide permiso a nadie).
5. El backend solicita y aprueba en la misma operación.
   - **Con NC (Flujo B):** emite la nota de crédito al SRI (≈10 s, mismo patrón de carga de la sección 7). La factura queda `ANULADO`. **Listo, terminó.**
   - **Sin NC (Flujo A):** la anulación queda `APROBADA` a la espera del trámite en el portal del SRI. La UI le dice explícitamente qué le falta hacer: *"Ahora anula esta factura en el portal del SRI y vuelve a confirmarlo aquí."* Cuando lo hace, confirma con `ConfirmarSriDialog` y la factura pasa a `ANULADO`.
6. **En ningún momento se le pide ir a `AnulacionesPage` a aprobar su propia solicitud.**

### Flujo C2: Anular una factura — usuario SIN permiso de aprobar (gym con separación de funciones)

1. Pasos 1–3 idénticos.
2. Clic en **"Solicitar anulación"** → toast: *"Tu solicitud fue enviada. Un administrador la revisará."*
3. La solicitud queda en `SOLICITADA`. El recepcionista **no queda bloqueado** — sigue atendiendo.
4. Un admin la ve en `AnulacionesPage`, la aprueba (`AprobacionDialog`) o la rechaza (`RechazoDialog`, motivo obligatorio).
5. Desde la aprobación, continúa igual que el paso 5 del flujo C1 (Flujo A o B según lleve NC).

### Flujo D: Descargar RIDE y reenviar email

1. En `FacturasPage`, hacer clic en "RIDE" en la fila de una factura AUTORIZADO → el PDF se descarga directamente.
2. Hacer clic en el ícono de sobre → toast "¿Reenviar el RIDE al correo {email}?" → toast de éxito o error.

### Flujo E: Descargar ATS mensual

1. Ir a "Reportes" en el módulo.
2. En Card 2, seleccionar año y mes.
3. Clic "Descargar ATS XML" → el archivo `ATS_2026_07.xml` se descarga.

---

## 13. Copy y microcopy en español ecuatoriano para recepcionista

### Principios de redacción

- **Segunda persona singular ("tú")**, no "usted".
- **Verbos directos**: "Ingresa", "Elige", "Descarga". No "Por favor ingrese".
- **Sin siglas técnicas al recepcionista**: nunca mostrar "RUC del receptor" en un campo, sino "Número de RUC del cliente".
- **Sin códigos SRI**: nunca mostrar "forma_pago: 19", sino "Tarjeta de crédito".
- **Sin estados internos**: nunca mostrar "DEVUELTO" o "NO_AUTORIZADO" al recepcionista.

### Traducción de errores SRI a lenguaje humano

| Mensaje técnico del backend | Mensaje en UI |
|---|---|
| `Tipo de identificación no reconocido: 99` | "El tipo de identificación seleccionado no es válido." |
| `Forma de pago no reconocida: 99` | "Esa forma de pago no está disponible. Elige otra." |
| `Solo facturas AUTORIZADO admiten NC` | "Solo puedes hacer una nota de crédito de facturas que ya están autorizadas." |
| `valor_modificacion debe ser positivo` | "El valor a ajustar debe ser mayor a cero." |
| `valor_modificacion ({v}) no puede exceder el total de la factura original ({total})` | "El valor a ajustar no puede ser mayor al total de la factura (${{total}})." |
| `Las facturas emitidas a consumidor final...` | "Esta factura fue emitida a consumidor final. Para anularla, debes hacerlo directamente en el portal del SRI." |
| `Fuera de la ventana SRI para anulación` | "El plazo para anular esta factura venció. Puedes emitir una nota de crédito en cambio." |
| `No es posible solicitar anulación...` | "Esta factura no puede anularse porque ya está en un estado final." |
| `El motivo de anulación es obligatorio` | "Escribe el motivo de la anulación." |

### Mensajes de proceso (durante el POST de 3–15 s)

```
"Enviando al SRI..." — durante la espera
"¡Factura autorizada!" — éxito
"Factura registrada — procesándose" — estado transitorio (no error)
```

### Mensajes de éxito (toast)

```
"Solicitud de anulación enviada."
"RIDE enviado al correo del cliente."
"Nota de crédito emitida correctamente."
"Anulación aprobada."
"Anulación rechazada."
"Ejecución confirmada. La factura quedó anulada."
```

---

## 14. Sección clave: qué es replicable a otros rubros

### 14.1 Lógica genérica de Ecuador/SRI — carpeta `src/lib/sri/`

Esta carpeta contiene **lógica pura TypeScript sin dependencias de React, axios ni PrimeReact**. Puede copiarse o extraerse como paquete npm para cualquier SaaS ecuatoriano (mecánicas, peluquerías, restaurantes, etc.).

**Archivos propuestos:**

```
src/lib/sri/
  validarCedula.ts          — algoritmo de dígito verificador de cédula ecuatoriana
  validarRuc.ts             — validación de RUC (natural, privada, pública, especial)
  validarBancarizacion.ts   — regla G10: si total > 500, excedente debe ser bancarizado
  calcularIva.ts            — IVA Ecuador (actualmente 15 %). Cuando el catálogo sri.tarifas_iva
                              sea consultable (pendiente G6), esta función recibirá la tarifa
                              del servidor en lugar de tenerla hardcodeada.
  ventanaAnulacion.ts       — calcular si hoy ≤ día 7 del mes siguiente a fecha de emisión
                              (necesita zone America/Guayaquil)
  tipos.ts                  — tipos TS compartidos: TipoIdReceptor, FormaPago, EstadoComprobante
  catalogos.ts              — mapas de código SRI → etiqueta UI (formas de pago, tipos de ID,
                              motivos de NC). Estos catálogos cambian raramente; cuando el
                              backend los exponga como endpoints consultables (pendiente G6),
                              esta capa se alimentará de la red en vez del hardcode.
```

**Regla de aislamiento:** ningún archivo en `src/lib/sri/` puede importar de `react`, `axios`, `primereact`, `react-hook-form`, ni de ningún otro paquete de UI/HTTP. Solo TypeScript puro. Los tests de `validarCedula.ts` y `validarBancarizacion.ts` corren sin DOM.

**Qué es específico de SRI Ecuador (se replica directamente):**
- Algoritmo de dígito verificador de cédula (módulo 10, pesos 2 1 2 1...)
- Algoritmo de validación de RUC (primeros 10 = cédula/especial, posición 3 determina tipo, últimos 3 = 001)
- Catálogo de formas de pago (códigos 01, 15, 16, 17, 18, 19, 20, 21)
- Catálogo de tipos de identificación (04, 05, 06, 07, 08)
- Catálogo de motivos de NC (DEVOLUCION, DESCUENTO, ANULACION, ERROR_PRECIO, ERROR_CALIDAD)
- Regla de bancarización G10 ($500 de umbral, códigos 16–20 son bancarizados)
- Ventana de anulación (día 7 del mes siguiente)
- IVA 15 % (Ecuador 2024–2026)
- Formato de número de comprobante SRI: `{estab}-{pto_emision}-{secuencial}` ej. `001-001-000000042`

### 14.2 Capa de UI de facturación — carpeta `src/ui/features/facturacion/`

Esta carpeta contiene todo lo específico de la app:

```
src/ui/features/facturacion/
  pages/
    FacturasPage.tsx
    NuevaFacturaPage.tsx
    NotasCreditoPage.tsx
    AnulacionesPage.tsx
    ReportesPage.tsx
  components/
    DetalleFacturaModal.tsx
    AnularFacturaDialog.tsx
    NuevaNcModal.tsx
    FacturaEmitidaModal.tsx
    CatalogoItemsDialog.tsx     — catálogo específico de gimnasio (tipos de membresía)
    AprobacionDialog.tsx
    RechazoDialog.tsx
    ConfirmarSriDialog.tsx
    BadgeEstadoComprobante.tsx
    FormaPagoBadge.tsx
    ModuloNoDisponiblePage.tsx
  hooks/
    useFacturas.ts
    useNuevaFactura.ts
    useAnulaciones.ts
  store/
    facturacion.store.ts        — Zustand: estado de la página de facturas (filtros, paginación)
```

**Qué es específico de gimnasio (cambiaría en otros rubros):**
- `CatalogoItemsDialog.tsx`: en un gimnasio muestra tipos de membresía. En una mecánica mostraría servicios/repuestos. En una peluquería mostraría servicios de corte/coloración. El `codigo_principal` del ítem varía según el rubro.
- El campo `id_membresia` en el request de factura (específico del modelo de datos del gimnasio).
- La búsqueda de cliente por cédula conecta a la tabla `core.personas` del gimnasio; en otro rubro conectaría a su propio modelo de cliente.
- Los permisos (`facturacion:leer`, `facturacion:emitir`, `facturacion:anular`, `facturacion:reportes`) tienen nombres genéricos y se replican tal cual.

### 14.3 Frontera de responsabilidades

```
src/lib/sri/          ← Lógica pura, 0 dependencias UI. Extraíble a npm package.
    │
    │ importado por
    ▼
src/ui/features/facturacion/   ← UI específica de la app, con componentes del design system.
    │
    │ importa (catálogo de ítems del rubro)
    ▼
src/ui/features/core/         ← Módulo de membresías/clientes (específico de gimnasio)
```

En otro rubro, el único archivo que cambia entre proyectos dentro de `facturacion/` es `CatalogoItemsDialog.tsx` y la llamada de búsqueda de cliente. La lógica SRI, los modales de anulación, el panel de reportes y toda `src/lib/sri/` se replican sin modificaciones.

---

## 15. Preguntas abiertas y decisiones diferidas

### Decisiones que requieren respuesta del producto o backend antes de implementar

1. **Permisos exactos de facturación:** ¿cuáles son los strings de permiso para el sistema de roles existente (`facturacion:leer`, `facturacion:emitir`, `facturacion:anular`, `facturacion:reportes`)? Estos permisos deben crearse en el backend (tabla `auth.permisos`) y asignarse a los roles de staff. Hoy no existen. Bloquea la implementación de `<IfPermission>` e `<PermissionGuard>`.

2. **¿Qué rol puede aprobar anulaciones?** El backend requiere `admin_compania`, `super_admin` o `Dueño`. En el sistema de permisos del frontend, ¿esto se maneja con un permiso (`anulaciones:aprobar`) o con verificación directa del rol? Definir antes de implementar `AnulacionesPage`.

3. **Catálogo de ítems ampliado:** ¿los gimnasios venden productos físicos (suplementos, ropa) además de membresías? Si sí, ¿hay un endpoint para listarlos? Actualmente `CatalogoItemsDialog` solo incluiría tipos de membresía. Si hay productos, se necesita un endpoint adicional en `core-service`.

4. ~~**Sucursal activa del usuario**~~ — ✅ **Resuelto 2026-07-14.** `cod_establecimiento` y `cod_punto_emision` viven en **`facturacion.puntos_emision`** (PK `id_compania, id_sucursal, cod_establecimiento, cod_punto_emision`). No están en `tenant` ni en `config_sri`. La UI los obtiene de `GET /api/v1/admin/puntos-emision` — **endpoint que todavía hay que construir**, junto con toda la configuración inicial. Ver [wizard-configuracion-sri.md](../billing-service/pendientes/wizard-configuracion-sri.md), que es **prerrequisito de este módulo**: sin la configuración creada, `POST /facturas` devuelve `404` y ninguna de estas pantallas sirve.

5. **`codigo_numerico`:** el request requiere 9 dígitos como parte de la clave de acceso. El doc de integración recomienda generarlo determinísticamente (hash del contexto). ¿Quién lo genera en el flujo manual? ¿El frontend genera un UUID truncado, o el backend lo asigna automáticamente? Aclarar con el backend-developer.

6. **Búsqueda de cliente por cédula:** la UI especifica auto-búsqueda en `GET /api/v1/personas?cedula=…`. Verificar que este endpoint existe en `auth-service` y que el staff tiene acceso a él. Si no existe, la auto-búsqueda se omite y el recepcionista escribe el nombre manualmente.

7. **Webhook de autorización asíncrona:** hoy la UI no tiene forma de saber cuándo una factura en estado transitorio pasa a AUTORIZADO, salvo polling o que el usuario recargue la página. ¿Se implementa polling automático en `FacturasPage` para los registros en estado ERROR/DEVUELTO/NO_AUTORIZADO? ¿Cada cuánto tiempo? (Sugerencia: polling cada 30 s solo para los registros visibles en pantalla, hasta 5 intentos.)

8. **Reintento automático en Flujo B anulación:** el doc de backend anota que cuando la NC queda DEVUELTO/ERROR al aprobar en Flujo B, el cierre de la anulación no es automático (`NotaCreditoService` tiene TODO). La UI en ese caso mostrará la anulación en APROBADA indefinidamente. ¿El admin debe hacer algo manualmente? ¿O esperamos a que se resuelva en backend antes de exponer el Flujo B en la UI?

9. **Ítem de sidebar:** ¿el ítem "Facturación" aparece en el sidebar solo si el plan incluye el módulo, o siempre aparece y la página muestra `ModuloNoDisponiblePage`? La segunda opción es preferible para que el gym siempre sepa que la funcionalidad existe.

10. **Número de factura en la UI:** ¿mostramos el secuencial con formato `001-001-000000042` (legible) o la clave de acceso completa? La spec usa el formato legible. Confirmar que el endpoint incluye los tres componentes (`cod_establecimiento`, `cod_punto_emision`, `secuencial`) para construirlo en cliente.

### Decisiones diferidas a implementación

- ~~El doble control de anulación~~ — **decidido, no diferido.** Es un solo paso para quien tenga permiso de aprobar (ver sección 5 y flujos C1/C2). Se resuelve por permisos, no por pantallas.

- El polling automático para estados transitorios: implementarlo solo si hay tiempo en el sprint. La experiencia mínima funciona sin él (el recepcionista puede recargar la página).

---

