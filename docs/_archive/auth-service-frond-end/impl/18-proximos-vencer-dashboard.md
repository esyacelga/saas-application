# IMPL_18 — Card "Próximos a Vencerse" en el Dashboard del Gimnasio

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../STATUS.md).

> **Estado:** Pendiente de implementación
> **Última actualización:** 2026-06-20
> **Requiere cambio en backend:** Sí — `core-service` (ver sección "Cambios en el backend")

---

## Objetivo

Reemplazar la KPI card "Entradas del Mes" del Dashboard del gimnasio por una card interactiva "Próximos a Vencerse" que muestra el total de clientes cuya membresía vence en 3 días o menos, y al hacer clic abre un panel lateral con la lista detallada de esos clientes.

---

## Contexto y análisis previo

### Umbral definido en el backend

El `core-service` tiene un job programado (`ClienteStatusJobService.java`) que corre diariamente a las **00:10** y asigna el estado `proximo_vencer` a todo cliente cuya membresía tenga `dias_restantes <= 3`. Esto aplica tanto para membresías por **calendario** como por **accesos**:

```java
// ClienteStatusJobService.java — líneas 63 y 73
// Accesos restantes <= 3  → proximo_vencer
// Días calendario restantes <= 3  → proximo_vencer
// Días < 0  → vencido
```

El umbral de **3 días es el único umbral del backend**. No existe `activo_con_aviso` ni otro estado intermedio.

### Cómo obtener los datos

El endpoint `GET /api/v1/clientes?estado=proximo_vencer` ya existe y devuelve exactamente los clientes que se necesitan. No se requiere filtrado adicional en el frontend.

Cada item de la lista incluye (tras el cambio de backend descrito más abajo):

```typescript
// core.dto.ts — ClienteListItem
{
  id: number
  nombre: string
  ci: string
  telefono: string | null
  estado: 'proximo_vencer'
  membresia_activa: {
    id: number
    tipo: string                          // ej. "Plan Mensual"
    modo_control: 'calendario' | 'accesos'
    fecha_fin: string                     // ISO date — ej. "2026-06-23"
    dias_restantes: number                // días de calendario hasta fecha_fin
    accesos_restantes: number | null      // NUEVO — accesos restantes si modo_control='accesos', null si 'calendario'
  }
}
```

### Gap en membresías por accesos

El backend marca `proximo_vencer` correctamente cuando quedan <= 3 accesos, pero el campo `dias_restantes` del listado **siempre contiene días de calendario** hasta `fecha_fin` (calculado con `(m.fecha_fin - CURRENT_DATE)::int`). Esto significa que un cliente con 10 pases, 7 usados y `fecha_fin` en 30 días mostraría `dias_restantes = 30` — un valor engañoso en el panel.

**Decisión:** Agregar el campo `accesos_restantes` al DTO de lista en el backend (ver sección "Cambios en el backend"). Con ese campo, el panel puede mostrar la métrica correcta según `modo_control`.

### Tarjeta que se reemplaza

La card **"Entradas del Mes"** (`stats.total_entradas`) viene de `attendanceRepository.getEstadisticas({periodo: 'mes'})`. Esa llamada continuará haciéndose porque otros elementos del dashboard (`promedio_diario`, `hora_pico`, etc.) la consumen. Solo se retira la card visual; el request se mantiene.

### Patrón de referencia

La implementación sigue exactamente el patrón del `SinSuscripcionPanel` + su `AlertKpiCard` ya existente en `DashboardPage.tsx`. Las diferencias son de contenido, no de estructura.

---

## Componentes involucrados

### Frontend

| Archivo | Cambio |
|---|---|
| `src/ui/features/admin/pages/DashboardPage.tsx` | Agregar llamada API, nuevo estado, nueva card, nuevo panel |
| `src/infrastructure/http/core/core.dto.ts` | Agregar `accesos_restantes: number \| null` a `MembresiaResumen` |
| `src/i18n/locales/es.json` | 3 claves nuevas bajo `dashboard` |
| `src/i18n/locales/en.json` | 3 claves nuevas bajo `dashboard` |
| `src/infrastructure/http/core/CoreRepository.ts` | Sin cambios — `getClientes({estado})` ya soporta el filtro |

### Backend (`core-service`)

