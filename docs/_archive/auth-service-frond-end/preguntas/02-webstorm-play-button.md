# ¿Cómo habilitar el botón Play en WebStorm para correr la aplicación?

> **ESTADO:** 📜 Histórico — nota personal de aprendizaje, no documentación técnica del sistema. Ver [../../STATUS.md](../../STATUS.md).

El botón ▶ de la barra superior está deshabilitado porque WebStorm no tiene ninguna **Run Configuration** creada para este proyecto. Hay que crearla una vez y queda guardada.

---

## Opción A — Desde el package.json (más rápido)

1. Abre `package.json` en el editor.
2. Busca el script `"dev": "vite"`.
3. Haz clic en el ícono ▶ verde que aparece en el margen izquierdo junto a la línea del script.
4. En el menú que aparece selecciona **Run 'dev'**.

WebStorm crea la Run Configuration automáticamente y a partir de ahora el botón ▶ de la barra superior estará activo con esa configuración.

---

## Opción B — Crear la configuración manualmente

1. Menú superior → **Run** → **Edit Configurations…**
2. Clic en **+** (Add New Configuration) → elige **npm**.
3. Rellena los campos:

   | Campo | Valor |
   |-------|-------|
   | Name | `dev` (o el nombre que quieras ver en la barra) |
   | package.json | ruta al `package.json` del proyecto |
   | Command | `run` |
   | Scripts | `dev` |
   | Node interpreter | la versión de Node instalada (detecta automáticamente) |

4. Clic en **OK**.

Ahora en el selector de la barra superior aparece `dev` y el botón ▶ queda habilitado.

---

## Resultado esperado

```
▶ dev   ←  selector con el nombre de la config
▶       ←  botón Play → ejecuta: npm run dev → Vite en http://localhost:5173
■       ←  botón Stop
```

WebStorm abre un panel **Run** en la parte inferior con el output del servidor Vite. El puerto es `5173` según la configuración del proyecto.

---

## Nota

Si el botón ▶ sigue gris después de crear la config, verifica que:
- El **Node interpreter** esté seleccionado (no en blanco).
- El campo **package.json** apunte al archivo correcto.
- Hiciste clic en **Apply** antes de **OK**.

---

# ¿Cómo hacer debug en WebStorm? (ejemplo: entender el flujo de `/login`)

WebStorm tiene un debugger integrado para React/Vite. No necesitas instalar nada extra si usas Chrome o Edge.

---

## Paso 1 — Crear una Run Configuration de tipo JavaScript Debug

1. Menú superior → **Run** → **Edit Configurations…**
2. Clic en **+** → elige **JavaScript Debug**.
3. Rellena:

   | Campo | Valor |
   |-------|-------|
   | Name | `Debug dev` |
   | URL | `http://localhost:5173` |
   | Browser | Chrome (o Edge) |

4. Clic en **OK**.

> Ahora tienes dos configs: `dev` (para correr el servidor) y `Debug dev` (para adjuntar el debugger al navegador).

---

## Paso 2 — Correr el servidor primero

Selecciona la config `dev` en el selector y pulsa ▶.  
Espera a que Vite diga `ready in X ms`.

---

## Paso 3 — Lanzar el debug

Selecciona la config `Debug dev` en el selector y pulsa el ícono **🐛 (Debug)** en lugar del ▶.

WebStorm abre Chrome apuntando a `http://localhost:5173` y conecta el debugger. El panel **Debug** aparece en la parte inferior.

---

## Paso 4 — Poner breakpoints en el flujo de `/login`

Estos son los archivos relevantes para el flujo de login de staff. Abre cada uno y haz clic en el número de línea donde quieres pausar la ejecución:

| Qué quieres inspeccionar | Archivo | Dónde poner el breakpoint |
|--------------------------|---------|---------------------------|
| El formulario manda los datos | `src/ui/features/auth/pages/LoginPage.tsx` | dentro del `onSubmit` del formulario |
| El caso de uso recibe las credenciales | `src/application/use-cases/LoginStaff.ts` | primera línea del método `execute()` |
| La petición HTTP sale al backend | `src/infrastructure/http/auth/axios.instance.ts` | en el interceptor de request |
| La respuesta llega y guarda el token | `src/application/use-cases/LoginStaff.ts` | después del `await` que llama al repositorio |
| El store se actualiza | `src/infrastructure/store/auth/auth.store.ts` | dentro de `setAuth()` o similar |
| El guard deja pasar | `src/ui/router/guards/AuthGuard.tsx` | primera línea del componente |

---

## Paso 5 — Navegar a `/login` y observar

1. En el Chrome abierto por WebStorm, ve a `http://localhost:5173/login`.
2. Rellena el formulario y haz clic en **Iniciar sesión**.
3. WebStorm pausa la ejecución en el primer breakpoint que encuentre.
4. Usa los controles del panel Debug:

```
F8  →  Step Over    (ejecuta la línea actual, salta a la siguiente)
F7  →  Step Into    (entra dentro de la función llamada)
F9  →  Resume       (continúa hasta el siguiente breakpoint)
```

5. En el panel **Variables** ves el estado de todas las variables en ese momento (credenciales, respuesta del backend, token recibido, etc.).

---

## Ejemplo concreto: inspeccionar el token que devuelve el backend

Pon un breakpoint en `LoginStaff.ts` justo después de la llamada al repositorio:

```typescript
// LoginStaff.ts
const result = await this.authRepository.loginStaff(credentials)
// ← breakpoint aquí
```

Cuando WebStorm pause, en el panel **Variables** verás el objeto `result` con el `accessToken` y el `user` que devolvió el backend. Puedes expandir cada propiedad para inspeccionarla sin necesidad de `console.log`.

---

## Flujo visual del debug en `/login`

```
Chrome: usuario llena formulario y hace clic en Iniciar sesión
        │
        ▼
WebStorm pausa en LoginPage.tsx → onSubmit()
  → puedes ver: { email, password } que el usuario escribió
        │
        ▼
WebStorm pausa en LoginStaff.ts → execute()
  → puedes ver: las credenciales antes de ir al backend
        │
        ▼
Petición HTTP sale (Axios)
        │
        ▼
WebStorm pausa en LoginStaff.ts → línea después del await
  → puedes ver: accessToken, user devueltos por el backend
        │
        ▼
WebStorm pausa en auth.store.ts → setAuth()
  → puedes ver: cómo queda el store actualizado
        │
        ▼
AuthGuard evalúa → redirige a /admin/dashboard
```

---

## Atajo rápido sin crear config de Debug

Si ya tienes Chrome abierto manualmente en `http://localhost:5173`, puedes usar:

**Run → Attach to Process…** → selecciona el proceso de Chrome.

WebStorm se adjunta al navegador existente y los breakpoints funcionan igual.
