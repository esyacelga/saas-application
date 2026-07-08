# Directrices de Diseño — Auth Service Front-End

> **ESTADO:** 🟡 Referencia de diseño UI/UX. Verificar contra el código para el detalle actual. Ver [../STATUS.md](../STATUS.md).

## 1. Sistema de temas

El layout de plataforma soporta múltiples temas seleccionables en tiempo real. El tema activo se gestiona mediante `useThemeStore` (Zustand + `localStorage` por usuario) y se aplica como `data-layout` en `document.body` y en el div raíz del layout.

### Temas disponibles

| ID | Nombre | Tipo |
|----|--------|------|
| `light` | Light | Claro (blanco azulado) |
| `dark` | Dark | Oscuro (negro azulado) |
| `dark-blue` | Dark Blue | Oscuro (azul marino) |
| `ocean-blue` | Ocean Blue | Claro (azul cielo pastel) |
| `slate-carbon` | Slate Carbon | Oscuro (gris neutro) |
| `mint-pastel` | Mint Pastel | Claro (verde menta) |

### Persistencia por usuario

El tema se guarda en `localStorage` con la key `gym-theme-{sub}`, donde `sub` es el campo `sub` del JWT (ID único del usuario). Esto permite que cada usuario tenga su propio tema en el mismo dispositivo, sin base de datos.

- `gym-theme-guest` — tema para la pantalla de login (sin sesión activa)
- `gym-theme-{sub}` — tema del usuario autenticado

El store (`src/infrastructure/store/theme/theme.store.ts`) expone `syncUser(sub)` que se llama automáticamente desde `auth.store.ts` en `setSession`, `setAccessToken` y `logout`. **No llamar `syncUser` manualmente desde componentes.**

### Agregar un nuevo tema

1. Añadir la variante al tipo `AppTheme` en `src/infrastructure/store/theme/theme.store.ts`
2. Añadir el bloque de **17 variables CSS** en `src/index.css` bajo `[data-layout="<nombre>"]` (ver sección 2)
3. Añadir las reglas de PrimeReact (DataTable, Paginator, Buttons, Dialog, Dropdown panel) siguiendo el patrón de los temas existentes en `src/index.css`
4. Registrar el tema en el array `THEMES` de los tres layouts: `PlatformLayout.tsx`, `AdminLayout.tsx` y `PublicLayout.tsx`, con su `label` y `color` de muestra (el color del círculo en el selector)

### Selector de tema

El selector de tema está disponible en los tres layouts:

- **`PlatformLayout`** y **`AdminLayout`**: botón `Palette` en el footer del sidebar. Expandido muestra menú desplegable; colapsado cicla entre temas con cada clic.
- **`PublicLayout`**: botón compacto (icono `Palette` + círculo de color) en la esquina superior derecha junto al `LanguageSwitcher`.

---

## 2. Variables CSS por tema

Todas las páginas deben usar estas variables CSS en lugar de colores Tailwind hardcodeados. Esto garantiza que el cambio de tema sea automático.

### Variables de página

```css
var(--page-bg)       /* fondo del área de contenido principal */
var(--page-border)   /* bordes y divisores internos */
var(--page-text)     /* texto principal */
var(--page-muted)    /* texto secundario, labels, hints */
var(--page-surface)  /* superficie elevada: cards, sub-headers, tab activo */
```

### Variables de inputs

```css
var(--input-bg)          /* fondo de <input> y <select> */
var(--input-border)      /* borde de <input> y <select> */
var(--input-text)        /* texto del valor */
var(--input-placeholder) /* texto placeholder e iconos de búsqueda */
```

### Variables de sidebar

```css
var(--sidebar-bg)          /* fondo del sidebar */
var(--sidebar-border)      /* borde derecho y separadores internos */
var(--sidebar-text)        /* texto e iconos en estado inactivo */
var(--sidebar-text-hover)  /* texto e iconos en estado hover / nombre de usuario */
var(--sidebar-active-bg)   /* fondo del nav item activo */
var(--sidebar-active-text) /* texto del nav item activo */
var(--sidebar-item-hover)  /* fondo hover de items del menú desplegable */
```