| Archivo | Cambio |
|---|---|
| `ClientePersistenceAdapter.java` | Agregar subquery SQL para `accesos_restantes` |
| `ClienteListItem.java` (domain record) | Agregar campo `accesosRestantes` |
| `ClienteListItemResponse.java` (DTO record) | Agregar campo `accesosRestantes` + actualizar `from()` |

---

## Flujo de usuario

```
Dashboard cargado
  └─ KPI card "Próximos a Vencerse" muestra el total
       ├─ total = 0  →  card en verde (sin urgencia)
       └─ total >= 1  →  card en ámbar
            └─ Clic en la card
                 └─ Abre ProximosVencerPanel (drawer lateral)
                      └─ Lista de clientes con dias_restantes 0-3
                           [Avatar] [Nombre / CI]  [dias badge]  [fecha_fin]
                                └─ Clic en "Renovar"
                                     └─ Abre VenderMembresiaModal
                                          ├─ Staff confirma → POST /clientes/{id}/membresias
                                          │    ├─ toast.success
                                          │    ├─ Cliente desaparece de la lista del panel
                                          │    └─ KPI proximosVencerTotal - 1
                                          └─ Staff cancela → modal cierra, panel queda abierto
```

---

## Especificación de la card KPI

Reutiliza el componente `AlertKpiCard` ya existente en `DashboardPage.tsx`.

| Propiedad | Valor |
|---|---|
| Label | `t('dashboard.kpi.proximosVencer')` |
| Sub-label | `t('dashboard.kpi.proximosVencerSub')` |
| Valor | `proximosVencerTotal ?? 0` |
| Icono (count > 0) | `Clock` (lucide-react) |
| Icono (count = 0) | `CheckCircle2` (lucide-react) |
| Color (count > 0) | `#f59e0b` (ámbar — misma paleta que `SinSuscripcionPanel`) |
| Color (count = 0) | `#10b981` (verde esmeralda) |
| onClick | `() => setShowProximosPanel(true)` |

---

## Especificación del panel lateral `ProximosVencerPanel`

### Props

```typescript
interface ProximosVencerPanelProps {
  total: number
  onClose: () => void
  onRenovada: (idCliente: number) => void
}
```

### Comportamiento

- Al montar: llama `coreRepository.getClientes({ estado: 'proximo_vencer', limit: 100 })`.
- Ordena la lista ascendente por `membresia_activa.dias_restantes` (más urgentes primero).
- Muestra un skeleton de carga mientras espera la respuesta.
- Si la lista está vacía (estado sincronizado): muestra mensaje "Ningún cliente próximo a vencer".

### Fila de cada cliente

```
[Avatar inicial]  [Nombre]          [badge urgencia]   [fecha_fin]
                  [CI en mono]      [tipo membresía]               [btn Renovar]
```

El badge de urgencia varía según `modo_control`:

**`modo_control = 'calendario'`** — usa `dias_restantes`:
- `0` → `"Vence hoy"` — rojo
- `1` → `"1 día"` — rojo
- `2` → `"2 días"` — ámbar
- `3` → `"3 días"` — ámbar claro

**`modo_control = 'accesos'`** — usa `accesos_restantes` (nuevo campo):
- `0` → `"Sin accesos"` — rojo
- `1` → `"1 acceso"` — rojo
- `2` → `"2 accesos"` — ámbar
- `3` → `"3 accesos"` — ámbar claro

**`fecha_fin`:** siempre visible, formateado como `dd/MM/yyyy`. Para membresías por accesos da contexto sobre cuándo vence la ventana de uso.

**Botón "Renovar":** mismo estilo ámbar que el botón "Asignar" del `SinSuscripcionPanel`.

### Acción "Renovar"

Abre `VenderMembresiaModal` con `idCliente` y `nombreCliente`. Al confirmar:
1. Cierra el modal.
2. Elimina el cliente del array local (`filter`).
3. Llama `props.onRenovada(idCliente)` para que `DashboardPage` decremente `proximosVencerTotal`.

### Posición y z-index

Panel fijo a la derecha, idéntico al `SinSuscripcionPanel` (mismo `fixed inset-y-0 right-0`, mismo backdrop semitransparente). Si ambos paneles están abiertos simultáneamente (situación que no debería ocurrir en flujo normal), el más reciente tiene prioridad visual.

