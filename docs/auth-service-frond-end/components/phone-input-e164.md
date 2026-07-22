# PhoneInputE164 — Componente compartido de teléfono internacional

> **ESTADO:** ✅ Implementado  
> **Ubicación:** `src/ui/components/PhoneInputE164.tsx`  
> **Alcance:** Fase 1/2 — Formularios de compañía uniformados; Fase 2 pendiente (formularios de persona)

---

## Propósito

Input de teléfono internacional con salida siempre en formato E.164 (`+593999123456`). Utiliza la librería `react-phone-number-input` (que internamente usa `libphonenumber-js`, el motor que emplea WhatsApp Web).

Se exportan tres entidades:
- **`PhoneInputE164`** — componente controlado primitivo (manejo manual de props)
- **`PhoneInputE164Controller`** — wrapper react-hook-form (recomendado)
- **`parsePhoneToE164(raw)`** — helper para hidratar valores legacy de la BD

---

## API del componente

### `PhoneInputE164` (primitivo)

```typescript
export function PhoneInputE164({
  value: string | undefined,
  onChange: (value: string | undefined) => void,
  defaultCountry?: string            // default: 'EC' (Ecuador)
  disabled?: boolean                  // default: false
  id?: string
  className?: string
  placeholder?: string
  error?: boolean                     // muestra borde rojo si true
}: PhoneInputE164Props)
```

**Uso directo (raro — preferir Controller):**
```tsx
const [phone, setPhone] = useState<string | undefined>()

<PhoneInputE164
  value={phone}
  onChange={setPhone}
  placeholder="Ingresa tu teléfono"
/>
```

### `PhoneInputE164Controller` (react-hook-form)

```typescript
export function PhoneInputE164Controller<T extends FieldValues>({
  name: Path<T>,                    // nombre del campo en el form
  control: Control<T>,               // desde useForm({ control })
  defaultCountry?: string            // default: 'EC'
  disabled?: boolean
  placeholder?: string
  className?: string
}: PhoneInputE164ControllerProps<T>)
```

**Uso recomendado (con react-hook-form):**
```tsx
import { useForm } from 'react-hook-form'
import { PhoneInputE164Controller } from '@/ui/components/PhoneInputE164'

const { control, handleSubmit } = useForm({
  defaultValues: { whatsapp: '+593999123456' }
})

<form onSubmit={handleSubmit(onSubmit)}>
  <PhoneInputE164Controller
    name="whatsapp"
    control={control}
    placeholder="WhatsApp"
  />
  <button type="submit">Guardar</button>
</form>
```

### `parsePhoneToE164(raw)` — Helper para valores legacy

```typescript
export function parsePhoneToE164(
  raw: string | null | undefined
): {
  value: string | undefined   // E.164 si parse exitoso o input vacío, undefined si error
  parsedOk: boolean           // true = puede editarse; false = formato antiguo no reconocido
}
```

**Lógica de Opción A** (implementada):
- `null`, `undefined`, cadena vacía → `{ value: undefined, parsedOk: true }`
- `"0999123456"` (nacional con 0) → `{ value: "+593999123456", parsedOk: true }`
- `"+593999123456"` (ya E.164) → `{ value: "+593999123456", parsedOk: true }`
- `"593999123456"` (sin +) → `{ value: "+593999123456", parsedOk: true }`
- `"999123456"` (sin 0 ni prefijo) → `{ value: "+593999123456", parsedOk: true }`
- `"garbage-123"` (basura) → `{ value: undefined, parsedOk: false }` — se muestra warning al usuario

**Uso en hidratación de BD:**
```tsx
useEffect(() => {
  if (compania) {
    const parsed = parsePhoneToE164(compania.whatsapp)
    setWhatsappLegacy(!parsed.parsedOk)  // mostrar warning si no reconoce
    reset({
      whatsapp: parsed.value,  // undefined si no pudo parsear
    })
  }
}, [compania, reset])
```

