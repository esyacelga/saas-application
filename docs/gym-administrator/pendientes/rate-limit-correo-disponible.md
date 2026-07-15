# Pendiente — Rate limiting del endpoint público `/correo-disponible`

> **Estado:** 📋 **Pendiente de implementación** (endpoint funcional, sin mitigación).
> **Fecha:** 2026-07-14.
> **Área:** platform-service / seguridad (auto-registro público).
> **Prioridad:** media — el riesgo real es bajo por diseño (solo `onBlur`), pero es un
> endpoint público sin candado.

## Contexto

Para validar el correo automáticamente en el registro público (paso "Tus datos"), se
agregó un endpoint público:

```
GET /api/v1/companias/correo-disponible?correo=...
→ { disponible: boolean, existe: boolean }
```

Declarado público en `SecurityConfig` (`permitAll()`), lo consume el frontend `onBlur`
del campo correo en `Step4DatosPropios.tsx` para mostrar "Correo disponible" /
"Este correo ya está registrado".

## El riesgo — enumeración de correos

Al ser público y responder si un correo **existe** en el sistema, cualquiera puede
sondear la base para descubrir qué correos están registrados (enumeración de cuentas).

**Mitigaciones ya presentes (reducen, no eliminan):**

- La respuesta es un DTO mínimo (`CorreoDisponibleResponse`) con solo dos booleanos —
  no filtra nombre, gimnasio ni ningún otro dato de la cuenta.
- La verificación en el front es solo `onBlur` (una llamada al perder foco), no en cada
  tecla — no hay debounce por diseño, así que el volumen legítimo es muy bajo.

## Lo que falta (implementación)

Agregar **rate limiting por IP** al endpoint, reutilizando el patrón de rate-limit que ya
existe para el registro (`/auto-registro`):

1. Localizar el filtro/interceptor de rate-limit del auto-registro en platform-service y
   aplicar la misma política (o una tabla de límites por ruta) a `/correo-disponible`.
2. Límite sugerido: más permisivo que el de registro (el usuario legítimo puede reintentar
   varios correos), pero con techo por IP y ventana corta (p. ej. N/min) que corte el
   sondeo masivo. Ajustar N tras medir el uso real.
3. Responder `429` cuando se exceda (el front ya mapea `429` → "Demasiados intentos…").

### Notas de implementación

- **No cambiar la forma de la respuesta** — el front ya depende de `{ disponible, existe }`.
- **Considerar** que la misma cuenta abre el mismo riesgo desde `/auto-registro` (que sí
  tiene rate limit) vía el 409 de correo; alinear ambos límites para no dejar un bypass.
- **Alternativa/complemento** (opcional, no bloqueante): respuesta con retardo constante o
  no distinguir "existe" de "no existe" — descartado por ahora porque rompería el objetivo
  UX (avisar al usuario legítimo que su correo ya está en uso).

## Origen

Se introdujo junto con la validación automática de correo en el registro público
(cambio del 2026-07-14). El autor del cambio se comprometió a agregar esta mitigación
y la dejó documentada aquí en vez de bloquear la entrega funcional.

## Relacionado

- Población de `ci_validada` (mismo paquete de cambios):
  [validacion-cedula-persona.md](validacion-cedula-persona.md).
- Endpoint y cadena `correoEnUso`: `platform-service` →
  `CompaniaController.correoDisponible` / `CompaniaService.correoEnUso`.
