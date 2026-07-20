# Especificación UX — Solicitud de Membresía

**Versión:** 1.0  
**Fecha:** 2026-07-17  
**Estado:** Spec definida, implementación pendiente  
**Idioma:** Español (los ejemplos de UI reflejan `es.json`; las claves son idioma-agnósticas)

---

## 1. Visión General

Cuando un cliente sin membresía activa entra a `/membresia`, en lugar de mostrar solo un estado vacío, debe ver un catálogo de tipos de membresía disponibles en su gimnasio. Al seleccionar uno, genera una solicitud que el dueño procesa en el dashboard.

### Flujo principal
```
GET /membresia
  ↓
¿Tiene membresia_activa?
  ├─ Sí → MembresiaCard (tarjeta actual, sin cambios)
  ├─ No ↓
      ¿Tiene solicitud_pendiente?
      ├─ Sí → SolicitudPendienteCard
      └─ No → CatalogoMembresias
```

---

## 2. Estructura de Datos

### `MiPerfilResponse` (backend)

Extender el tipo retornado por `GET /api/v1/clientes/me/perfil` para incluir:

```typescript
{
  // ... campos existentes (id, nombre, etc.)
  membresia_activa?: { ... },          // ya existe
  solicitud_pendiente?: {               // NUEVO
    id: number,
    id_tipo_membresia: number,
    tipo_nombre: string,                // ej. "Plan Monthly"
    precio_actual: number,              // ej. 49.99
    creacion_fecha: string              // ISO 8601: "2026-07-17T14:30:00Z"
  } | null
}
```

### `TipoMembresia` (tipos de membresía)

Estructura retornada por `GET /api/v1/tipos-membresia`:

```typescript
[
  {
    id: number,
    nombre: string,                     // ej. "Plan Mensual"
    precio: number,                     // ej. 49.99
    modo_control: "calendario" | "accesos",
    // Cuando modo_control = "calendario":
    duracion_meses: number,             // ej. 1, 3, 12
    // Cuando modo_control = "accesos":
    accesos_incluidos: number           // ej. 12
  }
]
```

---

## 3. Componentes Nuevos

### 3.1 `<CatalogoMembresias/>`

**Ubicación:** `gym-member-pwa/src/ui/pages/membresia/CatalogoMembresias.tsx` (co-locado en el mismo archivo que `MembresiaPage` o en sub-carpeta `components/`)

**Props:**
```typescript
interface CatalogoMembresiasProps {
  onSolicitudCreada?: () => void;  // callback opcional para invalidar store
}
```

**Responsabilidades:**
1. Fetch `GET /api/v1/tipos-membresia` via `coreRepository.getTiposMembresia()`.
2. Mostrar lista de cards.
3. Al hacer "Solicitar": abrir modal de confirmación.
4. POST via `coreRepository.solicitarMembresia({ id_tipo_membresia: number })`.
5. Manejo de errores y éxito con toast.

**Estados:**
- `loading` — skeleton cards o spinner.
- `error` — toast de error con i18n key `membresia.catalogo.errors.load` y botón reintentar.
- `empty` — lista vacía (caso extremo: ningún tipo habilitado). Mensaje i18n `membresia.catalogo.vacia`.
- `success` — cards renderizadas.

**UI:**

```tsx
<div className="space-y-4">
  <h2 className="text-xl font-bold">{t("membresia.catalogo.titulo")}</h2>
  {/* Subtitle opcional: "Elige un plan y solicita tu membresía" */}
  
  <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
    {tipos.map((tipo) => (
      <div key={tipo.id} className="border rounded-lg p-4 space-y-3">
        
        {/* Header */}
        <div>
          <h3 className="font-semibold text-base">{tipo.nombre}</h3>
          <p className="text-sm text-gray-600">
            ${tipo.precio.toFixed(2)}
          </p>
        </div>

        {/* Modo y duración */}
        <div className="flex gap-2">
          <span className="inline-block px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
            {tipo.modo_control === "calendario"
              ? t("membresia.catalogo.modo.calendario")
              : t("membresia.catalogo.modo.accesos")}
          </span>
          
          <span className="inline-block px-2 py-1 rounded-full text-xs bg-gray-100 text-gray-800">
            {tipo.modo_control === "calendario"
              ? formatDuracion(tipo.duracion_meses) // "1 mes", "12 meses", etc.
              : `${tipo.accesos_incluidos} ${t("membresia.catalogo.accesos")}`}
          </span>
        </div>

        {/* Botón */}
        <button
          onClick={() => abrirModalConfirmacion(tipo)}
          className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700"
        >
          {t("membresia.catalogo.solicitar")}
        </button>
      </div>
    ))}
  </div>
</div>
```