### Bloque completo por tema (17 variables)

Cada tema en `src/index.css` debe definir las 17 variables. Ejemplo de estructura:

```css
[data-layout="<nombre>"] {
  --page-bg:      #...;
  --page-border:  #...;
  --page-text:    #...;
  --page-muted:   #...;
  --page-surface: #...;
  --input-bg:          #...;
  --input-border:      #...;
  --input-text:        #...;
  --input-placeholder: #...;
  --sidebar-bg:        #...;
  --sidebar-border:    #...;
  --sidebar-text:      #...;
  --sidebar-text-hover:#...;
  --sidebar-active-bg: #...;
  --sidebar-active-text:#...;
  --sidebar-item-hover:#...;
}
```

### Temas claros vs. oscuros

Los temas claros (`light`, `ocean-blue`, `mint-pastel`) usan sidebar oscuro con texto claro para mantener contraste. El sidebar **nunca** es claro independientemente del tema de página.

| Tipo | page-bg | sidebar-bg | active-bg |
|------|---------|------------|-----------|
| Oscuro | `#020817`–`#18181b` | aún más oscuro | naranja `rgba(249,115,22,0.15)` |
| Claro | `#eff6ff`–`#f0fdf4` | color profundo del matiz | sólido (`#f97316` o color del tema) |

### Uso correcto en JSX

```tsx
/* ✅ Correcto — responde al tema */
<div style={{ borderBottom: '1px solid var(--page-border)', color: 'var(--page-text)' }}>

/* ✅ Correcto — input temático */
<input style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }} />

/* ✅ Correcto — sidebar temático */
<aside style={{ background: 'var(--sidebar-bg)', borderRight: '1px solid var(--sidebar-border)' }}>

/* ❌ Incorrecto — color hardcodeado que no cambia con el tema */
<div className="border-slate-800 text-white bg-gym-900">
```

---

## 3. Tipografía y escala

Fuente base: **Inter** (Google Fonts). Todos los tamaños son relativos (`rem`).

| Uso | Tamaño |
|-----|--------|
| Título de página (`h1`) | `text-lg` (1.125rem) |
| Subtítulo de sección | `text-sm` (0.875rem) |
| Labels de tabla (header) | `0.57rem` |
| Texto de filas de tabla | `0.64rem` |
| Texto de hints / muted | `text-xs` (0.75rem) |
| Badges y counters | `0.55rem` |
| Botones de acción | `0.6rem` |
| Código / permisos (`<code>`) | `0.6rem` font-mono |

---

## 4. Tablas (PrimeReact DataTable)

Los estilos de tabla se aplican globalmente desde `src/index.css` según el `data-layout` activo. **No agregar clases de color directamente en los componentes DataTable.**

### Props estándar del DataTable

```tsx
<DataTable
  value={datos}
  loading={loading}
  globalFilter={globalFilter}
  globalFilterFields={['campo1', 'campo2']}
  header={tableHeader}
  emptyMessage={<EmptyState />}
  onRowClick={e => seleccionar(e.data)}
  rowClassName={(row) => `cursor-pointer${seleccionado?.id === row.id ? ' bg-orange-950/40' : ''}`}
  paginator
  rows={10}
  rowsPerPageOptions={[5, 10, 25]}
  sortField="nombre"
  defaultSortOrder={1}
  stripedRows
  showGridlines={false}
  size="small"
/>
```

### Fila seleccionada

La fila activa (maestro → detalle) usa `rowClassName` con `bg-orange-950/40` y en el template del nombre se muestra un punto naranja + texto naranja:

```tsx
const nombreTemplate = (item) => (
  <span className="flex items-center gap-1.5">
    {seleccionado?.id === item.id && (
      <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
    )}
    <span style={{ fontWeight: 600, color: seleccionado?.id === item.id ? '#f97316' : 'var(--page-text)' }}>
      {item.nombre}
    </span>
  </span>
)
```

### Header de búsqueda estándar

