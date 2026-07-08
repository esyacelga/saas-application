# BITÁCORA API

Base URL: `/api/v1/bitacora`

La bitácora registra automáticamente **toda operación de escritura** (POST, PUT, PATCH, DELETE) ejecutada en el sistema. Es de solo lectura — no es posible crear ni modificar entradas manualmente.

Los campos de response usan **camelCase**.

---

## GET /bitacora

Consulta el log de auditoría de la compañía del token, con filtros opcionales y paginación.

El filtrado por `idCompania` es automático a partir del JWT — nunca se puede consultar la bitácora de otra compañía.

**Seguridad:** `tipo=staff` + permiso `usuarios:leer`.

### Request

```http
GET /api/v1/bitacora?modulo=seguridad&desde=2026-05-01T00:00:00Z&hasta=2026-05-31T23:59:59Z&idUsuario=42&pagina=1&limit=50
Authorization: Bearer {accessToken}
```

| Param (query) | Tipo | Requerido | Descripción |
|---|---|---|---|
| `modulo` | string | No | Filtra por módulo: `seguridad`, `usuarios`, `clientes`, etc. |
| `desde` | string (ISO-8601) | No | Fecha y hora de inicio del rango (ej. `2026-05-01T00:00:00Z`) |
| `hasta` | string (ISO-8601) | No | Fecha y hora de fin del rango |
| `idUsuario` | number | No | Filtra por ID del usuario staff que realizó la acción |
| `pagina` | number | No | Página 1-based (default: `1`) |
| `limit` | number | No | Registros por página (default: `50`) |

### Response 200

```json
{
  "total": 150,
  "pagina": 1,
  "datos": [
    {
      "id": 1001,
      "idUsuario": 42,
      "nombreUsuario": "Juan Pérez",
      "modulo": "seguridad",
      "accion": "crear_usuario",
      "entidadId": 45,
      "ip": "192.168.1.10",
      "fecha": "2026-05-18T14:32:00Z"
    },
    {
      "id": 1002,
      "idUsuario": 42,
      "nombreUsuario": "Juan Pérez",
      "modulo": "seguridad",
      "accion": "actualizar_rol",
      "entidadId": 2,
      "ip": "192.168.1.10",
      "fecha": "2026-05-18T14:45:00Z"
    }
  ]
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `total` | number | Total de registros que coinciden con los filtros |
| `pagina` | number | Página actual |
| `datos` | array | Registros de la página solicitada |
| `datos[].id` | number | ID del registro de bitácora |
| `datos[].idUsuario` | number | ID del usuario staff que ejecutó la acción |
| `datos[].nombreUsuario` | string | Nombre del usuario staff |
| `datos[].modulo` | string | Módulo donde se realizó la acción |
| `datos[].accion` | string | Nombre de la acción ejecutada (ej. `crear_usuario`, `login_exitoso`) |
| `datos[].entidadId` | number \| null | ID del registro afectado por la acción |
| `datos[].ip` | string \| null | Dirección IP del cliente |
| `datos[].fecha` | string (ISO-8601) | Timestamp exacto de la acción |

### Acciones registradas

Algunos valores comunes del campo `accion`:

| Acción | Cuándo se registra |
|---|---|
| `login_exitoso` | Login correcto de staff |
| `login_fallido` | Intento de login con credenciales incorrectas |
| `crear_usuario` | `POST /usuarios` exitoso |
| `actualizar_usuario` | `PATCH /usuarios/{id}` exitoso |
| `activar_usuario` | `PUT /usuarios/{id}/activar` exitoso |
| `desactivar_usuario` | `PUT /usuarios/{id}/desactivar` exitoso |
| `crear_rol` | `POST /roles` exitoso |
| `actualizar_permisos_rol` | `PUT /roles/{id}/permisos` exitoso |
| `eliminar_rol` | `DELETE /roles/{id}` exitoso |
| `crear_usuario_app` | `POST /app-usuarios` exitoso |

### Errores

| Código | Cuándo |
|---|---|
| 401 | Token ausente o inválido |
| 403 | El token no es de tipo `staff` o le falta el permiso `usuarios:leer` |