**Helper: `formatDuracion(meses: number): string`**

```typescript
function formatDuracion(meses: number): string {
  if (meses === 1) return t("membresia.duracion.un_mes");     // "1 mes"
  if (meses === 3) return t("membresia.duracion.tres_meses"); // "3 meses"
  if (meses === 12) return t("membresia.duracion.un_anio");   // "1 año"
  return `${meses} ${t("membresia.duracion.meses")}`;         // "6 meses", etc.
}
```

**Modal de confirmación:**

```tsx
<Modal open={modalOpen} onClose={cerrarModal}>
  <h3 className="text-lg font-bold">
    {t("membresia.catalogo.confirmar.titulo")}
  </h3>
  <p className="text-gray-600 mt-2">
    {t("membresia.catalogo.confirmar.mensaje", { nombre: tipoSeleccionado?.nombre })}
  </p>
  <p className="font-semibold mt-4">
    ${tipoSeleccionado?.precio.toFixed(2)}
  </p>
  <div className="flex gap-2 mt-6">
    <button onClick={cerrarModal} className="flex-1 border rounded py-2">
      {t("comun.cancelar")}
    </button>
    <button
      onClick={confirmarSolicitud}
      disabled={loading}
      className="flex-1 bg-blue-600 text-white rounded py-2"
    >
      {loading ? t("comun.cargando") : t("membresia.catalogo.confirmar.boton")}
    </button>
  </div>
</Modal>
```

**Manejo de errores:**

```typescript
try {
  const response = await coreRepository.solicitarMembresia(tipo.id);
  
  // Éxito
  showToast(t("membresia.catalogo.exito"), "success");
  invalidarPerfil();  // re-fetch perfil para que aparezca SolicitudPendienteCard
  onSolicitudCreada?.();
  
} catch (error) {
  if (error.response?.status === 409 && error.response?.data?.codigo === "solicitud_ya_existe") {
    showToast(t("membresia.catalogo.errors.solicitud_ya_existe"), "warning");
    invalidarPerfil();  // re-fetch: podría haber otra solicitud más reciente
  } else if (error.response?.status === 400) {
    showToast(t("membresia.catalogo.errors.solicitar"), "error");
  } else {
    showToast(t("comun.error_desconocido"), "error");
  }
}
```

---

### 3.2 `<SolicitudPendienteCard/>`

**Ubicación:** `gym-member-pwa/src/ui/pages/membresia/SolicitudPendienteCard.tsx` (co-locado o en `components/`)

**Props:**
```typescript
interface SolicitudPendienteCardProps {
  solicitud: {
    id: number;
    id_tipo_membresia: number;
    tipo_nombre: string;
    precio_actual: number;
    creacion_fecha: string;  // ISO 8601
  };
}
```

**UI:**

```tsx
import { Clock } from "lucide-react";
import { formatDistanceToNow } from "date-fns";
import { es } from "date-fns/locale";

export function SolicitudPendienteCard({ solicitud }: SolicitudPendienteCardProps) {
  const tiempoRelativo = formatDistanceToNow(new Date(solicitud.creacion_fecha), {
    locale: es,
    addSuffix: true
  });

  return (
    <div className="border-l-4 border-yellow-500 bg-yellow-50 p-4 rounded">
      
      {/* Icono + Header */}
      <div className="flex gap-3">
        <Clock className="w-6 h-6 text-yellow-600 flex-shrink-0 mt-1" />
        <div className="flex-1">
          <h3 className="font-semibold text-base">
            {t("membresia.solicitud.titulo")}
          </h3>
          
          {/* Nombre del tipo */}
          <p className="text-sm text-gray-700 mt-1">
            {solicitud.tipo_nombre}
          </p>
          
          {/* Mensaje principal */}
          <p className="text-sm text-gray-600 mt-2">
            {t("membresia.solicitud.mensaje")}
          </p>
          
          {/* Precio */}
          <p className="font-semibold text-base mt-2">
            ${solicitud.precio_actual.toFixed(2)}
          </p>
          
          {/* Tiempo relativo */}
          <p className="text-xs text-gray-500 mt-2">
            {t("membresia.solicitud.hace", { time: tiempoRelativo })}
          </p>
        </div>
      </div>

      {/* Sin botón cancelar — decisión de producto */}
    </div>
  );
}
```