Si `parsedOk === false`, el input queda vacío y se muestra un **warning inline**: "Formato antiguo detectado. Re-ingresa el número."

---

## País por defecto y selector

- **País por defecto:** `EC` (Ecuador)
- **Selector:** El componente expone un dropdown de países internacionales. El usuario puede cambiar a cualquier país, pero ⚠️ **caveat backend** (ver abajo).
- **Para cambiar el país por defecto:**
  ```tsx
  <PhoneInputE164Controller
    name="whatsapp"
    control={control}
    defaultCountry="US"  // ahora USA
  />
  ```

---

## Validación Zod

En el schema Zod del formulario:

```typescript
const schema = z.object({
  whatsapp: z.string()
    .optional()
    .refine(
      (v) => !v || /^\+[1-9]\d{6,14}$/.test(v),
      'Número de WhatsApp inválido'
    ),
})
```

- Si el campo está vacío → acepta (`.optional()`)
- Si no vacío → debe ser E.164 válido (regex `/^\+[1-9]\d{6,14}$/`)

---

## Comportamiento en formularios existentes

En los **4 formularios de compañía** (Fase 1) ya implementados:

1. `src/ui/features/platform/pages/CompaniaDetallePage/SucursalesTab/EditarCompaniaModal.tsx`
2. `src/ui/features/platform/pages/RegistrarGymWizard/steps/Step1Empresa.tsx`
3. `src/ui/features/platform/pages/CompaniasPage/RegistrarGymModal.tsx`
4. `src/ui/features/admin/pages/ConfiguracionPage.tsx`

**Cambios en común:**
- El campo `telefono` fue **ocultado del UI** (comentado en JSX). El campo sigue en el schema Zod como opcional y se envía al backend como `undefined`.
- El campo `whatsapp` ahora usa `<PhoneInputE164Controller name="whatsapp" ... />` en lugar de un input plano.
- Al abrir un formulario con datos existentes, `parsePhoneToE164` intenta hidratar. Si falla → input vacío + warning "Formato antiguo detectado. Re-ingresa el número."
- Validación: regex E.164 en el schema Zod.

---

## Caveat: Backend `PhoneNumberE164Normalizer` (EC-only)

**Importante para integradores:**

El backend `platform-service` tiene una clase `PhoneNumberE164Normalizer` que **solo procesa números ecuatorianos** (prefijo `+593`). 

**Locación:** `platform-service/src/main/java/com/gymadmin/platform/domain/validation/PhoneNumberE164Normalizer.java`

**Comportamiento:**
- Acepta sin error números en E.164 de otros países (p.ej. `+14155552671` USA).
- **Pero** cuando intenta enviar WhatsApp, el normalizer rechaza silenciosamente cualquier número que no sea `+593...` (válido, 9 dígitos tras el 593).
- **Resultado:** El input se guarda en la BD, pero **no se envía WhatsApp**. No hay error visible.

**Ejemplos:**
- Usuario elige país USA en el selector del input y guarda `+14155552671`.
- El frontend valida que sea E.164 ✅ (es válido).
- El backend guarda el número ✅.
- Cuando se dispara un job de WhatsApp (notificación de vencimiento, etc.), el `PhoneNumberE164Normalizer.normalizar("+14155552671")` retorna `Optional.empty()` (rechaza).
- **No se envía el mensaje**, pero **no se registra error** — la omisión es silenciosa.

**Mitigación:**
Si en el futuro se requiere multi-país (tenants fuera de Ecuador), `PhoneNumberE164Normalizer` es el único punto de extensión. Hoy es por diseño: el sistema es EC-only.

**Para los 4 formularios de compañía de hoy:**
Todas las compañías son ecuatorianas (por contexto del negocio), así que el caveat es teórico. Pero **si un usuario selecciona un país diferente y guarda, silenciosamente no recibirá WhatsApp** — este es un hallazgo conocido que hay que documentar para QA y soporte.

---

## i18n (Internationalization)

Nuevas claves agregadas en `src/i18n/locales/es.json` y `en.json`:

