# Métodos de Pago API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `MetodoPagoController`).

Catálogo interno de métodos de pago por compañía (efectivo, transferencia, tarjeta, etc.). Se utiliza al completar ventas de membresías desde el flujo de solicitudes PWA (`CompletarVentaClienteModal`).

Base path: `/api/v1/metodos-pago`
Service: core-service (port 8083)

Tabla origen: `config.metodos_pago` (schema cross-cutting). Sin FK desde `core.membresias.id_metodo_pago`.

---

## Endpoints

### GET /api/v1/metodos-pago
**Auth:** Bearer JWT (`tipo: staff`)
**Permission:** `requireGymStaff()` — cualquier staff de la compañía (incluye Recepción, ya que se usa al completar ventas)
**Description:** Listar métodos de pago activos (`activo = true` AND `eliminado = false`) de la compañía del usuario autenticado. El filtro por `id_compania` se aplica implícitamente desde el JWT — no se acepta como parámetro.

**Response 200:**
```json
[
  {
    "id": 1,
    "nombre": "Efectivo",
    "activo": true
  },
  {
    "id": 2,
    "nombre": "Transferencia",
    "activo": true
  }
]
```

**Response fields:**
- `id` — ID del método de pago
- `nombre` — Nombre visible (e.g., "Efectivo", "Transferencia", "Tarjeta")
- `activo` — Boolean; en este endpoint siempre `true` (solo se listan activos)

Si la compañía no tiene métodos activos, la respuesta es `[]` (no 404).

Campos NO expuestos (uso interno): `id_compania`, `id_sucursal`, `eliminado`, campos de auditoría.

**Errors:**
- `401` — token ausente o inválido (`no_autenticado`)
- `403` — token de otra compañía o `tipo != staff` (`acceso_denegado`)

---

## Reglas de acceso por endpoint

| Endpoint | Método | Rol/Permiso |
|----------|--------|-------------|
| `/metodos-pago` | GET | `requireGymStaff()` |

---

## Consumidores conocidos

- **web-app-owner** — `CompletarVentaClienteModal` (flujo de completar solicitudes de socio PWA, `origen='cliente'`).