```tsx
const tableHeader = (
  <div className="flex items-center justify-end">
    <div className="relative">
      <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
        style={{ color: 'var(--input-placeholder)' }} />
      <input
        type="text"
        value={globalFilter}
        onChange={e => setGlobalFilter(e.target.value)}
        placeholder="Buscar..."
        className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
        style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
      />
    </div>
  </div>
)
```

### Densidad de tabla (valores actuales)

| Elemento | Padding | Font size | Line height |
|----------|---------|-----------|-------------|
| Header `<th>` | `0.16rem 0.52rem` | `0.57rem` | `1.2` |
| Celdas `<td>` | `0.06rem 0.52rem` | `0.64rem` | `1.2` |
| Paginador | `0.15rem 0.3rem` | `0.66rem` | — |
| Botones paginador | `min-width: 1.32rem` `height: 1.32rem` | `0.66rem` | — |
| Dropdown paginador | `height: 1.44rem` | `0.66rem` | — |

### Reglas obligatorias para cada tema en `index.css`

Cada tema debe definir reglas para:
- `.p-datatable` — card (bg, border, border-radius, box-shadow)
- `.p-datatable-header` — zona del filtro/búsqueda
- `.p-datatable-thead > tr > th` — headers de columna
- `.p-datatable-tbody > tr` — filas (bg, color, border-color, font-size)
- `.p-datatable-tbody > tr > td` — celdas (padding, line-height)
- `.p-datatable-tbody > tr:hover` — hover de fila
- `.p-datatable-striped > tr:nth-child(even)` — filas alternas
- `.p-paginator` — footer del paginador
- `.p-paginator .p-paginator-page.p-highlight` — página activa
- `.p-paginator .p-dropdown` — selector de filas por página
- `body[data-layout="<tema>"] .p-dropdown-panel` — menú portado a body
- `.p-button.p-button-warning` — botón de acción primario
- `.p-dialog` / `.p-confirm-dialog` — modales

### Color de acento

El acento naranja **`#f97316`** es constante en todos los temas **oscuros** para:
- Columna ordenada activa, página activa del paginador, botones `p-button-warning`, tab activo, badge de tab activo.

En temas **claros con paleta de color propio** (`ocean-blue`, `mint-pastel`) el acento activo del sidebar puede ser el color del tema (ej. `#3b82f6` en ocean-blue, `#f97316` en mint-pastel). El acento de página siempre es naranja.

---

## 5. Botones

Los botones de acción siguen un tamaño reducido uniforme en todos los temas.

| Tipo | font-size | padding |
|------|-----------|---------|
| `p-button-warning` | `0.6rem` | `0.25rem 0.5rem` |
| `p-button-info` | `0.6rem` | `0.25rem 0.5rem` |
| Icono de botón | `0.6rem` | — |

Los botones dentro de **dialogs** conservan su tamaño PrimeReact por defecto — no se reducen.

### Botones de acción en tabla (icon-only o text)

```tsx
/* Acción principal — naranja */
<Button
  label="Editar permisos"
  text size="small"
  onClick={...}
  pt={{ root: { className: 'text-orange-500 hover:text-orange-600 !text-[0.6rem] !px-1.5 !py-0.5' } }}
/>

/* Acción secundaria — color muted */
<Button
  label="Editar"
  text size="small"
  onClick={...}
  pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}
  style={{ color: 'var(--page-muted)' }}
/>

/* Acción destructiva */
<Button label="Eliminar" text size="small" severity="danger"
  pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }} />

/* Icon-only con tooltip */
<Button icon="pi pi-pencil" text size="small"
  tooltip="Editar" tooltipOptions={{ position: 'top' }}
  pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }} />
```

---

## 6. Layout de páginas

### Estructura completa estándar

