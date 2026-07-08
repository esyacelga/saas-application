# Backend Prompt — Endpoint: Sucursales por Compañía

> **ESTADO:** 📜 Histórico — prompt usado para pedir un cambio al backend. NO es documentación de estado actual. Ver [../../STATUS.md](../../STATUS.md).

## Contexto

El frontend del panel de plataforma necesita, al momento de crear un rol de plataforma
(`POST /platform/roles`), enviar también el campo `id_sucursal`. Para eso, antes de
crear el rol, el modal consulta las sucursales de la compañía seleccionada. Si la
compañía no tiene sucursales configuradas, el frontend usa `id_sucursal = 1` como
valor por defecto y no muestra el dropdown.

El campo `id_sucursal` ya es requerido por el backend al crear un rol (de ahí el error
actual). Este endpoint cierra ese gap en el formulario de creación.

---

## Endpoint a implementar

```
GET /api/v1/platform/companias/{idCompania}/sucursales
```

### Seguridad
- Requiere token JWT de operador de plataforma (mismo mecanismo que los demás endpoints
  bajo `/platform/...`).
- Cualquier rol de plataforma (`super_admin`, `soporte`, `viewer`) puede consultar.

### Path variable

| Nombre       | Tipo    | Descripción               |
|--------------|---------|---------------------------|
| `idCompania` | `Long`  | ID de la compañía         |

### Respuesta exitosa — `200 OK`

Retorna un array (puede ser vacío `[]` si la compañía no tiene sucursales).

```json
[
  { "id": 1, "nombre": "Casa Matriz" },
  { "id": 2, "nombre": "Sucursal Norte" },
  { "id": 3, "nombre": "Sucursal Sur" }
]
```

Cada objeto tiene exactamente dos campos:

| Campo    | Tipo     | Descripción              |
|----------|----------|--------------------------|
| `id`     | `Long`   | ID de la sucursal        |
| `nombre` | `String` | Nombre visible al usuario|

### Respuesta cuando la compañía no existe — `404 Not Found`

```json
{ "error": "Compañía no encontrada" }
```

### Respuesta vacía (compañía sin sucursales) — `200 OK`

```json
[]
```

> **No** retornar 404 si la compañía existe pero no tiene sucursales. Retornar lista
> vacía para que el frontend aplique el valor por defecto `id_sucursal = 1`.

---

## Contrato del cuerpo al crear un rol (referencia)

El frontend ya envía `id_sucursal` en el body de `POST /platform/roles`:

```json
{
  "nombre": "Administrador de clientes",
  "descripcion": "Gestiona la cartera de clientes",
  "id_compania": 4,
  "id_sucursal": 2
}
```

---

## Sugerencia de implementación en Java (Spring Boot)

```java
// Controller
@GetMapping("/platform/companias/{idCompania}/sucursales")
public ResponseEntity<List<SucursalBasicaDto>> getSucursalesByCompania(
    @PathVariable Long idCompania
) {
    List<SucursalBasicaDto> sucursales = companiaService.getSucursalesByCompania(idCompania);
    return ResponseEntity.ok(sucursales);
}

// DTO de respuesta
public record SucursalBasicaDto(Long id, String nombre) {}

// Service — ejemplo con JPA
public List<SucursalBasicaDto> getSucursalesByCompania(Long idCompania) {
    if (!companiaRepository.existsById(idCompania)) {
        throw new EntityNotFoundException("Compañía no encontrada");
    }
    return sucursalRepository.findByCompaniaId(idCompania)
        .stream()
        .map(s -> new SucursalBasicaDto(s.getId(), s.getNombre()))
        .toList();
}
```

---

## Comportamiento esperado en el frontend

| Escenario                          | Comportamiento UI                                      |
|------------------------------------|--------------------------------------------------------|
| Compañía seleccionada con sucursales | Aparece dropdown de sucursal (obligatorio)           |
| Compañía seleccionada sin sucursales | No se muestra dropdown; se envía `id_sucursal = 1`   |
| Error al cargar sucursales         | Se silencia, se usa `id_sucursal = 1` por defecto     |
| Se cambia la compañía seleccionada | Se resetea la sucursal y se vuelve a consultar        |