---

## Cambios en `DashboardPage.tsx`

### 1. Nuevo estado

```typescript
const [proximosVencerTotal, setProximosVencerTotal] = useState<number | null>(null)
const [showProximosPanel, setShowProximosPanel] = useState(false)
```

### 2. Nueva llamada en `Promise.allSettled`

```typescript
coreRepository.getClientes({ estado: 'proximo_vencer', limit: 1 })
```

Extrae `results[5].value.total` (índice tras los 5 existentes).

### 3. Handler de renovación

```typescript
const handleRenovada = useCallback((_idCliente: number) => {
  setState(prev => ({
    ...prev,
    proximosVencerTotal: Math.max(0, (prev.proximosVencerTotal ?? 1) - 1),
    totalClientesActivos: (prev.totalClientesActivos ?? 0) + 1,
  }))
}, [])
```

### 4. Reemplazo de card en JSX

Eliminar la `KpiCard` de "Entradas del Mes" y agregar en su lugar:

```tsx
<AlertKpiCard
  label={t('dashboard.kpi.proximosVencer')}
  subLabel={t('dashboard.kpi.proximosVencerSub')}
  value={proximosVencerTotal ?? 0}
  icon={proximosVencerTotal ? Clock : CheckCircle2}
  color={proximosVencerTotal ? '#f59e0b' : '#10b981'}
  onClick={() => setShowProximosPanel(true)}
/>
```

### 5. Montaje del panel

```tsx
{showProximosPanel && (
  <ProximosVencerPanel
    total={proximosVencerTotal ?? 0}
    onClose={() => setShowProximosPanel(false)}
    onRenovada={handleRenovada}
  />
)}
```

---

## Claves i18n

### `es.json` — bajo `dashboard.kpi`

```json
"proximosVencer": "Próximos a Vencer",
"proximosVencerSub": "membresías en ≤ 3 días",
"renovar": "Renovar"
```

### `en.json` — bajo `dashboard.kpi`

```json
"proximosVencer": "Expiring Soon",
"proximosVencerSub": "memberships in ≤ 3 days",
"renovar": "Renew"
```

---

## Permiso requerido

El botón "Renovar" abre `VenderMembresiaModal`, que llama a `POST /clientes/{id}/membresias`. Requiere permiso `membresias:crear` en el rol del staff. Si el token no tiene ese permiso, el backend devuelve 403 y el modal muestra el toast de error genérico. El botón no se oculta en frontend — consistente con el patrón del `SinSuscripcionPanel`.

---

## Cambios en el backend (`core-service`)

### Contexto del problema

`ClientePersistenceAdapter.java` calcula `dias_restantes` siempre como días de calendario:

```sql
GREATEST(0, (m.fecha_fin - CURRENT_DATE)::int) AS membresia_dias_restantes
```

Para membresías `modo_control = 'accesos'`, este valor es irrelevante para mostrar urgencia. El campo correcto es `m.dias_acceso_total - COUNT(asistencias)`, pero no existe en la respuesta de lista.

---

### 1. `ClientePersistenceAdapter.java` — modificar SQL

**Archivo:** `src/main/java/com/gymadmin/core/infrastructure/adapter/out/persistence/adapter/ClientePersistenceAdapter.java`

Agregar un `LEFT JOIN` a `asistencia.asistencias` y una columna calculada condicionada al `modo_control`:

```sql
SELECT c.id, p.nombre, p.ci, p.telefono, c.estado,
       m.id AS membresia_id, tm.nombre AS membresia_tipo,
       tm.modo_control AS membresia_modo_control,
       m.fecha_fin AS membresia_fecha_fin,
       GREATEST(0, (m.fecha_fin - CURRENT_DATE)::int) AS membresia_dias_restantes,
       CASE
           WHEN tm.modo_control = 'accesos'
           THEN GREATEST(0, m.dias_acceso_total - COUNT(a.id))
           ELSE NULL
       END AS membresia_accesos_restantes
FROM core.clientes c
JOIN identidad.personas p ON p.id = c.id_persona
LEFT JOIN core.membresias m ON m.id_cliente = c.id AND m.estado = 'activa' AND m.eliminado = false
LEFT JOIN core.tipos_membresia tm ON tm.id = m.id_tipo_membresia
LEFT JOIN asistencia.asistencias a ON a.id_membresia = m.id
WHERE c.id_compania = :idCompania AND c.eliminado = false
  AND (:estado IS NULL OR c.estado = :estado)
  AND (:buscar IS NULL OR lower(p.nombre) LIKE :buscar OR lower(p.ci) LIKE :buscar)
  [sinMembresiaClause]
GROUP BY c.id, p.nombre, p.ci, p.telefono, c.estado,
         m.id, tm.nombre, tm.modo_control, m.fecha_fin, m.dias_acceso_total
ORDER BY c.id DESC LIMIT :limit OFFSET :offset
```