```
┌─ PageHeader (px-6 py-5, sticky, border-bottom) ───────────────────────┐
│  Ícono + Título + subtítulo                    Botón de acción (warning)│
├─ Stats bar (px-6 py-3, border-bottom) — opcional ──────────────────────┤
│  Métrica 1  │  Métrica 2  │  Métrica 3  │  Métrica 4                  │
├─ Tab bar (px-4 pt-3, border-bottom) ───────────────────────────────────┤
│  [ Tab Maestro (badge nº) ]  [ Tab Detalle — disabled si no hay sel. ] │
├─ Contenido del tab activo ─────────────────────────────────────────────┤
│  Tab Maestro: p-4 → DataTable con row click                            │
│  Tab Detalle: sub-header + contenido específico                        │
└────────────────────────────────────────────────────────────────────────┘
```

### PageHeader

Usar siempre el componente `<PageHeader>` de `@/ui/components/PageHeader`. Ya aplica `var(--page-bg)`, `var(--page-border)` y `var(--page-text)` automáticamente.

```tsx
<PageHeader
  title={t('page.title')}
  description={t('page.description')}
  action={
    <IfPermission permiso="entidad:crear">
      <Button label="Nuevo" icon="pi pi-plus" severity="warning" size="small" onClick={...} />
    </IfPermission>
  }
/>
```

### Stats bar

Métricas separadas por divisores verticales. Los valores numéricos en `text-2xl font-bold`. Usar colores semánticos para métricas de estado (verde activos, rojo inactivos).

```tsx
<div className="flex items-center gap-6 px-6 py-3 flex-shrink-0"
  style={{ borderBottom: '1px solid var(--page-border)' }}>
  <div className="flex items-center gap-2">
    <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{total}</span>
    <span className="text-xs" style={{ color: 'var(--page-muted)' }}>Total</span>
  </div>
  <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
  <div className="flex items-center gap-2">
    <span className="text-2xl font-bold text-green-500">{activos}</span>
    <span className="text-xs" style={{ color: 'var(--page-muted)' }}>Activos</span>
  </div>
</div>
```

### Tabs (maestro → detalle)

- Usar tabs cuando la página tiene relación **maestro → detalle**.
- El tab de detalle debe estar **`disabled`** hasta que se seleccione un ítem del maestro.
- Al hacer clic en una fila del maestro, navegar automáticamente al tab de detalle (`setActiveTab('detalle')`).
- El tab activo tiene `background: var(--page-surface)`, `color: #f97316`, bordes visibles y `marginBottom: -1px` para fusionarse con la línea inferior.
- El tab inactivo tiene `color: var(--page-muted)`, sin fondo.
- Los badges en los tabs usan naranja cuando el tab está activo, y `var(--page-border)` cuando está inactivo.

```tsx
<button
  onClick={() => setActiveTab('maestro')}
  className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors"
  style={activeTab === 'maestro' ? {
    background: 'var(--page-surface)',
    color: '#f97316',
    borderTop: '1px solid var(--page-border)',
    borderLeft: '1px solid var(--page-border)',
    borderRight: '1px solid var(--page-border)',
    borderBottom: '1px solid var(--page-surface)',
    marginBottom: '-1px',
  } : { color: 'var(--page-muted)' }}
>
  <IconComponent size={12} />
  {t('tab.label')}
  <span className="ml-1 px-1.5 py-0.5 rounded-full text-[0.55rem] font-bold"
    style={{
      background: activeTab === 'maestro' ? '#f97316' : 'var(--page-border)',
      color: activeTab === 'maestro' ? '#fff' : 'var(--page-muted)',
    }}>
    {count}
  </span>
</button>

<button
  onClick={() => seleccionado && setActiveTab('detalle')}
  disabled={!seleccionado}
  className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors disabled:cursor-not-allowed disabled:opacity-40"
  style={activeTab === 'detalle' ? { /* igual que activo */ } : { color: 'var(--page-muted)' }}
>
  {seleccionado ? seleccionado.nombre : t('tab.selectHint')}
</button>
```

### Sub-header del tab detalle

Muestra contexto del ítem seleccionado + acciones relevantes.

