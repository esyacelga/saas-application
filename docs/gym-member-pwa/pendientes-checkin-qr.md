# Pendientes — Flujo QR desde auth-service-front-end

## Contexto

El panel de administración (`auth-service-front-end`) genera un código QR por sucursal.
Al escanearlo, el cliente abre esta PWA en:

```
/login?qr=<qrToken>
```

El `LoginPage` ya resuelve el token contra el backend y muestra logo, nombre de la
compañía y nombre de la sucursal. **Lo que falta** es cerrar el ciclo: después de que el
cliente inicia sesión, registrar su asistencia automáticamente con el mismo token que
ya fue escaneado, sin pedirle que lo escanee de nuevo.

---

## Qué devuelve el backend al resolver el QR

**Endpoint:** `GET /api/v1/auth/gimnasio/by-qr/{qrToken}` (auth-service:8080, público)

**Response (snake_case):**
```json
{
  "id_compania":     12,
  "id_sucursal":     5,
  "nombre_compania": "GymX Fitness",
  "nombre_sucursal": "Sucursal Norte",
  "logo_url":        "https://..."
}
```

Estos campos ya están en `GymByQrResponse` (`src/application/usecase/auth.types.ts`).

---

## Pendiente 1 — Guardar el `qrToken` en estado durante el login

**Archivo:** `src/ui/pages/login/LoginPage.tsx`

El `qrToken` se lee en el `useEffect` pero no se guarda en estado. Después del login
manual/Google/Facebook se pierde. Hay que capturarlo:

```tsx
// Agregar junto a los otros useState:
const [pendingQrToken, setPendingQrToken] = useState<string | null>(null)

// En el useEffect, guardar el token:
useEffect(() => {
  const qrToken = searchParams.get('qr')
  if (!qrToken) return
  setPendingQrToken(qrToken)            // ← agregar esta línea
  authRepository
    .getGymByQr(qrToken)
    .then((data) => { ... })
}, [])
```

---

## Pendiente 2 — Auto check-in después del login

Una vez que el usuario inicia sesión (manual, Google o Facebook), si vino desde un QR
hay que registrar la asistencia automáticamente **antes** de navegar a `/home`.

El endpoint ya existe: `POST /api/v1/asistencias/qr` (attendance-service:8084, requiere JWT).

### Opción A — Check-in inmediato en `LoginPage` (recomendada)

Después de `setTokens()`, llamar al attendance-service y navegar según el resultado:

```tsx
// En onSubmit (login manual):
const onSubmit = async (data: FormData) => {
  setLoading(true)
  try {
    const res = await authRepository.loginManual(data)
    setTokens(res.access_token, res.refresh_token)

    if (pendingQrToken) {
      // navegar a check-in pasando el token para auto-procesar
      navigate('/check-in', { replace: true, state: { autoQrToken: pendingQrToken } })
    } else {
      navigate('/home', { replace: true })
    }
  } catch {
    toast.error(t('login.errors.invalidCredentials'))
  } finally {
    setLoading(false)
  }
}
```

Hacer lo mismo en `handleGoogleSuccess` y `handleFacebookLogin`.

### Opción B — Check-in directo sin redirigir a `/check-in`

Si se prefiere no redirigir, hacer el POST aquí mismo y navegar a `/home` con el
resultado en state para mostrarlo en un modal/toast:

```tsx
if (pendingQrToken) {
  try {
    const checkIn = await attendanceRepository.checkInQr(pendingQrToken)
    navigate('/home', { replace: true, state: { checkInResult: checkIn } })
  } catch {
    navigate('/home', { replace: true })
  }
}
```

---

## Pendiente 3 — Recibir `autoQrToken` en `CheckInPage` y procesarlo

**Archivo:** `src/ui/pages/attendance/CheckInPage.tsx`

Si se elige la Opción A, `CheckInPage` debe detectar cuando llega con un token ya
listo (no escanear de nuevo) y procesarlo directamente:

```tsx
import { useLocation } from 'react-router-dom'

export function CheckInPage() {
  const location = useLocation()
  const autoQrToken = (location.state as { autoQrToken?: string } | null)?.autoQrToken

  // Procesar automáticamente al montar si viene con token
  useEffect(() => {
    if (!autoQrToken) return
    setState('loading')
    attendanceRepository.checkInQr(autoQrToken)
      .then((data) => { setResult(data); setState('success') })
      .catch((err) => { setErrorMsg(getApiErrorMessage(err, t('checkin.error.defaultMessage'))); setState('error') })
  }, [autoQrToken])

  // El resto del componente no cambia — si no hay autoQrToken,
  // muestra la cámara normalmente.
  ...
}
```

Con esto, el flujo queda:
```
Escanea QR → /login?qr=TOKEN → login → /check-in (con autoQrToken en state)
             → auto POST /asistencias/qr → muestra resultado de asistencia
```

---

## Pendiente 4 — Usar `idSucursal` si es necesario

**Archivo:** `src/ui/pages/login/LoginPage.tsx`

El `idSucursal` ya se captura en estado (`useState<number | null>(null)`).
Por ahora no se usa en ningún flujo posterior. Posibles usos futuros:

- Filtrar el historial de asistencias por sucursal
- Mostrar información de la sucursal en el home
- Validación extra antes de hacer check-in

Si se decide almacenarlo de forma global, agregar al auth store:

```ts
// En auth.store.ts:
interface AuthStore {
  ...
  idSucursalActual: number | null
  setIdSucursalActual: (id: number | null) => void
}

// En la implementación:
setIdSucursalActual: (id) => set({ idSucursalActual: id }),
```

Y en `LoginPage`, llamar `setIdSucursalActual(data.id_sucursal)` después de `setTokens()`.

---

## Resumen de archivos a modificar

| Archivo | Cambio |
|---|---|
| `src/ui/pages/login/LoginPage.tsx` | Guardar `pendingQrToken` en estado; navegar a `/check-in` con state si viene de QR |
| `src/ui/pages/attendance/CheckInPage.tsx` | Leer `autoQrToken` de `location.state` y procesar automáticamente |
| `src/infrastructure/store/auth.store.ts` | (Opcional) Agregar `idSucursalActual` si se necesita globalmente |

---

## Flujo completo esperado

```
1. Dueño imprime QR de la sucursal (desde admin → Mi Sucursal)
2. Cliente llega al gym, abre cámara del celular
3. Escanea QR → navegador abre: http://localhost:5174/login?qr=a3f7b2...
4. LoginPage llama GET /auth/gimnasio/by-qr/a3f7b2...
   → obtiene: nombre_compania, nombre_sucursal, logo_url, id_compania, id_sucursal
5. Login muestra: [logo] GymX Fitness / Sucursal Norte / formulario
6. Cliente ingresa credenciales y hace login
7. App llama POST /auth/app/login → obtiene JWT
8. Con JWT activo, navega a /check-in con { autoQrToken: 'a3f7b2...' }
9. CheckInPage llama POST /asistencias/qr { qr_token: 'a3f7b2...' }
10. Muestra resultado: ✅ fecha, hora, membresía, accesos restantes
```