**Notas:**
- Usa `lucide-react` (ya instalado en el proyecto).
- Usa `date-fns` para formatear tiempo relativo (verificar si ya está en `package.json`; si no, agregar).
- El locale de `date-fns` es español (`es`).
- Sin botón de acción: staff cancela desde el dashboard.

---

## 4. Integración en `MembresiaPage.tsx`

**Ubicación:** `gym-member-pwa/src/ui/pages/membresia/MembresiaPage.tsx`

**Cambios a la lógica de renderizado:**

```tsx
export function MembresiaPage() {
  const { data, isLoading, error } = useClientePerfil();  // custom hook o similar

  if (isLoading) return <LoadingMembresia />;
  if (error) return <ErrorMembresia />;
  if (!data) return <EmptyMembresia />;

  // ========================
  // NUEVA LÓGICA (3 BRANCHES)
  // ========================
  
  if (data.membresia_activa) {
    // Ya tiene membresía: mostrar tarjeta actual (sin cambios)
    return (
      <div className="p-4 space-y-4">
        <h1 className="text-2xl font-bold">{t("membresia.titulo")}</h1>
        <MembresiaCard membresia={data.membresia_activa} />
      </div>
    );
  }

  if (data.solicitud_pendiente) {
    // Tiene solicitud en proceso: mostrar tarjeta de solicitud
    return (
      <div className="p-4 space-y-4">
        <h1 className="text-2xl font-bold">{t("membresia.titulo")}</h1>
        <SolicitudPendienteCard solicitud={data.solicitud_pendiente} />
      </div>
    );
  }

  // No tiene membresía ni solicitud: mostrar catálogo
  return (
    <div className="p-4 space-y-4">
      <h1 className="text-2xl font-bold">{t("membresia.titulo")}</h1>
      <CatalogoMembresias
        onSolicitudCreada={() => {
          // Invalidar store y re-fetch del perfil
          invalidarPerfil();
        }}
      />
    </div>
  );
}
```

---

## 5. Repositorio y Métodos

### Extensión de `CoreRepository.ts` (puerto)

```typescript
export interface CoreRepository {
  // ... métodos existentes ...

  // NUEVOS:
  getTiposMembresia(): Promise<TipoMembresia[]>;

  solicitarMembresia(data: {
    id_tipo_membresia: number;
  }): Promise<SolicitudMembresia>;
}
```

### Implementación en `CoreHttpRepository.ts`

```typescript
async getTiposMembresia(): Promise<TipoMembresia[]> {
  const response = await this.httpClient.get<TipoMembresia[]>(
    `${this.baseUrl}/tipos-membresia`
  );
  return response.data;
}

async solicitarMembresia(data: {
  id_tipo_membresia: number;
}): Promise<SolicitudMembresia> {
  const response = await this.httpClient.post<SolicitudMembresia>(
    `${this.baseUrl}/clientes/me/membresias/solicitar`,
    data
  );
  return response.data;
}
```

---

## 6. Internacionalización (i18n)

### Nuevas claves en `src/i18n/locales/es.json`