```tsx
<div className="flex items-center justify-between px-5 py-3 flex-shrink-0"
  style={{ borderBottom: '1px solid var(--page-border)' }}>
  <div className="flex items-center gap-2">
    {/* Badge de contexto (ej. nombre del rol, compañía) */}
    <span className="inline-block text-xs font-medium px-2 py-0.5 rounded-full"
      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)', color: 'var(--page-muted)' }}>
      {seleccionado.contexto}
    </span>
    <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
      {count} elemento{count !== 1 ? 's' : ''} asignado{count !== 1 ? 's' : ''}
    </span>
  </div>
  <div className="flex items-center gap-2">
    <Button icon="pi pi-plus" label="Acción" severity="warning" size="small" outlined onClick={...} />
  </div>
</div>
```

### Headers de sección interna

```tsx
<div className="px-4 py-2.5 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
  <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
    TÍTULO DE SECCIÓN
  </p>
</div>
```

### Estado vacío de tab detalle (sin selección)

```tsx
<div className="flex flex-col items-center justify-center py-16 text-center gap-3 px-8">
  <div className="w-16 h-16 rounded-full flex items-center justify-center"
    style={{ background: 'var(--page-surface)' }}>
    <IconComponent size={28} style={{ color: 'var(--page-muted)' }} />
  </div>
  <div>
    <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
      Selecciona un ítem de la lista
    </p>
    <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
      Haz clic en una fila para ver el detalle
    </p>
  </div>
</div>
```

### Empty state de tabla

```tsx
<div className="flex flex-col items-center justify-center py-14 text-center">
  <IconComponent size={36} className="mb-3" style={{ color: 'var(--page-border)' }} />
  <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('entity.emptyTitle')}</p>
  <p className="text-xs mt-1 mb-4" style={{ color: 'var(--page-muted)' }}>{t('entity.emptyDescription')}</p>
  <Button label={t('entity.new')} icon="pi pi-plus" severity="warning" size="small" onClick={onAdd} />
</div>
```

---

## 7. Sidebar

El sidebar responde al tema activo mediante las variables `--sidebar-*`. Todos los colores van via `style={{}}`, no por clases Tailwind.

### Patrón de NavLink temático

```tsx
<NavLink
  to="/ruta"
  className={cn('flex items-center px-2 py-2 rounded-md text-xs font-medium transition-colors', collapsed ? 'justify-center' : 'gap-2.5')}
  style={({ isActive }) => isActive
    ? { background: 'var(--sidebar-active-bg)', color: 'var(--sidebar-active-text)' }
    : { color: 'var(--sidebar-text)' }
  }
>
```

### Selector de tema en sidebar

El selector vive en el footer del sidebar. En estado expandido muestra un menú desplegable; en estado colapsado cicla entre temas.

```tsx
{!collapsed ? (
  <div className="relative">
    <button onClick={() => setThemeMenuOpen(p => !p)}
      className="flex items-center gap-1.5 text-xs transition-colors py-1 w-full"
      style={{ color: 'var(--sidebar-text)' }}>
      <Palette size={12} />
      <span>Tema: <span style={{ color: 'var(--sidebar-text-hover)' }}>{THEMES.find(t => t.value === theme)?.label}</span></span>
    </button>
    {themeMenuOpen && (
      <div className="absolute bottom-7 left-0 rounded-lg shadow-xl py-1 z-50 min-w-[130px]"
        style={{ background: 'var(--sidebar-border)', border: '1px solid var(--sidebar-item-hover)' }}>
        {THEMES.map(t => (
          <button key={t.value} onClick={() => { setTheme(t.value); setThemeMenuOpen(false) }}
            className="flex items-center gap-2 w-full px-3 py-1.5 text-xs transition-colors"
            style={{ color: theme === t.value ? '#fb923c' : 'var(--sidebar-text)' }}>
            <span className="w-2.5 h-2.5 rounded-full flex-shrink-0"
              style={{ background: t.color, border: '1px solid var(--sidebar-text)' }} />
            {t.label}
            {theme === t.value && <span className="ml-auto" style={{ color: '#fb923c' }}>✓</span>}
          </button>
        ))}
      </div>
    )}
  </div>
) : (
  <button title="Cambiar tema"
    onClick={() => { const idx = THEMES.findIndex(t => t.value === theme); setTheme(THEMES[(idx + 1) % THEMES.length].value) }}
    className="p-1.5 rounded-md transition-colors" style={{ color: 'var(--sidebar-text)' }}>
    <Palette size={13} />
  </button>
)}
```

