# Internal API — core-service

> **ESTADO:** ✅ Refleja el código actual (verificado contra `InternalCoreController`).

Endpoints privados consumidos exclusivamente por otros servicios internos (platform-service). **NO son públicos** — protegidos con secreto compartido.

Base path: `/internal/v1`  
Service: core-service (port 8083)  
Autenticación: Header `X-Internal-Call` (not OAuth2)

---

## Endpoints

### GET /internal/v1/companias/{id}/clientes-activos/count
**Auth:** Header `X-Internal-Call: {INTERNAL_SECRET}`  
**Description:** Contar clientes activos de una compañía. Usado por platform-service para verificar estado de paquetes/planes y validar límites por tenant.

**Path param:**
- `id` — Company ID in `platform.companias`

**Header:**
- `X-Internal-Call` (required) — Secret value matching `${INTERNAL_SECRET}` env variable (default: `platform-secret-dev`)

**Response 200:**
```json
{
  "count": 42
}
```

**Response fields:**
- `count` — Number of active clients

**Definición de "activo":**
Estados considerados activos: `activo`, `proximo_vencer`, `congelado`, `riesgo_abandono`  
Estados excluidos: `vencido`  
Además: `eliminado = false` (soft-deletes no cuentan)

**Errors:**
- `400` — invalid company ID
- `403` — missing or incorrect `X-Internal-Call` header (invalid secret)
- `404` — company not found (returns empty count or error based on implementation)

---

### GET /internal/v1/companias/{id}/clientes-por-vencer
**Auth:** Header `X-Internal-Call: {INTERNAL_SECRET}`
**Description:** Lista los socios de una compañía cuya **membresía activa está por vencer**. Consumido por **attendance-service** (REQ-SAAS-001, Fase 4) para enviar avisos por WhatsApp. Evita duplicar en attendance la detección de vencimiento (que ya vive en `ClienteStatusJobService`) y el JOIN a `identidad.personas`.

**Path param:**
- `id` — Company ID

**Query params:**
- `dias` (opcional, default `3`) — umbral de anticipación, rango `[0, 30]`. En modo `calendario` son los días al `fecha_fin`; en modo `accesos` son las entradas restantes.
- `modo` (opcional, default `todos`) — `calendario` | `accesos` | `todos`.

**Header:**
- `X-Internal-Call` (required) — secreto que coincide con `${INTERNAL_SECRET}` (default `platform-secret-dev`).

**Response 200:**
```json
{
  "compania_id": 1,
  "fecha_corte": "2026-07-14",
  "clientes": [
    {
      "id_cliente": 10,
      "id_persona": 55,
      "id_sucursal": 1,
      "nombre": "Ana Calendario",
      "telefono": "0987654321",
      "correo": "ana@example.com",
      "modo_control": "calendario",
      "fecha_fin": "2026-07-17",
      "dias_para_vencer": 3,
      "accesos_restantes": null,
      "estado_cliente": "proximo_vencer",
      "acepta_whatsapp": true,
      "fecha_consentimiento_wa": "2026-07-01T10:00:00-05:00"
    }
  ]
}
```

**Response fields:**
- `compania_id` — id de la compañía consultada.
- `fecha_corte` — "hoy" de negocio resuelto en zona **America/Guayaquil** (issue C4). **No** se delega al cliente para evitar desfases de día.
- `clientes[]`:
  - `telefono` — va **sin normalizar** a E.164; normalizar es responsabilidad de attendance (no acopla core al formato internacional).
  - `dias_para_vencer` — días entre `fecha_corte` y `fecha_fin` (viaja en ambos modos).
  - `accesos_restantes` — solo en modo `accesos` (`GREATEST(0, dias_acceso_total - asistencias)`); `null` en calendario.
  - `estado_cliente` — para que el consumidor pueda saltar `congelado` (RN-05), aunque la query ya lo excluye.
  - `acepta_whatsapp` / `fecha_consentimiento_wa` — opt-in de WhatsApp (evita un segundo JOIN a `personas` en attendance).

**Reglas:**
- Excluye clientes en estado `congelado` (RN-05) y `vencido`.
- `calendario`: incluye membresías calendario con `fecha_fin - fecha_corte` en `[0, dias]`.
- `accesos`: incluye membresías por accesos con `accesos_restantes <= dias`.
- `todos`: aplica el umbral correspondiente a cada modo.

**Errors:**
- `400` — `dias` fuera de rango `[0, 30]` o `modo` inválido.
- `403` — header `X-Internal-Call` ausente o inválido.

---

## Seguridad

- **No autenticación JWT** — usa header secreto compartido (`X-Internal-Call`)
- **No se expone en Swagger/OpenAPI** — anotada con `@Hidden`
- **Solo platform-service puede llamar** — verificar IP/firewall además del header
- **Secreto en variable de entorno:** `INTERNAL_SECRET` o fallback a `platform-secret-dev`

---

## Códigos de error comunes

| Código | Significado |
|--------|-------------|
| 400 | Parámetros inválidos |
| 403 | Header `X-Internal-Call` inválido o faltante |
| 404 | Compañía no encontrada |