```json
{
  "membresia": {
    "titulo": "Mi Membresía",
    "empty": {
      "title": "Sin membresía activa",
      "description": "No tienes una membresía activa. Selecciona un plan.",
      "retry": "Reintentar"
    },
    "catalogo": {
      "titulo": "Membresías disponibles",
      "subtitulo": "Elige un plan y solicita tu membresía",
      "solicitar": "Solicitar",
      "vacia": "No hay membresías disponibles en este momento",
      "modo": {
        "calendario": "Por calendario",
        "accesos": "Por accesos"
      },
      "accesos": "accesos",
      "confirmar": {
        "titulo": "Confirmar solicitud",
        "mensaje": "Solicitar {{nombre}}. Acércate a caja para completar el pago.",
        "boton": "Confirmar"
      },
      "exito": "Solicitud creada. Acércate a caja para completar el pago.",
      "errors": {
        "load": "Error cargando tipos de membresía",
        "solicitar": "Error al crear la solicitud",
        "solicitud_ya_existe": "Ya tienes una solicitud pendiente"
      }
    },
    "duracion": {
      "un_mes": "1 mes",
      "tres_meses": "3 meses",
      "un_anio": "1 año",
      "meses": "meses"
    },
    "solicitud": {
      "titulo": "Solicitud enviada",
      "mensaje": "Acércate a caja para completar el pago",
      "hace": "Enviada {{time}}"
    }
  },
  "comun": {
    "cancelar": "Cancelar",
    "cargando": "Cargando...",
    "error_desconocido": "Error desconocido"
  }
}
```

### Nuevas claves en `src/i18n/locales/en.json`

```json
{
  "membresia": {
    "titulo": "My Membership",
    "empty": {
      "title": "No active membership",
      "description": "You don't have an active membership. Select a plan.",
      "retry": "Retry"
    },
    "catalogo": {
      "titulo": "Available memberships",
      "subtitulo": "Choose a plan and request your membership",
      "solicitar": "Request",
      "vacia": "No memberships available at this time",
      "modo": {
        "calendario": "By calendar",
        "accesos": "By visits"
      },
      "accesos": "visits",
      "confirmar": {
        "titulo": "Confirm request",
        "mensaje": "Request {{nombre}}. Go to the desk to complete payment.",
        "boton": "Confirm"
      },
      "exito": "Request created. Go to the desk to complete payment.",
      "errors": {
        "load": "Error loading membership types",
        "solicitar": "Error creating request",
        "solicitud_ya_existe": "You already have a pending request"
      }
    },
    "duracion": {
      "un_mes": "1 month",
      "tres_meses": "3 months",
      "un_anio": "1 year",
      "meses": "months"
    },
    "solicitud": {
      "titulo": "Request sent",
      "mensaje": "Go to the desk to complete payment",
      "hace": "Sent {{time}} ago"
    }
  },
  "comun": {
    "cancelar": "Cancel",
    "cargando": "Loading...",
    "error_desconocido": "Unknown error"
  }
}
```

---

## 7. Flujos de Error

### Escenario 1: Fallo al cargar tipos de membresía

```
Usuario entra a /membresia sin membresía activa
  ↓
GET /api/v1/tipos-membresia falla (500, timeout, etc.)
  ↓
Mostrar toast: "Error cargando tipos de membresía"
Mostrar botón "Reintentar"
  ↓
Al reintentar: new GET /api/v1/tipos-membresia
```

### Escenario 2: Crear solicitud exitosa

```
Usuario hace click en "Solicitar"
  ↓
Modal de confirmación
  ↓
POST /api/v1/clientes/me/membresias/solicitar
  ↓
200 OK
  ↓
Toast: "Solicitud creada. Acércate a caja para completar el pago."
  ↓
Invalidar cache de perfil
  ↓
Re-fetch GET /api/v1/clientes/me/perfil
  ↓
Response incluye solicitud_pendiente
  ↓
Renderizar <SolicitudPendienteCard/> (misma página, efecto smooth)
```

### Escenario 3: Solicitud ya existe

```
Usuario hace click en "Solicitar"
  ↓
POST /api/v1/clientes/me/membresias/solicitar
  ↓
409 Conflict
{
  "codigo": "solicitud_ya_existe",
  "mensaje": "Ya existe una solicitud pendiente"
}
  ↓
Toast warning: "Ya tienes una solicitud pendiente"
  ↓
Invalidar perfil (puede haber cambios en backend)
  ↓
Re-fetch y re-renderizar
```

### Escenario 4: Validación (400)

```
Usuario (por cualquier motivo) hace POST con id_tipo_membresia inválido
  ↓
400 Bad Request
{
  "field": "id_tipo_membresia",
  "message": "Tipo no existe"
}
  ↓
Toast error: "Error al crear la solicitud"
No invalidar perfil
```

---

## 8. Testing Considerations

### Unit Tests (Componentes)