### Array THEMES (igual en los tres layouts)

```tsx
const THEMES: { value: AppTheme; label: string; color: string }[] = [
  { value: 'light',        label: 'Light',        color: '#f8fafc' },
  { value: 'dark',         label: 'Dark',         color: '#020817' },
  { value: 'dark-blue',    label: 'Dark Blue',    color: '#0d1b2a' },
  { value: 'ocean-blue',   label: 'Ocean Blue',   color: '#bfdbfe' },
  { value: 'slate-carbon', label: 'Slate Carbon', color: '#18181b' },
  { value: 'mint-pastel',  label: 'Mint Pastel',  color: '#f0fdf4' },
]
```

---

## 8. Inputs y selects nativos

Siempre usar las variables de input. Patrón estándar:

```tsx
<input
  className="text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
  style={{
    background: 'var(--input-bg)',
    border: '1px solid var(--input-border)',
    color: 'var(--input-text)',
  }}
/>

<select
  className="text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500"
  style={{
    background: 'var(--input-bg)',
    border: '1px solid var(--input-border)',
    color: 'var(--input-text)',
  }}
>
```

El icono de búsqueda (`<Search />`) debe usar `color: var(--input-placeholder)`.

---

## 9. Dialogs (shadcn / base-ui)

Los dialogs usan el componente shadcn `Dialog` (`@/components/ui/dialog`) basado en base-ui. Se renderizan en portal.

### Reglas obligatorias

- **Nunca usar `backdrop-blur`** en el overlay — el overlay debe ser `bg-black/60` sólido.
- **El `DialogContent` no debe tener `bg-popover`** — los colores se aplican desde `index.css` via `[data-slot="dialog-content"]`.
- **El `DialogFooter` no debe tener `bg-muted/50`** — fondo semitransparente visible en temas oscuros.

### Estilos globales en `index.css`

```css
[data-slot="dialog-content"] {
  background: var(--page-surface) !important;
  color: var(--page-text) !important;
  border: 1px solid var(--page-border) !important;
  box-shadow: 0 25px 50px -12px rgba(0,0,0,0.8) !important;
}
[data-slot="dialog-footer"] {
  background: var(--page-bg) !important;
  border-top-color: var(--page-border) !important;
}
```

Los PrimeReact `p-dialog` siguen usando sus propios selectores por tema (`[data-layout="X"] .p-dialog`).

### ConfirmDialog de PrimeReact

Para confirmaciones de acciones destructivas o importantes usar `confirmDialog()` de PrimeReact (no el componente shadcn). Requiere montar `<ConfirmDialog />` en el root del componente:

```tsx
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog'

// En el JSX del componente:
<ConfirmDialog />

// Para disparar:
confirmDialog({
  message: 'Descripción de la acción',
  header: 'Título',
  icon: 'pi pi-exclamation-triangle',
  acceptLabel: 'Confirmar',
  rejectLabel: 'Cancelar',
  acceptClassName: 'p-button-danger', // o 'p-button-success', etc.
  defaultFocus: 'reject',
  accept: async () => { /* acción */ },
})
```

---

## 10. Selects (shadcn / base-ui)

Los selects usan el componente shadcn `Select` (`@/components/ui/select`) basado en base-ui.

### Reglas obligatorias

- **Menú dinámico**: `SelectContent` debe usar `min-w-[max(var(--anchor-width),8rem)] w-max`.
- **Fondo sólido**: `SelectContent` no debe tener `bg-popover` ni `ring-1 ring-foreground/10`.
- **Texto seleccionado legible**: usar siempre la prop `label` en `SelectItem`.

### Patrón correcto con label separado

```tsx
<SelectItem value={plan.id} label={plan.nombre}>
  <span>{plan.nombre}</span>
  <span className="ml-auto pl-4 text-[0.65rem] opacity-60">${plan.precio}/mes</span>
</SelectItem>
```

### Estilos globales en `index.css`