```json
{
  "phoneInput": {
    "placeholder": "Ingresa tu teléfono",
    "legacyFormat": "Formato antiguo detectado. Re-ingresa el número.",
    "invalid": "Número de teléfono inválido"
  }
}
```

Uso en el código:
```tsx
const { t } = useTranslation()
<PhoneInputE164Controller
  name="whatsapp"
  control={control}
  placeholder={t('phoneInput.placeholder')}
/>
```

---

## Datos tecnicospor tema (CSS)

El componente usa **variables CSS** (no Tailwind hardcodeado) para adaptarse a todos los temas:

```css
var(--input-bg)       /* fondo del input */
var(--input-border)   /* borde del input */
var(--input-text)     /* color del texto */
```

Se define un selector `.PhoneInput` interno que overridea estilos de `react-phone-number-input` vía clases Tailwind (p.ej. `[&_.PhoneInputInput]:px-3`).

Si se agrega un nuevo tema en `src/index.css`, estas tres variables se incluyen automáticamente y el input se adapta.

---

## Ejemplo completo (formulario de compañía)

```tsx
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { PhoneInputE164Controller, parsePhoneToE164 } from '@/ui/components/PhoneInputE164'

const schema = z.object({
  nombre: z.string().min(2),
  whatsapp: z.string()
    .optional()
    .refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Inválido'),
})

export function EditarCompaniaModal({ compania, onUpdated }) {
  const { control, reset, handleSubmit } = useForm({
    resolver: zodResolver(schema),
  })

  useEffect(() => {
    if (compania) {
      const { value, parsedOk } = parsePhoneToE164(compania.whatsapp)
      reset({
        nombre: compania.nombre,
        whatsapp: value,
      })
      if (!parsedOk) {
        // mostrar warning inline o toast
      }
    }
  }, [compania])

  const onSubmit = handleSubmit(async (data) => {
    await platformRepository.actualizarCompania(compania.id, data)
    onUpdated(data)
  })

  return (
    <form onSubmit={onSubmit}>
      <PhoneInputE164Controller
        name="whatsapp"
        control={control}
        placeholder="WhatsApp"
      />
      <button type="submit">Guardar</button>
    </form>
  )
}
```

---

## Fase 2 (pendiente)

Aún hay **6 formularios de persona** que usan inputs de `telefono` planos y necesitan migrar al mismo componente:

1. `src/ui/features/core/components/EditarClienteModal.tsx`
2. `src/ui/features/core/components/RegistrarClienteModal.tsx`
3. `src/ui/features/admin/pages/PersonasPage/CrearPersonaStep.tsx`
4. `src/ui/features/admin/pages/PersonaDetallePage/DatosPersonalesTab.tsx`
5. `src/ui/features/admin/pages/PersonasPage/CrearPersonaModal.tsx`
6. `src/ui/features/admin/pages/OperadoresPage/CrearOperadorModal.tsx`

El plan es uniformar estos 6 formularios en una segunda iteración, replicando el patrón de Fase 1 (ocultar `telefono`, usar `PhoneInputE164Controller` para el número interactivo).

---

## Resumen

| Aspecto | Detalles |
|---------|----------|
| **Propósito** | Input E.164 internacional para WhatsApp |
| **Exporta** | `PhoneInputE164` + `PhoneInputE164Controller` + `parsePhoneToE164()` |
| **País default** | Ecuador (`EC`) |
| **Salida** | E.164 (ej. `+593999123456`) |
| **Validación** | Regex `/^\+[1-9]\d{6,14}$/` en Zod |
| **Caveat backend** | `PhoneNumberE164Normalizer` solo procesa `+593` (EC-only) |
| **Fase 1 ✅** | 4 formularios de compañía |
| **Fase 2 📋** | 6 formularios de persona (pendiente) |
| **CSS** | Temas mediante variables CSS (`--input-bg`, etc.) |
| **i18n** | Claves `phoneInput.*` en `es.json` / `en.json` |
