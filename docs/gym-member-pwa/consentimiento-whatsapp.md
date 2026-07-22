# Consentimiento de WhatsApp del Socio — gym-member-pwa

> **Documento técnico:** Opt-in/opt-out para recordatorios de vencimiento de membresía vía WhatsApp.  
> **Fecha de verificación:** 2026-07-21 contra código fuente (auth-service `PersonaApplicationService`, `PersonaHandler`; core-service `PersonaPersistenceAdapter`; gym-member-pwa `ProfilePage.tsx`).

---

## Resumen ejecutivo

La PWA permite que el socio **revoque** su consentimiento de WhatsApp desde `/profile`. Es el **único lugar del sistema** donde se puede hacer opt-out. Los recordatorios de membresía expirada solo se envían si `identidad.personas.acepta_whatsapp = true`. Sin consentimiento explícito, la columna queda en `false` por defecto — el socio nunca recibe avisos, sin error visible.

---

## Aclaración: dos consentimientos distintos

Existen **dos** campos `acepta_whatsapp` en tablas diferentes que no deben confundirse:

| Consentimiento | Tabla | Propósito | Interfaz |
|---|---|---|---|
| **Dueño del gym** | `tenant.companias` | Autoriza avisos de vencimiento de LA SUSCRIPCIÓN del gym | Panel de administrador (gym-administrator) |
| **Socio/cliente** | `identidad.personas` | Autoriza avisos de vencimiento de SU MEMBRESÍA | PWA (perfil) |

Esta documentación cubre únicamente el del **socio** (`identidad.personas`).

---

## Puntos de captura declarados

El DDL de `identidad.personas` declara tres puntos donde se captura consentimiento del socio:

| # | Punto | Status | Responsable | Detalles |
|---|-------|--------|-------------|----------|
| 1 | **Recepción** | ✅ Impl. | core-service `POST /clientes` | Casilla desmarcada. Preserva fecha original si el socio ya consintió en otro gym (no reescribe). |
| 2 | **Perfil PWA** | ✅ Impl. | gym-member-pwa `ProfilePage` + auth-service `PATCH /personas/{id}/consentimiento-wa` | Switch on/off. **ÚNICO lugar de opt-out.** Reescribe fecha al cambiar. |
| 3 | **Registro público** | ❌ Pendiente | gym-member-pwa (backlog) | Auto-registro: cuando esté implementado, capturará consentimiento inicial. |

### Asimetría PWA vs. Recepción

**PWA (auth-service, línea 114 de `PersonaApplicationService`):**
```java
OffsetDateTime fecha = acepta ? OffsetDateTime.now() : null;
return personaPort.updateConsentimientoWa(id, acepta, fecha)
```
Siempre reescribe. Declara en primera persona → OK que refleje la declaración actual.

**Recepción (core-service, línea 93 de `PersonaPersistenceAdapter`):**
```sql
UPDATE identidad.personas
   SET acepta_whatsapp = true, fecha_consentimiento_wa = now()
 WHERE id = :id AND acepta_whatsapp = false  -- ← preserva si ya es true
```
Solo otorga, nunca revoca. Un tercero afilia al socio → no puede reescribir su declaración previa.

---

## Regla de negocio: opt-in afirmativo (Meta compliance)

Meta exige consentimiento **explícito y afirmativo**:
- ❌ Casilla pre-marcada → sin valor probatorio, arriesga el bloqueo de WhatsApp.
- ✅ Casilla desmarcada que el usuario marca → prueba de opt-in.

Por eso en todos los puntos de captura el control nace **desmarcado**.

---

## Implementación en ProfilePage

**Ubicación:** `src/ui/pages/profile/ProfilePage.tsx`

**Componente sub-página:** `WhatsAppConsentBlock` (línea 357)

**Props (recibidas del padre):**
- `consent` — objeto `ConsentimientoWaPersonaResponse | null`
- `saving` — boolean (en vuelo)
- `onToggle` — callback `(acepta: boolean) => Promise<void>`
- `idPersona` — declarado en interfaz pero no usado dentro del componente

**Estado en ProfilePage (línea 62-63):**
```tsx
const [waConsent, setWaConsent] = useState<ConsentimientoWaPersonaResponse | null>(null)
const [savingWa, setSavingWa] = useState(false)
```

**Flujo de click (línea 314-326):**
1. Usuario hace click en toggle.
2. Callback `onToggle(acepta)` en ProfilePage llama a `authRepository.patchConsentimientoWaPersona(user.id_persona, acepta)`.
3. En response, actualiza estado local: `setWaConsent(res)`.
4. Toast de confirmación o error.

**Nota:** no existe `usePerfil()` ni invalidación de perfil — el estado se maneja localmente en ProfilePage mediante `useState`.

