# auth-service-frond-end — Opciones ocultas temporalmente

> **ESTADO:** ✅ Refleja el código actual (2026-07-21). Registro de opciones del panel admin **ocultas a propósito** en la navegación, con cómo restaurarlas. No confundir con features sin implementar (esas viven en [pendientes-backlog.md](pendientes-backlog.md)).

El código de estas opciones **sigue presente y funcional** — solo se retiró su acceso desde el menú. La ruta permanece registrada en el router, por lo que el módulo sigue accesible por URL directa y la reversión es de una línea.

---

## Cuentas App — oculta desde 2026-07-21

- **Qué es:** el ítem "Cuentas App" (`nav.appAccounts`) del sidebar del panel admin, que abría el asistente para registrar/gestionar cuentas de clientes en la app móvil (`/admin/clientes/app`).
- **Motivo:** ocultamiento temporal a pedido.
- **Alcance del cambio:** se comentó **únicamente** la entrada del menú. NO se tocó:
  - La ruta `/admin/clientes/app` (sigue registrada en [`src/ui/router/index.tsx`](../../auth-service-frond-end/src/ui/router/index.tsx)).
  - La página `ClientesAppPage.tsx` ni sus componentes/steps.
  - Las claves i18n `appAccounts.*`.
- **Dónde:** [`src/ui/layouts/AdminLayout.tsx`](../../auth-service-frond-end/src/ui/layouts/AdminLayout.tsx), array `ALL_NAV_ITEMS` — la línea del NavItem está comentada. El import del icono `Smartphone` (lucide-react) se retiró de ese archivo para no dejar un import sin uso.

### Cómo restaurarla

1. En `AdminLayout.tsx`, descomentar la línea del NavItem `Cuentas App` dentro de `ALL_NAV_ITEMS`.
2. Volver a añadir `Smartphone` al import de `lucide-react` en la cabecera del mismo archivo.
3. `npx tsc -b --noEmit` para verificar.

> Como el resto del módulo nunca se removió, no hay nada más que restaurar (ruta, página y traducciones siguen intactas).