- **`CatalogoMembresias.test.tsx`**
  - Mock `coreRepository.getTiposMembresia()`.
  - Renderiza lista de tipos.
  - Click en "Solicitar" abre modal.
  - Llamada a `solicitarMembresia()`.
  - Validar toast de éxito/error.

- **`SolicitudPendienteCard.test.tsx`**
  - Props válidas → renderiza tarjeta.
  - Tiempo relativo se calcula correctamente.
  - Sin botón cancelar.

- **`MembresiaPage.test.tsx`**
  - Mock `useClientePerfil()` con 3 escenarios (activa, solicitud, vacío).
  - Renderiza componente correcto según estado.

### Integration Tests

- Flujo end-to-end: Login → `/membresia` sin membresía → ver catálogo → click solicitar → ver solicitud pendiente.

---

## 9. Dependencias

| Librería | Versión | Por qué | Ya instalada |
|----------|---------|--------|--------------|
| `react-i18next` | `^13.0` | i18n | Posiblemente no (ver Tarea 7) |
| `i18next` | `^24.0` | i18n | Posiblemente no |
| `date-fns` | `^3.0` | Tiempo relativo | Posiblemente no |
| `lucide-react` | `^0.x` | Iconos | Sí, según descripción |

---

## 10. Consideraciones de Diseño

1. **Sin botón cancelar en `SolicitudPendienteCard`**: Solo staff puede cancelar solicitudes desde el dashboard. Es una decisión de UX para evitar cancelaciones accidentales y fomenza la interacción con staff en caja.

2. **Catálogo siempre visible**: No hay "ocultar" el catálogo después de solicitar. Al re-fetch, desaparece automáticamente (se renderiza `SolicitudPendienteCard` en su lugar).

3. **Duraciones hardcodeadas en helper**: Si el backend retorna valores arbitrarios de meses/accesos, el helper debe ser extensible (ej. usar patrones genéricos).

4. **Precio en solicitud**: El precio se calcula en backend (puede variar por plan, descuentos, etc.). Frontend solo muestra.

5. **Invalidación de cache**: Asume un sistema de cache/store (Zustand o similar) con método `invalidarPerfil()`. Alternativa: polling o WebSocket si se requiere update real-time.

---

## 11. Roadmap de Implementación

**Fase 1 (Backend):**
1. Crear tabla `tipos_membresia` si no existe.
2. Crear tabla `solicitudes_membresia`.
3. Endpoint `GET /api/v1/tipos-membresia`.
4. Endpoint `POST /api/v1/clientes/me/membresias/solicitar`.
5. Extender `MiPerfilResponse` con campo `solicitud_pendiente`.

**Fase 2 (Frontend):**
1. Crear `CatalogoMembresias.tsx`.
2. Crear `SolicitudPendienteCard.tsx`.
3. Actualizar `MembresiaPage.tsx` con lógica de 3 branches.
4. Agregar métodos a `CoreRepository` / `CoreHttpRepository`.
5. Agregar i18n keys.
6. Tests unitarios.

**Fase 3 (Cross-cutting):**
1. Dashboard admin: mostrar solicitudes en "Mi Empresa" o sección nueva.
2. Botón "Aceptar/Rechazar" en dashboard.
3. Envío de notificación a cliente (WhatsApp, email) cuando se procesa.

---

## 12. Preguntas Frecuentes

**P: ¿Qué pasa si un tipo de membresía se desactiva mientras el cliente ve el catálogo?**  
R: El backend rechaza la solicitud con 404 o 400. Frontend muestra toast de error. Cliente debe refrescar.

**P: ¿Puedo tener múltiples solicitudes pendientes?**  
R: No. El backend valida (409 `solicitud_ya_existe`). Frontend asume máximo 1 en `solicitud_pendiente`.

**P: ¿Dónde ve el dueño las solicitudes?**  
R: En el dashboard `gym-administrator` (otro feature, no documentado aquí). Ver [`docs/gym-administrator/requirements/solicitudes-membresia.md`](../gym-administrator/requirements/solicitudes-membresia.md).

**P: ¿El cliente puede cambiar de idea después de solicitar?**  
R: Solo staff puede rechazar o cancelar desde caja/dashboard. No hay botón en PWA.

**P: ¿Se notifica al dueño automáticamente?**  
R: Depende del backend. Probablemente vía WebSocket, polling o task scheduler. No documentado aquí.
