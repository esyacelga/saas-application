# Restructuración de onboarding y facturación — documento paraguas

> **ESTADO:** 📋 **Planeado — sin implementar.** Índice que ata tres piezas de diseño relacionadas.
> **Fecha:** 2026-07-14
> **Origen:** revisión de UX del onboarding motivada por la construcción del módulo de facturación electrónica SRI.

---

## 1. El principio que une todo: disclosure progresivo

**No pidas un dato hasta que haya una razón concreta para pedirlo.**

Al diseñar el módulo de facturación aparecieron, en cadena, varias pantallas que pedían datos en el momento equivocado — sobre todo **datos tributarios y de contacto exigidos en el registro**, cuando el usuario solo quería probar la app. Corregir eso es un mismo principio de producto aplicado en tres lugares distintos.

El resultado que buscamos:

| Momento del usuario | Qué se le pide | Qué NO se le pide todavía |
|---|---|---|
| *Registrarse / probar* | Nombre del gym, su cuenta (nombre, cédula, correo, contraseña), plan | RUC, teléfono, WhatsApp, certificados |
| *Activar facturación* | RUC, razón social, certificado `.p12`, punto de emisión | — |
| *Activar WhatsApp* (futuro) | Número de WhatsApp | — |
| *Completar perfil* | Teléfono de contacto | — |

---

## 2. Las tres piezas

Ninguna abre trabajo de documentación nuevo — las tres ya están especificadas. Este paraguas las conecta y ordena.

### Pieza 1 · Adelgazar el registro de gimnasios
📄 [../../auth-service-frond-end/registro-quitar-ruc.md](../auth-service-frond-end/registro-quitar-ruc.md)

- **Qué es:** rediseño puntual — quitar del **Paso 1** del registro el RUC, el teléfono y el WhatsApp. Los pasos 2 (sucursal), 3 (plan) y 4 (tu cuenta) **no se tocan**.
- **Naturaleza:** cirugía en un solo paso de un wizard que ya funciona bien. **Riesgo bajo.**
- **Backend:** ninguno nuevo — solo la migración compartida (§3).

### Pieza 2 · Wizard de configuración de facturación
📄 [../../billing-service/pendientes/wizard-configuracion-sri.md](../../billing-service/pendientes/wizard-configuracion-sri.md)

- **Qué es:** construir desde cero el onboarding de facturación (4 pasos: datos fiscales, certificado, punto de emisión, prueba real contra el SRI). Es donde reaparece el RUC que sacamos del registro.
- **Naturaleza:** funcionalidad nueva completa. **Riesgo alto** — incluye la decisión de cifrado del `.p12` y la prueba real contra el SRI.
- **Backend:** 7 endpoints nuevos (hoy `AdminController` solo lee) + la migración compartida.

### Pieza 3 · Módulo de facturación (pantallas de emisión)
📄 [../../auth-service-frond-end/facturacion-diseno.md](../../auth-service-frond-end/facturacion-diseno.md)

- **Qué es:** las 6 pantallas de emisión/anulación/reportes. **Prerrequisito: la Pieza 2** — sin configuración, `POST /facturas` responde `404`.
- **Naturaleza:** funcionalidad nueva completa. **Riesgo medio.**
- **Backend:** ya existe (23 endpoints). Faltan los permisos `facturacion:*` en la BD.

### No confundir su naturaleza

> ⚠️ Las tres comparten principio y migración, pero **no son el mismo tipo de trabajo.** La Pieza 1 es quitar tres campos de un formulario que anda. Las Piezas 2 y 3 son construir features nuevas con backend. Quien planifique el esfuerzo no debe tratarlas por igual.

---

## 3. La migración compartida (⚠️ aplicar UNA sola vez)

Las Piezas 1 y 2 tocan **la misma tabla**, `tenant.companias`. **Deben ir en una sola story Liquibase** para no aplicar dos migraciones separadas a la Neon:

```sql
ALTER TABLE tenant.companias
    -- Pieza 2 (wizard facturación): columnas fiscales muertas, nadie las lee
    DROP COLUMN IF EXISTS nombre_comercial,
    DROP COLUMN IF EXISTS dir_matriz,
    DROP COLUMN IF EXISTS obligado_contabilidad,
    DROP COLUMN IF EXISTS contribuyente_especial,
    -- Pieza 1 (adelgazar registro): el RUC deja de ser obligatorio
    ALTER COLUMN ruc DROP NOT NULL;
```

- **`ruc` se conserva** (solo nullable) — identidad del tenant. El `UNIQUE` **no se toca**: en Postgres permite varios NULL (varios gyms sin RUC) e impide dos RUC reales iguales.
- **`telefono` y `whatsapp` NO se tocan** — ya son nullable; solo se dejan de enviar desde el registro.
- ⚠️ **Antes de aplicar:** reconfirmar que nadie lee las 4 columnas fiscales (verificado 2026-07-14, pero el código pudo cambiar) y coordinar con el equipo — `gradle.properties` apunta a una Neon en la nube, posiblemente compartida.

---

## 4. Orden de implementación

```
Transversal (bloquea todo lo de facturación):
  · Permisos facturacion:* en BD
  · Decisión de cifrado del .p12          ← no se puede improvisar después
  · Migración compartida §3 (Piezas 1+2)

Pieza 1 (registro)  ──independiente──►  puede hacerse ya, solo depende de la migración
                                        (bajo riesgo, buena primera entrega)

Pieza 2 (wizard facturación)  ──►  Pieza 3 (módulo emisión)
   depende de: permisos + cifrado + endpoints nuevos + migración
```

**Sugerencia:** la Pieza 1 es la entrega más rápida y de menor riesgo — buen punto de arranque una vez decidida la migración. Las Piezas 2 y 3 son el grueso y van en orden (el módulo no sirve sin el wizard).

---

## 5. Replicable a otros rubros

Todo esto es genérico de un SaaS ecuatoriano, no de gimnasios:
- El registro pide solo lo mínimo para probar; lo fiscal y lo de contacto llegan cuando se necesitan.
- La facturación (RUC, certificado, config SRI) es un onboarding aparte, opcional, porque no todo negocio factura desde el día uno.
- La frontera `src/lib/sri/` (validaciones, catálogos, cálculos — sin React ni axios) se extrae tal cual a un SaaS de mecánicas, peluquerías, etc. Ver [facturacion-diseno.md §14](../../auth-service-frond-end/facturacion-diseno.md#14-sección-clave-qué-es-replicable-a-otros-rubros).

---

## 6. Pendientes registrados (fuera del alcance actual)

- **Activación de notificaciones por WhatsApp:** cuando se implemente el canal `CANAL_WHATSAPP` (hoy declarado sin emisor), su pantalla de activación **debe pedir el número y hacerlo obligatorio** — el otro lado del disclosure progresivo. Detalle en [registro-quitar-ruc.md §7](../auth-service-frond-end/registro-quitar-ruc.md#7-pendiente-relacionado-no-es-parte-de-este-cambio).
