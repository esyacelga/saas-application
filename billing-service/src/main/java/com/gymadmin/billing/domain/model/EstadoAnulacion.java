package com.gymadmin.billing.domain.model;

/**
 * Máquina de estados fiscal de una solicitud de anulación de comprobante
 * ({@code facturacion.anulaciones.estado}). Refleja la CHECK constraint del DDL.
 * <p>
 * Transiciones permitidas:
 * <pre>
 *   SOLICITADA ─aprobar──→ APROBADA ─confirmarSri (Flujo A)──→ EJECUTADA
 *        │                     │
 *        │                     └─NC AUTORIZADO (Flujo B)─────→ EJECUTADA
 *        │
 *        └─rechazar─────→ RECHAZADA
 * </pre>
 * En Flujo B (con NC) la anulación queda en {@code APROBADA} hasta que la NC
 * asociada llega a estado {@code AUTORIZADO}. Si la NC devuelve {@code DEVUELTO}
 * o {@code ERROR}, la anulación permanece en {@code APROBADA} y el scheduler de
 * G2 la reintentará; solo transiciona a {@code EJECUTADA} cuando la NC autoriza.
 */
public enum EstadoAnulacion {
    SOLICITADA,
    APROBADA,
    RECHAZADA,
    EJECUTADA
}
