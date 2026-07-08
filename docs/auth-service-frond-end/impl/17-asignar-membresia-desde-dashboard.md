# IMPL_17 — Asignar membresía desde el panel "Sin suscripción" del Dashboard

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../STATUS.md).

> **Estado:** Implementado v1.0  
> **Última actualización:** 2026-06-20

---

## Objetivo

Permitir que el staff asigne una membresía a un cliente directamente desde el panel lateral "Sin suscripción" del Dashboard, sin necesidad de navegar a la página de clientes.

---

## Contexto

El Dashboard del gimnasio muestra una KPI card (`AlertKpiCard`) con el total de clientes que no tienen membresía activa. Al hacer clic abre un drawer (`SinSuscripcionPanel`) con la lista de esos clientes. Anteriormente, cada fila tenía un enlace "Ver →" que llevaba a `/admin/clientes/{id}`. El requerimiento es reemplazar ese enlace por un botón "Asignar" que abre el modal `VenderMembresiaModal` directamente en el panel.

---

## Componentes involucrados

| Archivo | Rol |
|---|---|
| `src/ui/features/admin/pages/DashboardPage.tsx` | Contiene `SinSuscripcionPanel` y `DashboardPage` — toda la lógica vive aquí |
| `src/ui/features/core/components/VenderMembresiaModal.tsx` | Modal reutilizable para vender/asignar membresía — no se modifica |
| `src/infrastructure/http/core/CoreRepository.ts` | `venderMembresia` ya existe — no se modifica |
| `src/i18n/locales/es.json` / `en.json` | Nueva clave `dashboard.asignarMembresia` |

---

## Flujo de usuario

```
Panel "Sin suscripción" abierto
  └─ Usuario ve fila: [Avatar] [Nombre / CI]  [Asignar]
       └─ Clic en "Asignar"
            └─ Abre VenderMembresiaModal con idCliente y nombreCliente
                 ├─ Usuario selecciona tipo, fecha, descuento → "Confirmar"
                 │    ├─ POST /clientes/{id}/membresias  ✓
                 │    ├─ toast.success
                 │    ├─ Cliente se elimina de la lista del panel
                 │    └─ KPI sinSuscripcionTotal - 1  (vía callback onVendida de DashboardPage)
                 └─ Usuario cancela → modal cierra, panel queda abierto
```

---

## Cambios implementados

### 1. `SinSuscripcionPanel`

- Se agrega estado local `modalCliente: { id: number; nombre: string } | null` para controlar qué cliente tiene el modal abierto.
- El enlace `<Link to="/admin/clientes/{id}">Ver →</Link>` se reemplaza por un `<button>Asignar</button>` con el mismo estilo ámbar.
- Se monta `<VenderMembresiaModal>` dentro del panel, condicionado a `modalCliente !== null`.
- En `onVendida` del modal:
  1. Se cierra el modal (`setModalCliente(null)`).
  2. Se elimina el cliente del array local (`setClientes(prev => prev.filter(c => c.id !== id))`).
  3. Se llama `props.onVendida(id)` para que `DashboardPage` decremente el KPI.

### 2. `DashboardPage`

- `handleVendida` ya existía con la lógica de decrementar `sinSuscripcionTotal` — se conecta correctamente pasándolo a `SinSuscripcionPanel`.
- La prop `onVendida` de `SinSuscripcionPanel` ahora recibe `(idCliente: number) => void`.

### 3. i18n

Nueva clave agregada en `dashboard`:

```json
"asignarMembresia": "Asignar"   // es.json
"asignarMembresia": "Assign"    // en.json
```

---

## Decisiones de diseño

- **No se navega fuera del panel**: la experiencia permanece en el drawer para que el staff pueda asignar varias membresías seguidas sin perder el contexto del dashboard.
- **El modal se monta dentro del drawer**: técnicamente el `Dialog` de shadcn usa un portal que escapa al DOM del drawer, por lo que no hay problema de z-index ni de scroll.
- **El cliente desaparece de la lista inmediatamente** al confirmar, sin reload, lo que da sensación de respuesta rápida.
- **El enlace "Ver →" se elimina**: el botón "Asignar" es la única acción inline. Para ver el perfil completo el staff puede navegar desde la página de clientes.

---

## Permiso requerido

El modal `VenderMembresiaModal` llama a `POST /clientes/{id}/membresias`. El backend requiere token `staff` con permiso `membresias:crear` (o equivalente configurado en el rol). El frontend no bloquea el botón por permiso — si el usuario no tiene acceso el backend devolverá 403 y el modal mostrará el toast de error genérico.

---

## Sin cambios en backend

No se requiere ningún cambio en `core-service`. El endpoint `POST /clientes/{idCliente}/membresias` ya existe y es el mismo que usa `ClientesPage`.