---

## Endpoint PATCH en auth-service

**Ruta:** `PATCH /api/v1/personas/{id}/consentimiento-wa`  
**Documentación:** [../auth-service/api/personas.md#patch-personasidconsentimiento-wa](../auth-service/api/personas.md#patch-personasidconsentimiento-wa)

**Handler:** `PersonaHandler.actualizarConsentimientoWa()` (línea 116)

**Respuestas HTTP:**
- **200** — Consentimiento actualizado.
- **400** — Body inválido (campo `acepta` requerido y debe ser boolean).
- **404** — Persona no encontrada.
- **401** — Token ausente o inválido.

**Comportamiento en backend:**
- Si `acepta=true` → sella `fecha_consentimiento_wa = NOW()` como prueba ante Meta. **Siempre reescribe** (a diferencia de recepción).
- Si `acepta=false` → limpia la fecha a `NULL`. El socio ha revocado.

---

## DTO response

```typescript
export interface ConsentimientoWaPersonaResponse {
  idPersona: number
  aceptaWhatsapp: boolean
  fechaConsentimientoWa: string | null  // ISO 8601, null si no consintió
}
```

Ubicación: `src/application/usecase/auth.types.ts` (línea 96).

---

## Fallas silenciosas

**Caso:** Socio nunca consintió (default `false`)

1. Sin consentimiento explícito → `acepta_whatsapp = false`.
2. Job de WhatsApp evalúa `WHERE acepta_whatsapp = true` → no incluye al socio.
3. **Resultado:** no recibe aviso de vencimiento, sin error visible.

**Cómo ocurre:**
- Se registró online sin pasar por recepción (auto-registro aún no existe).
- Recepción lo registró sin marcar `Acepta WhatsApp`.
- Nunca visitó `/profile` para consentir.

**Mitigation:** implementar punto #3 (registro público en backlog) para que todo socio tenga oportunidad de consentir durante creación de cuenta.

---

## Casos de uso

### A: Socio consiente en recepción

1. Recepcionista marca `Acepta WhatsApp = true` al crear cliente.
2. Backend: `INSERT identidad.personas` con `acepta_whatsapp=true, fecha_consentimiento_wa=NOW()`.
3. Socio comienza a recibir avisos de vencimiento.
4. Socio puede revocar en `/profile` (PWA).

### B: Socio se afilia a otro gym, nunca consintió el primero

1. Persona existe en BD con `acepta_whatsapp=false`.
2. Recepción del nuevo gym intenta marcar `true`.
3. Backend: `UPDATE WHERE acepta_whatsapp=false` … → marca como true, sella fecha.
4. Socio comienza a recibir avisos desde este gym.

### C: Socio revoca consentimiento en PWA

1. Entra a `/profile`, ve switch activado (`aceptaWhatsapp=true`).
2. Click para desactivar.
3. Frontend: `PATCH /personas/{id}/consentimiento-wa` con `{ "acepta": false }`.
4. Backend: `acepta_whatsapp=false, fecha_consentimiento_wa=NULL`.
5. PWA actualiza switch a inactivo, quita fecha.
6. Socio nunca más recibe avisos (hasta re-consentir).

---

## Referencia de código

| Ubicación | Responsabilidad |
|-----------|-----------------|
| `gym-member-pwa/src/ui/pages/profile/ProfilePage.tsx:62-63` | Estado local `waConsent`, `savingWa` |
| `gym-member-pwa/src/ui/pages/profile/ProfilePage.tsx:357` | Componente `WhatsAppConsentBlock` |
| `gym-member-pwa/src/infrastructure/http/AuthHttpRepository.ts:79` | `patchConsentimientoWaPersona(id, acepta)` |
| auth-service `PersonaApplicationService:113-126` | `actualizarConsentimientoWa(id, acepta)` use case |
| auth-service `PersonaHandler:116` | Handler HTTP + validación |
| core-service `PersonaPersistenceAdapter:87-97` | `otorgarConsentimientoWa(idPersona)` — recepción |
| DDL `14_create_table_identidad_personas.sql:22-23` | Columnas `acepta_whatsapp`, `fecha_consentimiento_wa` |

---

## Sumario

1. **Dos consentimientos:** no confundir dueño (tenant) con socio (identidad).
2. **Único opt-out:** solo PWA permite revocar.
3. **Reescritura asimétrica:** PWA reescribe fecha (primera persona), recepción preserva (tercero).
4. **Opt-in afirmativo:** Meta exige casilla desmarcada.
5. **Falla silenciosa:** sin consentimiento, no hay aviso visible de problema.
6. **Estado local:** ProfilePage maneja `waConsent` localmente, sin hook de perfil.