```css
[data-slot="select-content"] {
  background: var(--page-surface) !important;
  color: var(--page-text) !important;
  border: 1px solid var(--page-border) !important;
}
[data-slot="select-trigger"] {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
  color: var(--input-text) !important;
}
[data-slot="select-item"]:focus {
  background: var(--page-bg) !important;
}
```

---

## 11. Tooltips

Los tooltips de PrimeReact se reducen globalmente al ~40% del tamaño default.

```css
.p-tooltip .p-tooltip-text {
  font-size: 0.55rem !important;
  padding: 0.18rem 0.45rem !important;
  line-height: 1.3 !important;
  border-radius: 0.3rem !important;
}
.p-tooltip .p-tooltip-arrow {
  width: 0.4rem !important;
  height: 0.4rem !important;
}
```

Estas reglas son globales (sin selector de tema) y aplican en todos los temas automáticamente.

---

## 12. Badges de estado

Los badges de estado (activo/inactivo, rol, compañía) siguen estos patrones:

### Estado activo/inactivo (semántico — no cambia con el tema)

```tsx
<span
  className={`inline-flex items-center gap-1.5 font-medium px-2 py-0.5 rounded-full ${
    activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
  }`}
  style={{ fontSize: '0.55rem' }}
>
  <span className={`w-1.5 h-1.5 rounded-full ${activo ? 'bg-green-600' : 'bg-red-500'}`} />
  {activo ? 'Activo' : 'Inactivo'}
</span>
```

### Badge de contexto (responde al tema)

Para mostrar el rol, compañía u otro contexto en sub-headers:

```tsx
<span
  className="inline-block text-xs font-medium px-2 py-0.5 rounded-full"
  style={{
    background: 'var(--page-surface)',
    border: '1px solid var(--page-border)',
    color: 'var(--page-muted)',
    fontSize: '0.55rem',
  }}
>
  {contexto}
</span>
```

---

## 13. Grid de detalle (permisos por módulo)

Cuando el tab detalle muestra una colección agrupada (ej. permisos agrupados por módulo), usar un grid de cards:

```tsx
<div className="grid grid-cols-2 gap-4">
  {Object.entries(porModulo).map(([modulo, items]) => (
    <div key={modulo} className="rounded-lg p-3"
      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
      <p className="text-xs font-semibold uppercase tracking-wider mb-2"
        style={{ color: 'var(--page-muted)' }}>
        {modulo}
      </p>
      <ul className="space-y-1.5">
        {items.map(item => (
          <li key={item} className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
            <code className="font-mono" style={{ fontSize: '0.6rem', color: 'var(--page-text)' }}>
              {item}
            </code>
          </li>
        ))}
      </ul>
    </div>
  ))}
</div>
```

---

## 14. Principios UX aplicados

- **Maestro → Detalle con tabs**: cuando una entidad tiene sub-entidades, usar tabs en lugar de drawers o paneles side-by-side.
- **Disabled explícito**: los tabs o acciones que requieren una selección previa deben tener `disabled` + `cursor-not-allowed` + `opacity-40`.
- **Acento único**: un solo color de acento (`#f97316` naranja) en toda la UI de página. El sidebar de temas claros puede usar el color del tema como activo.
- **Contraste sidebar/contenido**: el sidebar siempre tiene fondo oscuro independientemente del tema de página, para crear jerarquía visual.
- **Densidad de información**: las tablas usan una escala reducida (~20-40% menor que los defaults de PrimeReact) para mostrar más datos sin scroll.
- **Variables CSS sobre clases Tailwind**: para cualquier color que deba responder al tema, usar `style={{ color: 'var(--page-text)' }}` en lugar de `className="text-white"`.
- **Fondos sólidos en overlays**: ningún componente flotante (dialog, select, tooltip, menú) debe usar `backdrop-blur` ni fondos con canal alpha. Solo el overlay/mask puede tener opacidad (`bg-black/60`).
- **Persistencia por usuario**: el tema se guarda por `sub` del JWT en localStorage. No usar una key global compartida entre todos los usuarios del mismo dispositivo.