> **Nota:** Se agrega `GROUP BY` porque se usa `COUNT(a.id)`. Se agrupa por todos los campos del `SELECT` no agregados.

También actualizar el método `rowToListItem()` para leer la nueva columna:

```java
// leer la nueva columna del Row
Integer accesosRestantes = row.get("membresia_accesos_restantes", Integer.class);
```

---

### 2. `ClienteListItem.java` — domain record

**Archivo:** `src/main/java/com/gymadmin/core/domain/model/ClienteListItem.java`

Agregar el campo al record `MembresiaResumen`:

```java
public record MembresiaResumen(
        Long id,
        String tipo,
        String modoControl,
        String fechaFin,
        int diasRestantes,
        Integer accesosRestantes   // null cuando modo_control = 'calendario'
) {}
```

---

### 3. `ClienteListItemResponse.java` — DTO record

**Archivo:** `src/main/java/com/gymadmin/core/infrastructure/adapter/in/web/dto/ClienteListItemResponse.java`

Agregar el campo al record interno `MembresiaResumen` y actualizar el método `from()`:

```java
public record MembresiaResumen(
        Long id,
        String tipo,
        String modoControl,
        String fechaFin,
        int diasRestantes,
        Integer accesosRestantes   // serializado por Jackson como "accesos_restantes"
) {
    public static MembresiaResumen from(ClienteListItem.MembresiaResumen m) {
        return new MembresiaResumen(
            m.id(), m.tipo(), m.modoControl(), m.fechaFin(),
            m.diasRestantes(), m.accesosRestantes()
        );
    }
}
```

Jackson serializa `accesosRestantes` → `"accesos_restantes"` automáticamente por la estrategia `SNAKE_CASE` configurada en `application.yml`.

---

### 4. `core.dto.ts` — frontend DTO

**Archivo:** `src/infrastructure/http/core/core.dto.ts`

Agregar el nuevo campo a `MembresiaResumen`:

```typescript
export interface MembresiaResumen {
  id: number
  tipo: string
  modo_control: 'calendario' | 'accesos'
  fecha_fin: string
  dias_restantes: number
  accesos_restantes: number | null   // null cuando modo_control = 'calendario'
}
```

---

### Sin cambios en otros servicios

`auth-service`, `platform-service` y `attendance-service` no requieren modificaciones.

---

## Notas de implementación

- **`ProximosVencerPanel` vive en `DashboardPage.tsx`** como función interna, igual que `SinSuscripcionPanel`. No crear un archivo separado a menos que supere las ~120 líneas.
- La card "Entradas del Mes" se elimina del JSX pero `stats.total_entradas` sigue disponible en el estado por si se necesita en otro lugar del dashboard en el futuro.
- Si en el futuro se requiere mostrar clientes con `dias_restantes <= 7` o `<= 10`, el backend necesitará un nuevo query param o un nuevo estado; el frontend no puede controlar ese umbral.
- El `GROUP BY` agregado al SQL no afecta la paginación — `LIMIT` y `OFFSET` se aplican después del agrupamiento, y `COUNT(*)` para la paginación vive en una query separada (`contarListItems`). Verificar que esa query también agregue el `LEFT JOIN` a asistencias si se necesita en el futuro, aunque para el conteo no es necesario.
- El orden del panel es ascendente por urgencia: primero `accesos_restantes = 0` / `dias_restantes = 0`, luego 1, 2, 3. Para membresías mixtas en el mismo panel, ordenar por el campo relevante: si `modo_control = 'accesos'` usar `accesos_restantes`, si `'calendario'` usar `dias_restantes`.
