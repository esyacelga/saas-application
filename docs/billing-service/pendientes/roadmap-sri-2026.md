# Roadmap billing-service — Fases de implementación SRI 2025-2026

> **ESTADO:** 📋 **División en fases — pendiente de arrancar desarrollo**
> **Fecha:** 2026-07-11
> **Base:** [gap-analysis-sri-2026.md](gap-analysis-sri-2026.md) (detalle de los 13 GAPs) y [anulacion-sri.md](anulacion-sri.md) (diseño de G3).
> **Decisión de producto:** primero corregir los GAPs de backend; el rediseño de pantallas va después.

Cuando arranquemos el desarrollo de una fase concreta, se abre la checklist detallada por GAP en el gap-analysis (o en el doc específico si existe).

---

## Mapa de dependencias

```
Fase 0 (Fundamentos)              Fase 1 (Pipeline SRI)
  G5 Secuencial atómico            G2 Transmisión inmediata
  G6 Catálogos consultables ──┐    G1 XML v2.2.0 ◄──── G6
                              │           │
                              ▼           ▼
                        Fase 2 (Ciclo de vida)
                          G4 Notas de crédito
                          G3 Anulación fiscal ◄── G4
                                    │
                                    ▼
                        Fase 3 (Cumplimiento)
                          G10 Bancarización ◄── G6
                          G9 ATS completo ◄── G4
                                    │
                                    ▼
                        Fase 4 (Complementarios opcionales)
                          G7 Notas de débito
                          G8 Retenciones
                          G13 Guías de remisión
                                    │
                                    ▼
                        Fase 5 (Frontend)
                          Rediseño auth-service-frond-end
                                    │
                                    ▼
                        Fase 6 (Diferibles / externos)
                          G11 QR en RIDE
                          G12 Sync finanzas.ingresos
```

---

## Fase 0 · Fundamentos

**Objetivo:** eliminar bugs latentes y quitar hardcodes **antes** de agregar código nuevo encima.
**GAPs:** G5 (secuencial atómico), G6 (catálogos SRI consultables).
**Dependencias:** ninguna. Los dos GAPs son paralelizables (dos personas).
**Habilita:** todas las fases posteriores.

---

## Fase 1 · Pipeline SRI moderno

**Objetivo:** cumplir con la transmisión inmediata desde 2026-01-01 y modernizar la versión del XML antes de emitir nuevos tipos de comprobante.
**GAPs:** G2 (transmisión inmediata), G1 (XML v2.1.0 → v2.2.0).
**Dependencias:** G1 depende de G6 (Fase 0) para catálogos consistentes.
**Habilita:** que NC, ND y retención hereden el pipeline correcto en las fases siguientes.

---

## Fase 2 · Ciclo de vida del comprobante

**Objetivo:** cerrar el ciclo emisión → corrección → anulación. Condición legal para operar con RUC real.
**GAPs:** G4 (notas de crédito tipo 04), G3 (anulación fiscal).
**Dependencias:** G3 depende de G4 (la anulación fuera del día 7 se hace vía NC). G4 depende de Fase 1.
**Habilita:** Fase 3 (ATS completo requiere NC).

---

## Fase 3 · Cumplimiento tributario

**Objetivo:** cerrar validaciones de negocio y reportes que auditará el SRI en cruces automáticos.
**GAPs:** G10 (bancarización sobre USD 500), G9 (ATS completo).
**Dependencias:** G10 depende de G6 (Fase 0). G9 depende de G4 (Fase 2).
**Habilita:** contrato REST estable para arrancar Fase 5.

---

## Fase 4 · Documentos complementarios opcionales

**Objetivo:** habilitar tipos de comprobante que no todo gym necesita. Cada GAP se implementa **solo si aplica al modelo de negocio**.
**GAPs:** G7 (notas de débito — si cobran mora), G8 (retenciones — si son agente de retención), G13 (guías de remisión — si mueven mercancía física).
**Dependencias:** Fase 1.
**Habilita:** completar G9 (retenciones en ATS) cuando aplique.

---

## Fase 5 · Rediseño del frontend

**Objetivo:** rediseñar las pantallas de facturación en `auth-service-frond-end` con el nuevo contrato REST estable.
**Alcance:** modificar pantallas existentes (emisión, anulación, listado) y crear nuevas (NC, y opcionales ND/retención/guías si Fase 4 se hizo).
**Dependencias:** Fase 3 completa (contrato REST congelado); opcionalmente Fase 4.
**Prerrequisito:** invocar `ui-ux-designer` para especificar los flujos antes de que `frontend-developer` implemente.

---

## Fase 6 · Diferibles y dependencias externas

**Objetivo:** cerrar UX y sincronización cross-service cuando el resto del sistema los requiera.
**GAPs:** G11 (código QR en RIDE), G12 (sync `facturacion.comprobantes` → `finanzas.ingresos` — bloqueado por la existencia de finance-service).

---

## Estado global

| GAP | Fase | Estado | Detalle |
|-----|:----:|--------|---------|
| G5 Secuencial atómico | 0 | ✅ Completado | [gap-analysis §G5](gap-analysis-sri-2026.md#g5--secuencial-no-se-reserva-atómicamente) |
| G6 Catálogos SRI | 0 | ✅ Completado | [gap-analysis §G6](gap-analysis-sri-2026.md#g6--catálogos-sri-no-consultables-desde-código) |
| G2 Transmisión inmediata | 1 | 📋 Pendiente | [gap-analysis §G2](gap-analysis-sri-2026.md#g2--transmisión-inmediata-obligatoria-desde-2026-01-01) |
| G1 XML v2.1.0 → v2.2.0 | 1 | 📋 Pendiente | [gap-analysis §G1](gap-analysis-sri-2026.md#g1--versión-de-la-ficha-técnica-xml-v210-vs-v232) |
| G4 Notas de crédito | 2 | 📋 Pendiente | [gap-analysis §G4](gap-analysis-sri-2026.md#g4--notas-de-crédito-tipo-04) |
| G3 Anulación fiscal | 2 | 📋 Diseño listo | [anulacion-sri.md](anulacion-sri.md) |
| G10 Bancarización USD 500 | 3 | 📋 Pendiente | [gap-analysis §G10](gap-analysis-sri-2026.md#g10--sin-validación-de-bancarización-sobre-usd-500) |
| G9 ATS completo | 3 | 📋 Pendiente | [gap-analysis §G9](gap-analysis-sri-2026.md#g9--ats-mensual-solo-incluye-tipo-01) |
| G7 Notas de débito | 4 | 📋 Opcional | [gap-analysis §G7](gap-analysis-sri-2026.md#g7--notas-de-débito-tipo-05) |
| G8 Retenciones | 4 | 📋 Opcional | [gap-analysis §G8](gap-analysis-sri-2026.md#g8--retenciones-tipo-07) |
| G13 Guías de remisión | 4 | 📋 Opcional | [gap-analysis §G13](gap-analysis-sri-2026.md#g13--guías-de-remisión-tipo-06) |
| Rediseño de pantallas | 5 | 📋 Post-backend | Spec por `ui-ux-designer` |
| G11 QR en RIDE | 6 | 📋 Diferido | [gap-analysis §G11](gap-analysis-sri-2026.md#g11--ride-pdf-sin-código-qr) |
| G12 Sync finanzas.ingresos | 6 | 📋 Externa | [gap-analysis §G12](gap-analysis-sri-2026.md#g12--sincronización-con-finanzasingresos-no-existe) |
