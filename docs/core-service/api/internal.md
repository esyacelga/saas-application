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
